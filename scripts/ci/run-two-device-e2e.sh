#!/usr/bin/env bash
set -euo pipefail

source scripts/ci/prepare-two-device-e2e.sh
trap 'supabase stop --no-backup >/dev/null 2>&1 || true' EXIT

adb reverse tcp:54321 tcp:54321
gradle :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace
