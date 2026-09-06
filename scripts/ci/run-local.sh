#!/usr/bin/env bash
# Local alternative to GitHub Actions for Farm OS foundation CI.
# Mirrors workflow job names and commands. Does not skip or weaken
# .github/workflows/*.yml. Missing host tools are UNAVAILABLE, not PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

JAVA_HOME="${JAVA_HOME:-${HOME}/.local/jdk/jdk-17.0.20.1+1}"
ANDROID_HOME="${ANDROID_HOME:-${HOME}/.local/android-sdk}"
GRADLE_HOME="${GRADLE_HOME:-${HOME}/.local/gradle/gradle-9.3.1}"
DENO_HOME="${DENO_HOME:-${HOME}/.local/deno}"
MEILI_BIN="${MEILI_BIN:-${HOME}/.local/meilisearch/meilisearch-v1.53.1}"
MEILI_VERSION="v1.53.1"
export JAVA_HOME ANDROID_HOME
export PATH="${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${DENO_HOME}/bin:${PATH}"

if [[ ! -f local.properties && -d "${ANDROID_HOME}" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_HOME}" > local.properties
fi

RESULTS="$(mktemp)"
trap 'rm -f "${RESULTS}"' EXIT

record() {
  local job="$1" status="$2" detail="$3"
  printf '%s\t%s\t%s\n' "${job}" "${status}" "${detail}" >> "${RESULTS}"
  echo "[${status}] ${job}: ${detail}"
}

run_job() {
  local job="$1"
  shift
  local log
  log="$(mktemp)"
  if "$@" >"${log}" 2>&1; then
    record "${job}" "PASS" "executed on this host"
    rm -f "${log}"
    return 0
  fi
  record "${job}" "FAIL" "$(tail -c 400 "${log}" | tr '\n' ' ')"
  rm -f "${log}"
  return 1
}

need() {
  local name="$1"
  shift
  command -v "$@" >/dev/null 2>&1
}

ensure_meilisearch_bin() {
  if [[ -x "${MEILI_BIN}" ]]; then
    return 0
  fi
  mkdir -p "$(dirname "${MEILI_BIN}")"
  local url="https://github.com/meilisearch/meilisearch/releases/download/${MEILI_VERSION}/meilisearch-linux-amd64"
  curl -fsSL "${url}" -o "${MEILI_BIN}"
  chmod +x "${MEILI_BIN}"
}

job_smoke() {
  echo "Local CI runner is available for Farm OS"
  record "smoke" "PASS" "local runner echo"
}

job_handover_integrity() {
  run_job "handover-integrity" bash -lc '
    node scripts/design/verify-handover.cjs &&
    node docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test --registry docs/ux/FARM_OS_SCREEN_REGISTRY.yaml &&
    node --check docs/ux/animal-farm-visual-lock/scripts/assemble-pack.cjs &&
    node --check docs/ux/animal-farm-visual-lock/scripts/refine-registry.cjs &&
    node --check docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs
  '
}

job_android() {
  if ! need gradle gradle; then
    record "android" "UNAVAILABLE" "gradle 9.3.1 not on PATH"
    return 0
  fi
  if ! need java java; then
    record "android" "UNAVAILABLE" "JDK 17 not on PATH"
    return 0
  fi
  run_job "android" bash -lc '
    bash scripts/ci/verify-kotlin-architecture.sh &&
    bash scripts/ci/verify-visual-authority.sh &&
    gradle
      :domain:goat:test
      :domain:rabbit:test
      :domain:ops:test
      :core:network:testDebugUnitTest
      :core:sync:testDebugUnitTest
      :app:compileDebugKotlin
      :core:database:compileDebugAndroidTestKotlin
      :app:compileDebugAndroidTestKotlin
      --stacktrace --offline
  '
}

job_android_device_e2e() {
  record "android-device-e2e" "UNAVAILABLE" "emulator AVD and supabase CLI are not provisioned on this host"
}

job_edge_functions() {
  if ! need deno deno; then
    record "edge-functions" "UNAVAILABLE" "deno 2.9.6 not on PATH"
    return 0
  fi
  run_job "edge-functions" bash -lc '
    bash -n
      scripts/ci/verify-kotlin-architecture.sh
      scripts/ci/verify-search-pipeline.sh
      scripts/ci/prepare-two-device-e2e.sh
      scripts/ci/run-two-device-e2e.sh
      scripts/ci/run-local.sh &&
    deno check
      supabase/functions/meili-indexer/index.ts
      supabase/functions/meili-rebuild/index.ts
      supabase/functions/search-token/index.ts
      search/meilisearch/apply-index-contract.ts
      search/meilisearch/verify-live-contract.ts &&
    deno test --allow-env supabase/functions/tests/supabase_api_keys_test.ts
  '
}

job_meilisearch_contract() {
  if ! need deno deno; then
    record "meilisearch-contract" "UNAVAILABLE" "deno 2.9.6 not on PATH"
    return 0
  fi
  if ! need curl curl; then
    record "meilisearch-contract" "UNAVAILABLE" "curl is required to start or fetch Meilisearch"
    return 0
  fi
  export MEILI_HOST="${MEILI_HOST:-http://127.0.0.1:7700}"
  export MEILI_MASTER_KEY="${MEILI_MASTER_KEY:-farm-os-ci-master-key-32-bytes-minimum}"
  export MEILI_INDEX_PREFIX="${MEILI_INDEX_PREFIX:-farm-os}"
  local meili_pid=""
  local data_dir
  data_dir="$(mktemp -d)"
  cleanup_meili() {
    if [[ -n "${meili_pid}" ]]; then
      kill "${meili_pid}" 2>/dev/null || true
      wait "${meili_pid}" 2>/dev/null || true
    fi
    docker rm -f farm-os-meili >/dev/null 2>&1 || true
    rm -rf "${data_dir}"
  }
  trap cleanup_meili RETURN
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    docker rm -f farm-os-meili >/dev/null 2>&1 || true
    docker run -d --rm --name farm-os-meili \
      -p 7700:7700 \
      -e MEILI_MASTER_KEY="${MEILI_MASTER_KEY}" \
      -e MEILI_NO_ANALYTICS=true \
      getmeili/meilisearch:v1.53.1 >/dev/null
  else
    if ! ensure_meilisearch_bin; then
      record "meilisearch-contract" "UNAVAILABLE" "docker daemon missing and Meilisearch ${MEILI_VERSION} binary could not be fetched"
      return 0
    fi
    "${MEILI_BIN}" --http-addr 127.0.0.1:7700 --master-key "${MEILI_MASTER_KEY}" --env development --db-path "${data_dir}" --no-analytics >/tmp/farm-os-meili-local.log 2>&1 &
    meili_pid=$!
  fi
  local healthy=0
  for _ in $(seq 1 100); do
    if curl -fsS "${MEILI_HOST}/health" >/dev/null 2>&1; then
      healthy=1
      break
    fi
    sleep 0.1
  done
  if (( healthy != 1 )); then
    record "meilisearch-contract" "FAIL" "Meilisearch did not become healthy"
    return 1
  fi
  run_job "meilisearch-contract" deno run --allow-net=127.0.0.1:7700 \
    --allow-env=MEILI_HOST,MEILI_MASTER_KEY,MEILI_INDEX_PREFIX \
    --allow-read=search/meilisearch/animals-v1.settings.json \
    search/meilisearch/verify-live-contract.ts
}

job_search_pipeline() {
  if ! command -v supabase >/dev/null 2>&1 || ! command -v docker >/dev/null 2>&1; then
    record "search-pipeline" "UNAVAILABLE" "supabase CLI and Docker are required by verify-search-pipeline.sh"
    return 0
  fi
  run_job "search-pipeline" bash scripts/ci/verify-search-pipeline.sh
}

job_supabase() {
  if ! command -v supabase >/dev/null 2>&1 || ! command -v docker >/dev/null 2>&1; then
    record "supabase" "UNAVAILABLE" "supabase CLI 2.116.0 and Docker are required for db reset/pgTAP"
    return 0
  fi
  run_job "supabase" bash -lc 'supabase start -x studio,imgproxy,edge-runtime,logflare,vector,supavisor && supabase db reset && supabase test db'
}

job_local_nav_audit() {
  run_job "local-nav-audit" node scripts/design/audit-navigation.cjs --self-test --quiet
}

emit_report() {
  local out="${1:-}"
  python3 - "$RESULTS" "${out}" <<'PY'
import json, pathlib, subprocess, sys
results_path = pathlib.Path(sys.argv[1])
out = sys.argv[2]
rows = []
for line in results_path.read_text().splitlines():
    job, status, detail = line.split("\t", 2)
    rows.append({"job": job, "status": status, "detail": detail})
sha = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
counts = {"PASS": 0, "FAIL": 0, "UNAVAILABLE": 0}
for row in rows:
    counts[row["status"]] = counts.get(row["status"], 0) + 1
report = {
    "schema_version": 1,
    "evidence_class": "LOCAL_CI_ALTERNATIVE_NOT_GITHUB_HOSTED_CERTIFICATION",
    "tested_commit": sha,
    "runner": "local-host",
    "jobs": rows,
    "counts": counts,
    "status_law": "UNAVAILABLE is missing host tooling, not a source pass. FAIL is a source or local-runtime defect. This report does not promote PROJECT_GREEN and does not replace GitHub-hosted ubuntu-latest.",
}
text = json.dumps(report, indent=2) + "\n"
if out:
    dest = pathlib.Path(out)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text)
sys.stdout.write(text)
sys.exit(1 if counts.get("FAIL", 0) else 0)
PY
}

if [[ "${1:-}" == "--self-test" ]]; then
  bash -n "$0"
  echo "PASS local CI runner self-test"
  exit 0
fi

OUT=""
if [[ "${1:-}" == "--out" ]]; then
  OUT="${2:?--out requires a path}"
fi

echo "Farm OS local CI alternative at ${ROOT}"
set +e
job_smoke
job_handover_integrity
job_android
job_android_device_e2e
job_edge_functions
job_meilisearch_contract
job_search_pipeline
job_supabase
job_local_nav_audit
set -e
emit_report "${OUT}"
