# Public architecture

Flud Companion has two control paths and one Android execution point.

## LAN

`Browser → HTTP LAN Bridge :8765 → Android intent → Flud`

The Android Bridge serves the local Web Companion and API. A random LAN API token protects command endpoints. LAN mode is intended for the private home network only.

## Remote

`Remote PWA → user's Cloudflare Worker/R2 → outbound HTTPS polling → Android Bridge → Flud`

The Android device does not accept a public inbound connection. It polls the user's relay. The relay is optional and lives in the user's own Cloudflare account.

## Pairing

Local and Remote QR codes put pairing credentials in the URL fragment (`#...`). Browser URL fragments are not sent to the HTTP server as part of the initial request. The Web Companion reads the fragment and stores the credentials locally in that browser.

## Remote authentication

The Bridge generates a random Device ID and Remote token. The relay stores the SHA-256 hash of the token and checks authenticated requests against it. Device ID alone is not an authentication secret.

## Command safety

The relay maintains a one-slot queued/inflight mailbox. Client retries carry a request ID so a transient transport retry can be recognized as the same accepted command rather than intentionally creating a duplicate.

## Auto-start

Auto-start is an optional Android Accessibility service. It is armed only by an explicit command that requests Auto-start and its package scope is limited to Flud/Flud+. Guarded semantic confirmation is primary; D-pad navigation is only a delayed compatibility fallback.
