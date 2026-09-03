"""Common entity base shared by every domotalk platform."""

from __future__ import annotations

from homeassistant.core import callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import Entity

from .const import DOMAIN
from .coordinator import DomotalkCoordinator, signal_for


class DomotalkEntity(Entity):
    """Base for entities backed by one device/ambience on a domotalk hub.

    `should_poll` is off — state changes arrive via the coordinator's
    dispatcher signal (either a push-driven refresh, or the optimistic update
    an action applies immediately after a successful request), never polling.
    """

    _attr_should_poll = False
    _attr_has_entity_name = True

    def __init__(
        self,
        coordinator: DomotalkCoordinator,
        kind: str,
        device_id: int,
        name: str,
    ) -> None:
        self._coordinator = coordinator
        self._kind = kind
        self._device_id = device_id
        self._attr_unique_id = f"{coordinator.entry_id}_{kind}_{device_id}"
        self._attr_name = name
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, f"{coordinator.host}_{kind}_{device_id}")},
            name=name,
            via_device=(DOMAIN, coordinator.host),
        )

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass,
                signal_for(self._coordinator.entry_id, self._kind, self._device_id),
                self._handle_coordinator_update,
            )
        )

    @callback
    def _handle_coordinator_update(self) -> None:
        self.async_write_ha_state()

    @property
    def available(self) -> bool:
        return self._coordinator.client.connected
