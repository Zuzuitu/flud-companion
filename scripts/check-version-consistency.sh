#!/usr/bin/env bash
set -euo pipefail

version_name="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -n1)"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts | head -n1)"
relay_package_version="$(python3 - <<'PY'
import json
with open("selfhost/relay/package.json", encoding="utf-8") as f:
    print(json.load(f)["version"])
PY
)"
relay_runtime_version="$(sed -nE 's/^const VERSION = "([^"]+)";/\1/p' selfhost/relay/src/index.js | head -n1)"
invariant_version="$(python3 - <<'PY'
import json
with open("config/project-invariants.json", encoding="utf-8") as f:
    print(json.load(f)["current_release"]["version_name"])
PY
)"
invariant_code="$(python3 - <<'PY'
import json
with open("config/project-invariants.json", encoding="utf-8") as f:
    print(json.load(f)["current_release"]["version_code"])
PY
)"
invariant_tag="$(python3 - <<'PY'
import json
with open("config/project-invariants.json", encoding="utf-8") as f:
    print(json.load(f)["current_release"]["tag"])
PY
)"

test -n "$version_name"
test -n "$version_code"
test "$relay_package_version" = "$version_name"
test "$relay_runtime_version" = "$version_name-selfhost"
test "$invariant_version" = "$version_name"
test "$invariant_code" = "$version_code"
test "$invariant_tag" = "v$version_name"

grep -q 'BuildConfig.VERSION_NAME' app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt
grep -q 'BuildConfig.VERSION_NAME' app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt

if grep -Eq 'VERSION = "[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?"'   app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt   app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt; then
  echo "Android Bridge version reporting must not be hard-coded."
  exit 1
fi

grep -q "release-v$version_name-blue" README.md
grep -q "releases/tag/v$version_name" README.md
grep -q "FludCompanion-$version_name.apk" README.md

echo "Version consistency OK: $version_name (versionCode $version_code)"
