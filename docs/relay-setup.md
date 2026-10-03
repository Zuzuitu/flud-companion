# Remote relay setup

Remote is optional and uses infrastructure owned by the user.

## Recommended flow

1. Open `selfhost/relay` in this repository.
2. Deploy it to your Cloudflare account using the Deploy to Cloudflare flow once the repository is public, or use the manual Wrangler fallback during development.
3. Cloudflare creates the Worker and the R2 mailbox declared as `MAILBOX`.
4. Open the resulting `https://<name>.<account>.workers.dev` URL.
5. Confirm the landing page reports the relay online and copy its URL.
6. On the Android Bridge choose **Configure relay URL**, paste that HTTPS URL and enable **Remote relay**.
7. Scan **Remote QR** with the phone to open and pair the Remote PWA.

A custom domain is optional.

## Security model

The Android Bridge creates a random Device ID and Remote token locally. The token is sent over HTTPS for authenticated requests. The relay stores only its SHA-256 hash in R2. Device ID alone is not an authentication secret.

The Bridge polls outbound over HTTPS, so port `8765` does not need to be exposed to the internet.

## Command flow

`Remote PWA → Worker/R2 mailbox → Bridge outbound poll → Flud`

Only one command slot is queued/inflight at a time. Magnet sends include a request ID so transient browser/mobile-network retries are idempotent and do not intentionally duplicate an accepted command.
