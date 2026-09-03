"""The domotalk integration: a Home Assistant client for the DONA-Clone hub protocol.

Talks directly to the hub over the same `domotalk` WebSocket protocol the
DONA-Clone Android app uses (see `docs/PROTOCOL.md` in the repository root) —
this integration is an independent client of the hub, not a bridge to the app
itself. Once the hub's devices exist as HA entities, Home Assistant's own
Google Assistant integration (Nabu Casa Cloud, or the self-hosted
`google_assistant` component) exposes them for Google Home voice control; see
`homeassistant/README.md` for that part.
"""

from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import CONF_HOST, CONF_PASSWORD, CONF_USERNAME, Platform
from homeassistant.core import HomeAssistant

from .client import DomotalkError
from .const import CONF_SECURE, CONF_TRUST_ALL_CERTS, DOMAIN
from .coordinator import DomotalkCoordinator
from .errors import HubConnectionError

PLATFORMS: list[Platform] = [
    Platform.SWITCH,
    Platform.LIGHT,
    Platform.COVER,
    Platform.LOCK,
    Platform.BUTTON,
    Platform.SCENE,
]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    coordinator = DomotalkCoordinator(
        hass=hass,
        entry_id=entry.entry_id,
        host=entry.data[CONF_HOST],
        secure=entry.data[CONF_SECURE],
        trust_all_certs=entry.data[CONF_TRUST_ALL_CERTS],
        username=entry.data[CONF_USERNAME],
        password=entry.data[CONF_PASSWORD],
    )

    try:
        await coordinator.async_setup()
    except DomotalkError as err:
        await coordinator.async_shutdown()
        raise HubConnectionError(str(err)) from err

    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unload_ok:
        coordinator: DomotalkCoordinator = hass.data[DOMAIN].pop(entry.entry_id)
        await coordinator.async_shutdown()
    return unload_ok
