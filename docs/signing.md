# Android release signing

The repository must never contain an Android signing keystore or its passwords.

## Debug builds

GitHub Actions builds the debug APK with Android's generated debug signing key. This is suitable for CI validation and beta development, not for maintaining a production signing identity.

## Release builds

`app/build.gradle.kts` accepts release signing only through environment-provided values:

- `FLUD_SIGNING_KEYSTORE_PATH`
- `FLUD_SIGNING_STORE_PASSWORD`
- `FLUD_SIGNING_KEY_ALIAS`
- `FLUD_SIGNING_KEY_PASSWORD`

The GitHub release workflow reconstructs the keystore at runtime from the repository secret `FLUD_SIGNING_KEYSTORE_BASE64`, then supplies the remaining signing values through repository secrets. The keystore itself and its passwords must never be committed.

Required GitHub Actions repository secrets:

- `FLUD_SIGNING_KEYSTORE_BASE64`
- `FLUD_SIGNING_STORE_PASSWORD`
- `FLUD_SIGNING_KEY_ALIAS`
- `FLUD_SIGNING_KEY_PASSWORD`

## Release signing identity

The permanent signing certificate selected for the first public beta has SHA-256 fingerprint:

`C0:BB:C7:47:2B:10:C7:43:25:03:9B:BF:83:C9:BC:2F:92:E3:FF:86:9E:AF:49:59:9E:D6:7C:D5:46:BB:59:98`

The release workflow verifies the signed APK against this fingerprint and refuses publication if the signing identity does not match.

## Signing continuity

Users can install updates over an existing release only when the APK is signed with the same signing identity. Back up the production key securely outside the repository before publishing signed builds. Losing the key means installations signed with it cannot be updated by a differently signed APK.