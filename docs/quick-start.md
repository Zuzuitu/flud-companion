# Quick start

## LAN only

1. Install Flud or Flud+ and the Flud Companion Bridge APK on the Android device that runs Flud.
2. Open Flud Companion and choose **Quick setup → LAN only**.
3. Keep the phone and Android device on the same local network.
4. Scan **Local QR** with the phone camera.
5. The local Web Companion opens at `http://<android-device-ip>:8765/app` and stores the LAN token only in that browser.
6. Paste a magnet and choose **Send to Flud**.

No cloud account, VPN, inbound port or relay is required for LAN-only use.

## LAN + Remote

1. Complete the LAN setup above.
2. Deploy `selfhost/relay` to your own Cloudflare account.
3. Open the Worker URL and copy the HTTPS relay URL.
4. In the Android Bridge choose **Configure relay URL**, paste it and enable **Remote relay**.
5. Scan **Remote QR** with the phone.
6. The self-hosted Remote PWA stores Device ID + Remote token only in that browser.

## Optional Auto-start download

Enable **Flud Companion Auto-start** in Android Accessibility if you want the Bridge to confirm Flud's add-torrent screen automatically. The helper acts only after an explicit LAN or Remote auto-start request and only targets Flud/Flud+.

Keep pairing QR codes and tokens private.
