# Android release signing

The repository must never contain an Android signing keystore or its passwords.

## Debug builds

GitHub Actions builds the debug APK with Android's generated debug signing key. This is suitable for CI validation and development, not for maintaining the production signing identity.

## Release builds

`app/build.gradle.kts` accepts release signing only through environment-provided values:

- `FLUD_SIGNING_KEYSTORE_PATH`
- `FLUD_SIGNING_STORE_PASSWORD`
- `FLUD_SIGNING_KEY_ALIAS`
- `FLUD_SIGNING_KEY_PASSWORD`

The signing-verification workflow reconstructs the keystore at runtime from the repository secret `FLUD_SIGNING_KEYSTORE_BASE64`, then supplies the remaining signing values through repository secrets. The keystore itself and its passwords must never be committed.

Required GitHub Actions repository secrets:

- `FLUD_SIGNING_KEYSTORE_BASE64`
- `FLUD_SIGNING_STORE_PASSWORD`
- `FLUD_SIGNING_KEY_ALIAS`
- `FLUD_SIGNING_KEY_PASSWORD`

## Release signing identity

The permanent signing certificate selected for the first public beta has SHA-256 fingerprint:

`C0:BB:C7:47:2B:10:C7:43:25:03:9B:BF:83:C9:BC:2F:92:E3:FF:86:9E:AF:49:59:9E:D6:7C:D5:46:BB:59:98`

Compact lowercase form used by project invariants:

`c0bbc7472b10c74325039bbf83c9bc2f92e3ff869eaf49599ed67cd546bb5998`

The signing-verification workflow verifies the signed APK against this fingerprint and fails if the signing identity does not match.

## Hardware release candidates

Hardware candidates must use the same permanent production signing identity as public releases when they are installed over an existing Flud Companion installation. A debug APK is suitable for CI compilation checks, but it cannot replace a production-signed installation without uninstalling the app first.

For a Shield/Android TV hardware candidate:

- keep the candidate version code higher than the installed public release;
- build the release variant only through the existing signing-verification workflow or an equivalent environment that supplies the repository signing secrets without exposing them;
- require the permanent certificate SHA-256 check to pass before using the APK for an in-place hardware test;
- treat the resulting APK as a test candidate, not as a public GitHub Release;
- do not create or move a release tag until hardware validation is complete and the owner explicitly approves publication.

The verification workflow may upload the verified signed APK as a GitHub Actions artifact for hardware testing. That artifact must not be committed into the source tree.

## Signing continuity

Users can install updates over an existing release only when the APK is signed with the same signing identity. Back up the production key securely outside the repository before publishing signed builds. Losing the key means installations signed with it cannot be updated by a differently signed APK.

## Repository hygiene

Signing verification is read-only with respect to source history. CI must not commit run IDs, READY/PUBLISHED markers, generated signing status files, or other bookkeeping back to `main`.

Actual secret values, keystore files, and private backups must remain outside the public repository.
