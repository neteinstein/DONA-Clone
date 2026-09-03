"""Classifies the heterogeneous `deviceOut`/`deviceIn` JSON objects the hub returns.

Ported from `core/network/.../mapper/DeviceJsonMapper.kt` in the Android client.
The exact JSON key that would carry an explicit numeric type discriminator was
never observed as a literal in the decompiled original app (protocol notes
§3.2), so — like the Android client — this infers the concrete kind
structurally from which fields are present, which is robust to that ambiguity:
a `shutter` has `percentage` + `processDuration`, a `dimmer` has `percentage`
alone, a `pulse` has `status` + `duration`, and a plain `binaryOut` has
`status` alone. An explicit numeric `type` key, if the hub ever sends one, is
honoured first.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .const import (
    DPU_CODE_BINARY_OUT,
    DPU_CODE_DIMMER,
    DPU_CODE_PULSE,
    DPU_CODE_SHUTTER,
    PULSE_KIND_ARM_DISARM_COUPLED,
    PULSE_KIND_ARM_OUTPUT,
    PULSE_KIND_CHIME,
    PULSE_KIND_DISARM_OUTPUT,
    PULSE_KIND_LOCK,
    PULSE_KIND_SIREN,
)

PULSE_KIND_NAMES = {
    PULSE_KIND_SIREN: "siren",
    PULSE_KIND_CHIME: "chime",
    PULSE_KIND_LOCK: "lock",
    PULSE_KIND_ARM_OUTPUT: "arm_output",
    PULSE_KIND_DISARM_OUTPUT: "disarm_output",
    PULSE_KIND_ARM_DISARM_COUPLED: "arm_disarm_coupled",
}


@dataclass(frozen=True, kw_only=True)
class DeviceCommon:
    id: int
    name: str
    description: str | None = None
    enabled: bool = True
    online: bool = True
    room_id: int | None = None
    free_type_label: str | None = None
    raw: dict[str, Any]


@dataclass(frozen=True, kw_only=True)
class BinaryOutput(DeviceCommon):
    is_on: bool


@dataclass(frozen=True, kw_only=True)
class Pulse(DeviceCommon):
    kind: int
    kind_name: str
    duration_seconds: int | None = None


@dataclass(frozen=True, kw_only=True)
class Shutter(DeviceCommon):
    percentage: int


@dataclass(frozen=True, kw_only=True)
class Dimmer(DeviceCommon):
    percentage: int


@dataclass(frozen=True, kw_only=True)
class BinaryInput(DeviceCommon):
    is_active: bool


@dataclass(frozen=True, kw_only=True)
class AnalogInput(DeviceCommon):
    value: float


@dataclass(frozen=True, kw_only=True)
class UnknownDevice(DeviceCommon):
    raw_type_code: int | None = None


Device = BinaryOutput | Pulse | Shutter | Dimmer | BinaryInput | AnalogInput | UnknownDevice


def _status_is_on(raw: dict[str, Any]) -> bool:
    status = raw.get("status")
    if isinstance(status, bool):
        return status
    if isinstance(status, (int, float)):
        return status > 0
    return False


def _common_fields(raw: dict[str, Any]) -> dict[str, Any]:
    free_type_label = raw.get("type")
    if not isinstance(free_type_label, str):
        free_type_label = None
    return {
        "id": int(raw["id"]),
        "name": raw.get("name") or "",
        "description": raw.get("description"),
        "enabled": bool(raw.get("enabled", True)),
        "online": bool(raw.get("online", True)),
        "room_id": raw.get("room"),
        "free_type_label": free_type_label,
        "raw": raw,
    }


def classify_device_out(raw: dict[str, Any]) -> Device:
    common = _common_fields(raw)
    has_percentage = "percentage" in raw
    has_process_duration = "processDuration" in raw
    has_status = "status" in raw
    has_duration = "duration" in raw
    explicit_code = raw.get("type") if isinstance(raw.get("type"), int) else None

    if explicit_code == DPU_CODE_SHUTTER or (has_percentage and has_process_duration):
        return Shutter(**common, percentage=int(raw.get("percentage", 0)))

    if explicit_code == DPU_CODE_DIMMER or (has_percentage and not has_status):
        return Dimmer(**common, percentage=int(raw.get("percentage", 0)))

    if explicit_code == DPU_CODE_PULSE or (has_status and has_duration):
        kind = raw.get("subtype")
        kind = kind if isinstance(kind, int) else -1
        return Pulse(
            **common,
            kind=kind,
            kind_name=PULSE_KIND_NAMES.get(kind, "unknown"),
            duration_seconds=raw.get("duration") if isinstance(raw.get("duration"), int) else None,
        )

    if explicit_code == DPU_CODE_BINARY_OUT or has_status:
        return BinaryOutput(**common, is_on=_status_is_on(raw))

    return UnknownDevice(**common, raw_type_code=explicit_code)


def classify_device_in(raw: dict[str, Any]) -> Device:
    common = _common_fields(raw)
    if "status" in raw:
        return BinaryInput(**common, is_active=_status_is_on(raw))
    if "value" in raw:
        return AnalogInput(**common, value=float(raw["value"]))
    return UnknownDevice(**common, raw_type_code=None)


def build_action_options(
    raw_device: dict[str, Any],
    action: int,
    percentage: int | None = None,
) -> dict[str, Any]:
    """Build the `options` object for a `verb: action` request.

    The hub expects the full device object back with the changed field(s)
    updated, not a delta (protocol notes §4) — mirrors
    `DeviceJsonMapper.buildActionOptions`.
    """
    options: dict[str, Any] = {"object": dict(raw_device), "action": action}
    if percentage is not None:
        options["percentage"] = percentage
    return options
