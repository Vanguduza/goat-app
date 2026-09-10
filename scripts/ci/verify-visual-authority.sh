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
grep -q '^screen_count: 545$' docs/ux/FARM_OS_SCREEN_REGISTRY.yaml || {
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
if grep -q 'Synced ·' app/src/main/java/com/farmos/app/GoatModuleHost.kt; then
  echo 'ERROR: goat sync receipt claims Synced without a proven server outcome'; exit 1
fi

# ---------------------------------------------------------------------------
# Animal Farm visual lock (current owner-directed authority, 5-6 Sep 2026).
# The checks above predate the lock and bind only the superseded pastoral
# authority. These bind the lock itself so deleting a branch, renaming a pack
# file or drifting a token cannot quietly retire it.
# ---------------------------------------------------------------------------

lock_required=(
  docs/ux/ANIMAL_FARM_COLD_START_RESUME.md
  docs/ux/animal-farm-visual-lock/START-HERE.md
  docs/ux/animal-farm-visual-lock/DESIGN-SYSTEM.md
  docs/ux/animal-farm-visual-lock/AGENT-INSTRUCTIONS.md
  docs/ux/animal-farm-visual-lock/MIGRATION-AND-GATES.md
  docs/ux/animal-farm-visual-lock/theme/locked-web-tokens.json
  docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs
  core/design/src/main/kotlin/com/farmos/core/design/AnimalFarmThemeTokens.kt
)
for path in "${lock_required[@]}"; do
  [[ -s "$path" ]] || { echo "ERROR: missing Animal Farm visual-lock artifact: $path"; exit 1; }
done

grep -q 'animal-farm-visual-lock' AGENTS.md || {
  echo 'ERROR: AGENTS.md does not bind agents to the current Animal Farm visual lock'; exit 1;
}

# The native light palette must equal the locked web tokens byte-for-byte.
# Drift here is how a "small theme tweak" silently unlocks the visual lock.
python3 - <<'PYCHECK'
import json,re,sys,pathlib
tok=json.loads(pathlib.Path('docs/ux/animal-farm-visual-lock/theme/locked-web-tokens.json').read_text())['themes']['light']
src=pathlib.Path('core/design/src/main/kotlin/com/farmos/core/design/AnimalFarmThemeTokens.kt').read_text()
light=src[src.index('val Light'):src.index('val Dark')]
pairs={'background':'bg','surface':'surface','softSurface':'soft','ink':'ink','mutedInk':'muted',
       'divider':'line','primary':'primary','onPrimary':'on-primary','lime':'lime','onLime':'on-lime',
       'warningSurface':'warn','onWarning':'on-warn'}
bad=[]
for kt,web in pairs.items():
    m=re.search(rf'\b{kt}\s*=\s*Color\(0xFF([0-9A-Fa-f]{{6}})\)', light)
    if not m:
        bad.append(f'{kt}: not found in AnimalFarmColors.Light'); continue
    want=tok[web].lstrip('#').upper()
    if m.group(1).upper()!=want:
        bad.append(f'{kt}: native #{m.group(1).upper()} != locked #{want}')
if bad:
    print('ERROR: native light palette has drifted from the locked web tokens:')
    for b in bad: print('  -',b)
    sys.exit(1)
print(f'  locked-token parity: {len(pairs)} light tokens match locked-web-tokens.json')
PYCHECK

# Pastoral palette must not return to production Kotlin. The lock replaced it;
# these hexes are the fingerprint of the superseded illustrated family.
if grep -rnE '0xFF(4E7F52|7FB069|F4E6C7|C9483D|8B6847|1E4D2B|A9762B|7ECBF5)' \
   --include=*.kt app/ core/ feature/ domain/ data/ 2>/dev/null; then
  echo 'ERROR: superseded pastoral palette returned to production Kotlin'; exit 1
fi

# Caveat is explicitly not part of the current lock (DESIGN-SYSTEM.md typography).
if grep -rniE 'caveat' --include=*.kt --include=*.xml app/ core/ feature/ 2>/dev/null; then
  echo 'ERROR: Caveat handwritten family is not part of the Animal Farm lock'; exit 1
fi

echo 'Farm OS portable visual authority guardrails: PASS'
