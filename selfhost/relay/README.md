# Flud Companion — Self-hosted Remote relay

This relay is optional. **LAN-only users do not need it.**

Remote is bring-your-own infrastructure: each user deploys their own Cloudflare Worker + R2 mailbox. There is no shared project-owned relay, account, or domain.

## One-click deployment

[![Deploy to Cloudflare](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/Zuzuitu/flud-companion/tree/main/selfhost/relay)

Cloudflare reads `wrangler.jsonc`, provisions the R2 mailbox declared by the template, builds the Worker, and gives the user their own `*.workers.dev` URL.

After deployment:

1. Open the generated Worker URL. The relay landing page should say **Remote relay is ready**.
2. Tap **Copy relay URL**.
3. On the Android Bridge choose **Quick setup → LAN + Remote** or **Configure relay URL**.
4. Paste the Worker URL.
5. Scan **Remote QR** with the phone.
6. The Remote PWA opens already paired.

A custom domain is optional.

## Manual deployment fallback

From this folder:

```bash
npm install
npx wrangler r2 bucket create flud-companion-relay-mailbox
npm run deploy
```

If the bucket already exists in your Cloudflare account, skip the bucket-create command and deploy normally.

The relay template binds the bucket as `MAILBOX` and serves:

- setup landing page at `/` and `/setup`;
- Remote PWA at `/app`;
- health JSON at `/health`;
- machine-readable relay info at `/relay.json`.

## Authentication

The Android Bridge generates a random device ID and relay token locally. The relay binds the device ID to the SHA-256 hash of that token. Authenticated requests use:

`Authorization: Bearer <device relay token>`

Keep the Remote QR and relay token private.

## API

Bridge:
- `GET /bridge/poll/:deviceId`
- `POST /bridge/result/:deviceId`

Client:
- `GET /api/v1/device/:deviceId/status`
- `POST /api/v1/device/:deviceId/magnet`

Web:
- `GET /`
- `GET /setup`
- `GET /app`
- `GET /health`
- `GET /relay.json`
- `GET /manifest.webmanifest`
- `GET /sw.js`

## Auto-start helper

LAN and Remote controllers can request **Auto-start download**. This requires the optional Accessibility helper on the Android device. The helper first tries Flud's real confirmation control semantically; `Right → Right → OK` is only a D-pad compatibility fallback.

---
Flud Companion is an independent alexlab.media project. It is not affiliated with, endorsed by, or sponsored by Delphi Softwares or the developers of Flud. “Flud” is used only to identify compatibility.