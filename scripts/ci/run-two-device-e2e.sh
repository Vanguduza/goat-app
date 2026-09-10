#!/usr/bin/env bash
set -euo pipefail

source scripts/ci/prepare-two-device-e2e.sh
trap 'supabase stop --no-backup >/dev/null 2>&1 || true' EXIT

# Keep the local service credential in this shell only. Never pass it to Gradle or the APK.
E2E_ADMIN_API_KEY="${ADMIN_API_KEY}"
E2E_API_URL="${API_URL}"
E2E_USER_ID="${USER_ID}"
E2E_FARM_ID="${FARM_ID}"
unset ADMIN_API_KEY SERVICE_ROLE_KEY SECRET_KEY

adb reverse tcp:54321 tcp:54321

echo "Proving Room restart durability"
gradle :core:database:connectedDebugAndroidTest --stacktrace

echo "Proving live refresh/re-auth and second-device authoritative visibility"
gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.farmos.app.RevokedMembershipClientTest \
  --stacktrace

echo "Revoking the E2E farm membership on the authoritative server"
revoke_headers=(-H "apikey: ${E2E_ADMIN_API_KEY}")
if [[ "${E2E_ADMIN_API_KEY}" == eyJ* ]]; then
  revoke_headers+=(-H "Authorization: Bearer ${E2E_ADMIN_API_KEY}")
fi
curl --fail-with-body -sS -X DELETE \
  "${E2E_API_URL}/rest/v1/farm_users?farm_id=eq.${E2E_FARM_ID}&user_id=eq.${E2E_USER_ID}" \
  "${revoke_headers[@]}" \
  -H "Prefer: return=minimal" >/dev/null
REMAINING=$(curl --fail-with-body -sS \
  "${E2E_API_URL}/rest/v1/farm_users?farm_id=eq.${E2E_FARM_ID}&user_id=eq.${E2E_USER_ID}&select=farm_id" \
  "${revoke_headers[@]}")
jq -e 'length == 0' >/dev/null <<<"${REMAINING}" || {
  echo "${REMAINING}" >&2
  echo "ERROR: farm membership revocation did not remove the E2E membership" >&2
  exit 1
}
unset E2E_ADMIN_API_KEY

echo "Proving revoked membership is rejected by client sync and remembered farm context is cleared"
gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.farmos.app.RevokedMembershipClientTest \
  --stacktrace
