"""binaryOut devices (relays/switches/outlets) as switch entities."""

from __future__ import annotations

from typing import Any

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .devices import BinaryOutput
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkSwitch(coordinator, device)
        for device in coordinator.devices_out.values()
        if isinstance(device, BinaryOutput)
    )


class DomotalkSwitch(DomotalkEntity, SwitchEntity):
    def __init__(self, coordinator: DomotalkCoordinator, device: BinaryOutput) -> None:
        super().__init__(coordinator, "out", device.id, device.name)

    @property
    def _device(self) -> BinaryOutput:
        device = self._coordinator.devices_out[self._device_id]
        assert isinstance(device, BinaryOutput)  # noqa: S101
        return device

    @property
    def is_on(self) -> bool:
        return self._device.is_on

    async def async_turn_on(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_binary_output(self._device, turn_on=True)

    async def async_turn_off(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_binary_output(self._device, turn_on=False)
