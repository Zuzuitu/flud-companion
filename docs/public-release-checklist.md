# Public release checklist

Do not change repository visibility until every required gate below is satisfied.

## Source hygiene

- [x] Fresh sanitized repository; no historical development Git history imported.
- [x] `keystore/`, `.jks`, `.keystore`, `.p12`, `.pfx`, `.env`, APK/AAB/ZIP artifacts and local properties ignored.
- [x] Android build configuration does not contain a project signing password.
- [x] Private development relay configuration is excluded.
- [x] Development-only update workflow is excluded.
- [x] Final public-source security scan passes on the complete tree.

## Build and tests

- [x] Android debug APK builds in GitHub Actions.
- [x] Self-host relay syntax + Wrangler dry-run validation passes in GitHub Actions.
- [x] Clean-account LAN onboarding tested on real hardware.
- [x] Clean-account Remote onboarding tested over mobile data.
- [x] Android TV Accessibility fallback and guarded Auto-start safety tested on target firmware.

## Documentation

- [x] README describes 0.24.0 and sanitized architecture.
- [x] Quick start, relay setup, privacy, troubleshooting, browser support, FAQ, credits and security policy present.
- [x] Beta and clean-account checklists present.
- [x] Apache License 2.0 (`Apache-2.0`) chosen deliberately and added.

## Publication

- [x] Confirm no private relay/token/QR values are present in the sanitized source tree.
- [x] Confirm no signing material is present in the sanitized source tree.
- [x] Choose repository license: Apache-2.0.
- [ ] Make `flud-companion` public.
- [ ] Verify Deploy to Cloudflare from a logged-out/clean GitHub browser flow.
- [ ] Re-run a fresh Remote QR pairing against the newly deployed relay.
- [ ] Tag the public beta/release only after the public repository and one-click deployment are verified.

The historical development repository should remain private.