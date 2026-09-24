# Validation and installation notes

## Checked for this handoff

- 545 exact registry IDs/names/modules are represented, including all eight role variants.
- The canonical reference manifest validates only the locked HTML, owner decision/provenance documents, approved images, Inter font/license, and locked theme/geometry artifacts.
- The supplied locked HTML remains byte-identical, with sandbox and Content-Security-Policy intact.
- Eight in-memory negative tests reject missing IDs, duplicates, renamed identities, invalid recipes, wrong worker pattern, stale evidence, path escape and altered reference bytes.
- All three supplied JavaScript scripts pass `node --check`.
- The page recipes were inventory-reviewed and 35 initial name-based assignments corrected. They still require feature-level contract review: a broad registry name does not establish exact fields, permissions or route behavior.

No Android build, screenshot suite, live route export, physical-device test, media persistence or full feature certification is claimed by the authority package itself. Static source inspection is not a pixel audit. Repository implementation and CI evidence remain separate gates.

## Use

From this folder:

```text
node scripts/verify-pack.cjs
node scripts/verify-pack.cjs --self-test
```

To compare a refreshed source registry, supply its actual path:

```text
node scripts/verify-pack.cjs --registry PATH_TO_CURRENT_SCREEN_REGISTRY_YAML
```

The source reader supports the supplied simple YAML structure and explicitly fails rather than skipping unsupported entries. If the live format changes, integrate a proper YAML parser and preserve exact identity/coverage tests. Do not run the old registry generator before fixing its variant-loss risk.

After completing a screen contract and gathering real evidence under `evidence/SCREEN-ID.json`, use:

```text
node scripts/verify-pack.cjs --release FOS-HOME-012-D --expected-commit FULL_TESTED_COMMIT_SHA
```

Multiple selected IDs are comma-separated. Omitting the ID list after `--release` requests all entries. This must fail for the current pack because no native screen is certified. Evidence records must identify actual local artifact paths/hashes, CI and independent approvals. This script checks structure/integrity, not the truth of test logs or identity of reviewers. Protected remote CI and review are mandatory for trustworthy acceptance.

An optional `--routes PATH_TO_EXPORT_JSON` compares the exact `screen_ids` set with this registry. The implementation agent must build that export from actual navigation/composable ownership, including aliases and invoked atomic surfaces; hand-authoring an identical list does not prove reachability. Add executable entry/return tests as specified in MIGRATION-AND-GATES.

`assemble-pack.cjs` is a one-time provenance/packaging utility tied to the original workspace layout. It deliberately refuses to overwrite an assembled pack. Do not use it as a live registry generator. `refine-registry.cjs` records the initial reviewed recipe corrections; it is not a source of future product scope. Ongoing page decisions belong in reviewed contracts and the canonical live registry.

## Installation boundary

This pack is already installed in the active repository. Preserve the locked HTML/assets/theme manifest and keep root AGENTS.md plus the always-on design rule aligned with it. Configure required CI and independent approval ownership explicitly. Templates are examples until deliberately installed or instantiated.

Superseded native-theme source snapshots are intentionally absent from the working tree. Git history is sufficient for forensic provenance; production design work must use the current governed design module and this package only.
