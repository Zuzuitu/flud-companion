# Flud Companion - Project State

LAST_UPDATED: 2026-09-13
STATUS: Stable public release 0.24.9. The public release source is the immutable tag `v0.24.9`; `main` may move forward with post-release maintenance and governance changes.

This file is the canonical human-readable technical checkpoint for Flud Companion.

## Mandatory reading order

Before any material change, read in this order:

1. `docs/PROJECT_STATE.md`
2. `config/project-invariants.json`
3. `AGENTS.md`
4. the relevant implementation and supporting documentation

Repository truth overrides chat memory. Do not reconstruct current behavior from old conversations when the repository says otherwise.

## Repository role

- This repository is the public release/source repository for Flud Companion.
- The historical development repository must remain private. Do not import its full Git history into this public repository and do not make it public as part of work here.
- Public source must stay sanitized: no live pairing credentials, private QR codes, Cloudflare credentials, signing material, keystores, passwords, or private deployment configuration.

## Current public release

- Release: `0.24.9`
- Tag: `v0.24.9`
- Android `versionCode`: `40`
- Android `versionName`: `0.24.9`
- Application ID: `media.alexlab.fludremote`
- Minimum Android SDK: 23
- Target Android SDK: 33
- Compile SDK: 35
- Flud packages:
  - `com.delphicoder.flud`
  - `com.delphicoder.flud.paid`

The published release tag and GitHub Release assets are part of release integrity. Do not move a published tag, recreate a release, or replace its assets unless the owner explicitly requests a history/release rewrite and the consequences are understood.

## Product architecture

Flud Companion has one Android execution point and two control paths.

### LAN

`Browser -> HTTP LAN Bridge :8765 -> Android intent -> Flud`

The Android Bridge serves the local Web Companion and API. Command endpoints use the LAN token. LAN mode is for the private home network.

### Remote

`Remote PWA -> user's Cloudflare Worker/R2 -> outbound HTTPS polling -> Android Bridge -> Flud`

Remote mode is bring-your-own infrastructure. The public architecture does not expose an inbound home-network port and does not depend on a project-owned shared relay.

The public self-host template uses:

- Worker name: `flud-companion-relay`
- R2 binding: `MAILBOX`
- default R2 bucket name: `flud-companion-relay-mailbox`

The relay stores a SHA-256 hash of the Remote token. Live Remote tokens and pairing QR codes are secrets.

## Auto-start - canonical behavior

The current stable strategy is:

`semantic-v11+structural-list-stability+preflight-reopen+single-handoff+strict-confirmation`

This behavior is safety-critical and must not regress.

### Readiness before handoff

`FludAutoStartCoordinator` must not use a fixed cold-start delay.

- Use structural torrent-list readiness rather than the first visible torrent title.
- Fingerprint the visible torrent-row structure and reset readiness whenever that fingerprint changes.
- Warm Flud uses a short structural stability check.
- Cold/restoring Flud requires a longer unchanged structural fingerprint before handoff.
- Recheck readiness immediately before dispatch.
- If Flud disappears before any magnet handoff, Companion may reopen it and continue preflight.
- An explicit retry of the same still-pending magnet may restart preflight rather than being rejected as stale.
- Give up after the bounded preflight timeout rather than sending into an unstable Flud state.
- Issue exactly one magnet handoff after readiness.

This design was validated on real NVIDIA Shield hardware for the deeper cold-start case where Flud has been evicted from background memory and its torrent list is rebuilt progressively.

### After magnet handoff

Once the magnet has been handed to ready Flud:

- do not send Android Back as recovery;
- do not reopen Flud as recovery;
- do not re-send/re-handoff the magnet;
- do not click a generic main-screen Add/FAB;
- wait for the real Add torrent confirmation screen;
- confirmation requires the INFORMATION/FILES tab pair plus torrent-detail evidence;
- only then may semantic/D-pad/gesture fallback confirm the final add action.

The Accessibility helper status detector must retain the Android TV fallback that checks `Settings.Secure.ACCESSIBILITY_ENABLED` and `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` when `AccessibilityManager` is stale or reports a relative service name.

## Pairing persistence

Remote PWA pairing is browser-local. Saved pairing can be hidden/unhidden without deletion. Pairing credentials must never be committed or included in public screenshots.

## Signing identity

Release signing is permanent and must not change.

Certificate SHA-256:

`c0bbc7472b10c74325039bbf83c9bc2f92e3ff869eaf49599ed67cd546bb5998`

Required repository secret names are documented in `docs/signing.md`. Their values, the keystore, and passwords must never be committed or printed.

Any release candidate must fail verification if the APK signer certificate differs from the permanent fingerprint.

## Durable GitHub workflows

The public repository intentionally keeps only durable workflows:

- `.github/workflows/android.yml` - debug Android build validation
- `.github/workflows/relay.yml` - self-host relay validation
- `.github/workflows/public-readiness.yml` - public readiness checks
- `.github/workflows/public-source-scan.yml` - sanitized source scan
- `.github/workflows/verify-signing.yml` - permanent signing verification

Version-specific, one-shot, patch, trigger, publication-marker, or cleanup workflows do not belong in the long-lived public tree.

Workflows must not create bookkeeping commits on `main`. In particular, do not reintroduce `READY-*`, `PUBLISHED-*`, run-id marker files, or `github-actions[bot]` commits merely to record CI state.

## Git history policy

The public history was deliberately cleaned on 2026-09-11 into release-level snapshots. Before the persistent-memory checkpoint, the cleaned reachable release history consisted of:

- `0.24.0 Beta 1`
- `0.24.1 Beta 2`
- `0.24.1`
- `0.24.7`

The next release-level maintenance snapshot is `0.24.8`, which only aligns version reporting and preserves the validated 0.24.7 runtime behavior.

All were attributed to `Zuzuitu`.

Do not rewrite public history again as routine maintenance. A future force rewrite requires explicit owner approval, release/tag/asset verification, and a clear reason.

Real third-party human contributions must retain correct attribution. Do not falsify authorship merely to alter the Contributors page.

## Release rules

- Keep GitHub Releases and their assets intact.
- Preserve download counts by updating existing releases rather than deleting/recreating them.
- Never commit APK/ZIP binaries into the source tree.
- Build release APKs with the permanent signing identity.
- Verify signing before publication.
- Published tags should be treated as immutable after publication.
- Temporary publication machinery must be removed after use if it is not a reusable workflow.
- The historical private development repository remains private.

## Security rules

- Never expose port 8765 publicly as part of the intended Remote architecture.
- Never publish LAN tokens, Remote tokens, pairing QR codes, Cloudflare credentials, signing secrets, or private deployment details.
- Never place a production signing keystore in Git.
- Device ID is not the authentication secret; Remote token is.
- If a real secret is ever committed, history rewriting is not sufficient: rotate the secret.

## Known technical debt

The 0.24.9 release promotes the hardware-validated structural Auto-start preflight and improves the relay setup layout on smaller screens.

Some older comments inside `FludAutoStartService.kt` still mention earlier strategy generations. Runtime behavior and `STRATEGY` are authoritative; clean stale comments during a future normal source change without changing the validated safety behavior.

## Before the next release

Verify all of the following:

- `versionName`, `versionCode`, Bridge version reporting and relay Bridge version reporting are aligned;
- Auto-start retains torrent-list readiness and single-handoff behavior;
- the permanent signing fingerprint matches;
- Android build passes;
- relay validation passes;
- public source scan passes;
- no live pairing data or signing material is present;
- release notes and `CHANGELOG.md` describe only validated behavior;
- this checkpoint is updated after material architectural, release, security, or workflow changes.

