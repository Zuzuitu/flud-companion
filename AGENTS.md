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

`semantic-v10+torrent-list-ready+single-handoff+strict-confirmation`

When Auto-start is requested:

1. If Flud's real torrent list is already visible, use the fast path.
2. Otherwise open Flud normally without handing the magnet over yet.
3. Wait for the real torrent list or explicit empty-library state.
4. Require a short stable-readiness debounce.
5. Hand the magnet over exactly once.
6. After handoff, never use Back, app reopen, or magnet re-handoff as recovery.
7. Never click a generic main-screen Add/FAB.
8. Confirm only the real Add torrent screen.

Do not replace this with a fixed 30/45/60-second cold-start timer.

Do not claim an Auto-start regression is fixed until it is validated on real target hardware when the bug is hardware/startup-timing dependent.

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
- Version-specific one-shot publication helpers may be temporary, but they must not remain as long-lived project machinery unless intentionally generalized.
- Do not reintroduce READY/PUBLISHED/run-id marker files.

## Public documentation

This is a public repository. Technical memory must be useful but must not contain private operational secrets.

When a private fact is necessary to preserve as a rule, record the rule rather than the secret itself.
