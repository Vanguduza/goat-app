#!/usr/bin/env bash
set -euo pipefail

API_URL="http://127.0.0.1:54321"
DEVICE_API_URL="http://127.0.0.1:54321"
FARM_ID="33333333-3333-4333-8333-333333333333"
EMAIL="two-device@farmos.invalid"
PASSWORD="FarmOS-E2E-123456"
PUBLIC_API_KEY=""
ADMIN_API_KEY=""

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

cleanup_on_error() {
  if [[ "${1:-0}" != "0" ]]; then
    supabase stop --no-backup >/dev/null 2>&1 || true
  fi
}
trap 'cleanup_on_error $?' EXIT

echo "Starting isolated local Supabase authority for Android two-device E2E"
supabase start -x studio,imgproxy,edge-runtime,logflare,vector,supavisor >/tmp/farm-os-two-device-supabase-start.log
supabase db reset >/tmp/farm-os-two-device-supabase-reset.log

# Local CLI currently guarantees legacy keys and may additionally expose modern key names.
eval "$(supabase status -o env)"
PUBLIC_API_KEY="${PUBLISHABLE_KEY:-${ANON_KEY:-}}"
ADMIN_API_KEY="${SERVICE_ROLE_KEY:-${SECRET_KEY:-}}"
[[ -n "${PUBLIC_API_KEY}" ]] || fail "Supabase local publishable/anon API key missing"
[[ -n "${ADMIN_API_KEY}" ]] || fail "Supabase local service-role/secret API key missing"

admin_headers=(-H "apikey: ${ADMIN_API_KEY}")
if [[ "${ADMIN_API_KEY}" == eyJ* ]]; then
  admin_headers+=(-H "Authorization: Bearer ${ADMIN_API_KEY}")
fi

echo "Creating local authenticated Farm OS E2E user"
curl --fail-with-body -sS -X POST "${API_URL}/auth/v1/admin/users" \
  "${admin_headers[@]}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg email "${EMAIL}" --arg password "${PASSWORD}" \
    '{email:$email,password:$password,email_confirm:true}')" >/dev/null

echo "Signing in local E2E user"
LOGIN=$(curl --fail-with-body -sS -X POST "${API_URL}/auth/v1/token?grant_type=password" \
  -H "apikey: ${PUBLIC_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg email "${EMAIL}" --arg password "${PASSWORD}" \
    '{email:$email,password:$password}')")
ACCESS_TOKEN=$(jq -r '.access_token' <<<"${LOGIN}")
[[ -n "${ACCESS_TOKEN}" && "${ACCESS_TOKEN}" != "null" ]] || fail "Local E2E sign-in did not return an access token"

echo "Creating Farm OS E2E farm through the same authoritative RPC used by the app"
CREATE_FARM=$(curl --fail-with-body -sS -X POST "${API_URL}/rest/v1/rpc/farm_create_v1" \
  -H "apikey: ${PUBLIC_API_KEY}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg farm "${FARM_ID}" \
    '{p_farm_id:$farm,p_name:"Android Two Device E2E Farm"}')")
jq -e '.code == "ACCEPTED"' >/dev/null <<<"${CREATE_FARM}" || {
  echo "${CREATE_FARM}" | jq . >&2 || true
  fail "Could not create local E2E farm"
}

export FARM_OS_SUPABASE_URL="${DEVICE_API_URL}"
export FARM_OS_SUPABASE_PUBLISHABLE_KEY="${PUBLIC_API_KEY}"
export FARM_OS_E2E_EMAIL="${EMAIL}"
export FARM_OS_E2E_PASSWORD="${PASSWORD}"
export FARM_OS_E2E_FARM_ID="${FARM_ID}"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "FARM_OS_SUPABASE_URL=${FARM_OS_SUPABASE_URL}"
    echo "FARM_OS_SUPABASE_PUBLISHABLE_KEY=${FARM_OS_SUPABASE_PUBLISHABLE_KEY}"
    echo "FARM_OS_E2E_EMAIL=${FARM_OS_E2E_EMAIL}"
    echo "FARM_OS_E2E_PASSWORD=${FARM_OS_E2E_PASSWORD}"
    echo "FARM_OS_E2E_FARM_ID=${FARM_OS_E2E_FARM_ID}"
  } >>"${GITHUB_ENV}"
fi

trap - EXIT
echo "Android two-device E2E authority is ready; only the publishable key and disposable local test credentials were exported."
