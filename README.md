# Home Wi-Fi Monitor TV

A local-first Android TV dashboard for watching Wi-Fi device traffic on Xiaomi/MiWiFi-compatible routers. The TV app talks directly to the router from the television, so it does not need a companion computer once installed.

The repository also includes a small browser dashboard for development and debugging.

## Features

- Android TV interface designed for landscape screens and remote-control navigation.
- One-second dashboard refresh with cached slower router calls to avoid overloading the router UI.
- WAN download/upload metrics, active device count, Mesh node count, and per-device speed rows.
- Router settings dialog on first launch; credentials stay in the TV app's local storage.
- Optional local browser dashboard using the same router API parsing logic.

## Repository Layout

```text
android-tv/      Android TV app source
web-dashboard/   Optional Node.js browser dashboard and parser tests
LICENSE          MIT license
README.md        Project documentation
```

## Privacy Notes

This project is intended for a trusted home network.

- The app does not include telemetry, analytics, cloud sync, or external API calls.
- Router host and admin password are not hard-coded in the repository.
- Built APKs, screenshots, local environment files, keystores, and machine-specific Android files are ignored by Git.
- The browser dashboard has no built-in authentication. Keep it bound to localhost unless you put it behind your own trusted access control.

## Android TV App

Requirements:

- Android Studio or a compatible Android SDK setup
- JDK 17
- Android Gradle Plugin 8.7.3-compatible Gradle installation

Build from the Android project directory:

```bash
cd android-tv
gradle assembleDebug
```

For a release build:

```bash
cd android-tv
gradle assembleRelease
```

On first launch, open the settings dialog and enter your router admin host and password. The host can be a local hostname or any address your TV can reach on the home network.

Remote-control shortcuts:

- D-pad: move through filters, buttons, and device rows
- Menu or Settings key: open router settings
- Refresh key or `R`: refresh immediately
- Channel/Page keys: scroll the device list

## Browser Dashboard

The browser dashboard is optional. It is useful while testing router parsing and UI ideas on a development machine.

```bash
cd web-dashboard
npm test
cp .env.example .env
```

Edit `.env` with your router host and password, then start:

```bash
npm start
```

By default it binds to `localhost`. To expose it elsewhere on your trusted network, set `BIND_HOST` yourself and understand that the dashboard endpoint is not authenticated.

## Configuration

`web-dashboard/.env.example` documents the supported local environment variables:

- `ROUTER_HOST`: router admin host reachable from the machine running the dashboard
- `ROUTER_PASSWORD`: router admin password
- `PORT`: browser dashboard port
- `BIND_HOST`: interface hostname for the browser dashboard
- `SNAPSHOT_TTL_MS`: short cache window for browser dashboard snapshots

The Android TV app stores its configuration through the in-app settings dialog rather than a committed config file.

## Security

Use a router account and network environment you trust. The project reads router admin pages and device lists from the local router UI, which may include device names and network identifiers at runtime. Do not publish runtime logs, screenshots, built packages with embedded test data, or local `.env` files.

## License

MIT
