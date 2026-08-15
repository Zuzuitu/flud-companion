# Protocol reference

Current public protocol version: Bridge/relay `0.24.0`.

## LAN API

Base URL: `http://<android-ip>:8765`

Unauthenticated:

- `GET /`
- `GET /app`
- `GET /api/v1/health`

Authenticated with `Authorization: Bearer <LAN token>`:

- `GET /api/v1/capabilities`
- `GET /api/v1/status`
- `POST /api/v1/magnet`

Magnet request JSON:

```json
{
  "magnet": "magnet:?xt=urn:btih:...",
  "autoStart": false
}
```

## Remote relay

Bridge endpoints:

- `GET /bridge/poll/:deviceId`
- `POST /bridge/result/:deviceId`

Client endpoints:

- `GET /api/v1/device/:deviceId/status`
- `POST /api/v1/device/:deviceId/magnet`

Web/support endpoints:

- `GET /`
- `GET /setup`
- `GET /app`
- `GET /health`
- `GET /relay.json`
- `GET /manifest.webmanifest`
- `GET /sw.js`

Remote requests authenticate with `Authorization: Bearer <Remote token>`.

Remote magnet JSON:

```json
{
  "magnet": "magnet:?xt=urn:btih:...",
  "autoStart": false,
  "requestId": "client-generated-id"
}
```

A repeated recent `requestId` is treated idempotently when the first request was already accepted.

The Bridge sends `X-Flud-Bridge-Version`, `X-Flud-AutoStart` and `X-Flud-AutoStart-Mode` during polling so the relay can expose device capability/status without maintaining a separate inbound connection.
