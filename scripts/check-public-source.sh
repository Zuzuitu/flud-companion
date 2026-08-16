#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$1"
  exit 1
}

# Private key / certificate containers and Android signing stores never belong here.
if find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.pfx' -o -name '*.pem' -o -name '*.key' \) -not -path './.git/*' | grep -q .; then
  find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.pfx' -o -name '*.pem' -o -name '*.key' \) -not -path './.git/*'
  fail 'Private key or signing container found in source tree.'
fi

# Release artifacts should be produced by CI, not committed to source.
if find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.zip' \) -not -path './.git/*' | grep -q .; then
  fail 'Build/update artifact found in source tree.'
fi

# Catch common literal signing-password mistakes in Android build/config source.
if grep -RInE --include='*.gradle' --include='*.gradle.kts' --include='gradle.properties' \
  '(storePassword|keyPassword)[[:space:]]*=[[:space:]]*"[^$][^"]+"' . --exclude-dir=.git; then
  fail 'Literal signing password found in Gradle source.'
fi

# Catch accidentally pasted private-key material in any text source.
if grep -RIl --exclude-dir=.git --exclude='check-public-source.sh' 'BEGIN .*PRIVATE KEY' . | grep -q .; then
  fail 'Private-key material found in source tree.'
fi

echo 'Public-source scan passed.'
