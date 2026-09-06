"""deviceIn devices reporting a numeric `value` (temperature, humidity, light
level, ...) as sensors.

The hub reports only a bare `value` (protocol notes §3.2) with no unit or
device class of its own, so this exposes a plain, unitless numeric reading —
matching the DONA-Clone Android app's own Sensors-screen tile, which just
shows the raw number (`stateLabelFor`'s `AnalogInput` branch in the Android
client).
"""

from __future__ import annotations

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .devices import AnalogInput
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkSensor(coordinator, device)
        for device in coordinator.devices_in.values()
        if isinstance(device, AnalogInput)
    )


class DomotalkSensor(DomotalkEntity, SensorEntity):
    def __init__(self, coordinator: DomotalkCoordinator, device: AnalogInput) -> None:
        super().__init__(coordinator, "in", device.id, device.name)

    @property
    def _device(self) -> AnalogInput:
        device = self._coordinator.devices_in[self._device_id]
        assert isinstance(device, AnalogInput)  # noqa: S101
        return device

    @property
    def native_value(self) -> float:
        return self._device.value
