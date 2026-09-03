"""Home Assistant-facing errors for the domotalk integration."""

from __future__ import annotations

from homeassistant.exceptions import HomeAssistantError


class HubConnectionError(HomeAssistantError):
    """Raised when the hub can't be reached or a login attempt fails."""
