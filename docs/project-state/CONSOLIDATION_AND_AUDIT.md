# Farm OS — Consolidation & feature/page audit

**Date:** 10 September 2026
**Trunk:** single consolidated lineage; every pre-consolidation branch is an ancestor of it.
**Visual authority:** `docs/ux/animal-farm-visual-lock/` (Layer 3, owner-directed 5–6 Sep 2026) — enforced in CI.
**Modules:** unchanged. This consolidation removed no module, screen ID, feature or spec.

---

## 1. What was consolidated

| Former branch | Head | Disposition |
|---|---|---|
| `cursor/animal-farm-project-green-b11a` | `2243402` | Tip of the code lineage — strict superset of every other code branch |
| `agent/animal-farm-gate1-recovery` | `c603a90` | Ancestor of the tip — no unique commits |
| `implementation/animal-farm-visual-lock` | `afc5344` | Ancestor of the tip — no unique commits |
| `implementation/foundation-vertical-slice` | `e869816` | Ancestor of the tip — no unique commits |
| `cursor/docs-handbook-design-guardrails` | `402ee5f` | Ancestor of the tip — no unique commits |
| `main` | `12e2e8c` | Only branch with unique commits (3): the Project Truth Protocol |

The consolidation is therefore a **single clean merge** of the truth-protocol lineage into the code
lineage. It produced **zero conflicts**. Verified by `git merge-base --is-ancestor` for all six refs
against the trunk: every one is contained. No commit is lost by deleting the former branches, and
each head SHA is recorded above and in `PROJECT_CANONICAL_STATE.json` so any of them can be restored.

## 2. Layer 3 enforcement added

`scripts/ci/verify-visual-authority.sh` already existed but bound only the **superseded** pastoral
authority (`FARM_OS_VISUAL_AUTHORITY.md`) and never checked a single Layer 3 token. It now also:

| Guard | Proven to fail on |
|---|---|
| Locked-token parity | `primary` drifted from `#285640` to the Layer 2 green |
| Pastoral palette ban | a Layer 2 hex reintroduced into production Kotlin |
| Caveat font ban | `caveat` appearing in Kotlin/XML (excluded by the lock's typography rule) |
| Agent binding | `AGENTS.md` no longer naming `animal-farm-visual-lock` |
| Pack integrity | any locked artifact deleted, including `locked-web-tokens.json` |

Each guard was run against a deliberate violation and confirmed to fail before being confirmed to
pass — the repository's own failing-first rule. The parity check compares all 12 light tokens in
`AnimalFarmColors.Light` against `theme/locked-web-tokens.json` and reports the match count.

**Two CI gates would have gone dark on branch deletion** and were repointed:
`animal-farm-handover.yml` triggered on push to `implementation/animal-farm-visual-lock` (a branch
being deleted) and now triggers on `main`; `foundation-ci.yml` ran only on pull request and
`workflow_dispatch` and now also runs on push to `main`.

## 3. Layer 3 application — actual state

Correcting an earlier reading of mine: Layer 3 **is** the app-wide palette already. `FarmOsTheme()`
provides `AnimalFarmColors.forMode(mode)`, whose light values match the locked web tokens exactly,
and its shape scale (8/16/20/24/28dp) matches the locked geometry. The name is legacy; the
implementation is current.

What remained of the superseded family was **dead code**: seven pastoral constants (`Sage`, `Leaf`,
`Sky`, `Sunlight`, `BarnRed`, `Soil`, `Goat`) in `FosColors` with **zero references anywhere in the
repository**. They are removed, along with the now-unused `Color` import, and the ban guard above
prevents their return.

**Not done, and not claimable:** this environment has no Android SDK and no Gradle wrapper, so
nothing here was compiled, no screenshot golden was produced, and no screen was visually verified.
The registry records **0 screens with native visual evidence** and that number is unchanged. Per
`ANIMAL_FARM_COLD_START_RESUME.md`, VISUAL_GREEN requires native state/device/accessibility evidence
and independent approval at a tested commit. None of that is asserted.

## 4. Feature and page audit

545 registered screen IDs (537 base + 8 role variants) across 29 modules. "Referenced in Kotlin"
means a `FOS-*` ID appears in source — an upper bound on implementation, not evidence of a working
screen, and explicitly not visual evidence.

| Module | Registered | Referenced in Kotlin | % |
|---|---:|---:|---:|
| `GOAT` | 55 | 12 | 22% |
| `RABBIT` | 36 | 13 | 36% |
| `CATTLE` | 36 | 17 | 47% |
| `ATOM` | 35 | 0 | 0% |
| `SHEEP` | 32 | 17 | 53% |
| `HEALTH` | 30 | 13 | 43% |
| `POULTRY` | 27 | 12 | 44% |
| `ADMIN` | 25 | 0 | 0% |
| `GLOBAL` | 20 | 2 | 10% |
| `HOME` | 20 | 5 | 25% |
| `INV` | 19 | 9 | 47% |
| `SYNC` | 18 | 1 | 6% |
| `TASK` | 15 | 3 | 20% |
| `REPORT` | 15 | 0 | 0% |
| `AN` | 14 | 0 | 0% |
| `FIN` | 13 | 4 | 31% |
| `FEED` | 12 | 1 | 8% |
| `ASSET` | 12 | 1 | 8% |
| `SALES` | 12 | 1 | 8% |
| `AI` | 12 | 0 | 0% |
| `PASTURE` | 11 | 1 | 9% |
| `WATER` | 10 | 1 | 10% |
| `PROC` | 10 | 1 | 10% |
| `CAP` | 10 | 0 | 0% |
| `GEN` | 10 | 0 | 0% |
| `GROUP` | 9 | 1 | 11% |
| `LABOUR` | 9 | 1 | 11% |
| `SIM` | 9 | 0 | 0% |
| `SEARCH` | 9 | 0 | 0% |
| **TOTAL** | **545** | **116** | **21%** |

**Cross-check:** every `FOS-*` ID appearing in Kotlin exists in the registry — there are **no
unregistered screens** (0 orphans). The gap is entirely one of unbuilt screens, not undocumented ones.

Modules with no screen referenced in code at all: `ATOM` (35), `ADMIN` (25), `REPORT` (15), `AN` (14),
`AI` (12), `CAP` (10), `GEN` (10), `SEARCH` (9), `SIM` (9).

Registry integrity note carried forward from `verify-pack.cjs`: the source registry footer says 537
while 545 entries exist. No IDs are dropped; the generator must preserve the eight role variants
before it is re-run.

## 5. Verifier status on the trunk

| Verifier | Result |
|---|---|
| `scripts/ci/verify-visual-authority.sh` | PASS (12/12 locked tokens matched) |
| `scripts/design/verify-handover.cjs` | PASS — 39 handover files, exact bytes |
| `docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test` | PASS — 8 negative tests, 19 hashes, 545 IDs |
| Gradle build / unit tests / screenshots | **Not run** — no Android SDK or Gradle wrapper in this environment |

## 6. Also repaired

`docs/FARM_OS_VET_INTELLIGENCE_FEATURE_IMPLEMENTATION_SPEC.md` began mid-document at `## 4.` on every
branch. Its title block, Contents and §1–3 — the knowledge-base DDL (`score_definitions`,
`kpi_definitions`, `action_catalog`, `copilot_skills`, `withdrawal_board`,
`knowledge_seed_versions`, `analytics_cohort_baselines`) — survived only inside the consolidated
binder. Restored verbatim from the binder; no wording invented.

## 7. Release status

`release_blocked` stays **true**. Consolidation is a lineage fact, not a certification: FEATURE_GREEN,
MODULE_GREEN, MVP_GREEN and VISUAL_GREEN are all unmet, and 0 screens carry native visual evidence.
