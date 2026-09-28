# Agent instructions

- Follow `H:\memory\PROTOCOL.md` before editing this project.
- The app is diagnostic-first. Scans must remain read-only unless a future task explicitly authorizes a narrowly scoped setting change.
- Never log, persist, or include Home Assistant access tokens in reports.
- Never bypass TLS certificate validation.
- Build on a local temporary copy with `scripts\build-apk.ps1`; keep source canonical in this hub repository.
- User-facing APKs belong in `releases\`. Do not publish them externally without explicit approval.
- Record completed work in `claude\HANDOFF.md`.

