# Public release checklist

Do not change repository visibility until every required gate below is satisfied.

## Source hygiene

- [x] Fresh sanitized repository; no historical development Git history imported.
- [x] `keystore/`, `.jks`, `.keystore`, `.p12`, `.pfx`, `.env`, APK/AAB/ZIP artifacts and local properties ignored.
- [x] Android build configuration does not contain a project signing password.
- [x] Private development relay configuration is excluded.
- [x] Development-only update workflow is excluded.
- [ ] Final secret/hostname scan passes on the complete tree.

## Build and tests

- [x] Android debug APK builds in GitHub Actions.
- [ ] Self-host relay validation workflow passes.
- [ ] Clean-account LAN onboarding tested on hardware.
- [ ] Clean-account Remote onboarding tested on hardware/mobile data.
- [ ] Android TV Accessibility fallback and Auto-start safety tested on target firmware.

## Documentation

- [x] README describes 0.24.0 and sanitized architecture.
- [x] Quick start, relay setup, privacy, troubleshooting, browser support, FAQ, credits and security policy present.
- [x] Beta and clean-account checklists present.
- [ ] Repository license chosen deliberately and added.

## Publication

- [ ] Confirm no private relay/token/QR values are present.
- [ ] Confirm no signing material is present.
- [ ] Choose repository license.
- [ ] Make `flud-companion` public.
- [ ] Verify Deploy to Cloudflare from a logged-out/clean GitHub browser flow.
- [ ] Tag the public beta/release only after the public repository and one-click deployment are verified.

The historical `flud-remote` development repository should remain private.
