# Flud Companion

**Cross-platform controller:** use Flud Companion Web from **iPhone, Android, tablet, or desktop**. The only native APK is the Android Bridge installed on the device that runs Flud.

Flud Companion is an independent, unofficial companion project for controlling Flud on Android over LAN or a user-owned Remote relay. It is a personal alexlab.media project and is not affiliated with, endorsed by, or sponsored by Delphi Softwares or the developers of Flud. The name “Flud” is used only to identify compatibility.

## Current version — 0.23

### Quick setup

The Android Bridge supports:

- **LAN only** — direct local control, no cloud required;
- **LAN + Remote** — local control plus a user-owned self-hosted relay.

v0.24 freezes the approved **premium minimal** Flud Companion interface and makes the exact Remote PWA linked-rings mark canonical across Android Bridge, Local LAN UI, Remote PWA, app icon, and Android TV banner.

The three user-facing clients now include an in-app **How-to**, a remembered language selector for **English / Romanian / French / German**, and an optional **Offer me a beer** support action. Android Controls and Advanced actions use two-column grids to reduce TV scrolling.

The selected linked-rings mark is the primary visual identity. Android is reorganized around **Status → Controls → Pairing → Advanced**, while the phone UI uses a compact device status card, simplified magnet composer, Auto-start switch, premium primary action, recent-send list, and a quieter saved-pairing area.

The functional LAN/Remote protocol remains unchanged.

### Public beta candidate in 0.23

- Android TV focus no longer scales controls outside their card bounds, avoiding clipped rounded corners on focused buttons.
- Remote magnet sends automatically retry short-lived mobile/browser/relay transport failures.
- Retries are idempotent: the PWA reuses a request ID and the relay returns the already-accepted command instead of queueing a duplicate.
- The Accessibility helper button falls back to the Android Settings home screen on TV firmware that exposes no usable Accessibility deep-link.
- Release-prep checklists are included for clean-account onboarding, beta testing, security and public repository review.

### Release preparation

Chrome on iPhone is the primary day-to-day beta browser; Safari and Android Chrome remain compatibility smoke tests. This repository is the sanitized release-source repository. Signing material, private relay configuration and development-only artifacts must never be committed here.

### Remote relay onboarding

The public target flow is:

`Deploy to Cloudflare → Copy relay URL → enter it once in Bridge → scan Remote QR → done`

The relay root serves a setup landing page with:

- Worker online status;
- exact relay URL;
- **Copy relay URL**;
- shortcut to the Remote PWA;
- four short pairing steps.

The self-host template declares its R2 mailbox with a default resource name so Cloudflare can provision it during the one-click deployment flow.

### LAN

- Local Bridge API on port `8765`
- Local web controller at `http://<android-device-ip>:8765/app`
- One-scan Local QR pairing
- Optional Auto-start download
- Recent-send history stored only in the browser

### Remote

- Bring-your-own Cloudflare Worker + R2 relay
- No inbound home-network port required
- No Tailscale requirement
- No project-owned domain or hosted account required
- Remote PWA served by each user's own relay
- One-scan Remote QR pairing
- Optional Auto-start download

## Public architecture

`Phone PWA → user's relay → outbound HTTPS polling → Android Bridge → Flud`

or at home:

`Phone browser → LAN Bridge → Flud`

See `docs/quick-start.md`, `docs/relay-setup.md`, `docs/privacy.md`, `docs/troubleshooting.md`, `docs/browser-support.md`, `docs/faq.md`, `docs/credits.md`, `docs/new-user-zero.md`, `docs/beta-test.md`, `docs/public-release-checklist.md`, `SECURITY.md`, and `selfhost/relay/README.md`.

## Security

Keep LAN tokens, Remote tokens, and pairing QR codes private. The relay stores only a SHA-256 hash of the Remote token.

Use torrent/magnet content only where you have the right to download it.

## Support reminder

After every 50 successful magnet sends in a given browser/origin, the Web Companion shows a small optional support reminder. The counter stays in local browser storage; it is not telemetry and is not sent to alexlab.media. LAN and Remote origins keep independent counters by browser security design.
