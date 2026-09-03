"""Constants for the domotalk integration.

Every value here is cited against `docs/PROTOCOL.md` in the DONA-Clone repository
(the reverse-engineered wire protocol the original vendor app, and this repo's own
Android client, both speak) so the two clients stay independently compatible with
the same hub.
"""

from __future__ import annotations

DOMAIN = "domotalk"

# -- Connection (protocol notes §2.1) ---------------------------------------------
CONF_SECURE = "secure"
CONF_TRUST_ALL_CERTS = "trust_all_certs"

DEFAULT_SECURE = True
DEFAULT_TRUST_ALL_CERTS = True

WS_PATH = "/ws/"
SUBPROTOCOLS = ("domotalk", "ping-pong")
PING_INTERVAL_SECONDS = 2.0
CONNECT_TIMEOUT_SECONDS = 15.0
REQUEST_TIMEOUT_SECONDS = 10.0
CALLBACK_ID_WRAP = 10_000

# -- Coordinator behaviour ----------------------------------------------------------
# The push-update envelope itself is UNCONFIRMED (protocol notes §8) — the original
# app's own ViewModels treat any push as "something changed, re-fetch the subject"
# rather than parsing a delta (§8, last paragraph), so this integration does the same:
# debounce bursts of pushes, then re-read the affected lists.
PUSH_REFRESH_DEBOUNCE_SECONDS = 0.3
RECONNECT_CHECK_INTERVAL_SECONDS = 30

# -- Request envelope verbs (protocol notes §2.2) ----------------------------------
VERB_READ = "read"
VERB_CREATE = "create"
VERB_UPDATE = "update"
VERB_DELETE = "delete"
VERB_ACTION = "action"

# -- Subjects (protocol notes §4) --------------------------------------------------
SUBJECT_USER = "user"
SUBJECT_SESSION = "session"
SUBJECT_ROOM = "room"
SUBJECT_DEVICE_IN = "deviceIn"
SUBJECT_DEVICE_OUT = "deviceOut"
SUBJECT_BINARY_OUT = "binaryOut"
SUBJECT_PULSE = "pulse"
SUBJECT_SHUTTER = "shutter"
SUBJECT_DIMMER = "dimmer"
SUBJECT_AMBIENCE = "ambience"

# -- Action integer codes (protocol notes §4) --------------------------------------
ACTION_BINARY_OFF = 0
ACTION_BINARY_ON = 1

ACTION_PULSE_FIRE = 0

ACTION_SHUTTER_CLOSE = 0
ACTION_SHUTTER_OPEN = 1
ACTION_SHUTTER_SET_PERCENTAGE = 2

ACTION_DIMMER_SET_PERCENTAGE = 2

ACTION_AMBIENCE_STOP = 0
ACTION_AMBIENCE_RUN = 1

# -- Pulse subtypes (protocol notes §3.2, `PulseKind` in the Android client) -------
PULSE_KIND_SIREN = 10
PULSE_KIND_CHIME = 11
PULSE_KIND_LOCK = 20
PULSE_KIND_ARM_OUTPUT = 30
PULSE_KIND_DISARM_OUTPUT = 31
PULSE_KIND_ARM_DISARM_COUPLED = 32

# -- Numeric DpuDeviceCode, honoured first when present (protocol notes §3.2) ------
DPU_CODE_BINARY_OUT = 60
DPU_CODE_PULSE = 61
DPU_CODE_SHUTTER = 70
DPU_CODE_DIMMER = 71
