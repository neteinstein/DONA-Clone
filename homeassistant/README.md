# domotalk — a Home Assistant integration for the DONA hub

This is how you get **"Hey Google, turn on the living room"** working against
the same home-automation hub the DONA-Clone Android app controls.

## Why this exists

DONA-Clone (the app in the rest of this repository) is a pure Android
*client*: it opens an outbound WebSocket to the hub, has no server, and has
no voice or NLU layer — so Google Home has no way to reach it directly, and
nothing about the app itself needed to change.

Home Assistant, on the other hand, already has first-class Google Assistant
support. So this integration gives the hub a second, headless client that
Home Assistant (and, through it, Google Home) knows how to talk to. It
connects straight to the hub over the exact same `domotalk` WebSocket
protocol this repository reverse-engineered and documented in
[`../docs/PROTOCOL.md`](../docs/PROTOCOL.md) — it's an independent client of
the hub, not a bridge to the Android app.

```
Google Home  <-->  Google Assistant  <-->  Home Assistant  <-->  domotalk hub  <-->  DONA-Clone app
              (Nabu Casa cloud, or       (this integration,        (existing
               self-hosted               local network)            hardware)
               google_assistant)
```

## What's included

`custom_components/domotalk/` — a `local_push` Home Assistant integration:

| Hub device | HA entity |
|---|---|
| `binaryOut` (relay/switch/outlet) | `switch` |
| `dimmer` | `light` (brightness) |
| `shutter` | `cover` (open/close/set position) |
| `pulse`, subtype `lock` | `lock` |
| `pulse`, subtype `siren`/`chime` | `button` |
| `ambience` ("Scenario") | `scene` |

Not exposed, on purpose (matching the DONA-Clone app's own "not implemented,
on purpose" list): alarm arm/disarm pulse outputs, cameras, the video door
intercom.

State updates arrive live over the same push channel the hub uses for
everything else — no polling.

## Installing

1. Copy `custom_components/domotalk/` into your Home Assistant config
   directory, so you end up with `config/custom_components/domotalk/`.
   (Or add this repository as a custom HACS repository pointing at the
   `homeassistant/` folder, if you use HACS.)
2. Restart Home Assistant.
3. **Settings → Devices & Services → Add Integration → "DONA-Clone (domotalk
   hub)"**, and enter the same host and credentials you use in the app.

## Enabling Google Home voice control

Once the hub's devices show up as Home Assistant entities, turning on Google
Home is entirely Home Assistant/Google configuration — no more code needed.

**If you use Home Assistant Cloud (Nabu Casa):**
1. Settings → Home Assistant Cloud → enable "Google Assistant".
2. Choose which entities/areas to expose (or expose everything).
3. In the Google Home app: **Add → Set up device → Works with Google →
   search for "Home Assistant"** and link your account.

That's it — voice commands work immediately, and Nabu Casa handles the
account linking and cloud fulfillment for you.

**If you're self-hosting (no Nabu Casa):** use Home Assistant's built-in
[`google_assistant`](https://www.home-assistant.io/integrations/google_assistant/)
integration instead. It needs a public HTTPS endpoint reachable by Google and
a Google Actions/Cloud project you register yourself — more setup, but still
entirely Home Assistant/Google-side configuration; see the linked docs for
the exact steps.

## Known limitations

Several protocol details are marked `UNCONFIRMED` in
[`../docs/PROTOCOL.md`](../docs/PROTOCOL.md) §10 because they could only be
recovered from static analysis of the original app, not a live hub:

- The exact envelope of unsolicited push updates. This integration follows
  the original app's own strategy (§8): treat any push as "something
  changed", and re-read the affected lists, rather than trying to parse a
  delta out of an unconfirmed shape.
- Whether `filters` belongs at the top level of a request or nested inside
  `options` — not exercised by this integration (it only ever reads full
  lists), so it doesn't matter here, but is worth knowing if you extend it.
- The exact semantics of non-zero `pulse` action codes.

If something doesn't behave as expected against your real hub, check that
section first.
