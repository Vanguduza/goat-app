#!/usr/bin/env bash
set -euo pipefail

fail=0
while IFS= read -r line; do
  ref="${line#*@}"
  ref="${ref%% *}"
  if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
    echo "ERROR: mutable GitHub Action reference: $line" >&2
    fail=1
  fi
done < <(grep -RhoE 'uses:[[:space:]]+[^[:space:]#]+@[^[:space:]#]+' .github/workflows | sed 's/^[[:space:]]*//')

if (( fail != 0 )); then
  exit 1
fi

grep -q '^distributionUrl=https\\://services.gradle.org/distributions/gradle-9.3.1-bin.zip$' gradle/wrapper/gradle-wrapper.properties
grep -q '^distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06$' gradle/wrapper/gradle-wrapper.properties
test -f gradle/wrapper/gradle-wrapper.jar
test -x gradlew

echo "Immutable Actions and Gradle wrapper guardrails: PASS"
