#!/usr/bin/env bash
set -euo pipefail

fail=0

check_max_lines() {
  local path="$1"
  local max="$2"
  local count
  count=$(wc -l < "$path")
  if (( count > max )); then
    echo "ERROR: $path has $count lines; stabilization ceiling is $max. Split responsibilities before adding more code." >&2
    fail=1
  fi
}

python3 scripts/development/verify_quantum_control_plane.py --check
python3 scripts/development/feature_dependencies.py --check
python3 scripts/design/build-runtime-navigation-evidence.py --self-test
python3 scripts/design/build-route-screen-feature-gap.py --self-test
bash scripts/ci/verify-actions-pinned.sh

echo "Checking Kotlin files for duplicate imports"
while IFS= read -r -d '' file; do
  duplicates=$(grep '^import ' "$file" | sort | uniq -d || true)
  if [[ -n "$duplicates" ]]; then
    echo "ERROR: duplicate imports in $file" >&2
    echo "$duplicates" >&2
    fail=1
  fi
done < <(find app core data domain feature -type f -name '*.kt' -print0)

# These are no-growth ceilings, not desired end-state sizes. They stop further
# expansion until the current large orchestration files are decomposed.
check_max_lines "data/herd/src/main/kotlin/com/farmos/data/herd/FarmOsPullReconciler.kt" 1200
check_max_lines "data/herd/src/main/kotlin/com/farmos/data/herd/RoomOpsRepository.kt" 1400
check_max_lines "data/herd/src/main/kotlin/com/farmos/data/herd/FarmOsDataHelpers.kt" 300
check_max_lines "app/src/main/java/com/farmos/app/OperatingModuleHost.kt" 1900
check_max_lines "app/src/main/java/com/farmos/app/MainActivity.kt" 260
check_max_lines "app/src/main/java/com/farmos/app/FarmSessionContent.kt" 450

# Extracted module hosts are intentionally narrow. If one needs to grow past
# these ceilings, split state/read/write responsibilities rather than rebuilding
# a second all-domain host.
check_max_lines "app/src/main/java/com/farmos/app/TasksModuleHost.kt" 120
check_max_lines "app/src/main/java/com/farmos/app/MoneyModuleHost.kt" 120
check_max_lines "app/src/main/java/com/farmos/app/InventoryModuleHost.kt" 180
check_max_lines "app/src/main/java/com/farmos/app/HealthModuleHost.kt" 240
check_max_lines "app/src/main/java/com/farmos/app/RabbitModuleHost.kt" 240
check_max_lines "app/src/main/java/com/farmos/app/PoultryModuleHost.kt" 230
check_max_lines "app/src/main/java/com/farmos/app/FeedModuleHost.kt" 140
check_max_lines "app/src/main/java/com/farmos/app/WaterModuleHost.kt" 120
check_max_lines "app/src/main/java/com/farmos/app/SalesModuleHost.kt" 120
check_max_lines "app/src/main/java/com/farmos/app/ProcurementModuleHost.kt" 160
check_max_lines "app/src/main/java/com/farmos/app/RabbitCommerceModuleHost.kt" 220
check_max_lines "app/src/main/java/com/farmos/app/GroupsModuleHost.kt" 150
check_max_lines "app/src/main/java/com/farmos/app/PastureModuleHost.kt" 170
check_max_lines "app/src/main/java/com/farmos/app/LabourModuleHost.kt" 110
check_max_lines "app/src/main/java/com/farmos/app/AssetsModuleHost.kt" 130

if grep -q 'RecordPoultryFlockDay\|recordFlockDay' "app/src/main/java/com/farmos/app/OperatingModuleHost.kt"; then
  echo "ERROR: poultry flock-day mutation/read ownership leaked back into OperatingModuleHost; keep it in PoultryModuleHost." >&2
  fail=1
fi

if (( fail != 0 )); then
  exit 1
fi

echo "Kotlin architecture stabilization checks passed"


echo "Checking exact persisted decimal boundaries"
for file in   app/src/main/java/com/farmos/app/SalesModuleHost.kt   app/src/main/java/com/farmos/app/ProcurementModuleHost.kt   app/src/main/java/com/farmos/app/RabbitCommerceModuleHost.kt   app/src/main/java/com/farmos/app/FeedModuleHost.kt   app/src/main/java/com/farmos/app/WaterModuleHost.kt   app/src/main/java/com/farmos/app/OperatingModuleHost.kt
do
  if grep -Fq 'toDoubleOrNull()' "$file"; then
    echo "ERROR: floating-point parsing remains on a persisted decimal boundary in $file" >&2
    fail=1
  fi
done

if grep -Fq 'WorkManager.getInstance(app).enqueue(' app/src/main/java/com/farmos/app/FarmSessionContent.kt; then
  echo "ERROR: on-demand sync must use unique work rather than plain enqueue" >&2
  fail=1
fi
if ! grep -Fq 'enqueueUniqueWork(' app/src/main/java/com/farmos/app/FarmSessionContent.kt; then
  echo "ERROR: on-demand sync unique-work serialization is missing" >&2
  fail=1
fi
if ! grep -Fq 'suspend fun claim(' core/database/src/main/kotlin/com/farmos/core/database/FarmOsDatabase.kt; then
  echo "ERROR: atomic outbox claim DAO contract is missing" >&2
  fail=1
fi
if ! grep -Fq 'outbox.claim(' core/sync/src/main/kotlin/com/farmos/core/sync/SyncEngine.kt; then
  echo "ERROR: SyncEngine must claim a mutation before transport" >&2
  fail=1
fi
if ! grep -Fq '/auth/v1/logout?scope=' core/network/src/main/kotlin/com/farmos/core/network/SupabaseClients.kt; then
  echo "ERROR: user sign-out must attempt Supabase server session revocation" >&2
  fail=1
fi

if (( fail != 0 )); then
  exit 1
fi
