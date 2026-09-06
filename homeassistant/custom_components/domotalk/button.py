"""Every deviceOut `pulse` device as a momentary button, except subtype `lock`
(20), which gets its own richer `lock` entity in lock.py instead.

Mirrors the DONA-Clone Android app's Home screen, which renders every pulse
device as a plain tap-to-fire tile with no special-casing by subtype (see
`DevicesUiState.homeDisplayItemsByRoom`/`DeviceRoomGrid.DeviceCell` in the
Android client) — that includes the alarm arm-output/disarm-output/
arm+disarm-coupled subtypes (30/31/32) and any unrecognized subtype, not just
siren/chime. A dedicated alarm-panel *feature* (the separate `alarm` protocol
subject, with its own armOutput/disarmOutput/alertInput config) is a
different thing and genuinely isn't implemented by either client — but a bare
pulse relay of that subtype sitting in the deviceOut list is just another
Home-screen tile, so it's exposed the same way here.
"""

from __future__ import annotations

from homeassistant.components.button import ButtonEntity
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
        DomotalkPulseButton(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, Pulse) and device.kind != PULSE_KIND_LOCK
    )


class DomotalkPulseButton(DomotalkEntity, ButtonEntity):
    def __init__(self, coordinator: DomotalkCoordinator, device: Pulse) -> None:
        super().__init__(coordinator, "out", device.id, device.name)

    @property
    def _device(self) -> Pulse:
        device = self._coordinator.devices_out[self._device_id]
        assert isinstance(device, Pulse)  # noqa: S101
        return device

    async def async_press(self) -> None:
        await self._coordinator.async_fire_pulse(self._device)
