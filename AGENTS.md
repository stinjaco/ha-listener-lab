# Agent instructions

- This repo is **public**. Never commit internal IP addresses, hostnames, home-network or hub file paths, tokens or keys
  (in files, commit messages or PR text). Use generic wording such as "the Home Assistant address".
- If the machine you work on has a shared agent protocol or work-claim file, follow it. It is not part of this repo.
- The app is diagnostic-first. Scans must remain read-only unless a future task explicitly authorizes a narrowly scoped setting change.
- Never log, persist, or include Home Assistant access tokens in reports.
- Never bypass TLS certificate validation.
- Build on a local temporary copy with `scripts\build-apk.ps1`; this GitHub repo is the canonical source. The script expects
  a folder with `jdk17`, `gradle` and `android-sdk` (set `HA_LISTENER_ANDROID_TOOLS`). It runs `gradle testReleaseUnitTest assembleRelease`.
- Unit tests are in `app/src/test`. `ListenerAnalyzerTest` is plain JUnit (needs `org.json`); `MainActivityStartupTest` uses
  Robolectric and needs the Android SDK.
- User-facing APKs belong in `releases\`. Do not publish them externally without explicit approval.
- Record completed work in `claude\HANDOFF.md`.
