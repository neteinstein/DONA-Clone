"""dimmer devices as light entities.

The hub only ever reports a 0-100 percentage (protocol notes §3.2/§4); this
maps that linearly onto HA's 0-255 brightness scale.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.light import ColorMode, LightEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .devices import Dimmer
from .entity import DomotalkEntity

_MAX_HA_BRIGHTNESS = 255
_MAX_HUB_PERCENTAGE = 100


def _percentage_to_brightness(percentage: int) -> int:
    return round(percentage * _MAX_HA_BRIGHTNESS / _MAX_HUB_PERCENTAGE)


def _brightness_to_percentage(brightness: int) -> int:
    return round(brightness * _MAX_HUB_PERCENTAGE / _MAX_HA_BRIGHTNESS)


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkLight(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, Dimmer)
    )


class DomotalkLight(DomotalkEntity, LightEntity):
    _attr_color_mode = ColorMode.BRIGHTNESS
    _attr_supported_color_modes = {ColorMode.BRIGHTNESS}

    def __init__(self, coordinator: DomotalkCoordinator, device: Dimmer) -> None:
        super().__init__(coordinator, "out", device.id, device.name)

    @property
    def _device(self) -> Dimmer:
        device = self._coordinator.devices_out[self._device_id]
        assert isinstance(device, Dimmer)  # noqa: S101
        return device

    @property
    def is_on(self) -> bool:
        return self._device.percentage > 0

    @property
    def brightness(self) -> int:
        return _percentage_to_brightness(self._device.percentage)

    async def async_turn_on(self, **kwargs: Any) -> None:
        brightness = kwargs.get("brightness")
        percentage = _brightness_to_percentage(brightness) if brightness is not None else _MAX_HUB_PERCENTAGE
        await self._coordinator.async_set_dimmer_percentage(self._device, percentage)

    async def async_turn_off(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_dimmer_percentage(self._device, 0)
