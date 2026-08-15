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

For GitHub Actions release workflows, reconstruct or mount the keystore from GitHub Actions secrets at runtime and delete it after the job. Never commit the keystore, encoded keystore content or passwords.

## Signing continuity

Users can install updates over an existing release only when the APK is signed with the same signing identity. Back up the production key securely outside the repository before publishing signed builds.
