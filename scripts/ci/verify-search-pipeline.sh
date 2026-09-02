#!/usr/bin/env bash
set -euo pipefail

API_URL="http://127.0.0.1:54321"
MEILI_HOST="http://127.0.0.1:7700"
MEILI_MASTER_KEY="farm-os-pipeline-master-key-32-bytes-minimum"
MEILI_INDEX_PREFIX="farm-os"
MEILI_INDEX="${MEILI_INDEX_PREFIX}-animals-v1"
INDEXER_SECRET="farm-os-indexer-ci-secret"
REBUILD_SECRET="farm-os-rebuild-ci-secret"
SEARCH_KEY_UID="5c9cd5bf-ae37-47b1-a2e2-59f87413c119"
FARM_A="11111111-1111-4111-8111-111111111111"
FARM_B="22222222-2222-4222-8222-222222222222"
ANIMAL_A="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
ANIMAL_B="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
FUNCTION_PID=""
FUNCTION_LOG="/tmp/farm-os-function.log"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

assert_json() {
  local json="$1"
  local expression="$2"
  local message="$3"
  if ! jq -e "$expression" >/dev/null <<<"$json"; then
    echo "Assertion failed: $message" >&2
    echo "$json" | jq . >&2 || echo "$json" >&2
    exit 1
  fi
}

stop_function() {
  if [[ -n "${FUNCTION_PID}" ]] && kill -0 "${FUNCTION_PID}" 2>/dev/null; then
    kill "${FUNCTION_PID}" 2>/dev/null || true
    wait "${FUNCTION_PID}" 2>/dev/null || true
  fi
  FUNCTION_PID=""
}

cleanup() {
  stop_function
  docker rm -f farm-os-meili >/dev/null 2>&1 || true
  supabase stop --no-backup >/dev/null 2>&1 || true
  docker network rm farm-os-ci >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_meili_health() {
  for _ in $(seq 1 100); do
    if curl -fsS "${MEILI_HOST}/health" >/dev/null 2>&1; then
      return
    fi
    sleep 0.1
  done
  fail "Meilisearch did not become healthy"
}

wait_meili_task() {
  local uid="$1"
  for _ in $(seq 1 200); do
    local task
    task=$(curl -fsS "${MEILI_HOST}/tasks/${uid}" -H "Authorization: Bearer ${MEILI_MASTER_KEY}")
    local status
    status=$(jq -r '.status' <<<"$task")
    case "$status" in
      succeeded) return ;;
      failed|canceled)
        echo "$task" | jq . >&2
        fail "Meilisearch task ${uid} ${status}"
        ;;
    esac
    sleep 0.1
  done
  fail "Meilisearch task ${uid} did not finish"
}

start_function() {
  local source="$1"
  shift
  stop_function
  : >"${FUNCTION_LOG}"
  env "$@" deno run --allow-net --allow-env "${source}" >"${FUNCTION_LOG}" 2>&1 &
  FUNCTION_PID=$!
  for _ in $(seq 1 100); do
    if ! kill -0 "${FUNCTION_PID}" 2>/dev/null; then
      cat "${FUNCTION_LOG}" >&2
      fail "Function ${source} exited before serving"
    fi
    if curl -sS -o /dev/null "http://127.0.0.1:8000/" 2>/dev/null; then
      return
    fi
    sleep 0.1
  done
  cat "${FUNCTION_LOG}" >&2
  fail "Function ${source} did not start"
}

rpc() {
  local token="$1"
  local function="$2"
  local body="$3"
  curl --fail-with-body -sS -X POST "${API_URL}/rest/v1/rpc/${function}" \
    -H "apikey: ${ANON_KEY}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "${body}"
}

service_get() {
  local path="$1"
  curl --fail-with-body -sS "${API_URL}${path}" \
    -H "apikey: ${SERVICE_ROLE_KEY}" \
    -H "Authorization: Bearer ${SERVICE_ROLE_KEY}"
}

wait_all_inflight_tasks() {
  local jobs
  jobs=$(service_get "/rest/v1/search_index_jobs?select=meili_task_uid&state=eq.in_flight&meili_task_uid=not.is.null")
  while read -r uid; do
    [[ -n "$uid" && "$uid" != "null" ]] && wait_meili_task "$uid"
  done < <(jq -r '.[].meili_task_uid' <<<"$jobs")
}

run_indexer() {
  start_function \
    "supabase/functions/meili-indexer/index.ts" \
    "SUPABASE_URL=${API_URL}" \
    "SUPABASE_SERVICE_ROLE_KEY=${SERVICE_ROLE_KEY}" \
    "MEILI_HOST=${MEILI_HOST}" \
    "MEILI_ADMIN_KEY=${MEILI_MASTER_KEY}" \
    "MEILI_INDEX_PREFIX=${MEILI_INDEX_PREFIX}" \
    "INDEXER_INVOKE_SECRET=${INDEXER_SECRET}"
  local response
  response=$(curl --fail-with-body -sS -X POST "http://127.0.0.1:8000/" \
    -H "x-farmos-indexer-secret: ${INDEXER_SECRET}")
  stop_function
  echo "$response"
}

echo "Creating isolated local Docker network"
docker network create farm-os-ci >/dev/null

echo "Starting pinned Meilisearch v1.53.1"
docker run -d --name farm-os-meili \
  --network farm-os-ci \
  -p 7700:7700 \
  -e "MEILI_MASTER_KEY=${MEILI_MASTER_KEY}" \
  -e MEILI_NO_ANALYTICS=true \
  getmeili/meilisearch:v1.53.1 >/dev/null
wait_meili_health

echo "Starting local Supabase authority"
supabase start --network-id farm-os-ci -x studio,imgproxy,edge-runtime,logflare,vector,supavisor >/tmp/farm-os-supabase-start.log
supabase db reset >/tmp/farm-os-supabase-reset.log
# The CLI env format is shell-compatible and still exposes legacy local keys for compatibility tests.
eval "$(supabase status -o env)"
: "${ANON_KEY:?Supabase local ANON_KEY missing}"
: "${SERVICE_ROLE_KEY:?Supabase local SERVICE_ROLE_KEY missing}"

echo "Applying the production Meilisearch index contract"
MEILI_HOST="${MEILI_HOST}" \
MEILI_MASTER_KEY="${MEILI_MASTER_KEY}" \
MEILI_INDEX_PREFIX="${MEILI_INDEX_PREFIX}" \
  deno run \
    --allow-net=127.0.0.1:7700 \
    --allow-env=MEILI_HOST,MEILI_MASTER_KEY,MEILI_INDEX_PREFIX \
    --allow-read=search/meilisearch/animals-v1.settings.json \
    search/meilisearch/apply-index-contract.ts

echo "Creating a search-only Meilisearch parent key"
SEARCH_KEY_JSON=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/keys" \
  -H "Authorization: Bearer ${MEILI_MASTER_KEY}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn \
    --arg uid "$SEARCH_KEY_UID" \
    --arg index "$MEILI_INDEX" \
    '{uid:$uid,name:"Farm OS pipeline search key",description:"CI only",actions:["search"],indexes:[$index],expiresAt:null}')")
MEILI_SEARCH_KEY=$(jq -r '.key' <<<"$SEARCH_KEY_JSON")
[[ -n "$MEILI_SEARCH_KEY" && "$MEILI_SEARCH_KEY" != "null" ]] || fail "Meilisearch did not return search key"

create_user() {
  local email="$1"
  local password="$2"
  curl --fail-with-body -sS -X POST "${API_URL}/auth/v1/admin/users" \
    -H "apikey: ${SERVICE_ROLE_KEY}" \
    -H "Authorization: Bearer ${SERVICE_ROLE_KEY}" \
    -H "Content-Type: application/json" \
    -d "$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password,email_confirm:true}')" >/dev/null
  curl --fail-with-body -sS -X POST "${API_URL}/auth/v1/token?grant_type=password" \
    -H "apikey: ${ANON_KEY}" \
    -H "Content-Type: application/json" \
    -d "$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')" \
    | jq -r '.access_token'
}

TOKEN_A=$(create_user "farm-a@farmos.invalid" "FarmOS-ci-A-123456!")
TOKEN_B=$(create_user "farm-b@farmos.invalid" "FarmOS-ci-B-123456!")
[[ "$TOKEN_A" != "null" && "$TOKEN_B" != "null" ]] || fail "Could not sign in local test users"

echo "Creating isolated farms through the authoritative RPC"
FARM_A_CREATE=$(rpc "$TOKEN_A" "farm_create_v1" "$(jq -cn --arg id "$FARM_A" '{p_farm_id:$id,p_name:"Farm A"}')")
FARM_B_CREATE=$(rpc "$TOKEN_B" "farm_create_v1" "$(jq -cn --arg id "$FARM_B" '{p_farm_id:$id,p_name:"Farm B"}')")
assert_json "$FARM_A_CREATE" '.code == "ACCEPTED"' "Farm A creation rejected"
assert_json "$FARM_B_CREATE" '.code == "ACCEPTED"' "Farm B creation rejected"

echo "Registering one goat per farm through goat_register_v1"
REGISTER_A=$(rpc "$TOKEN_A" "goat_register_v1" "$(jq -cn \
  --arg mutation "aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa" \
  --arg farm "$FARM_A" \
  --arg animal "$ANIMAL_A" \
  '{p_mutation_id:$mutation,p_farm_id:$farm,p_device_id:"device-a",p_expected_stream_version:0,p_occurred_at_epoch_ms:1700000000000,p_payload:{animalId:$animal,tag:"A-001",name:"Nala",sex:"FEMALE",dateOfBirthEpochDay:19723}}')")
REGISTER_B=$(rpc "$TOKEN_B" "goat_register_v1" "$(jq -cn \
  --arg mutation "bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb" \
  --arg farm "$FARM_B" \
  --arg animal "$ANIMAL_B" \
  '{p_mutation_id:$mutation,p_farm_id:$farm,p_device_id:"device-b",p_expected_stream_version:0,p_occurred_at_epoch_ms:1700000000000,p_payload:{animalId:$animal,tag:"B-001",name:"Zuri",sex:"FEMALE",dateOfBirthEpochDay:19724}}')")
assert_json "$REGISTER_A" '.code == "ACCEPTED" and .streamVersion == 1' "Farm A goat registration rejected"
assert_json "$REGISTER_B" '.code == "ACCEPTED" and .streamVersion == 1' "Farm B goat registration rejected"

JOBS=$(service_get "/rest/v1/search_index_jobs?select=state,projection_version&order=job_id.asc")
assert_json "$JOBS" 'length == 2 and all(.[]; .state == "pending" and .projection_version == 1)' "Authoritative mutations did not create two pending search jobs"

echo "Dispatching authoritative search jobs through the real indexer function"
INDEXER_FIRST=$(run_indexer)
assert_json "$INDEXER_FIRST" '.dispatched == 2' "Indexer did not dispatch both registration jobs"
wait_all_inflight_tasks
INDEXER_RECONCILE=$(run_indexer)
assert_json "$INDEXER_RECONCILE" '.completed == 2' "Indexer did not reconcile successful Meilisearch tasks"
DONE_JOBS=$(service_get "/rest/v1/search_index_jobs?select=state&projection_version=eq.1")
assert_json "$DONE_JOBS" 'length == 2 and all(.[]; .state == "done")' "Registration search jobs are not done"

echo "Serving the real search-token function and proving membership isolation"
start_function \
  "supabase/functions/search-token/index.ts" \
  "SUPABASE_URL=${API_URL}" \
  "SUPABASE_ANON_KEY=${ANON_KEY}" \
  "MEILI_SEARCH_KEY=${MEILI_SEARCH_KEY}" \
  "MEILI_SEARCH_KEY_UID=${SEARCH_KEY_UID}" \
  "MEILI_INDEX_PREFIX=${MEILI_INDEX_PREFIX}"

TOKEN_A_JSON=$(curl --fail-with-body -sS -X POST "http://127.0.0.1:8000/" \
  -H "Authorization: Bearer ${TOKEN_A}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg farm "$FARM_A" '{farmId:$farm}')")
TENANT_A=$(jq -r '.token' <<<"$TOKEN_A_JSON")

TOKEN_B_JSON=$(curl --fail-with-body -sS -X POST "http://127.0.0.1:8000/" \
  -H "Authorization: Bearer ${TOKEN_B}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg farm "$FARM_B" '{farmId:$farm}')")
TENANT_B=$(jq -r '.token' <<<"$TOKEN_B_JSON")

CROSS_STATUS=$(curl -sS -o /tmp/farm-os-cross-farm-token.json -w '%{http_code}' -X POST "http://127.0.0.1:8000/" \
  -H "Authorization: Bearer ${TOKEN_A}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg farm "$FARM_B" '{farmId:$farm}')")
[[ "$CROSS_STATUS" == "403" ]] || {
  cat /tmp/farm-os-cross-farm-token.json >&2
  fail "Farm A user obtained or reached a Farm B tenant-token path (HTTP ${CROSS_STATUS})"
}
stop_function

SEARCH_A=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_A}" \
  -H "Content-Type: application/json" \
  -d '{"q":"","limit":20}')
SEARCH_B=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_B}" \
  -H "Content-Type: application/json" \
  -d '{"q":"","limit":20}')
assert_json "$SEARCH_A" --argdummy 2>/dev/null || true
assert_json "$SEARCH_A" '(.hits | map(.id)) == ["aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"]' "Farm A tenant search leaked or lost documents"
assert_json "$SEARCH_B" '(.hits | map(.id)) == ["bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"]' "Farm B tenant search leaked or lost documents"

OVERRIDE_A=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_A}" \
  -H "Content-Type: application/json" \
  -d "$(jq -cn --arg farm "$FARM_B" '{q:"",filter:("farm_id = \""+$farm+"\""),limit:20}')")
assert_json "$OVERRIDE_A" '.hits | length == 0' "Caller-supplied filter overrode Farm A tenant rule"

echo "Stopping Meilisearch and proving authoritative capture is independent"
docker stop farm-os-meili >/dev/null
WEIGHT_A=$(rpc "$TOKEN_A" "goat_record_weight_v1" "$(jq -cn \
  --arg mutation "aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa" \
  --arg farm "$FARM_A" \
  --arg animal "$ANIMAL_A" \
  '{p_mutation_id:$mutation,p_farm_id:$farm,p_device_id:"device-a",p_expected_stream_version:1,p_occurred_at_epoch_ms:1700000001000,p_payload:{animalId:$animal,measurementId:"aaaaaaaa-3333-4333-8333-aaaaaaaaaaaa",weightGrams:32450,measuredAtEpochMillis:1700000001000}}')")
assert_json "$WEIGHT_A" '.code == "ACCEPTED" and .streamVersion == 2' "Weight mutation was blocked by search outage"
MEASUREMENTS=$(service_get "/rest/v1/measurements?farm_id=eq.${FARM_A}&animal_id=eq.${ANIMAL_A}&select=value_long")
assert_json "$MEASUREMENTS" 'length == 1 and .[0].value_long == 32450' "Authoritative weight projection was not committed during search outage"

OUTAGE_INDEXER=$(run_indexer)
assert_json "$OUTAGE_INDEXER" '.dispatched == 0' "Indexer reported a successful dispatch while Meilisearch was offline"
RETRY_JOB=$(service_get "/rest/v1/search_index_jobs?entity_id=eq.${ANIMAL_A}&projection_version=eq.2&select=state,attempts&order=job_id.desc&limit=1")
assert_json "$RETRY_JOB" 'length == 1 and .[0].state == "retry" and .[0].attempts == 1' "Search outage did not produce a durable retry job"

echo "Restoring Meilisearch and recovering the durable retry"
docker start farm-os-meili >/dev/null
wait_meili_health
sleep 3
RECOVERY_DISPATCH=$(run_indexer)
assert_json "$RECOVERY_DISPATCH" '.dispatched == 1' "Recovered indexer did not dispatch the retry job"
wait_all_inflight_tasks
RECOVERY_RECONCILE=$(run_indexer)
assert_json "$RECOVERY_RECONCILE" '.completed == 1' "Recovered indexer did not reconcile the retry task"
RECOVERED_DOC=$(curl --fail-with-body -sS "${MEILI_HOST}/indexes/${MEILI_INDEX}/documents/${ANIMAL_A}" \
  -H "Authorization: Bearer ${MEILI_MASTER_KEY}")
assert_json "$RECOVERED_DOC" '.updated_projection_version == 2' "Recovered search document is not at authoritative stream version 2"

echo "Clearing the search projection and exercising the real rebuild function"
DELETE_TASK=$(curl --fail-with-body -sS -X DELETE "${MEILI_HOST}/indexes/${MEILI_INDEX}/documents" \
  -H "Authorization: Bearer ${MEILI_MASTER_KEY}")
wait_meili_task "$(jq -r '.taskUid' <<<"$DELETE_TASK")"
EMPTY_SEARCH=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_A}" \
  -H "Content-Type: application/json" \
  -d '{"q":"","limit":20}')
assert_json "$EMPTY_SEARCH" '.hits | length == 0' "Search projection was not empty before rebuild"

start_function \
  "supabase/functions/meili-rebuild/index.ts" \
  "SUPABASE_URL=${API_URL}" \
  "SUPABASE_SERVICE_ROLE_KEY=${SERVICE_ROLE_KEY}" \
  "MEILI_REBUILD_SECRET=${REBUILD_SECRET}"
REBUILD_RESPONSE=$(curl --fail-with-body -sS -X POST "http://127.0.0.1:8000/" \
  -H "x-farmos-rebuild-secret: ${REBUILD_SECRET}" \
  -H "Content-Type: application/json" \
  -d '{"pageSize":100,"maxPages":5}')
stop_function
assert_json "$REBUILD_RESPONSE" '.queued == 2 and .complete == true' "Rebuild function did not enqueue both authoritative animals"

REBUILD_DISPATCH=$(run_indexer)
assert_json "$REBUILD_DISPATCH" '.dispatched == 2' "Indexer did not dispatch full rebuild jobs"
wait_all_inflight_tasks
REBUILD_RECONCILE=$(run_indexer)
assert_json "$REBUILD_RECONCILE" '.completed == 2' "Indexer did not reconcile full rebuild tasks"

REBUILT_A=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_A}" \
  -H "Content-Type: application/json" \
  -d '{"q":"","limit":20}')
REBUILT_B=$(curl --fail-with-body -sS -X POST "${MEILI_HOST}/indexes/${MEILI_INDEX}/search" \
  -H "Authorization: Bearer ${TENANT_B}" \
  -H "Content-Type: application/json" \
  -d '{"q":"","limit":20}')
assert_json "$REBUILT_A" '.hits | length == 1 and .hits[0].id == "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" and .hits[0].updated_projection_version == 2' "Farm A was not rebuilt from authoritative version 2"
assert_json "$REBUILT_B" '.hits | length == 1 and .hits[0].id == "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb" and .hits[0].updated_projection_version == 1' "Farm B was not rebuilt from authoritative version 1"

echo "Farm OS search pipeline passed: authoritative RPCs, durable indexing, tenant isolation, outage recovery, and full rebuild are proven live."
