# Changelog

## 0.24.1

- Fix iPhone/iPad Home Screen PWA pairing transfer by using a short-lived Secure bootstrap cookie, then moving credentials into the installed PWA's own local storage.
- Harden Android TV cold/frozen-start magnet handling for Flud launches that need roughly 7-8 seconds to become fully interactive.
- Never run the D-pad confirmation fallback on Flud's main screen.
- Detect the real Add torrent screen before confirmation and target the top-right add action, including an unlabeled image button.
- Recover once from an accidental .torrent file picker and allow one controlled magnet re-handoff when a slow start swallows the first intent.
- Extend the guarded Accessibility request window from 20 to 30 seconds.

## 0.24.0

- Explicit cross-platform Web Companion positioning for iPhone, Android, tablet and desktop.
- Premium-minimal Android Bridge, LAN controller and Remote PWA release source.
- EN / RO / FR / DE language support and in-app How-to.
- Optional browser-local support reminder after each 50 successful sends; no telemetry is added.
- Guarded Auto-start helper with semantic confirmation and delayed Android TV D-pad fallback.
- Safe Accessibility-settings fallback for Android TV firmware without a usable deep link.
- Remote relay uses a user-owned Cloudflare Worker + R2 mailbox.
- Remote retries are idempotent through client request IDs.
- Sanitized public-source build: no historical development Git history, private relay configuration or project keystore.
- Android and Remote relay validation workflows included.

## 0.23.0

- Public-beta hardening of Android TV focus behavior, Remote transport retry and release-preparation documentation.

## 0.22.0

- Android TV focus clipping fixes.
- Idempotent Remote magnet requests and mobile-network retry hardening.
- Clean-account, beta-test and security release preparation.

## 0.21.0

- Release-candidate UI/focus cleanup.
- Public-compatible Accessibility settings routing.
- Privacy and troubleshooting documentation foundation.
