"""Config flow for the domotalk integration.

Validates by running the exact same connect + login sequence the coordinator
uses at runtime (protocol notes §2), against a throwaway client/session, so a
bad host or credentials are caught at setup time rather than on first use.
"""

from __future__ import annotations

import logging
from typing import Any

import aiohttp
import voluptuous as vol
from homeassistant import config_entries
from homeassistant.config_entries import ConfigFlowResult
from homeassistant.const import CONF_HOST, CONF_PASSWORD, CONF_USERNAME

from .client import ConnectFailed, DomotalkClient, DomotalkError, InvalidCredentials
from .const import CONF_SECURE, CONF_TRUST_ALL_CERTS, DEFAULT_SECURE, DEFAULT_TRUST_ALL_CERTS, DOMAIN

_LOGGER = logging.getLogger(__name__)

STEP_USER_SCHEMA = vol.Schema(
    {
        vol.Required(CONF_HOST): str,
        vol.Required(CONF_USERNAME): str,
        vol.Required(CONF_PASSWORD): str,
        vol.Optional(CONF_SECURE, default=DEFAULT_SECURE): bool,
        vol.Optional(CONF_TRUST_ALL_CERTS, default=DEFAULT_TRUST_ALL_CERTS): bool,
    }
)


async def _async_validate(data: dict[str, Any]) -> None:
    """Run one throwaway connect+login. Raises on failure."""
    async with aiohttp.ClientSession() as session:
        client = DomotalkClient(session)
        try:
            await client.connect(data[CONF_HOST], data[CONF_SECURE], data[CONF_TRUST_ALL_CERTS])
            await client.login(data[CONF_USERNAME], data[CONF_PASSWORD])
        finally:
            await client.disconnect()


class DomotalkConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle a config flow for domotalk."""

    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        errors: dict[str, str] = {}

        if user_input is not None:
            self._async_abort_entries_match({CONF_HOST: user_input[CONF_HOST]})
            try:
                await _async_validate(user_input)
            except InvalidCredentials:
                errors["base"] = "invalid_auth"
            except ConnectFailed:
                errors["base"] = "cannot_connect"
            except DomotalkError:
                _LOGGER.exception("Unexpected error validating the domotalk hub")
                errors["base"] = "unknown"
            else:
                await self.async_set_unique_id(user_input[CONF_HOST])
                self._abort_if_unique_id_configured()
                return self.async_create_entry(title=user_input[CONF_HOST], data=user_input)

        return self.async_show_form(step_id="user", data_schema=STEP_USER_SCHEMA, errors=errors)
