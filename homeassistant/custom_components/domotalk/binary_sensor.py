"""deviceIn devices reporting a plain on/off `status` (door/window contacts,
motion, ...) as binary sensors.

Mirrors the DONA-Clone Android app's Sensors screen: a `BinaryInput` never
has a tap action of its own (see `DeviceDisplayItem.isActionlessSensor` in
the Android client), so it only ever shows there, never on Home — hence its
own read-only platform here rather than a switch.
"""

from __future__ import annotations

from homeassistant.components.binary_sensor import BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .devices import BinaryInput
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkBinarySensor(coordinator, device)
        for device in coordinator.devices_in.values()
        if isinstance(device, BinaryInput)
    )


class DomotalkBinarySensor(DomotalkEntity, BinarySensorEntity):
    def __init__(self, coordinator: DomotalkCoordinator, device: BinaryInput) -> None:
        super().__init__(coordinator, "in", device.id, device.name)

    @property
    def _device(self) -> BinaryInput:
        device = self._coordinator.devices_in[self._device_id]
        assert isinstance(device, BinaryInput)  # noqa: S101
        return device

    @property
    def is_on(self) -> bool:
        return self._device.is_active
