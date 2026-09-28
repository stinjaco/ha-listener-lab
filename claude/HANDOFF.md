# Handoff

## 2026-09-28 — Initial Android discovery app

- Created a native Android app with a deliberately small connection-and-report interface.
- Added read-only Home Assistant REST and WebSocket inventory for config, states, device registry, entity registry, Assist pipelines, and supported satellite configuration.
- Added generic classification for standard Assist satellites and common ESPHome, Wyoming, VoIP, Voice PE, Atom Echo, S3-BOX, and ReSpeaker clues.
- Added plain-English safe next steps and report copying; access tokens are held in memory only and omitted from reports.
- Added context-specific post-identification options and an Android link that can open the entered instance in the Home Assistant Companion app.
- Added JVM classification tests and a reproducible signed APK build script.
- No Home Assistant instance was contacted, no live device commands were sent, and no external release was published.
