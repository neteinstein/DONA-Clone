"""shutter devices as cover entities."""

from __future__ import annotations

from typing import Any

from homeassistant.components.cover import CoverDeviceClass, CoverEntity, CoverEntityFeature
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .devices import Shutter
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkCover(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, Shutter)
    )


class DomotalkCover(DomotalkEntity, CoverEntity):
    _attr_device_class = CoverDeviceClass.SHUTTER
    _attr_supported_features = (
        CoverEntityFeature.OPEN | CoverEntityFeature.CLOSE | CoverEntityFeature.SET_POSITION
    )

    def __init__(self, coordinator: DomotalkCoordinator, device: Shutter) -> None:
        super().__init__(coordinator, "out", device.id, device.name)

    @property
    def _device(self) -> Shutter:
        device = self._coordinator.devices_out[self._device_id]
        assert isinstance(device, Shutter)  # noqa: S101
        return device

    @property
    def current_cover_position(self) -> int:
        return self._device.percentage

    @property
    def is_closed(self) -> bool:
        return self._device.percentage <= 0

    async def async_open_cover(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_shutter_open_close(self._device, open_=True)

    async def async_close_cover(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_shutter_open_close(self._device, open_=False)

    async def async_set_cover_position(self, **kwargs: Any) -> None:
        position = kwargs["position"]
        await self._coordinator.async_set_shutter_percentage(self._device, position)
