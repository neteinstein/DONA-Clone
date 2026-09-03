"""pulse devices of kind `lock` (subtype 20) as lock entities.

The hub only exposes a single momentary "fire" action for pulse devices
(protocol notes §4: `pulse` actions are always sent as `action:0`) — there is
no separate, hardware-reported locked/unlocked state. This entity fires the
same pulse for both `lock` and `unlock` (an electric strike momentarily
releases and then re-locks itself) and tracks locked/unlocked optimistically
so voice commands like "lock"/"unlock the front door" have a sensible target,
while `assumed_state` tells Home Assistant/Google Home this state is a guess,
not a hardware read-back.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.lock import LockEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, PULSE_KIND_LOCK
from .coordinator import DomotalkCoordinator
from .devices import Pulse
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkLock(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, Pulse) and device.kind == PULSE_KIND_LOCK
    )


class DomotalkLock(DomotalkEntity, LockEntity):
    _attr_assumed_state = True

    def __init__(self, coordinator: DomotalkCoordinator, device: Pulse) -> None:
        super().__init__(coordinator, "out", device.id, device.name)
        self._is_locked = True

    @property
    def _device(self) -> Pulse:
        device = self._coordinator.devices_out[self._device_id]
        assert isinstance(device, Pulse)  # noqa: S101
        return device

    @property
    def is_locked(self) -> bool:
        return self._is_locked

    async def async_lock(self, **kwargs: Any) -> None:
        await self._coordinator.async_fire_pulse(self._device)
        self._is_locked = True
        self.async_write_ha_state()

    async def async_unlock(self, **kwargs: Any) -> None:
        await self._coordinator.async_fire_pulse(self._device)
        self._is_locked = False
        self.async_write_ha_state()
