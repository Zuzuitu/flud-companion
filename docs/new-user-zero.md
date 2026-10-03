# New user from zero

This is the shortest clean-account onboarding test for someone who has never used Flud Companion.

## What you need

- an Android or Android TV device with Flud or Flud+;
- the Flud Companion Bridge APK;
- a phone, tablet or desktop browser;
- only for Remote: a Cloudflare account capable of deploying Workers and R2.

## LAN-only path

1. Install Flud/Flud+.
2. Install and open Flud Companion Bridge.
3. Choose **Quick setup → LAN only**.
4. Start the Bridge if it is not already running.
5. Scan **Local QR** with the phone while both devices are on the same LAN.
6. Send a test magnet that you are legally entitled to download.
7. Optional: enable the Accessibility helper and repeat with **Auto-start download**.

Expected result: the magnet reaches Flud without any cloud service.

## Remote path

1. Complete the LAN setup first.
2. Deploy `selfhost/relay` into a clean Cloudflare account.
3. Open the Worker landing page and copy the relay URL.
4. In Bridge choose **Configure relay URL**, paste it and enable Remote relay.
5. Wait until Remote status reports connected/online.
6. Scan **Remote QR** with the phone.
7. Disable Wi-Fi on the phone so the test really uses mobile internet.
8. Send a legal test magnet.
9. Repeat once with Auto-start enabled if the helper is configured.

Expected result: the command travels through the user's Worker/R2 relay and is collected by the Bridge over outbound HTTPS polling.

Do not use a developer's existing relay, token or QR during this test.
