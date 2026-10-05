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

readarray -t expected < <(python3 - <<'PY'
import json
with open("config/project-invariants.json", encoding="utf-8") as f:
    data=json.load(f)
candidate=data.get("development_candidate")
if candidate:
    print(candidate["version_name"])
    print(candidate["version_code"])
else:
    current=data["current_release"]
    print(current["version_name"])
    print(current["version_code"])
PY
)
expected_version="${expected[0]}"
expected_code="${expected[1]}"

readarray -t current < <(python3 - <<'PY'
import json
with open("config/project-invariants.json", encoding="utf-8") as f:
    current=json.load(f)["current_release"]
print(current["version_name"])
print(current["version_code"])
print(current["tag"])
PY
)
current_version="${current[0]}"
current_code="${current[1]}"
current_tag="${current[2]}"

test -n "$version_name"
test -n "$version_code"
test "$version_name" = "$expected_version"
test "$version_code" = "$expected_code"
test "$relay_package_version" = "$expected_version"
test "$relay_runtime_version" = "$expected_version-selfhost"

grep -q 'BuildConfig.VERSION_NAME' app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt
grep -q 'BuildConfig.VERSION_NAME' app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt

if grep -Eq 'VERSION = "[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?"' \
  app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt \
  app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt; then
  echo "Android Bridge version reporting must not be hard-coded."
  exit 1
fi

grep -q "release-v$current_version-blue" README.md
grep -q "releases/tag/$current_tag" README.md
grep -q "FludCompanion-$current_version.apk" README.md

echo "Version consistency OK: build=$version_name/$version_code; public=$current_version/$current_code"
