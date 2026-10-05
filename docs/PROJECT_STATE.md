# Flud Companion - Project State

LAST_UPDATED: 2026-10-05
STATUS: Stable public release 0.25.2. The public release source is the immutable tag `v0.25.2`; `main` may move forward with post-release maintenance and governance changes.

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

- Release: `0.25.2`
- Tag: `v0.25.2`
- Android `versionCode`: `45`
- Android `versionName`: `0.25.2`
- Application ID: `media.alexlab.fludremote`
- Minimum Android SDK: 23
- Target Android SDK: 33
- Compile SDK: 35
- Flud packages:
  - `com.delphicoder.flud`
  - `com.delphicoder.flud.paid`

The published release tag and GitHub Release assets are part of release integrity. Do not move a published tag, recreate a release, or replace its assets unless the owner explicitly requests a history/release rewrite and the consequences are understood.


## 0.25.2 release scope

0.25.2 fixes a real NVIDIA Shield regression observed after reboot:

- Bridge startup now gets a second boot lifecycle opportunity on `USER_UNLOCKED` and performs one bounded verification/retry if the foreground service does not reach RUNNING.
- Boot startup results are recorded as non-secret diagnostics.
- Accessibility `enabled in Settings` is no longer treated as proof that the service is actually connected.
- Auto-start holds the payload locally while an enabled Accessibility helper reconnects and fails safely if the service never becomes live.
- Flud preflight does not begin while Accessibility is disconnected, preventing the observed failure mode where Flud opened but the magnet was never handed over/confirmed.
- LAN status exposes helper enabled-vs-connected state; Remote polling advertises `ready` only when the Accessibility service is actually connected.
- v11 structural torrent-list readiness, pre-handoff recovery, exactly-one handoff and post-handoff boundaries are unchanged.

Hardware validation passed on NVIDIA Shield on 2026-10-05 for the full sequence: reboot -> Bridge online without manual launch -> Auto-start magnet completes.

## 0.25.1 release scope

0.25.1 is a maintenance release for version-reporting consistency.

- The Android package version is `0.25.1` / versionCode `44`.
- LAN Bridge and Remote polling report `BuildConfig.VERSION_NAME` rather than a separately hard-coded version string.
- This removes the RC/stable display mismatch observed after installing 0.25.0.
- Magnet, `.torrent`, relay transport and Auto-start behavior are unchanged from the validated 0.25.0 implementation.

## 0.25.0 release scope

0.25.0 promotes the validated `.torrent` file handoff feature to stable.

Stable behavior:
- direct single-file `.torrent` selection in LAN and Remote PWAs;
- iOS Files picker intentionally does not use an HTML `accept` filter because iOS can gray out valid `.torrent` files when MIME/UTType mapping is unknown;
- maximum accepted `.torrent` size: 5 MB;
- client-side filename/size checks plus strict bencode/metainfo validation in the Android Bridge;
- read-only `content://` handoff to Flud from private app cache;
- Remote transport uses the user's existing self-hosted R2 mailbox only as temporary payload storage and removes the payload after command completion/stale cleanup;
- v11 structural preflight and exactly-one handoff remain the Auto-start safety boundary for both magnets and `.torrent` files.

The LAN and Remote `.torrent` paths were validated on real iPhone + NVIDIA Shield hardware before stable publication.

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
- Give up after the bounded 180-second preflight timeout rather than sending into an unstable Flud state.
- Issue exactly one magnet handoff after readiness.

Current v11 gate parameters:
- warm structural stability: about 900 ms;
- cold/restoring structural stability: about 3.5 seconds;
- minimum stable samples: 3;
- if Flud disappears before handoff for about 1.5 seconds, preflight may reopen it;
- initial launch may be retried after about 8 seconds if Flud never becomes foreground;
- maximum pre-handoff recovery opens: 2;
- after recovery attempts are exhausted, fail safely rather than sending the magnet;
- immediately before handoff, verify the exact structural fingerprint again after a short final check.

This design was validated on real NVIDIA Shield hardware for the deeper cold-start case where Flud had been evicted from background memory and its torrent list was rebuilt progressively. One successful hardware validation is enough to promote the release that was explicitly approved by the owner, but future regressions must still be treated as real regressions rather than assuming this path can never fail again.

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


## Relay setup page - mobile layout

The self-hosted relay setup page is part of the public product surface.

For small/mobile screens:
- numbered setup steps must use a stable number column plus a flexible text column;
- instructional phrases such as `Quick setup -> LAN + Remote` and `Remote QR` must not be forced into a narrow word-by-word column;
- text must wrap naturally without overlapping adjacent text or step separators;
- changes must preserve the existing premium dark visual language and keep the setup instructions readable on iPhone-sized viewports.

0.24.9 includes this responsive layout correction.

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

With explicit owner authorization, the public history was deep-cleaned again on 2026-10-03 into a short release-level snapshot chain:

- `0.24.0 Beta 1`
- `0.24.1 Beta 2`
- `0.24.1`
- `0.24.7`
- `0.24.8`
- `0.24.9`
- `0.25.0`
- `0.25.1`
- `0.25.2`

Each reachable snapshot represents a real public release and is attributed to `Zuzuitu`. Development retries, temporary publishing helpers, intermediate patch commits and bookkeeping noise are intentionally not part of the normal public history.

The corresponding GitHub Releases, release assets and download counts must be preserved when release tags are moved to these cleaned snapshots. Old commit objects can remain temporarily addressable by SHA until GitHub garbage collection and may also persist in external clones or forks.

Do not rewrite public history again as routine maintenance. A future force rewrite requires explicit owner approval, release/tag/asset verification, production dependency checks and a clear reason.

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


## Session checkpoint - 2026-09-13

Decisions completed in this session:

- Deep-cleaned the public repository history and removed obsolete one-shot development/release noise while preserving public Releases and assets.
- Added persistent technical memory: `docs/PROJECT_STATE.md`, `config/project-invariants.json`, and `AGENTS.md`.
- Signing verification was converted to verification-only behavior; durable CI must not write run-ID or publication marker commits into `main`.
- 0.24.8 aligned Android package, LAN Bridge and Remote Bridge version reporting without changing validated Auto-start behavior.
- A deeper real-world cold-start regression was then reproduced when Flud had been evicted from background memory and rebuilt its torrent list progressively.
- Auto-start v11 replaced first-title readiness with structural torrent-list fingerprint stability, adaptive warm/cold gating, pre-handoff reopen recovery, same-pending-magnet retry refresh, and final fingerprint verification.
- The hard boundary remains: recovery/reopen is allowed only before the magnet has been handed to Flud. After handoff there is no Back recovery, no app reopen, and no magnet re-handoff.
- The v11 cold-start path was tested successfully on real NVIDIA Shield hardware and explicitly approved for public release.
- 0.24.9 was released publicly with deliberately concise release notes: summarize user-visible reliability/UI improvements without narrating the internal sequence of failed experiments.
- 0.24.9 also fixes the self-hosted relay setup page on small screens so setup phrases and the `Remote QR` instruction wrap cleanly without overlapping.
- The 2026-09-13 public baseline was `v0.24.9`, versionCode `40`, with Auto-start strategy `semantic-v11+structural-list-stability+preflight-reopen+single-handoff+strict-confirmation`.

## Session checkpoint - 2026-10-03

- Added direct `.torrent` file sending to both LAN and Remote PWAs.
- iPhone/iPad file selection was fixed by removing unreliable HTML MIME/extension pre-filtering while retaining validation after selection.
- Remote `.torrent` transport reuses the user-owned R2 mailbox temporarily; no shared relay or inbound home port was introduced.
- LAN and personal Remote paths were validated successfully on real iPhone + NVIDIA Shield hardware.
- The stable release is now `v0.25.0`, Android versionCode `43`.
- Release notes remain concise and user-facing, centered on the new `.torrent` file capability.

## Session checkpoint addendum - 2026-10-03

Decisions and validated outcomes completed after the initial 2026-10-03 checkpoint:

- The final picker copy is intentionally concise: `Choose .torrent` with a `Max 5 MB` hint; after selection the hint is replaced by the selected filename and size.
- iOS/iPadOS file selection intentionally uses no HTML `accept` filter for the torrent picker because Files can gray out valid `.torrent` files when MIME/UTType mapping is missing or inconsistent.
- LAN `.torrent` sending was validated successfully from iPhone to NVIDIA Shield with Auto-start.
- Personal Remote `.torrent` sending through `flud-remote.alexlab.media` was also validated successfully from iPhone to NVIDIA Shield.
- The personal/live relay source remains in the historical/private `flud-remote` repository and that repository must remain private.
- The public architecture remains user-owned/self-hosted: Remote `.torrent` payloads use the user's existing R2 mailbox only as temporary transport and do not introduce a shared alexlab.media relay for public users.
- Public stable `Flud Companion 0.25.0` was published with Android `versionCode 43` and tag `v0.25.0`.
- Public release notes intentionally highlight the new direct `.torrent` file sending capability without narrating internal debugging attempts.
- The 0.25.0 signed APK was built with the permanent release signing identity and the certificate fingerprint verification passed.
- The one-shot 0.25.0 publication workflow was removed immediately after publication; the durable workflow set remains only the five canonical workflows documented above.
- The private live relay was promoted from the tested `0.25.0-rc2` surface to `0.25.0` stable without changing its transport/security architecture.
- The owner explicitly authorized a new public-history deep clean after this checkpoint, provided Releases, release assets/download counts, signing identity, production architecture, technical memory and real contributor attribution are preserved.

## Session checkpoint patch - 2026-10-03

- A post-release consistency bug was confirmed: the installed 0.25.0 APK still reported `0.25.0-rc2` through LAN/Remote Bridge status because two runtime constants remained hard-coded.
- 0.25.1 replaces those Android Bridge version constants with `BuildConfig.VERSION_NAME`, preventing the same RC/stable mismatch from recurring.
- This patch intentionally changes only version reporting/version metadata; validated magnet, `.torrent`, relay transport and Auto-start behavior remain unchanged.

## Session checkpoint - 2026-10-05

- A reboot regression was reproduced on NVIDIA Shield: `Start after reboot` remained enabled while the Bridge did not always return online automatically.
- A second failure boundary was identified: Android Settings could report the Accessibility helper enabled while the service instance was not actually connected.
- Auto-start now distinguishes enabled-state from live Accessibility connection and will not begin Flud structural preflight until Accessibility is connected.
- While the helper reconnects, the payload remains local and unsent; if the helper never becomes live, the request fails safely.
- Boot startup now also reacts to `USER_UNLOCKED`, verifies that BridgeService reaches RUNNING and performs one bounded retry.
- Boot-start results are recorded only as non-secret diagnostics.
- The v11 structural readiness algorithm, pre-handoff recovery, exactly-one payload handoff and post-handoff safety boundary are unchanged.
- `0.25.2-rc1` / versionCode `45` passed CI, permanent signing verification and real NVIDIA Shield validation for reboot -> Bridge online -> Auto-start magnet completion.
- The validated candidate was promoted to stable `0.25.2` with the same versionCode `45`.

## Known technical debt

The 0.25.2 release preserves the hardware-validated `.torrent` handoff and structural Auto-start behavior while adding reboot/startup and Accessibility-liveness hardening.

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
- release notes and `CHANGELOG.md` describe validated user-facing behavior concisely and do not expose unnecessary internal failed-attempt history;
- this checkpoint is updated after material architectural, release, security, or workflow changes.

