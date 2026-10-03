# FAQ

## Do I need Cloudflare?

No for LAN-only use. Cloudflare is needed only if you want the optional Remote relay outside your home network.

## Do I need to open a router port?

No. Remote uses outbound HTTPS polling from the Android Bridge. The intended setup does not expose the Bridge's LAN port `8765` to the internet.

## Do I need Tailscale or a VPN?

No. You can still use your own VPN if you prefer, but the built-in Remote design does not require one.

## Where does the Remote relay run?

In your own Cloudflare account as a Worker with an R2 mailbox. Flud Companion does not require a shared project-owned relay.

## Is there an iPhone app?

The controller is a Web Companion/PWA rather than a native iOS app. It works from iPhone, Android, tablet and desktop. The only native APK is the Android Bridge installed on the device running Flud.

## Does the project upload my magnet history?

The recent-send list is browser-local. The relay receives the current magnet command because it must deliver it to the Android Bridge; it is not designed as a torrent-history service.

## What is Auto-start download?

It is an optional Accessibility helper on the Android device. After an explicit magnet command requests Auto-start, the helper tries to confirm the correct Flud add screen. It is not required for normal magnet delivery.

## Why is the repository still private during release preparation?

The sanitized repository should be made public only after the build, self-host relay, documentation, secret scan and repository license are ready. The historical development repository stays private because its history is not intended for publication.
