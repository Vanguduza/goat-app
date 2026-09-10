> **SUPERSESSION NOTICE — 05 September 2026**
> Visual authority is now `docs/ux/FARM_OS_VISUAL_AUTHORITY.md` plus the REV 2 screen/feature/visual mapping. This playbook remains binding for evidence, accessibility, repo-mining, screenshot and drift-control practices only where compatible. The old statement that `FARM_OS_DESIGN_SYSTEM_SPEC.md` alone is visual authority is superseded.

# Farm OS — Frontend & UX/UI Design Playbook: Guardrails, Skills & Tools

**Version:** 1.0 · **Date:** 22 August 2026
**Purpose:** One operational document for achieving clean, modern, professional frontend outcomes — especially when consulting or copying external repositories. Consolidates the anti-generic law (`FARM_OS_DESIGN_SYSTEM_SPEC.md` §16), defines loadable **agent skills**, and catalogs the **toolchain** that enforces quality mechanically.
**Reads with:** `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` (gates/EDRs) · `FARM_OS_DESIGN_SYSTEM_SPEC.md` (visual law) · `.cursor/rules/farmos-design-guardrails.mdc` (session binding) · `AGENTS.md`.
**Binding on:** all humans and agents producing UI code, designs, or reviews for Farm OS.

---

## Contents

| § | Title |
|---|-------|
| 1 | Why repo copying produces generic UIs — the five root causes |
| 2 | The repo-mining protocol (copying safely) |
| 3 | Licence hygiene for reference repos |
| 4 | Guardrails — the complete list |
| 5 | Agent skills (loadable playbooks) |
| 6 | Toolchain catalog |
| 7 | Quality scorecards & thresholds |
| 8 | Gate-ready acceptance checklist |
| 9 | Traceability |

---

## 1. Why repo copying produces generic UIs — the five root causes

| # | Root cause | Mechanism | Countermeasure (§) |
|---|---|---|---|
| R1 | **Blind authoring** | Agents write composables without ever seeing rendered pixels; errors invisible until runtime | Gallery-first + screenshot evidence (4.3, 4.4, 6.4) |
| R2 | **Average-pull** | Model training data averages all repos consulted; output regresses to the mean aesthetic | Visual-authority split (4.1), single-source tokens (6.1) |
| R3 | **Token bypass** | Default MaterialTheme colors/typography used because importing them is easier than building tokens | Lint bans (4.5), generated theme package (6.1) |
| R4 | **Placeholder feel** | Lorem/Test fixtures hide density and rhythm problems; screens feel synthetic | Realistic-fixture law (4.2), fixtures module (6.6) |
| R5 | **Silent drift** | No measurement of aesthetic debt; each small deviation passes review | Debt budget trending (4.10), scorecards (§7) |

Every guardrail below maps to at least one root cause. If a proposed practice does not counter R1–R5, it does not belong in this document.

## 2. The repo-mining protocol (copying safely)

Consulting repositories is encouraged — for the right layers, with the right provenance. What is forbidden is *unscoped* consultation where visual patterns ride along with engineering ones.

### 2.1 Layer extraction table

| Layer | May copy/mine? | Form allowed | Examples |
|---|---|---|---|
| Gradle module graph, convention plugins | Yes | Code, cited | NIA `core/` aggregation, build-logic |
| Testing harness (screenshot, contract) | Yes | Code, cited | Roborazzi setup, NIA `testing:` modules |
| Sync/offline architecture | Yes | Code or pattern, cited | Outbox, pull-cursor implementations |
| DI/composition patterns | Yes | Pattern, cited | Hilt module structure |
| Theme *code structure* | Yes | Structure only | Where tokens live, how schemes switch |
| Theme *values* (colors, type, shapes) | **No** | Never | Any palette/type scale from any repo |
| Screen layouts, navigation graphs | **No** | Never | Any screen composition |
| Motion specs | **No** | Never | Springs, transitions, expressive motion |
| Microcopy | **No** | Never | Any product string |
| Domain vocabulary | **No** | Never | Species/livestock terminology from other apps |

### 2.2 Provenance rule

Every PR that mines a repo must include a `References:` footer citing `repo@commit/path` per extracted item, stating the layer mined (per 2.1). Reviewers reject unclosed borrowings. A cumulative **provenance ledger** lives at `docs/design/provenance-ledger.md` — append-only, one row per extraction.

### 2.3 Adaptation requirement

Copied engineering code must be renamed to Farm OS conventions and pass our lints before merge — verbatim vendored blocks are prohibited except behind adapters. If more than ~40% of a copied file survives unchanged, justify it in the PR body or split the extraction.

## 3. Licence hygiene for reference repos

| Licence | Code reuse | Ideas/patterns | Farm OS stance |
|---|---|---|---|
| Apache-2.0 (nowinandroid, compose-samples) | Allowed with attribution + NOTICE | Allowed | Preferred mining source |
| MIT | Allowed with attribution | Allowed | Fine |
| GPL/LGPL (e.g. Field-Book GPLv2) | **Contaminating — never copy code into proprietary APK** | Allowed (ideas only) | Mine UX wisdom visually; zero code transfer |
| "Other/NOASSERTION" | Treat as All Rights Reserved | Ask owner first | Do not mine until cleared |

Rule: licence check happens **before** reading code deeply, recorded in the provenance ledger row. GPL contamination discovered later forces a rewrite of every touched file — prevention is cheap, cure is not.

## 4. Guardrails — the complete list

Consolidated from design spec §16 plus extensions. CI-enforced items marked ⚙.

1. **Visual-authority split** — `docs/ux/FARM_OS_VISUAL_AUTHORITY.md`, its manifest and the REV 2 Screen Atlas are the visual authority; repos remain engineering references. An agent needing a layout decision cites its `FOS-*` Screen ID and canonical visual lineage, never an external repo screen.
2. **Realistic-fixture law** ⚙ — named fixtures only (Nala goat, Cage B wave, August ledger); placeholder strings banned from committed code/tests.
3. **Gallery-first design-before-code** — new screens appear in the Gallery debug entry with fixtures and receive owner pixel approval before integration PRs exist.
4. **Screenshot-evidence gate** ⚙ — Roborazzi/Paparazzi goldens (light/dark/outdoor × font-scale 1.0/1.3/2.0) updated in the same PR; diffs block merge; tests green ≠ UI done.
5. **Static design lints** ⚙ — raw Color/dp/sp off-scale, non-Inter fonts, ad-hoc TextStyle, M2 APIs, default M3 theme accessors outside `:core-design`, gradients, emoji: all fail the build.
6. **Density & device matrix** ⚙ — goldens across 360/411/780dp widths and 100–200% font scale; Test Lab smoke on top OEM skins each release train.
7. **One-screen-one-pattern audit** — every shipped screen maps to exactly one §6 pattern instance; orphan layouts are release blockers and EDR candidates.
8. **Microcopy gate** ⚙ — voice checklist over string resources: verb-first buttons, sentence case, species-exact vocabulary, figures not words, no exclamation marks, no marketing tone.
9. **Weekly sunlight walk** — owner walks core flows outdoors on real hardware (outdoor mode ON); findings are P1 visual bugs; release trains require a clean walk.
10. **Aesthetic-debt budget** ⚙ — lint violations trended per module; rising trend freezes new feature UI in that module until zero.
11. **UI-dependency admission** — any new UI library (charts beyond Vico, Lottie, image tooling) requires an EDR stating licence, maintenance signal, and why existing tools are insufficient (mirrors handbook Ch.16.8).
12. **Interaction-state completeness** — every interactive surface ships idle/loading/empty/error/disabled states via the shared wrapper; bare lists fail review.
13. **Motion restraint** — durations/easing per spec §7; springs/bounce/overshoot banned; reduce-motion respected.
14. **Fresh-context critique** — a reviewer agent with no authorship stake scores changed screens against the §7 rubric before merge; independent verification per handbook Ch.12.

## 5. Agent skills (loadable playbooks)

Skills are self-contained instruction sets an agent loads before a task type. Stored under `.cursor/skills/` as `SKILL.md` files; the rule file (`.cursor/rules/farmos-design-guardrails.mdc`) tells agents when to load which.

### 5.1 `screen-implementation` skill

Load before implementing any screen. Steps:

1. Read design spec §6; identify the pattern code for this screen. If none fits, STOP → propose EDR, do not improvise.
2. Read the pattern's template in `:core-design/patterns/` and its gallery entry.
3. Compose only from tokens/components; declare all five interaction states.
4. Write Compose previews with named fixtures first; run Roborazzi capture locally.
5. Self-audit against §8 checklist below; attach goldens to PR; add `References:` footer if any repo was mined.

### 5.2 `repo-mining` skill

Load before consulting any external repository. Steps:

1. Classify intended layer against table 2.1. Visual layers: stop — not minable.
2. Licence check per §3; record in provenance ledger draft row.
3. Extract minimal reference; note adaptation plan (renames, lint compliance).
4. Implement adaptation; confirm <40% verbatim survival or justify in PR.
5. Append ledger row + `References:` footer in PR.

### 5.3 `ui-review` skill

Load for reviewing UI changes (also drives the fresh-context critic agent). Steps:

1. Render changed screens from goldens; never review from diff alone.
2. Score against rubric §7; any dimension <4 blocks merge with specifics.
3. Walk the anti-goals table (spec §0) — flag any AI-tell observed.
4. Verify states matrix (idle/loading/empty/error/disabled), density matrix, copy voice.
5. Output verdict: APPROVE / BLOCK(with items) / EDR-needed.

### 5.4 `gallery-curation` skill

Load when adding/updating Gallery entries. Steps:

1. One entry per component/pattern state combination; name = `Pattern_Variant_State`.
2. Fixtures module data only; no ad-hoc literals.
3. Regenerate all theme/scale variants; verify no golden diffs beyond intended change.
4. Update PATTERNS.md mapping table when introducing patterns/variants.

### 5.5 `copy-voice` skill

Load before writing or editing user-facing strings. Steps:

1. Apply voice rules (verb-first, sentence case, species vocabulary map, figures, no exclamations).
2. Check forbidden list (`!`, "welcome to", "amazing", "simply/easily/just", emoji).
3. Errors must state cause + fix + data-safety where relevant.
4. Run string-resource lint locally before PR.

## 6. Toolchain catalog

| # | Tool | Role | Enforcement point | Cost |
|---|---|---|---|---|
| 6.1 | **Material Theme Builder + token codegen** | Generates light/dark tonal palettes from seed green; outputs Kotlin/XML token sources into `:core-design/theme/generated/` | Generated artifacts committed; drift fails CI (schema-authority applied to design) | Free web tool |
| 6.2 | **Konsist / custom Lint rules** | Token bans, pattern composition checks, TextStyle/M2/default-accessor bans | CI blocking | OSS |
| 6.3 | **Roborazzi** (JVM screenshots) | Golden images per component × theme × scale × width | PR-blocking diffs | OSS |
| 6.4 | **Gallery debug app** (`GalleryActivity` + PATTERNS.md) | In-app living style guide; owner pixel-review surface; gate evidence source | Manual gate item | Build cost only |
| 6.5 | **Paparazzi** | Alternative/native-render goldens where Roborazzi insufficient (edge cases) | Same as 6.3 | OSS |
| 6.6 | **`:core-fixtures` module** | Named demo datasets importable in one line (Nala, Cage B wave, August ledger, House-2 mortality series) | Realistic-fixture law enabler | Build cost only |
| 6.7 | **JankStats + Macrobenchmark** | Frame-time budgets on capture forms/dashboards; startup budget | Release gate thresholds (jank >5% frames fails) | OSS |
| 6.8 | **Baseline Profiles** | Cold-start <1s target; perceived polish multiplier | Release pipeline step | OSS |
| 6.9 | **Firebase Test Lab device matrix** | OEM-skin smoke (Samsung OneUI, MIUI, Pixel) on release trains | Release gate | Free tier |
| 6.10 | **Accessibility Scanner + TalkBack manual pass** | Contrast/targets/contentDescription audit | Gate checklist | Free |
| 6.11 | **Pseudolocale + RTL preview runs** | Exposes fixed-width containers and truncation early | Preview hygiene; CI pseudolocale build variant optional | Built-in |
| 6.12 | **Compose compiler metrics** | Skippability/stability reports catch recomposition storms that read as jank | Advisory; trends reviewed at gates | Built-in |

Adoption phasing: 6.1–6.4 are Phase-0 (before first feature UI); 6.5–6.8 by end of Phase 1; 6.9–6.12 by first release train.

## 7. Quality scorecards & thresholds

Two rubrics; used by reviewers and the fresh-context critic agent. Each dimension scored 1–5; **any dimension <4 blocks merge**.

### 7.1 Screen scorecard

| Dimension | 5 looks like | 1 looks like |
|---|---|---|
| Pattern fidelity | Recognisably one §6 pattern; zero orphan elements | Ad-hoc layout, mixed patterns |
| Token discipline | No raw values anywhere | Hex literals inline |
| Hierarchy & rhythm | Clear scan path; consistent spacing grid | Uneven gaps, competing emphasis |
| Data presentation | Tabular numerals, right-aligned figures, quiet chrome | Decorative charts, pill-spam status |
| Copy voice | Verb-first, terse, species-exact | Marketing tone, generic labels |
| States completeness | All five states designed and shown | Only happy path |

### 7.2 Repo-mining scorecard (per extraction)

| Dimension | 5 | 1 |
|---|---|---|
| Layer legality | Engineering-only layers | Any visual layer borrowed |
| Provenance | Ledger row + commit-pinned refs | Uncited borrowing |
| Adaptation depth | Renamed/restructured to Farm OS idioms | Verbatim block dropped in |
| Licence cleanliness | Cleared pre-read | Discovered post-hoc |

### 7.3 Aggregate health metrics (reviewed at phase gates)

- Aesthetic-debt count per module (target 0, trend ↓)
- % screens with current goldens (target 100%)
- Gallery coverage of shipped components (target 100%)
- Mining provenance rows vs uncited borrowings found (target 0)
- Jank budget on core flows (<5% frozen frames), cold start (<1s with baseline profile)

## 8. Gate-ready acceptance checklist

A UI slice passes when every box is checkable:

- [ ] Pattern code declared and matches implementation
- [ ] Tokens only; lints green; no debt added
- [ ] Five states implemented via shared wrapper
- [ ] Named fixtures used everywhere visible
- [ ] Goldens updated: light/dark/outdoor × 1.0/1.3/2.0 × 360/411/780
- [ ] Gallery entries added/updated; PATTERNS.md mapping current
- [ ] Copy voice verified; string lint green
- [ ] TalkBack walk clean; targets ≥48dp (64 outdoor)
- [ ] Jank/startup budgets met on touched flows
- [ ] Fresh-context critique ≥4 on all dimensions
- [ ] Any repo mining: layer-legal, licence-cleared, adapted, ledger row appended
- [ ] Owner pixel sign-off recorded in gate report

## 9. Traceability

| Section | Source |
|---|---|
| §1 root causes | Design spec §16 rationale |
| §2 protocol | Handbook Ch.16.8 dependency admission + DDE authority ranks |
| §4 guardrails | Design spec §16.1–16.10 (consolidated + extensions 11–14) |
| §5 skills | New — operationalizes guardrails as loadable playbooks |
| §6 tools | Design spec §14 + this doc's additions |
| §7 scorecards | Handbook Ch.12 independent verification principle |
| Binding rule file | `.cursor/rules/farmos-design-guardrails.mdc` |

*End of frontend & UX playbook.*

