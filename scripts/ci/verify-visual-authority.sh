#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/ux/FARM_OS_VISUAL_AUTHORITY.md
  docs/ux/FARM_OS_CANONICAL_VISUAL_REFERENCE_MANIFEST.yaml
  docs/ux/FARM_OS_SCREEN_REGISTRY.yaml
  docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml
  docs/ux/FARM_OS_QUANTUM_COMPLETE_SCREEN_FEATURE_VISUAL_MAPPING_REV2.md
  core/design/src/main/kotlin/com/farmos/core/design/FarmIllustratedComponents.kt
  app/src/main/java/com/farmos/app/FarmOsSplashScreen.kt
  app/src/main/java/com/farmos/app/SpeciesNavigatorScreen.kt
  feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt
  feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatCaptureScreens.kt
  core/design/src/main/kotlin/com/farmos/core/design/FarmOperationalPage.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/HealthExperienceScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/InventoryExperienceScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/FinanceExperienceScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/TasksBoardScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/PoultryExperienceScreen.kt
  app/src/main/java/com/farmos/app/SpeciesHerdScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/SheepOperationsScreen.kt
  feature/ops/src/main/kotlin/com/farmos/feature/ops/CattleOperationsScreen.kt
  feature/rabbit/src/main/kotlin/com/farmos/feature/rabbit/RabbitProgrammeScreen.kt
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

grep -q 'FOS-GLOBAL-001' app/src/main/java/com/farmos/app/FarmOsSplashScreen.kt || {
  echo 'ERROR: splash is not mapped to FOS-GLOBAL-001'; exit 1;
}
grep -q 'FOS-HOME-002' app/src/main/java/com/farmos/app/SpeciesNavigatorScreen.kt || {
  echo 'ERROR: species navigator is not mapped to FOS-HOME-002'; exit 1;
}
grep -q 'FOS-GOAT-003' feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt || {
  echo 'ERROR: goat profile reference is missing'; exit 1;
}
grep -q 'FOS-GOAT-011 · I3 reference' feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatCaptureScreens.kt || {
  echo 'ERROR: goat I3 weight reference is missing'; exit 1;
}
grep -q 'FOS-GOAT-051 · I4' feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt || {
  echo 'ERROR: goat I4 lifecycle reference is missing'; exit 1;
}
if (( $(wc -l < feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatVerticalSliceScreen.kt) > 120 )); then
  echo 'ERROR: goat proving mega-screen has grown back instead of remaining an atomic compatibility entrypoint'; exit 1
fi

registry_count=$(grep -c '^  - screen_id:' docs/ux/FARM_OS_SCREEN_REGISTRY.yaml)
if [[ "$registry_count" -lt 500 ]]; then
  echo "ERROR: quantum screen registry collapsed below atomic coverage floor: $registry_count"; exit 1
fi
grep -q '^screen_count: 537$' docs/ux/FARM_OS_SCREEN_REGISTRY.yaml || {
  echo 'ERROR: generated atomic screen registry is stale; run scripts/design/generate_screen_registry.py'; exit 1;
}

grep -q 'FOS-SHEEP-010' feature/ops/src/main/kotlin/com/farmos/feature/ops/SheepOperationsScreen.kt || { echo 'ERROR: sheep visual reference family missing'; exit 1; }
grep -q 'FOS-CATTLE-018' feature/ops/src/main/kotlin/com/farmos/feature/ops/CattleOperationsScreen.kt || { echo 'ERROR: cattle visual reference family missing'; exit 1; }
grep -q 'FOS-POULTRY-001' feature/ops/src/main/kotlin/com/farmos/feature/ops/PoultryExperienceScreen.kt || { echo 'ERROR: poultry visual reference family missing'; exit 1; }
grep -q 'FOS-RABBIT-001' feature/rabbit/src/main/kotlin/com/farmos/feature/rabbit/RabbitProgrammeScreen.kt || { echo 'ERROR: rabbit visual reference family missing'; exit 1; }
grep -q 'FOS-HEALTH-001' feature/ops/src/main/kotlin/com/farmos/feature/ops/HealthExperienceScreen.kt || { echo 'ERROR: health visual reference family missing'; exit 1; }
grep -q 'FOS-INV-001' feature/ops/src/main/kotlin/com/farmos/feature/ops/InventoryExperienceScreen.kt || { echo 'ERROR: inventory visual reference family missing'; exit 1; }
grep -q 'FOS-FIN-001' feature/ops/src/main/kotlin/com/farmos/feature/ops/FinanceExperienceScreen.kt || { echo 'ERROR: finance visual reference family missing'; exit 1; }

grep -q '^visual_green_count: 0$' docs/ux/FARM_OS_SCREEN_REGISTRY.yaml || {
  echo 'ERROR: visual-green count changed without evidence registry update'; exit 1;
}

# Prevent the two known VD-4 foundation labels from silently returning.
if grep -q 'Text("Farm OS foundation"' app/src/main/java/com/farmos/app/FoundationAuthScreen.kt; then
  echo 'ERROR: generic foundation auth visual has returned'; exit 1
fi
if grep -q 'Species tiles open native records' app/src/main/java/com/farmos/app/FarmHomeScreen.kt; then
  echo 'ERROR: generic module-launcher home has returned'; exit 1
fi

# Animal Farm lock: shared primitives must not restore geometric scenery or feature slogans.
if grep -E 'Brush\.(linear|radial|vertical)Gradient|FarmIllustratedPalette|fun drawGoat|fun drawRabbit' \
  core/design/src/main/kotlin/com/farmos/core/design/FarmIllustratedComponents.kt; then
  echo 'ERROR: pastoral canvas scenery returned to FarmIllustratedComponents'; exit 1
fi
if grep -q 'Animals. Land. People' app/src/main/java/com/farmos/app/FarmOsSplashScreen.kt; then
  echo 'ERROR: splash slogans returned'; exit 1
fi
if grep -q 'Healthy animals. Thriving farms' feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt; then
  echo 'ERROR: goat dashboard slogan returned'; exit 1
fi
if grep -q 'Plan the work. Keep the farm moving' feature/ops/src/main/kotlin/com/farmos/feature/ops/TasksBoardScreen.kt; then
  echo 'ERROR: task board slogan returned'; exit 1
fi

echo 'Farm OS portable visual authority guardrails: PASS'
