# Security

## Supported releases

Security fixes are applied to the latest public Flud Companion release.

## Secrets

Never publish or report:

- LAN API tokens;
- Remote relay tokens;
- pairing QR codes that contain live credentials;
- Cloudflare API tokens;
- private deployment credentials.

A Device ID is not intended to be the authentication secret; the Remote token is.

## Remote architecture

Remote uses outbound HTTPS polling from the Android Bridge to a relay owned by the user. The intended public setup does not expose port 8765 to the internet and does not require a project-owned relay.

The relay stores a SHA-256 hash of the Remote token. Remote PWA magnet retries use an idempotent request ID so a lost mobile-network response can be retried without intentionally queueing the same command twice.

## Reporting a vulnerability

For the public release, use the repository's private security-advisory mechanism when available. Do not include working tokens or private pairing QR codes in a public issue.
