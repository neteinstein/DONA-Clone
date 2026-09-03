"""Async client for the `domotalk` WebSocket protocol.

Ported from the Kotlin reference implementation in this repository's Android
client (`core/network/.../socket/DomotalkSocket.kt` and
`core/network/.../api/DomotalkApiImpl.kt`) so this integration and the DONA-Clone
app independently agree on the same wire protocol against the same hub — see
`docs/PROTOCOL.md` for the full write-up and confidence ratings per claim.

This module has no Home Assistant imports on purpose: it only depends on
`aiohttp`, so it can be unit-tested against a bare WebSocket server without
spinning up Home Assistant itself.
"""

from __future__ import annotations

import asyncio
import hashlib
import itertools
import json
import logging
import ssl
from typing import Any, Callable

import aiohttp

from .const import (
    CALLBACK_ID_WRAP,
    CONNECT_TIMEOUT_SECONDS,
    PING_INTERVAL_SECONDS,
    REQUEST_TIMEOUT_SECONDS,
    SUBJECT_SESSION,
    SUBJECT_USER,
    SUBPROTOCOLS,
    VERB_ACTION,
    VERB_CREATE,
    VERB_DELETE,
    VERB_READ,
    WS_PATH,
)

_LOGGER = logging.getLogger(__name__)

PushListener = Callable[[dict[str, Any]], None]


class DomotalkError(Exception):
    """Base error for all domotalk client failures."""


class ConnectFailed(DomotalkError):
    """The initial WebSocket handshake failed."""


class NotConnected(DomotalkError):
    """A request was attempted while there is no open connection."""


class ConnectionLost(DomotalkError):
    """The connection dropped while a request was in flight."""


class RequestTimeout(DomotalkError):
    """No response arrived for a request's `callback_id` in time."""

    def __init__(self, verb: str, subject: str) -> None:
        super().__init__(f"Timed out waiting for a response to {verb!r} {subject!r}")


class MalformedResponse(DomotalkError):
    """The hub's response didn't match the shape the protocol notes document."""


class InvalidCredentials(DomotalkError):
    """No enabled user matched the configured username."""


def md5_hex(password: str) -> str:
    """Unsalted MD5 hex digest of a password.

    This is a real, weak property of the hub's own wire protocol (protocol notes
    §2.3), not a choice this integration makes — it's replicated purely for wire
    compatibility with existing hardware, exactly like `PasswordHasher.kt` in the
    Android client.
    """
    return hashlib.md5(password.encode("utf-8")).hexdigest()  # noqa: S324


class DomotalkClient:
    """One persistent WebSocket connection to a domotalk hub."""

    def __init__(self, session: aiohttp.ClientSession) -> None:
        self._session = session
        self._ws: aiohttp.ClientWebSocketResponse | None = None
        self._listen_task: asyncio.Task[None] | None = None
        self._pending: dict[int, asyncio.Future[dict[str, Any]]] = {}
        self._callback_ids = itertools.count(0)
        self._push_listeners: list[PushListener] = []
        self.token: str | None = None

    @property
    def connected(self) -> bool:
        return self._ws is not None and not self._ws.closed

    def add_push_listener(self, listener: PushListener) -> Callable[[], None]:
        """Register a callback invoked with every unsolicited push update (§8).

        Returns a function that removes the listener again.
        """
        self._push_listeners.append(listener)

        def _remove() -> None:
            if listener in self._push_listeners:
                self._push_listeners.remove(listener)

        return _remove

    async def connect(
        self,
        host: str,
        secure: bool,
        trust_all_certs: bool = True,
    ) -> None:
        """Open the WebSocket connection (protocol notes §2.1).

        `ws(s)://<host>/ws/` on the scheme's default port, subprotocols
        `domotalk`/`ping-pong`, a 2s ping interval. When `secure` and
        `trust_all_certs` are both set, certificate/hostname validation is
        disabled — the hub typically serves a self-signed LAN certificate, the
        same tradeoff `TrustAllCerts.kt` documents in the Android client.
        """
        scheme = "wss" if secure else "ws"
        url = f"{scheme}://{host}{WS_PATH}"

        connect_kwargs: dict[str, Any] = {
            "protocols": SUBPROTOCOLS,
            "heartbeat": PING_INTERVAL_SECONDS,
            "timeout": aiohttp.ClientWSTimeout(ws_close=CONNECT_TIMEOUT_SECONDS),
        }
        if secure and trust_all_certs:
            ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            ssl_context.check_hostname = False
            ssl_context.verify_mode = ssl.CERT_NONE
            connect_kwargs["ssl"] = ssl_context

        try:
            self._ws = await self._session.ws_connect(url, **connect_kwargs)
        except (aiohttp.ClientError, asyncio.TimeoutError, ssl.SSLError) as err:
            raise ConnectFailed(str(err)) from err

        self._listen_task = asyncio.create_task(self._listen())

    async def disconnect(self) -> None:
        if self._listen_task is not None:
            self._listen_task.cancel()
            self._listen_task = None
        if self._ws is not None:
            await self._ws.close()
            self._ws = None
        self.token = None
        self._fail_all_pending(ConnectionLost("client_disconnect"))

    async def login(self, username: str, password: str) -> str:
        """Run the full login sequence (protocol notes §2.3).

        `read user` resolves the configured username to a numeric id
        client-side (matched exactly/case-sensitively, same as the original
        app), then `create session` with that id and MD5(password). Stores and
        returns the resulting session token.
        """
        users = await self.request(VERB_READ, SUBJECT_USER)
        if not isinstance(users, list):
            raise MalformedResponse("Expected a JSON array from `read user`")

        user = next(
            (u for u in users if isinstance(u, dict) and u.get("name") == username),
            None,
        )
        if user is None or user.get("role", 0) == 0:
            raise InvalidCredentials(f"Could not find an enabled user named {username!r}")

        options = {"userId": user["id"], "password": md5_hex(password), "forever": True}
        session = await self.request(VERB_CREATE, SUBJECT_SESSION, options=options)
        token = session.get("token") if isinstance(session, dict) else None
        if not token:
            raise MalformedResponse("`create session` response had no token")

        self.token = token
        return token

    async def resume_session(self, token: str) -> None:
        """Re-bind a previously issued token to a fresh socket (protocol notes §2.3)."""
        self.token = token
        await self.request(VERB_ACTION, SUBJECT_SESSION, options={"token": token})

    async def logout(self) -> None:
        try:
            await self.request(VERB_DELETE, SUBJECT_SESSION)
        except DomotalkError:
            pass
        self.token = None

    async def request(
        self,
        verb: str,
        subject: str,
        options: dict[str, Any] | None = None,
        filters: list[dict[str, Any]] | None = None,
        timeout: float = REQUEST_TIMEOUT_SECONDS,
    ) -> Any:
        """Send one `domotalk` request and wait for the matching `callback_id` reply."""
        if self._ws is None or self._ws.closed:
            raise NotConnected("Not connected to the hub")

        callback_id = next(self._callback_ids) % CALLBACK_ID_WRAP
        future: asyncio.Future[dict[str, Any]] = asyncio.get_running_loop().create_future()
        self._pending[callback_id] = future

        payload: dict[str, Any] = {"verb": verb, "subject": subject}
        if options is not None:
            payload["options"] = options
        if filters is not None:
            payload["filters"] = filters
        if self.token is not None:
            payload["token"] = self.token
        payload["callback_id"] = callback_id

        try:
            await self._ws.send_json(payload)
        except (ConnectionResetError, RuntimeError) as err:
            self._pending.pop(callback_id, None)
            raise NotConnected(str(err)) from err

        try:
            response = await asyncio.wait_for(future, timeout=timeout)
        except asyncio.TimeoutError as err:
            raise RequestTimeout(verb, subject) from err
        finally:
            self._pending.pop(callback_id, None)

        return _extract_payload(response)

    async def _listen(self) -> None:
        assert self._ws is not None  # noqa: S101
        try:
            async for message in self._ws:
                if message.type == aiohttp.WSMsgType.TEXT:
                    self._handle_message(message.data)
                elif message.type in (
                    aiohttp.WSMsgType.ERROR,
                    aiohttp.WSMsgType.CLOSE,
                    aiohttp.WSMsgType.CLOSED,
                ):
                    break
        finally:
            self._fail_all_pending(ConnectionLost("connection closed"))

    def _handle_message(self, text: str) -> None:
        try:
            obj = json.loads(text)
        except ValueError:
            _LOGGER.debug("Dropped a non-JSON domotalk message")
            return
        if not isinstance(obj, dict):
            return

        callback_id = obj.get("callback_id")
        future = self._pending.get(callback_id) if isinstance(callback_id, int) else None
        if future is not None and not future.done():
            future.set_result(obj)
            return

        # Doesn't correlate to a pending request -> an unsolicited live state-update
        # push (protocol notes §8; envelope reconstructed best-effort, see docs).
        for listener in list(self._push_listeners):
            try:
                listener(obj)
            except Exception:  # noqa: BLE001
                _LOGGER.exception("domotalk push listener raised")

    def _fail_all_pending(self, error: DomotalkError) -> None:
        for future in self._pending.values():
            if not future.done():
                future.set_exception(error)
        self._pending.clear()


def _extract_payload(response: dict[str, Any]) -> Any:
    """Unwrap the `payload` field.

    It's sometimes a JSON-encoded string that must be re-parsed, and sometimes
    the object/array directly (protocol notes §2.2) — mirrors
    `DomotalkSocket.extractPayload` in the Android client.
    """
    if "payload" not in response:
        return response
    payload = response["payload"]
    if isinstance(payload, str):
        try:
            return json.loads(payload)
        except ValueError as err:
            raise MalformedResponse("payload was not valid JSON") from err
    return payload
