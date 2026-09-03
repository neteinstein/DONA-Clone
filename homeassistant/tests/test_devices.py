"""Tests for the structural deviceOut/deviceIn classification in devices.py.

Mirrors the cases `DeviceJsonMapper.kt`'s own tests exercise, since this is a
1:1 port — see the module docstring in `devices.py`.
"""

from __future__ import annotations

from _domotalk_modules import devices


def test_classify_binary_output() -> None:
    device = devices.classify_device_out({"id": 1, "name": "Kitchen light", "status": 1})
    assert isinstance(device, devices.BinaryOutput)
    assert device.is_on is True


def test_classify_binary_output_off() -> None:
    device = devices.classify_device_out({"id": 1, "name": "Kitchen light", "status": 0})
    assert isinstance(device, devices.BinaryOutput)
    assert device.is_on is False


def test_classify_dimmer() -> None:
    device = devices.classify_device_out({"id": 2, "name": "Lamp", "percentage": 40})
    assert isinstance(device, devices.Dimmer)
    assert device.percentage == 40


def test_classify_shutter() -> None:
    device = devices.classify_device_out(
        {"id": 3, "name": "Blinds", "percentage": 55, "processDuration": 12}
    )
    assert isinstance(device, devices.Shutter)
    assert device.percentage == 55


def test_classify_pulse_lock() -> None:
    device = devices.classify_device_out(
        {"id": 4, "name": "Front door", "status": 0, "duration": 3, "subtype": 20}
    )
    assert isinstance(device, devices.Pulse)
    assert device.kind == 20
    assert device.kind_name == "lock"


def test_classify_honours_explicit_type_code_first() -> None:
    # `percentage` alone would normally mean a dimmer, but an explicit numeric
    # type code (protocol notes §3.2) should win if the hub ever sends one.
    device = devices.classify_device_out({"id": 5, "name": "Weird", "percentage": 10, "type": 70})
    assert isinstance(device, devices.Shutter)


def test_classify_binary_input() -> None:
    device = devices.classify_device_in({"id": 6, "name": "Door sensor", "status": 1})
    assert isinstance(device, devices.BinaryInput)
    assert device.is_active is True


def test_classify_analog_input() -> None:
    device = devices.classify_device_in({"id": 7, "name": "Temp sensor", "value": 21.5})
    assert isinstance(device, devices.AnalogInput)
    assert device.value == 21.5


def test_build_action_options_merges_field_updates_over_raw() -> None:
    raw = {"id": 1, "name": "Kitchen light", "status": 0, "extra": "kept"}
    options = devices.build_action_options(raw, action=1)
    assert options["action"] == 1
    assert options["object"] == raw
    # buildActionOptions must round-trip the *full* device object, not a delta.
    assert options["object"]["extra"] == "kept"


def test_build_action_options_includes_percentage_when_given() -> None:
    raw = {"id": 2, "name": "Blinds", "percentage": 0, "processDuration": 10}
    options = devices.build_action_options(raw, action=2, percentage=75)
    assert options["percentage"] == 75
