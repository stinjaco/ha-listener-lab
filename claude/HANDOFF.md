# Handoff

## 2026-10-16 — Audit fixes (Claude, cloud session, branch `claude/dashboard-agent-optimization-nc8fc2`)

Four verified findings from the dashboard audit, left uncommitted on this branch. No Home Assistant instance or device was contacted. The cloud session has no Android SDK, so nothing was built here.

- `ListenerAnalyzer.java`: a candidate is `stale` when nothing of it answers in Home Assistant: every state-bearing entity is `unavailable` (`update.*` entities do not count as alive, ESPHome serves them from its dashboard while the board is offline) or a state-only `assist_satellite` is `unavailable`. Stale candidates are not confirmed listeners, do not set `firmwareCandidate`, are classified "registered but not answering", get the next check "Not answering in Home Assistant. It may run other firmware on purpose (for example an LCARS room listener)…" and option C "Identify first". The leftover ESPHome entries of the LCARS-reflashed ATOMs therefore no longer print DESKTOP RECOVERY PATH or show the DESKTOP FIRMWARE RECOVERY button. A device whose satellite is unavailable while other entities answer is unchanged ("Restore the unavailable device…").
- `ListenerAnalyzerTest.java`: three new tests (stale ESPHome ATOM entry with a live update entity, unavailable state-only satellite, offline satellite on a device that still answers).
- `MainActivity.java`: FIND LOCAL HOME ASSISTANT writes the discovered URL into the address field unconditionally (the prefilled standard address defeated the `isEmpty()` guards since the v1.1.1 hotfix), and a search without result always ends in "NO BROADCAST FOUND / USING <address>" instead of leaving "SEARCHING…" on screen.
- `OAuthManager.java`: a token refresh answered with HTTP 400 or 401 (revoked, expired or unknown refresh token) clears the saved vault and reports NO_CREDENTIAL, so CONNECT + ANALYZE reopens Home Assistant's authorization page instead of failing on every tap until FORGET SAVED CONNECTION.
- `HomeAssistantScanner.java`: a WebSocket failure after `auth_ok` is reported in SCAN NOTES whenever registry replies are missing (the `authenticated` flag is gone); `onClosing` ends the wait at once when Home Assistant closes the socket early (note "…closed the registry connection early…") instead of the full 14 s; "Unable to resolve host" gets a message that points to the IP address or discovery.

Verified here: `ListenerAnalyzer`, `ScanData`, `AnalysisResult` and `ListenerAnalyzerTest` compile with javac 21 (`--release 17`) against a throwaway org.json/JUnit stub outside the repo, all 7 analyzer tests pass, and the two new stale tests fail against the previous analyzer. The three Android/OkHttp files only passed a javac parse check (no syntax errors; the remaining errors are missing SDK symbols).

Still needs Jake's PC: `powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1` (release build, JVM tests including Robolectric, lint, signing). Then on the phone: FIND fills the address field and the status no longer sticks; a scan shows the two ATOM entries as "registered but not answering" with no DESKTOP FIRMWARE RECOVERY button; after deleting the Listener Lab refresh token in Home Assistant (Profile > Security), CONNECT + ANALYZE reopens the authorization page. Bump `versionName`/`versionCode` and the APK name in `releases\` for a release.

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
