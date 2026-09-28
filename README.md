# Listener Lab

Listener Lab is a small, read-only Android diagnostic app for an unfamiliar Home Assistant voice setup. It connects to a Home Assistant instance, inventories standardized Assist satellites and likely voice hardware, and produces a plain-English report that can be copied to whoever will help tune the system.

## What the app does

- Starts safely with the standard `http://homeassistant.local:8123` address and runs local-network discovery only when **Find Local Home Assistant** is tapped.
- Uses Home Assistant OAuth for normal connection and encrypts the refresh token with Android Keystore. A temporary long-lived token remains available as an advanced fallback.
- Reads Home Assistant configuration, entity states, device registry, entity registry, Assist pipeline list, and supported satellite configuration.
- Recognizes standardized `assist_satellite` entities and looks for ESPHome, Wyoming, VoIP, Home Assistant Voice, Atom Echo, S3-BOX, ReSpeaker, microphone, wake-word, VAD, pipeline, gain, and noise-suppression clues.
- Reports which controls are exposed and what the next safe diagnostic step should be.
- Presents platform-specific next options after identification, including conservative ESPHome, Wyoming, ReSpeaker, and Voice Preview Edition paths.
- Opens the entered Home Assistant address through Android so an associated Home Assistant Companion app can handle it.
- Unlocks the separate desktop recovery link only when the inventory contains credible ESPHome or supported voice-hardware evidence.
- Keeps all credentials out of diagnostic reports.
- If Android closes the app unexpectedly, the next launch offers a credential-free startup diagnostic and a safe retry path.

The scan does not change settings, flash firmware, edit YAML, restart Home Assistant, or send commands to devices.

The installed Home Assistant Companion app is useful for opening the instance and carrying out choices, but Android does not permit Listener Lab to copy that app's private server database or login. Listener Lab independently discovers the same local instance, then asks the user to approve standard Home Assistant OAuth. OAuth credentials are encrypted at rest and can be removed with **Forget saved connection**.

## Desktop recovery

`docs/desktop/` is a hosted Web Serial tool for Chromium-based desktop browsers. It requires a Listener Lab report that already identifies potentially flashable hardware, then requires USB inspection, an HTTPS ESP Web Tools manifest, manifest validation, three safety acknowledgements, and an explicit `FLASH` confirmation before it reveals the installer. It never supplies guessed firmware for an unknown board.

## Build

Use PowerShell from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

The script expects a folder containing `jdk17`, `gradle`, and `android-sdk`. Set `HA_LISTENER_ANDROID_TOOLS` when it is not in one of the known local tool locations.

The signed test APK is written to `releases\Listener_Lab_v1.1.1.apk`.

## Connect and analyze

Join the same network as Home Assistant and open Listener Lab. Try the prefilled standard address with **Connect + Analyze**, or tap **Find Local Home Assistant** to run mDNS discovery on demand. The app then opens Home Assistant's authorization page. If discovery is unavailable, enter the address manually. The manual-token panel is an advanced fallback.
