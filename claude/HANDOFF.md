# Handoff

## 2026-09-28 — v1.1.1 startup hotfix

- Removed automatic mDNS startup work; the app now opens in a safe state with `homeassistant.local` prefilled and exposes discovery as an explicit button.
- Added an application-level crash marker that records only the app version, Android SDK, exception class, and Listener Lab stack frames—never credentials, server addresses, or Home Assistant data.
- Added a startup recovery screen with copyable safe diagnostics and an in-app retry path.
- Added Robolectric Android 15 regression coverage for both a normal cold start and recovery after a recorded crash.
- Release build, JVM tests, lint, APK signing, and signature verification pass. No live Home Assistant or physical device was contacted.

## 2026-09-28 — Discovery, OAuth, guarded desktop recovery, and visual redesign

- Added standards-based mDNS discovery for `_home-assistant._tcp.local.` and the normal Home Assistant OAuth authorization-code flow.
- Added Android Keystore encryption for saved access/refresh credentials plus an explicit forget action; Companion app private data is not accessed.
- Reworked the Android screen into a hard-tech Listener Lab interface with an animated listener-map visualizer and clear scan states.
- Added a conditional firmware-candidate result. Google/Nest/Cast speakers alone do not unlock firmware recovery.
- Added a gated desktop Web Serial recovery tool. It requires a qualifying report, USB selection, valid HTTPS ESP Web Tools manifest, safety checks, and explicit confirmation before exposing the flash control.
- Added a GitHub Pages OAuth client landing page and expanded JVM tests.
- No live Home Assistant or physical device was contacted during development.

## 2026-09-28 — Initial Android discovery app

- Created a native Android app with a deliberately small connection-and-report interface.
- Added read-only Home Assistant REST and WebSocket inventory for config, states, device registry, entity registry, Assist pipelines, and supported satellite configuration.
- Added generic classification for standard Assist satellites and common ESPHome, Wyoming, VoIP, Voice PE, Atom Echo, S3-BOX, and ReSpeaker clues.
- Added plain-English safe next steps and report copying; access tokens are held in memory only and omitted from reports.
- Added context-specific post-identification options and an Android link that can open the entered instance in the Home Assistant Companion app.
- Added JVM classification tests and a reproducible signed APK build script.
- No Home Assistant instance was contacted, no live device commands were sent, and no external release was published.
