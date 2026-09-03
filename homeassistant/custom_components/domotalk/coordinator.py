"""Owns one persistent domotalk hub connection per config entry.

`iot_class: local_push` — the hub streams unsolicited state-change messages
over the same WebSocket (protocol notes §8), so entities update instantly
instead of being polled. The exact push envelope is documented as
UNCONFIRMED, and the original vendor app's own ViewModels are observed to
treat any push as "something changed, re-fetch the subject" rather than
parsing a delta out of it (§8, last paragraph) — this coordinator mirrors
that: a burst of pushes is debounced into one re-read of the device/ambience
lists, and only entities whose state actually changed are notified.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import timedelta
from typing import Any

import aiohttp
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_time_interval

from .client import ConnectFailed, DomotalkClient, DomotalkError
from .const import (
    ACTION_AMBIENCE_RUN,
    ACTION_AMBIENCE_STOP,
    ACTION_BINARY_OFF,
    ACTION_BINARY_ON,
    ACTION_DIMMER_SET_PERCENTAGE,
    ACTION_PULSE_FIRE,
    ACTION_SHUTTER_CLOSE,
    ACTION_SHUTTER_OPEN,
    ACTION_SHUTTER_SET_PERCENTAGE,
    DOMAIN,
    PUSH_REFRESH_DEBOUNCE_SECONDS,
    RECONNECT_CHECK_INTERVAL_SECONDS,
    SUBJECT_AMBIENCE,
    SUBJECT_BINARY_OUT,
    SUBJECT_DEVICE_IN,
    SUBJECT_DEVICE_OUT,
    SUBJECT_DIMMER,
    SUBJECT_PULSE,
    SUBJECT_ROOM,
    SUBJECT_SHUTTER,
    VERB_ACTION,
    VERB_READ,
)
from .devices import (
    BinaryOutput,
    Device,
    Dimmer,
    Pulse,
    Shutter,
    build_action_options,
    classify_device_in,
    classify_device_out,
)

_LOGGER = logging.getLogger(__name__)


def signal_for(entry_id: str, kind: str, device_id: int) -> str:
    """Dispatcher signal name an entity subscribes to for its own state."""
    return f"{DOMAIN}_{entry_id}_{kind}_{device_id}"


class DomotalkCoordinator:
    """Connects to one hub, caches its devices/ambiences, and dispatches updates."""

    def __init__(
        self,
        hass: HomeAssistant,
        entry_id: str,
        host: str,
        secure: bool,
        trust_all_certs: bool,
        username: str,
        password: str,
    ) -> None:
        self.hass = hass
        self.entry_id = entry_id
        self.host = host
        self.secure = secure
        self.trust_all_certs = trust_all_certs
        self.username = username
        self.password = password

        self._aiohttp_session = aiohttp.ClientSession()
        self.client = DomotalkClient(self._aiohttp_session)
        self._remove_push_listener: Any = None
        self._refresh_task: asyncio.Task[None] | None = None
        self._refresh_pending = False
        self._remove_reconnect_timer: Any = None
        self._connect_lock = asyncio.Lock()

        self.rooms: dict[int, dict[str, Any]] = {}
        self.devices_out: dict[int, Device] = {}
        self.devices_in: dict[int, Device] = {}
        self.ambiences: dict[int, dict[str, Any]] = {}

    async def async_setup(self) -> None:
        await self._async_connect_and_login()
        self._remove_push_listener = self.client.add_push_listener(self._handle_push)
        await self.async_refresh_all()
        self._remove_reconnect_timer = async_track_time_interval(
            self.hass,
            self._async_check_connection,
            timedelta(seconds=RECONNECT_CHECK_INTERVAL_SECONDS),
        )

    async def async_shutdown(self) -> None:
        if self._remove_reconnect_timer is not None:
            self._remove_reconnect_timer()
            self._remove_reconnect_timer = None
        if self._remove_push_listener is not None:
            self._remove_push_listener()
            self._remove_push_listener = None
        if self._refresh_task is not None:
            self._refresh_task.cancel()
        await self.client.disconnect()
        await self._aiohttp_session.close()

    async def _async_connect_and_login(self) -> None:
        async with self._connect_lock:
            if self.client.connected:
                return
            await self.client.connect(self.host, self.secure, self.trust_all_certs)
            if self.client.token:
                try:
                    await self.client.resume_session(self.client.token)
                    return
                except DomotalkError:
                    _LOGGER.debug("Session resume failed, logging in from scratch")
            await self.client.login(self.username, self.password)

    @callback
    def _async_check_connection(self, _now: Any) -> None:
        if self.client.connected:
            return
        self.hass.async_create_task(self._async_reconnect())

    async def _async_reconnect(self) -> None:
        try:
            await self._async_connect_and_login()
            await self.async_refresh_all()
        except (DomotalkError, ConnectFailed, aiohttp.ClientError):
            _LOGGER.warning("Reconnect to domotalk hub %s failed, will retry", self.host, exc_info=True)

    async def async_refresh_all(self) -> None:
        rooms = await self.client.request(VERB_READ, SUBJECT_ROOM)
        self.rooms = {int(r["id"]): r for r in rooms if isinstance(r, dict) and "id" in r}

        device_out_raw = await self.client.request(VERB_READ, SUBJECT_DEVICE_OUT)
        self.devices_out = {
            device.id: device
            for raw in device_out_raw
            if isinstance(raw, dict)
            for device in (classify_device_out(raw),)
        }

        device_in_raw = await self.client.request(VERB_READ, SUBJECT_DEVICE_IN)
        self.devices_in = {
            device.id: device
            for raw in device_in_raw
            if isinstance(raw, dict)
            for device in (classify_device_in(raw),)
        }

        ambience_raw = await self.client.request(VERB_READ, SUBJECT_AMBIENCE)
        self.ambiences = {
            int(a["id"]): a for a in ambience_raw if isinstance(a, dict) and "id" in a
        }

    # -- Actions -----------------------------------------------------------------

    async def async_set_binary_output(self, device: BinaryOutput, turn_on: bool) -> None:
        action = ACTION_BINARY_ON if turn_on else ACTION_BINARY_OFF
        await self.client.request(VERB_ACTION, SUBJECT_BINARY_OUT, build_action_options(device.raw, action))
        self._patch_device_out(device.id, {"status": 1 if turn_on else 0})

    async def async_fire_pulse(self, device: Pulse) -> None:
        await self.client.request(
            VERB_ACTION, SUBJECT_PULSE, build_action_options(device.raw, ACTION_PULSE_FIRE)
        )

    async def async_set_shutter_open_close(self, device: Shutter, open_: bool) -> None:
        action = ACTION_SHUTTER_OPEN if open_ else ACTION_SHUTTER_CLOSE
        await self.client.request(VERB_ACTION, SUBJECT_SHUTTER, build_action_options(device.raw, action))

    async def async_set_shutter_percentage(self, device: Shutter, percentage: int) -> None:
        options = build_action_options(device.raw, ACTION_SHUTTER_SET_PERCENTAGE, percentage)
        await self.client.request(VERB_ACTION, SUBJECT_SHUTTER, options)
        self._patch_device_out(device.id, {"percentage": percentage})

    async def async_set_dimmer_percentage(self, device: Dimmer, percentage: int) -> None:
        options = build_action_options(device.raw, ACTION_DIMMER_SET_PERCENTAGE, percentage)
        await self.client.request(VERB_ACTION, SUBJECT_DIMMER, options)
        self._patch_device_out(device.id, {"percentage": percentage})

    async def async_set_ambience(self, ambience_id: int, run: bool) -> None:
        raw = self.ambiences.get(ambience_id)
        if raw is None:
            return
        action = ACTION_AMBIENCE_RUN if run else ACTION_AMBIENCE_STOP
        await self.client.request(VERB_ACTION, SUBJECT_AMBIENCE, build_action_options(raw, action))
        self._patch_ambience(ambience_id, {"isPlaying": run})

    # -- Local optimistic state + push-driven refresh -----------------------------

    def _patch_device_out(self, device_id: int, field_updates: dict[str, Any]) -> None:
        current = self.devices_out.get(device_id)
        if current is None:
            return
        raw = {**current.raw, **field_updates}
        self.devices_out[device_id] = classify_device_out(raw)
        async_dispatcher_send(self.hass, signal_for(self.entry_id, "out", device_id))

    def _patch_ambience(self, ambience_id: int, field_updates: dict[str, Any]) -> None:
        current = self.ambiences.get(ambience_id)
        if current is None:
            return
        self.ambiences[ambience_id] = {**current, **field_updates}
        async_dispatcher_send(self.hass, signal_for(self.entry_id, "ambience", ambience_id))

    @callback
    def _handle_push(self, _message: dict[str, Any]) -> None:
        if self._refresh_task is not None and not self._refresh_task.done():
            self._refresh_pending = True
            return
        self._refresh_task = self.hass.async_create_task(self._async_debounced_refresh())

    async def _async_debounced_refresh(self) -> None:
        while True:
            self._refresh_pending = False
            await asyncio.sleep(PUSH_REFRESH_DEBOUNCE_SECONDS)
            try:
                await self._async_refresh_and_dispatch()
            except DomotalkError:
                _LOGGER.warning("Failed to refresh domotalk state after a push update", exc_info=True)
            if not self._refresh_pending:
                return

    async def _async_refresh_and_dispatch(self) -> None:
        previous_out, previous_in, previous_ambiences = self.devices_out, self.devices_in, self.ambiences
        await self.async_refresh_all()

        for device_id, device in self.devices_out.items():
            if previous_out.get(device_id) != device:
                async_dispatcher_send(self.hass, signal_for(self.entry_id, "out", device_id))
        for device_id, device in self.devices_in.items():
            if previous_in.get(device_id) != device:
                async_dispatcher_send(self.hass, signal_for(self.entry_id, "in", device_id))
        for ambience_id, ambience in self.ambiences.items():
            if previous_ambiences.get(ambience_id) != ambience:
                async_dispatcher_send(self.hass, signal_for(self.entry_id, "ambience", ambience_id))
