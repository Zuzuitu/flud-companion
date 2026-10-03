# Beta test checklist

Test with real devices before calling a release stable.

## Android Bridge

- Fresh install succeeds.
- Existing install upgrades without losing pairing identity/settings unless intentionally reset.
- Bridge starts and stops normally.
- Auto-start after reboot works when enabled.
- Flud and Flud+ detection behaves correctly.
- Local QR and Remote QR are readable on phone cameras.
- Android TV D-pad focus remains inside rounded controls without clipping.

## LAN

- Local PWA opens from the current Android IP on port 8765.
- Fresh QR pairing works.
- Wrong/missing token is rejected.
- Magnet delivery works with Auto-start off.
- Magnet delivery works with Auto-start on when Accessibility helper is enabled.
- Recent history and language preference survive a browser reopen.

## Remote

- Relay deploys into a clean Cloudflare account.
- Landing page, `/health`, `/relay.json` and `/app` load.
- Bridge can claim a new Device ID/token pair.
- Wrong token is rejected.
- Online/offline state changes correctly.
- Remote magnet reaches Flud over mobile data, not just home Wi-Fi.
- A transient retry with the same request ID does not intentionally queue a duplicate.
- A second unrelated command while one is pending returns a busy/conflict state.

## Auto-start safety

- Helper does nothing until an explicit Auto-start command arms it.
- Helper acts only in Flud/Flud+.
- Generic Flud Add/FAB is not treated as the confirmation button.
- If the `.torrent` file picker appears, the helper backs out once and keeps waiting for the magnet confirmation flow.
- Delayed D-pad fallback is tested on Android TV.

## Browser smoke tests

- Chrome on iPhone.
- Safari on iPhone/iPad.
- Chrome on Android.
- At least one current desktop browser.

## Release hygiene

- GitHub Actions Android build is green.
- Relay syntax/build validation is green.
- No keystores, signing passwords, private hostnames, live tokens, QR credentials, APKs or ZIP update artifacts exist in the public-source tree.
