#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/ux/FARM_OS_VISUAL_AUTHORITY.md
  docs/ux/FARM_OS_CANONICAL_VISUAL_REFERENCE_MANIFEST.yaml
  docs/ux/FARM_OS_SCREEN_REGISTRY.yaml
  docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml
  docs/ux/FARM_OS_QUANTUM_COMPLETE_SCREEN_FEATURE_VISUAL_MAPPING_REV2.md
  core/design/src/main/kotlin/com/farmos/core/design/FarmIllustratedComponents.kt
)
for path in "${required[@]}"; do
  [[ -s "$path" ]] || { echo "ERROR: missing visual authority artifact: $path"; exit 1; }
done

grep -q 'FARM_OS_VISUAL_AUTHORITY.md' AGENTS.md || {
  echo 'ERROR: AGENTS.md does not bind agents to canonical visual authority'; exit 1;
}
grep -q '## 11A. Visual product law' docs/00_PROJECT_TRUTH.md || {
  echo 'ERROR: Project Truth does not contain the visual product law'; exit 1;
}
grep -q 'FOS-GLOBAL-002' app/src/main/java/com/farmos/app/FoundationAuthScreen.kt || {
  echo 'ERROR: auth surface is not mapped to FOS-GLOBAL-002'; exit 1;
}
grep -q 'FOS-HOME-001' app/src/main/java/com/farmos/app/FarmHomeScreen.kt || {
  echo 'ERROR: farm home is not mapped to FOS-HOME-001'; exit 1;
}

# Prevent the two known VD-4 foundation labels from silently returning.
if grep -q 'Text("Farm OS foundation"' app/src/main/java/com/farmos/app/FoundationAuthScreen.kt; then
  echo 'ERROR: generic foundation auth visual has returned'; exit 1
fi
if grep -q 'Species tiles open native records' app/src/main/java/com/farmos/app/FarmHomeScreen.kt; then
  echo 'ERROR: generic module-launcher home has returned'; exit 1
fi

echo 'Farm OS portable visual authority guardrails: PASS'
