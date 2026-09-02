# DONA (winwel) Home-Automation App — Reverse-Engineered Protocol Specification

Source: static analysis of the jadx-decompiled DONA Android APK (package `com.winwel.dona.ui`), R8-obfuscated.
Decompiled source root: `apk_work/jadx_out/sources`
Decoded resources root: `apk_work/decoded`

All claims below cite the exact decompiled file and line numbers they were derived from. Anything not directly observed in code is explicitly marked **UNCONFIRMED**.

---

## 0. High-level architecture

The app is a **thin client**. Almost nothing is modeled persistently on-device (Room DB only stores 3 small local-only tables — see §3). All domain data (devices, rooms, scenes, alarms, users, logs) is fetched live, every session, from the hub over a **single persistent WebSocket connection**, using a custom JSON RPC-style protocol the app calls `domotalk`. There is **no REST/HTTP JSON API** and **no raw TCP/UDP socket use for device control** — the only raw-socket usage in the whole app is the LAN-discovery broadcaster (§1).

Two independent transports exist:
1. **UDP broadcast** — LAN discovery of hub/module hardware only (find the box's IP).
2. **WebSocket (ws/wss)** — everything else: login, reads, writes/actions, and live push updates, multiplexed over one connection using the `nv-websocket-client` library (obfuscated package `n4`, confirmed by its exact API shape — `n4/q0.java`, `n4/l0.java`).

Camera/video-door image feeds are a third, separate channel: plain HTTP(S) MJPEG URLs handed out by the hub over the WebSocket API and rendered directly (WebView or a custom bitmap decoder), not carried inside `domotalk`.

---

## 1. LAN discovery protocol

**Evidence:** `y4/d.java`, `y4/u.java`, `y4/b.java`, `y4/c.java`, `com/winwel/dona/ui/ui/ipsetup/IpSetupActivity.java`

### Transport
Plain UDP broadcast, implemented in `y4/u.java` (abstract `UdpBroadcaster`):
- Binds a `DatagramSocket` on a fixed local port (`new DatagramSocket(this.f10451a)`, `y4/u.java:99`), with `setBroadcast(true)` and `setReuseAddress(true)`.
- Sends `DatagramPacket`s to the **limited broadcast address `255.255.255.255`** (`y4/u.java:17,79`) on that same port number.
- After `f()` (start) is called it spawns a background loop that calls `socket.receive()` in a tight loop and forwards every received datagram to the abstract callback `d(byte[])` (`y4/u.java:37-57`).

### Ports and app-side sequence
`IpSetupActivity.w0()` creates **two independent broadcaster instances** (`IpSetupActivity.java:331-337`):
```java
y4.d dVar = new y4.d(0, 1, null);   // uses default port
dVar.n(new d());
y4.d dVar2 = new y4.d(7778);        // explicit port 7778
dVar2.n(new e());
```
`y4.d`'s default-arg constructor resolves the "default" port to **7777** (`y4/d.java:170-172`):
```java
public /* synthetic */ d(int i6, int i7, p5.g gVar) {
    this((i7 & 1) != 0 ? 7777 : i6);
}
```
So the app probes **UDP port 7777 and UDP port 7778 in parallel** — almost certainly two hub/module hardware generations or two device families (matches the two MTYPE families found in `y4/b.java`, see below).

Discovery is (re)triggered:
- Once automatically on first `IpSetupActivity` creation (`H` static flag, `IpSetupActivity.java:258-261`).
- On pull-to-refresh / the search menu action (`IpSetupActivity.java:283-284`, `320-329`).
- Additionally, `y4.d.a()` (invoked per broadcaster in `v0()`) sends the broadcast request every time `a()` is called.

### Request packet
`y4.d.a()` → `f6.b.b(...)` → class `b.b()` (`y4/d.java:91-109`):
```java
d.super.f();                                        // open+bind socket, start receive loop
byte[] bytes = dVar.f10395e.getBytes(v5.d.f9962b);   // f10395e == "domobroadcast"
d.super.e(bytes);                                    // broadcast it
new Timer("TimedDonaBroadcasterStop", false).schedule(new a(d.this), 2000L); // stop socket after 2s
```
`f10395e` is set in the constructor (`y4/d.java:140`): `this.f10395e = "domobroadcast";`

**Request = the ASCII string `"domobroadcast"` sent as a UDP broadcast datagram to `255.255.255.255:<port>`** (port 7777 or 7778), UTF-8 encoded. The socket stays open to receive replies for **2000 ms**, then is closed (`y4/d.java:87,102`).

### Response packet
`y4.d.d(byte[])` (`y4/d.java:148-155`):
```java
public void d(byte[] bArr) {
    if (bArr.length == this.f10395e.length() || bArr.length == 32) {
        return;                      // ignore echoes of own 13-byte broadcast, and 32-byte config-ack packets
    }
    f6.b.b(this, null, new a(new y4.b(bArr)), 1, null);   // otherwise parse as a device announcement
}
```
Any reply that is **not** exactly 13 bytes (`"domobroadcast".length()`) and **not** exactly 32 bytes is parsed as a JSON object by `y4.b` (`y4/b.java`, extends `org.json.JSONObject`):

```java
public b(byte[] bArr) {
    super(new String(bArr, UTF_8));
    MAC   = optString("MAC")
    IP    = optString("IP")
    GW    = optString("GW")
    SM    = optString("SM")
    DHCP  = optInt("DHCP") == 1
    MTYPE = optInt("MTYPE")     // module/device type code
    SN    = optString("SN")    // serial number
    HW    = optString("HW")    // hardware revision, dotted e.g. "1.2"
    FW    = optString("FW")    // firmware version, dotted "major.minor.patch"
}
```
(`y4/b.java:43-73`)

So **the discovery reply is a UTF-8 JSON object** with keys `MAC, IP, GW, SM, DHCP, MTYPE, SN, HW, FW`, e.g.:
```json
{"MAC":"AA:BB:CC:DD:EE:FF","IP":"192.168.1.50","GW":"192.168.1.1","SM":"255.255.255.0","DHCP":1,"MTYPE":0,"SN":"12345","HW":"1.2","FW":"1.4.0"}
```

`MTYPE` (only trusted if firmware ≥ 1.2, see `p()`/`j()` at `y4/b.java:75-125`) maps to a `y4.f` enum:
```
0 -> DPU            (wired)
1 -> D815           (wired)
2 -> D808           (wired)
3 -> WIFI_SHUTTER   (wireless)
4 -> WIFI_LIGHT     (wireless)
5 -> WIFI_OUTLET    (wireless)
else -> UNKNOWN
```
(`y4/b.java:204-206`, enum in `y4/f.java`). For older firmware (`FW` major<1 or (major==1 && minor<2)) the type is instead derived from `HW` (`y4/b.java:75-115`): HW `"0.x"`→DPU, `"1.x"`→D815, `"2.1.x"`→WIFI_SHUTTER, `"2.2.x"`→WIFI_LIGHT, `"2.3.x"`→WIFI_OUTLET.

The 32-byte binary packet that `d()` explicitly ignores (`y4/b.java:208-235`, method `v()`) is a **provisioning/config packet the app can also send back to a device** to set its network config, laid out as:
```
bytes[0..5]   = MAC (6 raw bytes)
byte [6]      = DHCP flag (0/1)
bytes[7..10]  = static IP (4 octets)
bytes[11..14] = subnet mask (4 octets)
bytes[15..18] = gateway (4 octets)
bytes[19..31] = reserved/zero-padded
```
This is used by the (out-of-scope for a hub client) Wi-Fi device onboarding flow (`y4/j.java`, `y4/o.java`, `y4/q.java`, `y4/t.java` — provisioning UI for new Wi-Fi light/shutter/outlet modules), not for talking to the main hub.

### De-duplication / UI
`IpSetupActivity.u0()` de-dupes discovered devices by MAC (`y4.c.b()`) across both broadcasters (`IpSetupActivity.java:303-318`). Tapping a discovered device opens `z4/h.java` ("HouseDetail") pre-filled with the device's `IP` (`y4/w.java:26-28`, `z4/h.java:42-49,284-287`) so the user can register it as a `Home` with that `localIp`.

**Summary for a replacement client:** broadcast the ASCII string `domobroadcast` to `255.255.255.255:7777` and `255.255.255.255:7778` (both, hub type unknown in advance), listen on the same two sockets for ~2s, and parse any non-13/non-32-byte UTF‑8 reply as JSON with the 9 keys above. `IP` is the hub's LAN address to then connect to over WebSocket (§2).

**UNCONFIRMED:** which physical hub SKUs answer on 7777 vs 7778; whether the hub itself needs to receive `domobroadcast` first (i.e. is it purely reactive, or does it also broadcast unsolicited announcements) — the code only shows the app-initiated request/reply pattern.

---

## 2. Authentication / session protocol

**Evidence:** `com/winwel/dona/ui/LoginActivity.java`, `u4/e.java`, `u4/d.java`, `u4/c.java`, `u4/b.java`, `n4/q0.java`, `n4/l0.java`, `p4/h.java`

### 2.1 Transport: WebSocket, not REST

`u4.e.a.b(String dnsOrIp, boolean secure, l callback)` (`u4/e.java:310-352`) builds the connection:
```java
q0Var.m(15000);                       // 15s handshake timeout
if (secure) {
    q0Var.n(u4.b.a("TLS"));           // custom SSLContext, see below
    q0Var.o(false);                   // verifyHostname = false
    url = "wss://" + dnsOrIp + "/ws/";
} else {
    url = "ws://" + dnsOrIp + "/ws/";
}
socket = q0Var.d(url);
socket.b("domotalk");                 // Sec-WebSocket-Protocol candidate #1
socket.b("ping-pong");                // Sec-WebSocket-Protocol candidate #2
socket.K(2000L);                      // WebSocket ping interval = 2000ms
socket.a(f9883k);                     // attach listener (class b, WebSocketAdapter)
socket.f();                           // connect asynchronously
```
`n4.q0` is the `com.neovisionaries.ws.client.WebSocketFactory` class (confirmed identical API surface at `n4/q0.java`: scheme validation `ws/wss/http/https`, default port selection **80 for ws / 443 for wss** when no port is given in the URL — `n4/q0.java:67-69`, `l() ` at line 71-82). `n4.l0` is `WebSocket` (`b`=addProtocol, `K`=setPingInterval, `a`=addListener, `f`=connectAsynchronously, `H`=sendText, `F`=disconnect — verified at `n4/l0.java:248-337`).

**Endpoint:** `ws://<host>/ws/` (cleartext) or `wss://<host>/ws/` (TLS) on the **default port for the scheme** (80 or 443) — no custom port is ever passed to the factory, so the hub's WebSocket server is expected to listen on 80/443 directly. The `secure` boolean is stored per-Home and is what toggles `ws` vs `wss` (`q4/n.java` fields `secureDns`/`secureLocalIp`; `LoginActivity.java:626-637`).

TLS trust is fully disabled for `wss` connections — `u4/b.java` builds an `SSLContext` with a **no-op `X509TrustManager`** (`u4/b.java:15-31`, `checkServerTrusted` does nothing) and `q0.o(false)` also disables hostname verification. This is consistent with talking to a LAN hub with a self-signed certificate — **any certificate is accepted**, so a replacement client can use a self-signed/no-cert TLS listener or plain `ws://` if the hub allows it. `AndroidManifest.xml` also sets `android:usesCleartextTraffic="true"` (`decoded/AndroidManifest.xml:12`), confirming cleartext `ws://` is expected/allowed; no `network_security_config.xml` resource exists in the decoded APK.

### 2.2 Message envelope

Every request sent over the socket is a JSON object built by the caller and finished off by `u4.e.a.l()`/`k()` (`u4/e.java:391-447`), which:
```java
if (token != null) json.put("token", token);
json.put("callback_id", <auto-incrementing int, wraps at 10000>);   // u4/e.java:297-303
socket.H(json.toString());                                          // send as text frame
```
The base shape of every request is:
```json
{
  "verb": "read" | "create" | "update" | "delete" | "action",
  "subject": "<entity name, see §4>",
  "options": { ... },        // present for create/update/action, and sometimes read
  "filters": [ {...}, ... ], // present on some read/action calls, see §2.4
  "token": "<session token>",   // added automatically once logged in
  "callback_id": <int>          // added automatically, used to correlate the response
}
```
Responses are correlated back to the caller purely by `callback_id`; the exact response-dispatch code lives in the (undecompilable) method `u4.e.b.h(WebSocket, String)` (`u4/e.java:608-614`, jadx: "Method not decompiled... instructions count: 878"). What is directly recoverable from the surviving fragments of that method (`u4/e.java:490-602`) is the **unsolicited push-update format** the hub sends for live device-state changes (not tied to any `callback_id`) — see §6.

Standard success response envelope (inferred from every callback consumer, e.g. `u4/c.java:113-136`, `w4/e.java:78-107`): `{"payload": <JSON string or array/object>, ...}` — note `payload` is frequently itself a **JSON-encoded string** that callers re-parse with `new JSONArray(jsonObject.getString("payload"))` (e.g. `x4/c.java:206`, `w4/e.java:87`), though for single-object reads it's read directly as `getJSONObject("payload")` (`w4/c.java:134`). Errors are surfaced to the app-level callback as a non-null `Exception` (transport-level: socket closed/timeout) — **no explicit wire-level error object shape was found** in decompilable code; error handling for RPC-level failures (e.g. wrong password) is inferred from behavior, not an observed schema — **UNCONFIRMED** exact error JSON shape.

### 2.3 Login sequence

`LoginActivity.C0()` (`LoginActivity.java:590-645`) tries, in order: (1) the Home's `dns` address if set, secure flag = `secureDns`; if that fails, (2) the Home's `localIp`, secure flag = `secureLocalIp`. Both attempts call `L0()` → `u4.e.f9873a.b(address, secure, callback)` (open the socket, §2.1), and on successful **socket connect** (not yet authenticated) proceed to the actual login:

**Step 1 — fetch user list** (`u4/c.java:119-122`):
```json
{"verb": "read", "subject": "user"}
```
Response `payload` is a JSON array of user objects (parsed by `q4.v`, `q4/v.java:44-47`):
```json
{"id": 1, "role": 0, "hidden": false, "photoUri": "...", "name": "Alice",
 "remoteAccessible": true, "house": 1, "enabled": true}
```
The app searches this array client-side for an entry whose `"name"` equals the username typed in (`u4/d.java:101-140` — **usernames are matched exactly/case-sensitively client-side, not verified server-side at this step**), and rejects users with `role == 0` (interpreted as "no such enabled user", error message hard-coded at `u4/d.java:124,132`: "Could not find user with that name...").

**Step 2 — create session** (`u4/c.java:113-117`, called with the resolved numeric `userId` and the password):
```json
{
  "verb": "create",
  "subject": "session",
  "options": { "userId": 3, "password": "<md5-hex>", "forever": true }
}
```
Response: `{"payload": {..., "token": "<opaque session token string>"}}` (`u4/d.java:81-83` reads `jsonObject.getString("token")`).

**Password hashing:** `p4/h.j(String)` (`p4/h.java:392-416`) — **MD5** of the UTF‑8 password bytes, hex-encoded lowercase, no salt:
```java
MessageDigest.getInstance("MD5").digest(password.getBytes(UTF_8)) -> lowercase hex string
```
This MD5 hex string is sent as `"password"` in the `create session` call above — **the password is only MD5-hashed, never salted, and this MD5 digest travels in the clear if `ws://` (non-TLS) is used.**

**Step 3 — store token.** `u4.d.a.e(token)` stores the token in the in-memory static field `u4.d.f9866e` (`u4/d.java:175-177`), which is then attached as `"token"` on every subsequent request automatically (§2.2). The token is **not persisted to disk** anywhere found in the decompiled code — the app must re-run the full login flow after process death (though the `Home` username/password themselves *are* persisted, see §3, so the app can auto-relogin silently).

**Session recovery** (used when the socket reconnects, `u4/e.java:113-116` `C0151a.b()`):
```json
{"verb": "action", "subject": "session", "options": {"token": "<token>"}}
```
(`u4/c.java:130-135`) — presumably re-validates/re-binds a previously issued token to the new socket instead of re-sending the password.

**Reconnect/backoff:** `u4.e.a.k()`/`l()` (used for the generic authenticated-request path) will, if the socket is null or not open, call `p4.h.f(context, callback)` to silently redo the whole connect+login flow from the saved `Home`/credentials once, then retry the original request exactly once (`e.f9884l` retry counter capped at 1, `u4/e.java:186-199,269-281`). `e.f9873a.j()` similarly force-disconnects and reconnects+resumes on demand (`u4/e.java:378-389`).

### 2.4 Query filters
Several `read`/`action` calls narrow results with a top-level `"filters"` array of `{ "field": "...", "operation": "equal"|"greater"|"lesser", "value": ... }` objects, e.g. filtering `deviceIn` by id (`v4/i.java:210-220`), or `masterLog` by `objectId` and a `date` range in **Unix seconds** (`v4/f.java:76-98`):
```json
{"verb":"read","subject":"masterLog","filters":[
  {"field":"objectId","operation":"equal","value":42},
  {"field":"date","operation":"greater","value":1690000000},
  {"field":"date","operation":"lesser","value":1690600000}
]}
```
Note: `userNotificationTarget` read instead nests filters **inside** `"options"` (`com/winwel/dona/ui/MainActivity.java:156-168`) — the placement of `filters` (top-level vs. under `options`) is inconsistent across call sites in the app itself; a replacement client should try to mirror whatever the real hub firmware actually expects (**UNCONFIRMED which placement the hub firmware really honors — this could not be resolved from decompiled code alone**).

---

## 3. Local data model (on-device Room DB) vs. live hub data model

### 3.1 What's actually persisted locally

`AppDatabase_Impl.java:42-46` — the **entire local Room schema**, only 3 tables (plus Room's own bookkeeping table):
```sql
CREATE TABLE IF NOT EXISTS `Home` (
  `name` TEXT NOT NULL, `dns` TEXT, `secureDns` INTEGER, `localIp` TEXT,
  `secureLocalIp` INTEGER, `username` TEXT, `password` TEXT,
  `stayConnected` INTEGER, `notificationId` TEXT, `codeOnDisarmAlarm` INTEGER,
  PRIMARY KEY(`name`)
);
CREATE TABLE IF NOT EXISTS `Division` (
  `floor` INTEGER, `id` INTEGER, `name` TEXT, `dns` TEXT, PRIMARY KEY(`id`)
);
CREATE TABLE IF NOT EXISTS `FavoriteDevice` (
  `id` INTEGER, `deviceId` INTEGER, `dns` TEXT, `counter` INTEGER, PRIMARY KEY(`id`)
);
```
- **`Home`** = one row per configured hub/site the phone knows about — connection profile: `name` (label, PK), `dns` (cloud/DDNS host, optional), `localIp` (LAN IP fallback), `secureDns`/`secureLocalIp` (bool → wss vs ws for each), `username`, `password` **(plaintext, as typed — not the MD5 form — stored client-side in Room)**, `stayConnected`, `notificationId` (FCM token last associated with this home), `codeOnDisarmAlarm` (whether the app should require re-entering a PIN before disarming — UI-only). Model class: `q4/n.java`.
- **`Division`** = a lightweight local cache of room/floor names (`id`, `name`, `floor`) plus which `Home` (`dns`) they belong to — used to avoid a network round trip for names before the real `room` read completes.
- **`FavoriteDevice`** = user's "favorite" device shortcuts (`deviceId`, which `Home`/`dns`, a `counter` for ordering).

**Everything else (devices, users, ambiences/scenes, alarms, logs) is NOT cached in Room** — it is fetched fresh from the hub every session over the WebSocket API and held only in in-memory `ArrayList`s inside per-screen ViewModels (`v4.i`, `w4.e`, `x4.g`, etc.). This confirms the hub is the sole source of truth.

### 3.2 Live domain model (from the WebSocket API's JSON, `q4`/`r4`/`t4`/`s4` packages)

**Base `Device` fields** — shared by every device subject (`q4/d.java:57-78`, `q4/d.i()` for the mirror used in write payloads):
```
id (int), name, description, enabled (bool), serialNumber, type (string — NOT the same
as the numeric q4.e code, this is a free-text device-type label), isStateful (bool),
isInterruptable (bool), subtype (int — see p4.h.b() icon-mapping switch below),
model (int), online (bool), ip, mask, gateway, dhcp (bool), room (int, room id / FK)
```
**Numeric `type` codes** (`q4/e.java`, used when the hub returns a `deviceOut`/`deviceIn` list to pick the concrete subclass — `x4/c.java:210-228`):
```
BinaryIn=10, Analog=20, Counter=30, OneWayInterruptor=40, ThreeWayInterruptor=50,
BinaryOut=60, Pulse=61, Shutter=70, Dimmer=71, VideoCamera=80, Intercom=81
```
**Output device subclasses** (all extend `r4.b`, which adds `module` (int) and `outPortNumber` (int) on top of the base `Device` — `r4/b.java`):
| Subject/type | Class | Extra fields | File |
|---|---|---|---|
| `binaryOut` (relay/switch/outlet) | `t4.b` | `status` (int, >0 = on) | `t4/b.java` |
| `pulse` (siren/chime/lock/arm-disarm output) | `t4.d` | `status` (int), `duration` (int) | `t4/d.java` |
| `shutter` | `t4.e` | `percentage` (int 0-100), `processDuration` (int) | `t4/e.java` |
| `dimmer` | `t4.c` | `percentage` (int 0-100) | `t4/c.java` |

`pulse` **subtype** values (icon/label mapping, `t4/d.java:48-86` and `p4/h.java` switch): `10`=siren, `11`=chime, `20`=lock, `30`=arm output, `31`=disarm output, `32`=arm+disarm (coupled).

**Input device base** (`deviceIn`, extends `r4.a`, adds `inPortNumber`, `module` — `r4/a.java`); concrete `BinaryIn` subclass `s4.b` adds `status` (int) with subtypes for door/window/gate/movement/flood/gas/fire/etc sensors (`s4/b.java`).

**Alarm** (`t4.a`, extends `t4.d`/Pulse — an alarm panel is itself a "pulse" output device wired to sensor inputs, `t4/a.java`):
```
alertInput (int, deviceIn id of the alarm's alert/status sensor)
statusInput (int, deviceIn id of arm/disarm status)
armOutput (int, deviceOut/pulse id to trigger ARM)
disarmOutput (int, deviceOut/pulse id to trigger DISARM)
coupledOutputs (bool)
```
**Ambience/Scene** (`q4.b`, `q4/b.java:112-179`):
```
id, name, isPlaying (bool), enabled (bool),
startTriggers: [ ... ] (list of trigger objects, class q4.u),
stopTriggers:  [ ... ] (list of trigger objects, class q4.u),
conditions:    [ ... ] (list of condition objects, class q4.c),
firstAction (int), firstActionType (int)
```
(Trigger/condition sub-objects `q4.u`/`q4.c` were not fully decompiled — **UNCONFIRMED** their exact internal fields; only that each serializes via a single `.a()` accessor into the array.)

**Room/Division** (`q4.f`, from the live `room` read, `q4/f.java:47-52`): `floor` (int), `id` (int), `name` (string).

**User** (`q4.v`, §2.3): `id, role, hidden, photoUri, name, remoteAccessible, house, enabled`.

**VideoCamera** (`r4.d`, on top of base `Device`, `r4/d.java`): `url` (full stream URL), `username`, `password` — see §5.

**VideoIntercom / video door station** (`r4.c`, on top of base `Device`, `r4/c.java`): `doorLock` (int, id of a `pulse` output device that unlocks the door), `feedPort` (int), `pictureFeedUri` (string, snapshot path), `videoFeedUri` (string, video path) — see §6.

---

## 4. Device / scene / alarm command protocol (all over the WebSocket, §2.2 envelope)

All "read list" calls follow the same pattern: `{"verb":"read","subject":"<X>"}` → `payload` = JSON array (or JSON-string-encoded array) of that subject's model.

| Subject | Read all | Notes |
|---|---|---|
| `deviceOut` | `x4/c.java:246-251` | Returns all output devices (`binaryOut`/`pulse`/`shutter`/`dimmer`), each tagged with numeric `type` (§3.2) so the client picks the right subclass. |
| `deviceIn` | `x4/c.java:231-237` | Returns all input/sensor devices. |
| `room` | `o4/h.java:106-111` | Returns `Division`/Room list. |
| `ambience` | `w4/e.java:160-166` | Returns scenes. |
| `alarm` | `v4/i.java:199-205` | Returns alarm panel configs (each an `Alarm`/pulse object, §3.2). |
| `videoCamera` | `.../surveillance/e.java:109-115` | Returns camera devices with stream URL/creds. |
| `videoIntercom` | `a5/g.java:109-115` | Returns video-door-station devices. |
| `user` | `u4/c.java:119-122` | Used for login (§2.3). |
| `masterLog` | `v4/f.java:76-99` | Event/audit log, filterable by `objectId` + `date` range. |

### Device actions (turn on/off, set value, pulse/trigger)

All actions share the shape:
```json
{
  "verb": "action",
  "subject": "binaryOut" | "pulse" | "shutter" | "dimmer" | "ambience",
  "options": {
    "object": { ...the FULL device JSON as returned by the read, with the field(s) you're changing updated... },
    "action": <int>,
    "percentage": <int, 0-100>   // only for shutter/dimmer "set position/level" actions
  }
}
```
Evidence: `x4/g.java:964-1039` (all four device-type actions), `w4/c.java:170-183` (ambience action), `a5/c.java:360-373` (pulse, used for the video-door lock relay).

**`action` integer semantics** (derived from the exact UI call sites that pick the value, `x4/g.java:345-448`):
- `binaryOut` (switch/relay): `1` = turn ON, `0` = turn OFF. UI toggles based on current `status`: `status>0 ? send 0 : send 1` (`x4/g.java:355-357`).
- `pulse` (momentary siren/chime/lock/arm/disarm trigger): always sent as `0` from the single "fire" button (`x4/g.java:360`, `a5/c.java:360-373`) — **UNCONFIRMED whether other integer values are meaningful for pulse** (the alarm arm/disarm outputs are themselves `pulse` devices, so triggering them is presumably also `action:0`).
- `shutter`: `0` = close, `1` = open, `2` = set to an explicit `percentage` (0-100) (`x4/g.java:364-378,442-444`).
- `dimmer`: `0`/`100` percentage shortcuts are sent via the same "set percentage" path (`action:2`) — the UI never sends a bare on/off for dimmers, only `action:2` with `percentage:0` (off) or `100` (full on), or the seek-bar value (`x4/g.java:369,378,446`).
- `ambience` (scene): `action:1` = start/run the scene, `action:0` = stop it — toggled off the scene's own `isPlaying` flag (`w4/c.java:153`: `isPlaying==true ? send 0 : send 1`).

### Update (persist config changes, not a live action)
```json
{"verb":"update","subject":"ambience","options":{"object": <full ambience JSON>}}
```
(`w4/e.java:183-193`) — used e.g. to persist toggling a scene's `enabled` flag.

### Delete
```json
{"verb":"delete","subject":"informationNotification", ...}   // z4/m.java:133-136
{"verb":"delete","subject":"session", ...}                     // z4/g0.java:350-351 (logout)
```

### Firebase push-target registration (so the hub knows where to send FCM alerts)
```json
{"verb":"create","subject":"userNotificationTarget",
 "options":{"object":{
   "manufacturer":"...", "model":"...", "version":"...", "platform":"Android",
   "user": <userId>, "notificationId": "<FCM token>"
 }}}
```
(`com/winwel/dona/ui/MainActivity.java:137-154`), and to check for an existing registration:
```json
{"verb":"read","subject":"userNotificationTarget",
 "options":{"filters":{"field":"userid","operation":"equal","value": <userId>}}}
```
(`MainActivity.java:156-169`).

---

## 5. Camera / surveillance protocol

**Evidence:** `com/winwel/dona/ui/ui/surveillance/{d,e,CamViewActivity,CamWebViewActivity,MyMjpegView}.java`, `r4/d.java`

1. App reads the camera list over the WebSocket: `{"verb":"read","subject":"videoCamera"}` → array of `r4.d` objects: `{"url": "...", "username": "...", "password": "...", ...base Device fields...}` (`r4/d.java:22-24`). **The hub supplies a complete, ready-to-use stream URL per camera** — the app does not construct it from `ip`/`port`; whatever host/port/path scheme the hub uses is opaque to the client (**UNCONFIRMED exact URL scheme/path the hub emits** — no observed sample string; it is very likely a `multipart/x-mixed-replace` MJPEG HTTP endpoint, based on how it's consumed, see below).
2. Tapping a camera launches `CamWebViewActivity` with `cam_url`, `cam_username`, `cam_password`, `title` extras (`.../surveillance/d.java:100-108`).
3. `CamWebViewActivity.onCreate()` (`CamWebViewActivity.java:69-136`) rewrites the URL to embed **HTTP Basic-Auth credentials directly in the URL's userinfo component** whenever both a username and password are present, e.g. `http://user:pass@host/path` (for `http://` — `CamWebViewActivity.java:90-113`) or `https://user:pass@host/path` (for `https://` — lines 116-130), then simply does `webView.loadUrl(rewrittenUrl)`. Android's WebView natively renders an MJPEG multipart stream loaded this way as a live image.
4. A second, unused-by-the-current-nav-flow path exists: `CamViewActivity` + `MyMjpegView` (`CamViewActivity.java`, `MyMjpegView.java`) — a **custom hand-rolled MJPEG client** (raw HTTP GET + manual multipart boundary parsing + `BitmapFactory.decodeByteArray` per frame, background `Thread`, `MyMjpegView.a.run()`). Its `onCreate` reads the same three extras but its username/password extraction results are discarded without being applied (`CamViewActivity.java:104-113`) — **this path appears to expect the credentials to already be embedded in the URL, or is dead/legacy code with a bug; treat `CamWebViewActivity`'s Basic-Auth-in-URL approach as the authoritative one for a replacement client.**

**Practical implication for a replacement client:** fetch the camera list via `read videoCamera`, then GET `http(s)://<username>:<password>@<host-from-url>/<path-from-url>` and treat the response as an MJPEG (`multipart/x-mixed-replace`) stream. **UNCONFIRMED**: the exact host/port/path the hub returns in `url` — this needs to be captured from a live hub or logged from the app.

---

## 6. Video door (intercom) protocol

**Evidence:** `a5/g.java`, `r4/c.java`, `a5/c.java`

1. Read the video-door stations: `{"verb":"read","subject":"videoIntercom"}` → array of `r4.c` objects (`r4/c.java:22-32`): base `Device` fields plus:
   - `doorLock` (int) — the id of a `pulse`-type `deviceOut` that unlocks the door strike.
   - `feedPort` (int) — presumably the port to combine with the device's own `ip` (a base `Device` field) for the video/picture feed.
   - `pictureFeedUri` (string) — path for a still snapshot.
   - `videoFeedUri` (string) — path for the live video feed.
   **UNCONFIRMED**: the code never assembles `ip`+`feedPort`+`*FeedUri` into a concrete URL anywhere in the decompiled sources found — only the raw fields are parsed and stored (no consumer code for `pictureFeedUri`/`videoFeedUri`/`feedPort` was located, meaning the UI screen that renders the door camera preview either reuses the generic camera viewer with a URL not shown here, or its consumer class was not among the files inspected). A replacement client will most likely need to try `http://<device.ip>:<feedPort><pictureFeedUri>` / `<videoFeedUri>` empirically against the real hub.
2. Unlocking the door: fetch the `pulse` device referenced by `doorLock` via a filtered `deviceOut` read —
   ```json
   {"verb":"read","subject":"deviceOut","filters":[{"field":"id","operation":"equal","value":<doorLock id>}]}
   ```
   (`a5/c.java:337-354`) — then fire it exactly like any other pulse action (§4):
   ```json
   {"verb":"action","subject":"pulse","options":{"object": <pulse device JSON>, "action": 0}}
   ```
   (`a5/c.java:360-373`).

---

## 7. Push notifications (Firebase Cloud Messaging)

**Evidence:** `com/winwel/dona/ui/network/MyFirebaseMessagingService.java`, `com/winwel/dona/ui/MainActivity.java`

- The app registers for FCM in the normal way; on token refresh (`MyFirebaseMessagingService.t(String)`, lines 58-63) the token is cached in `SharedPreferences` (`"com.winwel.dona.ui.token"`) and pushed to the hub via the `userNotificationTarget` `create` call (§4) — **so the hub itself (or a cloud relay it talks to) is responsible for sending FCM messages, most likely when the local WebSocket session is not connected** (e.g., app backgrounded/killed), for alerting the user (alarm triggered, door sensor, etc.).
- `MyFirebaseMessagingService.r(RemoteMessage)` (lines 33-56) only reads the **standard FCM `notification` payload** fields (`title`, `body`/`bodyLocalizationKey`) and shows a plain Android system notification that deep-links into `MainActivity` on tap. **No custom `data` payload with structured device-state fields was found being parsed** — i.e., FCM here is a "you have an alert, open the app" channel only. All actual state deltas are pushed live over the WebSocket (§2/§6 below) while connected, not via FCM.
- **UNCONFIRMED**: whether the hub also sends an FCM `data` payload in addition to `notification` (which would be invisible to `MyFirebaseMessagingService.r()` as decompiled, since it explicitly checks `p0Var.d() != null` — the `notification` block — and never inspects `p0Var.b()`/the `data` map). This could not be ruled out from static analysis alone.

---

## 8. Live state-update push protocol (over the same WebSocket)

**Evidence:** `u4/e.java:490-602` (partially decompiled fragment of `u4.e.b.h(WebSocket, String)`, the `onTextMessage` handler)

Aside from RPC responses correlated by `callback_id`, the hub pushes **unsolicited update messages** on the same socket whenever a device's live state changes (e.g., someone flips a physical switch, a sensor trips, a shutter finishes moving). The surviving decompiled fragment shows the message is unwrapped as:
```
message.request.options.object   -> the changed device's JSON object
object.id                        -> device id
```
and then, based on a device-type discriminator string (compared via `.equals()` in the observed fragment — the same subject vocabulary as elsewhere: `"shutter"`, `"counter"`, `"binaryIn"`, `"pulse"`, `"binaryOut"`, `"dimmer"`, `"analog"`), the relevant value field is extracted and every registered listener (`u4.e.f9873a.f()`, a list of `(deviceId, value) -> Unit` callbacks) is invoked:
- `"shutter"` or `"dimmer"` → reads `object.percentage` (double)
- `"counter"` or `"analog"` → reads `object.value` (double)
- `"binaryIn"`, `"pulse"`, or `"binaryOut"` → reads `object.status` (double)

So the inferred unsolicited push shape is approximately:
```json
{
  "request": {
    "options": {
      "object": { "id": 42, "<type>": "shutter", "percentage": 55, ... }
    }
  }
}
```
(The exact top-level key that carries the type discriminator — shown here as `"<type>"` — and whether there is a `"subject"`/`"verb"` at the top level of push messages, could not be fully recovered because jadx failed to decompile the surrounding control flow: `u4/e.java:602-608` — *"Method not decompiled... instructions count: 878"*.) **UNCONFIRMED** exact envelope; the value-extraction logic above is solid (it's literal string/getter calls jadx did manage to recover from the bytecode dump), but the enclosing message shape is a best-effort reconstruction from the fragment and should be confirmed against real traffic.

A UI-side registration API exists for these pushes: `u4.e.f9873a.h()` returns the callback list used by e.g. `v4/i.java` (Alarm ViewModel, `b` class at line 78-105) and `w4/e.java` (Ambience ViewModel, `a` class at line 30-58) to know when to re-fetch their subject after any change is observed.

---

## 9. Constants / magic values quick-reference

| Constant | Value | Source |
|---|---|---|
| Discovery broadcast string | `"domobroadcast"` | `y4/d.java:140` |
| Discovery UDP ports | `7777` and `7778` | `y4/d.java:171`, `IpSetupActivity.java:334` |
| Discovery broadcast address | `255.255.255.255` | `y4/u.java:17` |
| Discovery listen window | 2000 ms | `y4/d.java:102` |
| WebSocket path | `/ws/` | `u4/e.java:329` |
| WebSocket subprotocols | `domotalk`, `ping-pong` | `u4/e.java:333,337` |
| WebSocket connect timeout | 15000 ms | `u4/e.java:317` |
| WebSocket ping interval | 2000 ms | `u4/e.java:341` |
| Default ws/wss ports | 80 / 443 (scheme default, not hard-coded by app) | `n4/q0.java:68` |
| Password hash | MD5, hex, lowercase, no salt | `p4/h.java:392-416` |
| TLS trust | Trust-all `X509TrustManager`, hostname verification off | `u4/b.java:15-31`, `u4/e.java:319` |
| Cleartext HTTP/WS allowed | `android:usesCleartextTraffic="true"` | `decoded/AndroidManifest.xml:12` |

---

## 10. What we know vs. what needs live packet capture / a real hub to confirm

### Solid (directly read from decompiled logic, high confidence)
- UDP discovery: exact broadcast string, both ports, broadcast address, 2s window, and the full 9-field JSON reply schema.
- Transport for everything else is a single WebSocket to `ws(s)://<host>/ws/` on the scheme's default port, using `nv-websocket-client` with subprotocols `domotalk`/`ping-pong` and a 2s ping interval.
- Login sequence: `read user` → client-side name match → `create session` with numeric `userId` + MD5(password) → token → token attached to all subsequent requests as `"token"`.
- The `{verb, subject, options, callback_id}` request envelope and the full catalogue of subjects actually exercised by the app (`user`, `session`, `room`, `deviceIn`, `deviceOut`, `binaryOut`, `pulse`, `dimmer`, `shutter`, `ambience`, `alarm`, `videoCamera`, `videoIntercom`, `masterLog`, `informationNotification`, `notification`, `userNotificationTarget`).
- Action integer codes for binaryOut/shutter/dimmer/pulse/ambience and the `percentage` field for shutter/dimmer.
- Full local Room schema (3 tables) and the complete live device/room/scene/alarm/user field lists as parsed by the app's model classes.
- Camera viewing: HTTP(S) URL with Basic-Auth embedded in the userinfo, loaded as an MJPEG stream in a WebView.
- Password is MD5-only, TLS certificate/hostname validation is fully disabled by the app, and cleartext WS is explicitly allowed — all real, exploitable-if-relevant security properties, not guesses.

### Needs live hub / packet capture to fully confirm
- The **exact JSON success/error envelope** at the RPC level (is there a `"status"` field? what does a rejected `create session` actually look like on the wire?) — only the happy-path shape used by callers was recoverable.
- The **exact unsolicited push-update envelope** (§8) — jadx could not decompile the handler method body; the value-extraction sub-logic is solid but the surrounding JSON structure is inferred.
- Whether `"filters"` belongs at the top level of the request or nested under `"options"` — the app itself is inconsistent, so the real hub's accepted shape must be confirmed empirically.
- The **camera `url` field's actual scheme/host/port/path pattern** returned by a real hub (never observed as a literal string in the app, only that it's used opaquely).
- The **video-door feed URL construction** from `ip` + `feedPort` + `pictureFeedUri`/`videoFeedUri` — no consumer code was found that assembles these into a request.
- Whether FCM messages ever carry a structured `data` payload in addition to the plain `notification` payload the app is observed to handle.
- Whether UDP ports 7777 vs 7778 correspond to two different hub product lines, firmware branches, or one is legacy — both are always probed together by the app with no visible logic distinguishing "which hub do I have" beyond the reply's own `MTYPE`/`HW`/`FW` fields.
- Whether the hub ever broadcasts discovery announcements unprompted (vs. only replying to `domobroadcast`).
