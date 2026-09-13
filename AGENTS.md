# AGENTS.md

This repository has persistent technical memory. Do not start material work from chat memory alone.

## Required preflight

Before any material code, architecture, security, CI/CD, signing, release, or deployment change, read in this order:

1. `docs/PROJECT_STATE.md`
2. `config/project-invariants.json`
3. `AGENTS.md`
4. the relevant source files and supporting docs

Repository truth overrides chat memory.

## Non-negotiable rules

- Do not weaken `config/project-invariants.json` to make a change easier.
- Do not publish secrets, pairing tokens, live QR codes, signing material, keystores, passwords, or private deployment configuration.
- Do not make the historical development repository public.
- Do not change the permanent Android signing identity.
- Do not replace the user-owned Cloudflare relay model with a project-owned shared relay without an explicit architectural decision from the owner.
- Do not expose the Android LAN Bridge as a public inbound service.
- Do not rewrite public Git history or move published tags without explicit owner approval.
- Do not delete/recreate GitHub Releases merely to change source history; preserve assets and download counts.
- Do not let workflows commit CI bookkeeping markers to `main`.
- Preserve correct attribution for real human contributors.

## Auto-start safety

The validated Auto-start behavior is safety-critical.

Current strategy:

`semantic-v11+structural-list-stability+preflight-reopen+single-handoff+strict-confirmation`

When Auto-start is requested:

1. Do not trust the first visible torrent title as readiness.
2. Observe the structural fingerprint of visible torrent rows.
3. Reset the readiness clock every time that fingerprint changes.
4. Warm Flud may pass after about 900 ms of structural stability; cold/restoring Flud requires about 3.5 seconds and at least 3 stable samples.
5. If Flud disappears before magnet handoff, preflight may reopen it safely because the magnet is still local and unsent.
6. A second explicit send of the same still-pending magnet may refresh/restart preflight; it must not create a duplicate handoff.
7. Recheck the exact structural fingerprint immediately before dispatch.
8. Hand the magnet over exactly once.
9. After handoff, never use Back, app reopen, or magnet re-handoff as recovery.
10. Never click a generic main-screen Add/FAB.
11. Confirm only the real Add torrent screen.

The preflight timeout is 180 seconds. Maximum pre-handoff recovery opens is 2.

Do not replace structural readiness with a fixed 30/45/60-second cold-start timer.

Do not claim an Auto-start regression is fixed until it is validated on real target hardware when the bug is hardware/startup-timing dependent. A previously successful run does not make future contradictory hardware evidence invalid; investigate new regressions from repository truth and observed state transitions.

## Relay UI

The self-hosted relay setup page must remain readable on small/mobile screens. Keep numbered steps in a stable number column plus a flexible text column; do not allow emphasized phrases such as `Quick setup -> LAN + Remote` or `Remote QR` to collapse into a narrow word-by-word column or overlap neighboring text.

## Signing

The permanent certificate SHA-256 is:

`c0bbc7472b10c74325039bbf83c9bc2f92e3ff869eaf49599ed67cd546bb5998`

Release signing must fail closed if the certificate does not match.

`.github/workflows/verify-signing.yml` is verification-only. It must not create source commits or marker files.

## Normal development workflow

For material changes, use a reviewable branch/PR and green CI when practical. Direct maintenance on `main` should be deliberate and should not bypass invariants.

Before merging or publishing:

- run the relevant Android/relay/source checks;
- keep release/version metadata coherent;
- update `docs/PROJECT_STATE.md` when architecture, release state, signing, security rules, workflows, or important validated behavior changes.

## Releases

- Treat a published release tag as immutable.
- Never commit release binaries to the source tree.
- Preserve GitHub Release assets and download counts.
- Keep public release notes concise and user-facing. Do not narrate internal failed attempts unless they are materially relevant to security, compatibility, or user action.
- Version-specific one-shot publication helpers may be temporary, but they must not remain as long-lived project machinery unless intentionally generalized.
- Do not reintroduce READY/PUBLISHED/run-id marker files.

## Public documentation

This is a public repository. Technical memory must be useful but must not contain private operational secrets.

When a private fact is necessary to preserve as a rule, record the rule rather than the secret itself.
