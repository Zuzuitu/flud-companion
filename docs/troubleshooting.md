# Troubleshooting

## Bridge shows stopped

Open Flud Companion on the Android device and press **Start Bridge**. If you want it available after a reboot, enable **Auto-start after reboot**.

## Local QR opens but the browser is not paired

Make sure the phone and Android device are on the same LAN, then scan a fresh **Local QR**. Pairing credentials are carried in the URL fragment and saved locally by the browser.

## LAN page does not open

- Confirm the Bridge is running.
- Confirm both devices are on the same local network.
- Check the current Android device IP shown in Advanced details.
- Do not expose port `8765` to the public internet.

## Remote says Android is offline

- Confirm **Remote relay** is enabled in the Bridge.
- Confirm the configured relay URL is HTTPS and points to your Worker.
- Open the relay `/health` endpoint or its landing page.
- Confirm the Bridge has internet access. It must poll the relay outbound over HTTPS.

## Remote command already pending

The relay intentionally allows one queued/inflight command at a time. Wait for the Android Bridge to collect and finish the current command, then retry.

## Magnet was sent twice after a mobile-network problem

Current clients reuse a request ID during transport retries. The relay treats a repeated accepted request ID as idempotent and should not intentionally queue it twice.

## Auto-start does not confirm Flud

1. Enable **Flud Companion Auto-start** in Android Accessibility.
2. On Android TV firmware where the direct Accessibility link is unavailable, open Android Settings manually and navigate to Accessibility.
3. Send a new magnet with **Auto-start download** enabled.

The helper uses guarded semantic matching first. `Right → Right → OK` is a delayed compatibility fallback; it is intentionally not used immediately because Flud's main Add button can open the `.torrent` file picker.

## Auto-start is enabled but never connects after Android TV reboot

If LAN / Remote is online but the Bridge reports **Auto-start helper: needs reconnection**, Android still lists the service as enabled but the app has not received a live AccessibilityService connection. An enabled toggle is not proof that the helper can confirm a magnet.

1. Confirm the Bridge itself is online and open the Android TV **Settings > Accessibility > Flud Companion Auto-start** page.
2. If the helper remains unbound, toggle it **OFF then ON** as a diagnostic/recovery action. A release candidate is **not** considered reboot-fixed if this manual step is required after every restart.
3. Retry with a new magnet only after the helper reports connected. A previous pre-handoff failure did not send a payload; do not blindly retry a command that may already have reached Flud.

For read-only ADB diagnostics on an affected Android TV device, run these separately and redact output before sharing:

```sh
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility
```

The last command can expose other installed services and device information; share only the relevant, sanitized Flud Companion binding lines. The Bridge never force-enables Accessibility or writes Android's protected secure settings.

## Auto-start opened a file picker

The guarded helper detects the torrent file picker, backs out once and continues waiting for the magnet-confirmation screen. If the behavior persists, disable Auto-start and report the Android/Flud version without sharing any live tokens or QR codes.
