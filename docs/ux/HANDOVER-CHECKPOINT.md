# Animal Farm handover installation — 6 September 2026

Entry point: [ANIMAL_FARM_COLD_START_RESUME.md](ANIMAL_FARM_COLD_START_RESUME.md).
Original package: [animal-farm-visual-lock/START-HERE.md](animal-farm-visual-lock/START-HERE.md).

## Provenance and scope

Installed on `Vanguduza/goat-app`, `implementation/animal-farm-visual-lock`, from remote parent `e86981682748605e9db1570224e8c801c35b6058`, in a fresh isolated clone. Existing local calendar and foundation checkouts had unrelated tracked/untracked changes and were not modified, staged, stashed or copied into this checkpoint. No main checkout or merge was performed.

The original local `animal-farm-visual-lock` folder dated 5 September 2026 supplies all 39 files, including exact HTML, artwork, font/license, native references, registries, instructions, templates, theme data and scripts. Every file is preserved byte-for-byte. `ANIMAL_FARM_HANDOVER_FILES.json` inventories every original file with byte length and SHA-256; the package's own manifest additionally covers 19 reference/theme files. Original absolute source paths are provenance, not dependencies. Original installation-status prose is retained as historical source content; this document records the completed repository installation.

The referenced ChatGPT conversation timed out on three retrieval attempts. Its cached excerpt ends during repository identity; the uploaded attachments themselves could not be retrieved from that conversation. The locally recovered complete package was used directly. The repository resume prompt is an adaptation of its complete `DEVELOPER-START-PROMPT.md`, its detailed instructions/gates, the available cold-start excerpt and the current owner request. A line-by-line review against the unavailable remainder of the longer prompt remains pending. No missing conversation text was invented or represented as recovered.

## Review findings addressed

- The development prompt now uses repository-relative package paths and needs no original workstation or chat access.
- Conflicting older visual authority documents retain their historical content with explicit presentation supersession notices. Root AGENTS and the existing always-on design rule now point to the Animal Farm pack; its additional rule is installed. Feature scope, role permissions, architecture/security/offline rules and completion evidence remain binding.
- The partial migration is not assumed present: the remote target starts at the recovered snapshot. Uncommitted implementation work from other checkouts is excluded.
- The supplied registry contains 545 exact IDs, names and modules: 537 base plus eight role variants. All match the live registry at the parent commit. The stale 537 footer and the generator's variant-loss risk remain explicitly open; the generator was not run and no registry ID was changed.
- All visual and feature flags in the package remain false. Per-ID recipes require contract review. Imported source implementation claims are not acceptance evidence.

## Verification

From repository root:

```text
node scripts/design/verify-handover.cjs
node docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test --registry docs/ux/FARM_OS_SCREEN_REGISTRY.yaml
node --check docs/ux/animal-farm-visual-lock/scripts/assemble-pack.cjs
node --check docs/ux/animal-farm-visual-lock/scripts/refine-registry.cjs
node --check docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs
```

Local results: 39 original files verified; 19 reference/theme hashes passed; eight negative checks passed; 545 exact live registry identities matched; supplied scripts passed syntax checks. A release-evidence request for the worker screen correctly fails because no accepted native evidence exists. A separate GitHub Actions integrity workflow runs these package checks; required status enforcement and independent review ownership still require maintainer configuration and are not claimed here.

No product code, migrations, routes, evidence registries or green certificates are changed. No Android build, live route export, native screenshot/device test, backend test or feature certification was performed for this documentation/package checkpoint. Registry coverage is inventory coverage only. The next development step is live route/feature reconciliation and shared native baseline acceptance under the installed migration gates.
