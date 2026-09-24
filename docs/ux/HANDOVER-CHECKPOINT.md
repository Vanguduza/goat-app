# Animal Farm visual-authority consolidation checkpoint — 24 September 2026

Entry point: [ANIMAL_FARM_COLD_START_RESUME.md](ANIMAL_FARM_COLD_START_RESUME.md).  
Sole visual authority: [animal-farm-visual-lock/START-HERE.md](animal-farm-visual-lock/START-HERE.md).

## Current state

The repository no longer preserves superseded visual systems in the working tree. The owner-locked A management / D+C worker Animal Farm package is the only presentation authority. Historical pastoral palettes, Caveat/script styling, earlier design-system/playbook authorities, duplicated visual manifests and superseded native-theme reference source were removed. Their provenance remains available in Git history.

The retained package contains the exact locked home HTML, owner decision and screen-standard provenance, approved animal imagery, Inter font/license, locked web tokens/geometry, page patterns, current migration/evidence rules, screen registry alignment data and verification scripts.

## What remains binding outside presentation

Product scope, role permissions, domain safety, offline architecture, Supabase/RLS/RPC boundaries, Meilisearch/search rules, Feature IDs, Screen IDs and green-state evidence requirements remain governed by Project Truth and the relevant contracts. Removing a visual authority never removes a feature.

## Verification

From repository root:

```text
node scripts/design/verify-handover.cjs
node docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test --registry docs/ux/FARM_OS_SCREEN_REGISTRY.yaml
bash scripts/ci/verify-visual-authority.sh
```

These checks verify authority integrity and anti-drift constraints only. They do not claim `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN` or `MVP_GREEN`. Native device/state/accessibility evidence and independent approval remain required.
