"""Tests for DomotalkClient against a fake in-process domotalk WebSocket server.

No real hub is available to test against (see `../README.md`'s "Known
limitations" section) — this exercises the request/response envelope,
login sequence, and push-update handling against a minimal fake that
implements exactly the shapes documented in `docs/PROTOCOL.md`.
"""

from __future__ import annotations

import asyncio
import json

import aiohttp
import pytest
from aiohttp import web
from aiohttp.test_utils import TestServer

from _domotalk_modules import client as client_module

DomotalkClient = client_module.DomotalkClient
InvalidCredentials = client_module.InvalidCredentials
NotConnected = client_module.NotConnected


class FakeHub:
    """A minimal domotalk hub: one enabled user, echoes `action` requests."""

    def __init__(self) -> None:
        self.app = web.Application()
        self.app.router.add_get("/ws/", self._handle)
        self.received: list[dict] = []
        self.host: str = ""
        self._ws: web.WebSocketResponse | None = None

    async def _handle(self, request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse(protocols=("domotalk", "ping-pong"))
        await ws.prepare(request)
        self._ws = ws
        async for message in ws:
            if message.type != web.WSMsgType.TEXT:
                continue
            payload = json.loads(message.data)
            self.received.append(payload)
            await self._respond(ws, payload)
        return ws

    async def push(self, message: dict) -> None:
        assert self._ws is not None
        await self._ws.send_json(message)

    async def _respond(self, ws: web.WebSocketResponse, payload: dict) -> None:
        verb, subject, callback_id = payload["verb"], payload["subject"], payload["callback_id"]

        if verb == "read" and subject == "user":
            body = json.dumps([{"id": 1, "role": 1, "name": "alice", "enabled": True}])
            await ws.send_json({"callback_id": callback_id, "payload": body})
        elif verb == "create" and subject == "session":
            assert payload["options"]["userId"] == 1
            assert payload["options"]["password"] == client_module.md5_hex("hunter2")
            await ws.send_json({"callback_id": callback_id, "payload": {"token": "tok-123"}})
        elif verb == "action" and subject == "session":
            await ws.send_json({"callback_id": callback_id, "payload": {}})
        elif verb == "action":
            # Echo the full device object back, like a real `action` ack would.
            await ws.send_json({"callback_id": callback_id, "payload": payload["options"]["object"]})
        else:
            await ws.send_json({"callback_id": callback_id, "payload": []})


@pytest.fixture
async def hub() -> FakeHub:
    fake = FakeHub()
    server = TestServer(fake.app)
    await server.start_server()
    fake.host = f"{server.host}:{server.port}"
    yield fake
    await server.close()


@pytest.fixture
async def connected(hub: FakeHub):
    async with aiohttp.ClientSession() as session:
        c = DomotalkClient(session)
        await c.connect(hub.host, secure=False)
        yield c, hub
        await c.disconnect()


async def test_login_resolves_user_and_stores_token(connected) -> None:
    c, hub = connected
    token = await c.login("alice", "hunter2")
    assert token == "tok-123"
    assert c.token == "tok-123"
    assert hub.received[0] == {"verb": "read", "subject": "user", "callback_id": 0}


async def test_login_rejects_unknown_username(connected) -> None:
    c, _hub = connected
    with pytest.raises(InvalidCredentials):
        await c.login("nobody", "hunter2")


async def test_request_attaches_token_after_login(connected) -> None:
    c, hub = connected
    await c.login("alice", "hunter2")
    await c.request("action", "session", options={"token": "tok-123"})
    last = hub.received[-1]
    assert last["token"] == "tok-123"


async def test_action_round_trip_echoes_full_device_object(connected) -> None:
    c, _hub = connected
    raw_device = {"id": 42, "name": "Kitchen", "status": 0}
    result = await c.request(
        "action", "binaryOut", options={"object": raw_device, "action": 1}
    )
    assert result == raw_device


async def test_payload_string_is_unwrapped(connected) -> None:
    c, _hub = connected
    result = await c.request("read", "user")
    assert isinstance(result, list)
    assert result[0]["name"] == "alice"


async def test_unsolicited_push_reaches_listener(connected) -> None:
    c, hub = connected
    received: list[dict] = []
    c.add_push_listener(received.append)

    push_message = {"request": {"options": {"object": {"id": 9, "percentage": 50}}}}
    await hub.push(push_message)

    for _ in range(50):
        if received:
            break
        await asyncio.sleep(0.02)

    assert received == [push_message]


async def test_request_without_connection_raises() -> None:
    async with aiohttp.ClientSession() as session:
        c = DomotalkClient(session)
        with pytest.raises(NotConnected):
            await c.request("read", "user")
