"""ambience entries ("Scenarios" in the DONA-Clone app) as scene entities.

HA's scene domain only has an "activate" concept — matches the hub's own
`ambience` action semantics well enough for `action:1` (run); there is no HA
equivalent for `action:0` (stop), so stopping a running ambience isn't
exposed here.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.scene import Scene
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .coordinator import DomotalkCoordinator
from .entity import DomotalkEntity


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    coordinator: DomotalkCoordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities(
        DomotalkScene(coordinator, ambience_id, raw.get("name") or f"Scenario {ambience_id}")
        for ambience_id, raw in coordinator.ambiences.items()
    )


class DomotalkScene(DomotalkEntity, Scene):
    def __init__(self, coordinator: DomotalkCoordinator, ambience_id: int, name: str) -> None:
        super().__init__(coordinator, "ambience", ambience_id, name)

    async def async_activate(self, **kwargs: Any) -> None:
        await self._coordinator.async_set_ambience(self._device_id, run=True)
