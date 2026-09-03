"""pulse devices of kind `siren`/`chime` (subtypes 10/11) as momentary buttons.

Arm/disarm pulse outputs (subtypes 30/31/32) are deliberately not exposed —
alarm arm/disarm is out of scope for this integration, matching the
DONA-Clone Android app's own "not implemented, on purpose" list.
"""

from __future__ import annotations

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, PULSE_KIND_CHIME, PULSE_KIND_SIREN
from .coordinator import DomotalkCoordinator
from .devices import Pulse
from .entity import DomotalkEntity

_EXPOSED_KINDS = {PULSE_KIND_SIREN, PULSE_KIND_CHIME}


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkPulseButton(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, Pulse) and device.kind in _EXPOSED_KINDS
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
