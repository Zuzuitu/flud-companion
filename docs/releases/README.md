# Release notes

This folder keeps the human-readable notes for Flud Companion public releases.

## 0.24.1 Beta 2

Bug-fix beta validated on real NVIDIA Shield / Android TV hardware and iPhone PWA.

Fixed:

- iPhone/iPad Remote pairing now survives Add to Home Screen.
- Slow or frozen Flud cold starts now get a longer guarded startup window before recovery.
- Auto-start no longer runs the D-pad fallback on Flud's main screen.
- The helper detects the real Add torrent screen and can confirm the top-right add action.
- If Flud opens the .torrent file picker by mistake, Companion backs out and performs at most one controlled magnet re-handoff.
- The Android Bridge version label is read from the actual app build instead of being hard-coded.
- Remote PWA/service-worker cache version was advanced so updated relay UI is not mistaken for an older build.

Full notes: [v0.24.1-beta.2.md](v0.24.1-beta.2.md)

## 0.24.0 Beta 1

First signed public beta with LAN and Remote control, QR pairing, user-owned Cloudflare relay, Android TV Auto-start helper and EN/RO/FR/DE UI.

Full notes: [v0.24.0-beta.1.md](v0.24.0-beta.1.md)

For the complete version history, see the repository root [CHANGELOG.md](../../CHANGELOG.md).
