#!/usr/bin/env bash
set -euo pipefail

# Local-first slice. Server-era AuthRefreshAndReauthTest still needs a live Supabase and is excluded
# until that provenance test is retired.

echo "Proving Room restart durability"
./gradlew :core:database:connectedDebugAndroidTest --stacktrace

echo "Proving goat register, weight, file reopen, farm-LAN second device, local search, and lost-device isolation"
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.farmos.app.AuthRefreshAndReauthTest \
  --stacktrace
