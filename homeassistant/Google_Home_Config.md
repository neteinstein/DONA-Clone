# Google Home setup walkthrough

Step-by-step instructions for getting `"Hey Google, turn on the …"` working
against your hub, starting from nothing — including getting Home Assistant
itself running on a Raspberry Pi, if you don't already have a Home Assistant
instance. For what this integration is and why it's the right approach, see
[`README.md`](README.md) — this file is just the detailed setup checklist.

There are two independent paths, depending on whether you use Home Assistant
Cloud (Nabu Casa). Pick one; you don't need both.

- **Path A — Nabu Casa (Home Assistant Cloud):** a few clicks, no Google
  Cloud account needed. Use this unless you have a specific reason not to.
- **Path B — Self-hosted `google_assistant` integration:** no subscription,
  but you manage a public HTTPS endpoint and a Google Cloud/Actions project
  yourself. More setup, more moving parts to maintain.

## 0. First time only: set up Home Assistant on a Raspberry Pi

**If you already have Home Assistant running somewhere, skip to
[1. Install the domotalk integration](#1-install-the-domotalk-integration).**
This integration is a plugin — it only exists once there's a live Home
Assistant instance to install it into, and Home Assistant is server
software that has to run continuously on its own device (it isn't part of
the DONA-Clone app and doesn't run on your phone).

### What you need

- A Raspberry Pi — a **Pi 4 or Pi 5** is recommended; a Pi 3B+ works but is
  noticeably slower. (If you already own any of these, it's enough — no
  need to buy a newer one just for this.)
- A **microSD card, 32GB or bigger** (a USB drive/SSD works too on Pi 4/5,
  and is faster/more reliable long-term, but a microSD card is the simplest
  starting point).
- The **power supply** for your Pi model (USB-C for Pi 4/5).
- An **Ethernet cable**, if you can — plugging in beats Wi-Fi here, since
  this Pi needs to stay on and reachable 24/7.
- Any computer (Mac/Windows/Linux) to do the one-time setup from.

### Step 1 — flash Home Assistant OS onto the SD card

1. On your computer, install the free
   **[Raspberry Pi Imager](https://www.raspberrypi.com/software/)**.
2. Put the SD card into your computer (using a USB adapter if needed).
3. Open Raspberry Pi Imager:
   - **Choose Device** → pick your exact Pi model.
   - **Choose OS** → **Other specific-purpose OS** → **Home automation** →
     **Home Assistant** → pick **Home Assistant OS** for your Pi model.
   - **Choose Storage** → select the SD card (double-check you've picked the
     right drive — everything on it will be erased).
   - Click **Next**, then confirm **Write**. If it offers to apply OS
     customization (username/Wi-Fi/etc.), skip that — Home Assistant OS
     doesn't use it.
4. When it's done, eject the card and put it in the Pi (powered off).

### Step 2 — first boot

1. Connect the Ethernet cable first, then plug in power last.
2. Wait. The very first boot takes **10–20 minutes** while it expands
   itself and installs updates — there's no progress bar, this is normal;
   just leave it alone.
3. From another device on the same network, open a browser and go to
   **`http://homeassistant.local:8123`**. If that doesn't load, check your
   router's connected-devices list for something named "homeassistant" and
   use its IP address instead (e.g. `http://192.168.1.50:8123`).
4. You'll land on Home Assistant's onboarding screen: create your admin
   account, set your home's location, and click through (or skip) the
   integrations it offers to auto-detect — none of them are this hub, so
   none are required here.

You now have a working Home Assistant instance running on your Pi.

## 1. Install the domotalk integration

Home Assistant OS doesn't expose its files as a normal network/USB drive by
default, so the easiest way in as a first-time user is to add one small
official add-on that does:

0. On your computer, get a copy of this repository if you don't have one:
   on the repo's GitHub page, **Code → Download ZIP**, then unzip it. You
   only need the `homeassistant/custom_components/domotalk/` folder inside
   it for what follows.
1. **Settings → Add-ons → Add-on Store**, search for **"Samba share"**,
   install it, then start it (toggle "Start on boot" too).
2. From your computer, connect to Home Assistant's config folder as a
   network share:
   - **Mac**: Finder → **Go → Connect to Server…** → enter
     `smb://homeassistant.local` → Connect.
   - **Windows**: File Explorer → type `\\homeassistant.local` in the
     address bar → Enter.
   - (If prompted for credentials, use whatever the Samba share add-on's
     configuration page shows, or connect as Guest if you left it open.)
3. Open the **`config`** share, then the **`custom_components`** folder
   (create it if it isn't there yet).
4. Copy this repository's `homeassistant/custom_components/domotalk/`
   folder into it, so you end up with `config/custom_components/domotalk/`
   containing `manifest.json`, `client.py`, and the rest of its files.
5. Back in Home Assistant: **Settings → System → Restart Home Assistant**.

## 2. Add the hub and confirm it worked

1. **Settings → Devices & Services → Add Integration → "DONA-Clone (domotalk
   hub)"**, and fill in the form:
   | Field | What to enter |
   |---|---|
   | Host | The hub's IP or DDNS hostname — the same one you use in the DONA-Clone app |
   | Username | The same username you use to log into the app |
   | Password | The same password you use to log into the app |
   | Use a secure connection (wss://) | On if your hub setup uses `wss://`, off for plain `ws://` |
   | Trust the hub's certificate without validation | Leave on — the hub serves a self-signed certificate, same as the app does |
2. Confirm the setup succeeded and your devices/scenarios show up under
   **Settings → Devices & Services → domotalk** as entities. If a device you
   expect is missing, see [`README.md`](README.md#whats-included) for which
   hub device types map to which entities (alarms/cameras/the video
   intercom are intentionally not exposed).
3. Optional but recommended: rename any entity with an awkward name (edit
   the entity → change "Name") before exposing it to Google — the name you
   see in Home Assistant is the phrase Google Assistant will listen for.

## Path A — Nabu Casa (Home Assistant Cloud)

1. **Settings → Home Assistant Cloud**. If you don't already have Nabu Casa,
   you'll need a subscription (it includes far more than this, but this is
   the easiest path to Google Assistant support).
2. Under **Google Assistant**, toggle it **on**.
3. Choose which entities to expose — either "expose all" or, better, pick
   specific entities/areas so you don't clutter Google Home with things you
   never want to say out loud. All of `switch`/`light`/`cover`/`lock`/
   `button`/`scene` entities created by this integration are supported.
4. Open the **Google Home** app on your phone:
   - **Add → Set up device → Works with Google**.
   - Search for **"Home Assistant"**.
   - Sign in and follow the OAuth prompt to link your Nabu Casa account.
5. Your exposed devices should appear in the Google Home app within a few
   seconds. If they don't, go back to step 2 and confirm the toggle is still
   on, or check **Settings → Home Assistant Cloud → Google Assistant → Sync
   devices**.

Done. Test with a phrase per entity type — see [Testing it](#testing-it) below.

## Path B — Self-hosted `google_assistant` integration

Use this only if you're not using Nabu Casa. It requires a public HTTPS
endpoint that Google's servers can reach, and a Google Cloud project you
register and maintain yourself.

1. **Public HTTPS endpoint**: Home Assistant must be reachable from the
   internet over HTTPS (a reverse proxy with a real certificate, e.g. via
   Let's Encrypt/Traefik/Nginx, or Home Assistant's own remote-access
   feature). This is a prerequisite of Home Assistant's `google_assistant`
   integration in general, not specific to this hub — see Home Assistant's
   own docs if you don't already have this.
2. **Google Cloud project**:
   - Create a project at [console.cloud.google.com](https://console.cloud.google.com).
   - Enable the **HomeGraph API** for that project.
   - Create a **service account**, grant it the `Service Account Token
     Creator` role, and download its JSON key file. Place it somewhere
     Home Assistant can read (e.g. `config/google_assistant_key.json`).
3. **Actions on Google / Smart Home project**:
   - Go to the [Actions on Google console](https://console.actions.google.com)
     and create a new **Smart Home** project (use the same Google Cloud
     project from step 2).
   - Fill in the minimal app info it requires (name, images) — this doesn't
     need to be published publicly, just enough to pass validation for
     account linking.
   - Under **Account linking**, configure it to point at Home Assistant's
     OAuth endpoints (`https://<your-ha-url>/auth/authorize` and
     `.../auth/token`), with a client ID/secret you choose.
4. **`configuration.yaml`**:
   ```yaml
   google_assistant:
     project_id: your-google-cloud-project-id
     service_account: !include google_assistant_key.json
     report_state: true
     exposed_domains:
       - switch
       - light
       - cover
       - lock
       - button
       - scene
   ```
   Adjust `exposed_domains` if you only want some of this integration's
   entity types reachable by voice, and restart Home Assistant.
5. In the **Google Home** app: **Add → Set up device → Works with Google**,
   search for the app name you gave your Smart Home project in step 3, and
   link your account (this triggers the OAuth flow against your Home
   Assistant instance).
6. If devices don't show up, call the `google_assistant.request_sync`
   service from **Developer Tools → Actions** to force Google to re-fetch
   your device list.

This path has more to maintain long-term (the public endpoint, the Google
Cloud project) — Path A is simpler if you can use it.

## Testing it

Try one phrase per entity type once linked:

- Switch (`binaryOut`): *"Hey Google, turn on the [name]."*
- Light (`dimmer`): *"Hey Google, set the [name] to 50%."*
- Cover (`shutter`): *"Hey Google, open/close the [name]."*
- Lock (`pulse`, lock subtype): *"Hey Google, unlock the [name]."*
  (Locking is also accepted, but the hardware only exposes one momentary
  action — see the note in `README.md`'s "Known limitations".)
- Button (`pulse`, siren/chime): Google exposes momentary buttons as a
  "trigger" — try *"Hey Google, activate/turn on the [name]."*
- Scene (`ambience`): *"Hey Google, activate [name]."*

## Troubleshooting

- **`homeassistant.local:8123` doesn't load** → some networks/routers don't
  support that hostname. Find the Pi's IP from your router's connected-device
  list instead and use `http://<that-ip>:8123`.
- **Can't connect to the Samba share** → confirm the "Samba share" add-on is
  actually started (not just installed) under **Settings → Add-ons**, and
  that you're on the same network as the Pi. Some networks block SMB between
  devices — as a fallback, install the **"Studio Code Server"** or
  **"Terminal & SSH"** add-on instead and copy the files through that.
- **Entity shows "unavailable" in Home Assistant** → check
  **Settings → Devices & Services → domotalk** for a connection error; the
  integration will retry the connection automatically, but a wrong
  host/credentials needs re-entering via the integration's "Reconfigure"
  option.
- **Google says "that's not supported yet" for a command** → check the
  entity's domain is in your exposed list (Path A: the entity picker in HA
  Cloud settings; Path B: `exposed_domains` in `configuration.yaml`).
- **State in Google Home looks stale after a physical switch flip** → this
  integration re-reads state on any push notification from the hub rather
  than parsing a delta out of it, since the exact push envelope is
  unconfirmed reverse-engineered protocol (see `README.md`'s "Known
  limitations" and `docs/PROTOCOL.md` §8/§10 in the repository root) — there
  can be a brief debounce delay, not a permanent desync.
- **A device you expected isn't in Home Assistant at all** → it's probably
  one of the intentionally unexposed types (alarm arm/disarm, camera, video
  intercom) — see `README.md`'s "What's included" table.
