# DONA Clone

A native Android app that replaces the **WinWel DONA** home-automation app for the
existing physical hub ("DPU") already installed in the house it was built for. The
vendor is discontinuing the original app; this project exists so the owner doesn't
lose the ability to control their own hardware.

It talks **directly to the hub on the local network (or via DDNS)** using a protocol
recovered by reverse-engineering the original APK. There is no vendor cloud, no
account, and no telemetry involved.

## What it does

- Discovers the hub on the LAN (UDP broadcast) and lets you register it as a "House"
  (name, local IP, optional DDNS address, credentials).
- Logs in and keeps a live WebSocket session to the hub.
- Lists rooms and every device the hub reports (lights, dimmers, shutters, outlets,
  locks/sirens/chimes, and read-only sensors), with live updates when a physical
  switch or sensor changes state.
- Lets you control switches, dimmers and shutters, and fire momentary
  outputs (locks, sirens, chimes).
- Lists and triggers "Scenarios" (scenes/ambiences).

**Not implemented, on purpose:** alarms (arm/disarm panel), camera surveillance, the
video door intercom, and the "external app" delegation the original app used for sound
systems. The protocol notes below still document what the original app did for those,
in case that decision is revisited later.

## Protocol

Full write-up, with file:line citations against the decompiled original app and an
explicit confidence rating for every claim, lives in [`docs/PROTOCOL.md`](docs/PROTOCOL.md).
The short version:

- **Discovery:** UDP broadcast of the ASCII string `domobroadcast` to
  `255.255.255.255` on ports `7777` and `7778`; replies are a small JSON object with
  the hub's MAC/IP/serial/firmware.
- **Everything else** (login, reading devices/rooms/scenes, sending commands, live
  state pushes) is a single persistent **WebSocket** to `ws(s)://<host>/ws/`, carrying
  a custom JSON-RPC-ish protocol the original app calls `domotalk`:
  `{"verb": "read"|"create"|"update"|"delete"|"action", "subject": "...", "options": {...}}`.
- **Auth:** `read user` to resolve a username to a numeric id client-side, then
  `create session` with that id and an **MD5** hash of the password (no salt — a
  property of the hub's own protocol, not a choice made here). The resulting token is
  attached to every subsequent request.
- **Commands:** turning a device on/off, opening a shutter, or running a scene all go
  through `{"verb":"action","subject":"<type>","options":{"object": <full device JSON>, "action": N, "percentage"?: N}}`
  — the hub expects the complete device object back with the changed field(s) updated,
  not a delta.

Several details (the exact live-push envelope, whether `filters` belongs at the top
level or inside `options`) could not be recovered from static analysis alone and are
flagged `UNCONFIRMED` in the protocol doc — they're implemented as a best-effort
reconstruction and should be double-checked against the real hub's traffic if
something doesn't work.

### A note on security

This client deliberately reproduces two properties of the *original* app's protocol,
because breaking wire compatibility would mean it can't talk to the existing hub at
all:

- Passwords are hashed with unsalted MD5 before being sent (`core/data/.../PasswordHasher.kt`).
- A `wss://` (secure) connection accepts the hub's certificate without validating it —
  the original app does the same, because the hub typically serves a self-signed
  certificate on the LAN (`core/network/.../TrustAllCerts.kt`).

Neither of these is a choice this project is making for its own sake; they're
constraints imposed by the hardware. Both are isolated to the smallest possible scope
and documented at their definition site.

## Getting started

1. Open the project root in Android Studio (Ladybug or newer) and let it sync — the
   Gradle wrapper (`./gradlew`) will fetch the exact Gradle/AGP/Kotlin versions
   pinned in `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml`.
   Gradle 8.7 needs a JDK between 8 and 21 to *run* Gradle itself — if Android
   Studio reports an "Incompatible Gradle JVM version" (e.g. it picked JDK 22+ by
   default), set **Settings → Build, Execution, Deployment → Build Tools → Gradle →
   Gradle JDK** to JDK 17 or 21.
2. Build/run the `app` module on a device or emulator **on the same network as the
   hub** (LAN discovery needs real UDP broadcast, which doesn't work from most
   emulator NAT configurations — use a physical device, or an emulator configured
   with bridged networking, for the discovery flow).
3. On first launch: **Add a house** → either let it scan the network and tap the
   discovered hub to fill in the IP, or type in the local IP / DDNS address by hand →
   enter the same username/password you used in the original DONA app.

No backend, API keys, or `google-services.json` are needed — this app does not use
Firebase/FCM (the original app's push notifications depend on the vendor's cloud
relay, which this project has no access to and does not attempt to replicate).

### Building from the command line

```bash
./gradlew assembleDebug        # build a debug APK
./gradlew testDebugUnitTest    # run unit tests
./gradlew koverHtmlReport      # coverage report -> build/reports/kover/html/index.html
./gradlew ktlintCheck lintDebug
```

## Project structure & conventions

See [`AGENTS.md`](AGENTS.md) for the module map, architecture rules, and testing/lint
conventions this codebase follows.

## Continuous integration

Every pull request runs four independent jobs in parallel (`.github/workflows/android-ci.yml`):
compile, unit tests, code coverage, and lint.
