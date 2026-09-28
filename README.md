# Listener Lab

Listener Lab is a small, read-only Android diagnostic app for an unfamiliar Home Assistant voice setup. It connects to a Home Assistant instance, inventories standardized Assist satellites and likely voice hardware, and produces a plain-English report that can be copied to whoever will help tune the system.

## What the first release does

- Connects with a Home Assistant URL and a long-lived access token.
- Reads Home Assistant configuration, entity states, device registry, entity registry, Assist pipeline list, and supported satellite configuration.
- Recognizes standardized `assist_satellite` entities and looks for ESPHome, Wyoming, VoIP, Home Assistant Voice, Atom Echo, S3-BOX, ReSpeaker, microphone, wake-word, VAD, pipeline, gain, and noise-suppression clues.
- Reports which controls are exposed and what the next safe diagnostic step should be.
- Presents platform-specific next options after identification, including conservative ESPHome, Wyoming, ReSpeaker, and Voice Preview Edition paths.
- Opens the entered Home Assistant address through Android so an associated Home Assistant Companion app can handle it.
- Keeps the token in memory only and omits it from reports.

The scan does not change settings, flash firmware, edit YAML, restart Home Assistant, or send commands to devices.

The installed Home Assistant Companion app is useful for opening the instance and carrying out choices, but Android does not permit Listener Lab to copy that app's login. Listener Lab therefore asks for a separate token and keeps it only until the app process ends.

## Build

Use PowerShell from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

The script expects a folder containing `jdk17`, `gradle`, and `android-sdk`. Set `HA_LISTENER_ANDROID_TOOLS` when it is not in one of the known local tool locations.

The signed test APK is written to `releases\Listener_Lab_v1.0.0.apk`.

## Connect

In Home Assistant, create a long-lived access token from the user profile. Paste it into Listener Lab for the scan. The app does not save it when closed.
