# Farm OS — Vet Intelligence & Expert Research → Usable Features

**Version:** 1.0 · **Date:** 22 August 2026
**Purpose:** Turn the veterinary expert research pack (`FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md`), the shared Health module (`FARM_OS_HEALTH_MODULE_SPEC.md`) and the embedded AI module (`FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`) into **implemented, usable features**: concrete backend tables, domain engines, and screen-by-screen UX flows.
**Governs:** WHAT ships for vet-level intelligence. HOW it ships (gates, EDRs, tenancy) is governed by `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.
**Hosting law unchanged:** app + Supabase only. AI advisory-only. Species-native UX.

---

## Contents

| § | Title |
|---|-------|
| 1 | The knowledge pipeline: research pack → shipped feature |
| 2 | Backend: knowledge base schema (seeded content + farm acceptance) |
| 3 | Backend: clinical recording & intelligence projections |
| 4 | Domain engines (Kotlin) |
| 5 | UX flows: daily intelligence surfaces |
| 6 | UX flows: clinical capture forms |
| 7 | UX flows: protocol packs & vet acceptance |
| 8 | UX flows: Copilot & anomaly triage |
| 9 | Roles, safety gates & withdrawal enforcement |
| 10 | Offline behaviour |
| 11 | Seeding plan (what ships in the APK/Supabase on day 1) |
| 12 | Acceptance tests & golden fixtures |
| 13 | Traceability |

---

## 1. The knowledge pipeline: research pack → shipped feature

The research data becomes usable only through this pipeline. Every expert fact must land in one of four sinks — never in prose alone:

```
Vet research pack / health spec / ICAR sources
   |
   v
[Knowledge seed]  structured rows, versioned, shipped as SQL seeds + APK assets
   |-- disease_catalog        (signs, first aid, vet_class, red_flag, zoonotic)
   |-- formulary class rules  (class list; farm adds labelled products)
   |-- health_tips            (60+ tips, season/module tagged)
   |-- score definitions      (FAMACHA 1-5, BCS 1-5 / 1-9, locomotion, flystrike)
   |-- kpi_definitions        (ICAR formulas: kid/lamb survival, hen-housed eggs,
   |                           calving interval, SCC flag, FCR, hatchability)
   |-- protocol_pack_templates (CDT, ND, RHDV..., slots + offsets per species/kind)
   |-- copilot_skills         (module briefing Markdown, kind-aware)
   v
[Farm acceptance layer]  the farm's vet accepts/adapts -> health_protocol_packs (vet_accepted)
   v
[Task engine]  accepted packs generate WorkManager tasks at locked offsets
   v
[Intelligence layer]  recorded data -> charts -> Lane A anomalies -> action drafts -> Copilot explainers
   v
[Screens]  Health Today board, dashboard tip card, animal timeline, outbreak path, withdrawal board
```

Rules:

1. A research fact with no sink is not a feature. It either maps to a seed table, a score definition, a KPI, or a Copilot skill — or it stays out of the product.
2. Seeds are **content**, not code changes: shipped via versioned seed migrations + bundled JSON so updates don't require an APK rebuild for content-only fixes where Supabase serves the table.
3. Farm-level clinical authority always sits in the **accepted pack**, never in the raw catalog. Catalog = education; pack = operational truth.

## 2. Backend: knowledge base schema

Extends the health spec §2 tables. New/changed DDL (additive, `farm_id` + RLS on all farm-scoped rows):

```sql
-- 2.1 Score definitions (global reference content, read-only to apps)
CREATE TABLE score_definitions (
    code TEXT PRIMARY KEY,              -- famacha_goat, bcs_ruminant_15, bcs_beef_19, locomotion_cattle, flystrike_awi
    species_codes TEXT[] NOT NULL,
    scale_min INT NOT NULL,
    scale_max INT NOT NULL,
    labels JSONB NOT NULL,              -- { "1": "...", "2": "..." } display text per step
    interpretation JSONB NOT NULL,      -- { "4": {"risk":"anaemia likely","action_code":"selective_drench_check"} , ...}
    source TEXT NOT NULL                -- citation from vet pack §8
);

-- 2.2 KPI definitions (ICAR etc.), computed on-device from ledger/projections
CREATE TABLE kpi_definitions (
    code TEXT PRIMARY KEY,              -- kid_survival_90d, hen_housed_eggs, calving_interval_d
    species_codes TEXT[] NOT NULL,
    formula TEXT NOT NULL,              -- human-readable + machine evaluable DSL ref
    unit TEXT NOT NULL,
    direction TEXT NOT NULL,            -- higher_better | lower_better
    target_source TEXT NOT NULL,        -- farm_config | purpose_pack | none
    source TEXT NOT NULL
);

-- 2.3 Action catalog: the closed vocabulary of corrective actions (AI spec §4.2)
CREATE TABLE action_catalog (
    code TEXT PRIMARY KEY,              -- open_health_pack, call_vet, reweigh_7d, fec_check, biosecurity_walk ...
    species_codes TEXT[] NOT NULL,
    requires_role TEXT,                 -- null=any, 'vet', 'breeding_mgr'
    creates_task_template JSONB,        -- optional task draft shape
    side_effect_class TEXT NOT NULL DEFAULT 'local_record'
);

-- 2.4 Copilot skills registry (skills ship as Markdown assets; registry enables per module)
CREATE TABLE copilot_skills (
    code TEXT PRIMARY KEY,              -- goat-kidding-briefing, poultry-house-briefing
    module_ids TEXT[] NOT NULL,
    asset_path TEXT NOT NULL,
    min_role TEXT NOT NULL DEFAULT 'worker',
    version INT NOT NULL DEFAULT 1
);

-- 2.5 Withdrawal clocks live with treatments (health spec); add sale-blocking projection:
CREATE TABLE withdrawal_board (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    product_id UUID NOT NULL REFERENCES formulary_items(id),
    meat_until DATE,
    milk_until DATE,
    egg_until DATE,
    source_treatment_id UUID NOT NULL,
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);
CREATE INDEX idx_withdrawal_board_farm_active ON withdrawal_board (farm_id, meat_until, milk_until, egg_until);

-- 2.6 Seed bookkeeping
CREATE TABLE knowledge_seed_versions (
    seed_set TEXT PRIMARY KEY,          -- disease_catalog, health_tips, ...
    version INT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

RLS notes: `score_definitions`, `kpi_definitions`, `action_catalog`, `copilot_skills` are **global registries** (no farm_id) — readable by all authenticated users, writable only by service role during seeding. `withdrawal_board` and every farm-scoped table carries `farm_id = auth.jwt() ->> 'farm_id'`.

---

## 3. Backend: clinical recording & intelligence projections

Already specified elsewhere; restated here **only as implementation wiring** (these are dependencies, not redesigns):

- Health events, treatments, vaccination records: `FARM_OS_HEALTH_MODULE_SPEC.md` §0–§2 (event core, treatments referencing `formulary_items.id`).
- Measurements (weight, milk, eggs, scores): master plan measurement tables; each score row references `score_definitions.code`.
- Anomaly/recommendation projections: AI spec §8 (`analytics_anomalies`, `analytics_recommendations`, `ai_audit`).
- Breeding programme tables: breeding programme spec §schema (nest boxes, matings, COI).

New projection required by this spec:

```sql
-- Cohort baselines for Lane A (rebuildable from measurements; refreshed by worker)
CREATE TABLE analytics_cohort_baselines (
    farm_id UUID NOT NULL REFERENCES farms(id),
    feature_code TEXT NOT NULL,          -- growth_adg, egg_rate, mortality_daily ...
    species_code TEXT NOT NULL,
    poultry_kind_code TEXT,
    purpose_code TEXT,                   -- meat, layer, breeder, dairy...
    window_days INT NOT NULL DEFAULT 30,
    median NUMERIC(12,4) NOT NULL,
    mad NUMERIC(12,4) NOT NULL,
    n_points INT NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (farm_id, feature_code, species_code, poultry_kind_code, purpose_code, window_days)
);
```

Lane A detection reads baselines; recomputation is a WorkManager job after sync (idempotent upsert). Baselines are derived state — rebuildable from the ledger (handbook Ch.7).

## 4. Domain engines (Kotlin, in `:domain-*`)

| Engine | Module | Responsibility | Key inputs → outputs |
|---|---|---|---|
| `KnowledgeSeedLoader` | `:core-db` | Load bundled seed JSON → Room mirror + Supabase upsert via service role (Edge seed function); record `knowledge_seed_versions` | seed JSON → rows |
| `ScoreInterpreter` | `:domain-health` (shared) | Map a recorded score → interpretation + suggested `action_code` from `score_definitions.interpretation` | (score_code, value) → interpretation |
| `WithdrawalClock` | `:domain-health` | On treatment insert: compute `*_until` dates from `formulary_items` label days; write `withdrawal_board`; block sale/slaughter/milk/egg flows while active; FEFO decrement inventory | treatment → board rows + blocks |
| `ProtocolPackEngine` | `:domain-health` | Expand accepted `health_protocol_packs` slots (offset_rule) → vaccination/deworming-check tasks; handle from-birth vs from-birth-event anchors | pack + birth event → task drafts |
| `CohortBaselineEngine` | `:domain-ai` | Compute per-feature cohort median/MAD from measurements → `analytics_cohort_baselines` | measurements → baseline rows |
| `AnomalyEngine` (Lane A) | `:domain-ai` | AI spec §4 rules: z/MAD flags, polarity, severity; emit `analytics_anomalies` + action drafts from `action_catalog` only | series + baseline → anomalies |
| `TipSelector` | `:domain-ai` | Health spec §7 algorithm (module ∩, weather season preference, 14-day impression exclusion, stable hash pick) | tips + weather + impressions → tip |
| `CopilotEngine` | `:domain-ai` | AI spec §6 loop; skills from `copilot_skills`; tools from `FarmToolRegistry`; every run → `ai_audit` | user msg + skill → answer + citations |
| Species KPI engines | `:domain-<species>` | Compute `kpi_definitions` per module (kid survival, hen-housed eggs, calving interval, FCR, hatchability…) | projections → KPI rows |

All engines are pure Kotlin, unit-testable, no Android imports (handbook Ch.8). Engines never write to the ledger directly except via their owning domain module's event emitter.

## 5. UX flows: daily intelligence surfaces

### 5.1 Module dashboard — "Health tip of the day" card

```
[Dashboard top card]
  "Score FAMACHA in shade; heat changes eyelid colour."        (TipSelector output)
  Footer: Not a diagnosis. Follow your vet pack.   [Health >]
```

- Tap → Health home. Card suppressed if no tips remain (never repeats within 14 days).
- Weather-aware: Open-Meteo fetch (existing weather widget) feeds `season` preference.

### 5.2 Health Today board

Sections, in order:

1. **Withdrawal board strip** (if any active): animal/group, product, clock chip `milk: 3d left`, red if sale attempted while active.
2. **Overdue pack tasks**: vaccination/deworming-check tasks from ProtocolPackEngine, oldest first, each opens the capture form pre-filled.
3. **Red-flag anomalies** (`severity=critical`): FAMACHA 4–5, recumbency flags, mortality spike — each row: title, one-line evidence, two buttons `[Record check]` `[Call vet]`.
4. **Watch list**: `watch` polarity anomalies, swipe to dismiss (writes `status=dismissed`, audited).
5. **Playbook cards** (health spec §6): colostrum, needle hygiene, biosecurity… tap opens static playbook page with source citation.

Empty state: "No health actions today. Next pack task: CDT booster — 12 May."

### 5.3 Animal timeline (per-animal intelligence)

```
[Goat "Nala"]  FAMACHA 3 ▂▃▅  BCS 3  ADG 120 g/d (cohort band ±σ)
  Events: 02 Feb CDT dose ✓ · 28 Jan FAMACHA 3 ✓ · 20 Jan drench (FEC 750) ✓
  [Chart|Table|Both]  [Ask Copilot about Nala]
```

- Every chart implements `ChartableFeature` (AI spec §3); Vico; cohort band from baselines.
- Anomaly chips on the series (▲ negative, ● watch, ★ positive) tap → anomaly detail sheet: evidence JSON rendered as sentences, action drafts from `action_catalog` with `[Accept]` (creates task) / `[Dismiss]`.
- "Ask Copilot" opens the skill-scoped chat (`goat-kidding-briefing` etc.), which may only cite rows the session's tools returned.

### 5.4 Outbreak path (poultry mortality spike / notifiable hint)

```
Mortality spike detected (House 2, 3.1× 7-day baseline)
  [Quarantine house]  [Record mortality]  [Biosecurity walk]  [Notifiable info]
```

- `Quarantine house` sets group status, stops movements in-app, creates daily recheck task ×7.
- `Notifiable info` opens jurisdiction SOP card (phone number, farm-configured) — never auto-reports.

## 6. UX flows: clinical capture forms

Species-native forms (vet pack §0.1 event core + module sections). Shared skeleton, per-species fields:

| Step | Goat/sheep form | Rabbit form | Cattle form | Poultry (flock) form |
|---|---|---|---|---|
| Subject | animal picker (RFID/search) | doe/litter picker | animal picker | house/flock picker |
| Type chips | clinical, treatment, score, vax | clinical, treatment, score, vax | clinical, treatment, score, PD | mortality, vax, water/NH3/temp |
| Score pad | FAMACHA 1–5 photo guide; BCS 1–5 | body condition; sore hocks | BCS 1–9; locomotion | — (flock-level) |
| Product | formulary picker (FEFO first, cold-chain flag) | same | same | water-soluble class |
| Withdrawal preview | live chips: meat/milk dates | meat | meat/milk | egg |
| Red-flag branch | FAMACHA 4–5 → vet-now sheet | GI stasis → vet same-day | down cow → vet SOP | mass death → outbreak path |
| Save | one `domain_event` + outbox; works offline | | | |

Form rules: product free-typing is disabled (formulary only); every save emits the event-core JSON; score saves reference `score_definitions.code`; the form never blocks on connectivity.

## 7. UX flows: protocol packs & vet acceptance

```
Health > Protocol packs
  [Goat — dairy-temperate-v1]        status: draft
     Slots: CDT prepartum (−30 d) · CDT kid priming (+30/±4wk alt) · [optional: orf]
  [Accept pack]  (Owner + vet name + date required)
```

- Accepting writes `health_protocol_packs.status='vet_accepted'`, stamps `accepted_by_vet`/`accepted_at`; only then does ProtocolPackEngine generate tasks.
- Editing a slot after acceptance creates a **new pack version** (draft); the accepted one is retained for audit.
- Pack detail screen shows each slot's source citation (health spec §3 sources).

## 8. UX flows: Copilot & anomaly triage

Entry points: animal timeline "Ask", dashboard "Ask Farm Copilot", anomaly sheet "Explain".

```
User: "why is Nala flagged?"
CopilotEngine: skill=goat-kidding-briefing context; tools: get_animal, get_weight_series, get_timeline
Model API → tool calls → rows → final answer:
  "Nala's ADG is 118 g/d vs cohort median 152 (−1.9σ, watch). FAMACHA last recorded 3 on 28 Jan.
   Suggested next steps (drafts): reweigh in 7 days · FAMACHA check. This is not a diagnosis."
  [Reweigh task] [FAMACHA check] [Dismiss]      ← actions from action_catalog only
```

Triage rules: every Copilot-suggested action maps to `action_catalog.code`; Accept creates a draft task requiring confirm; answers cite row IDs; runs are audited (`ai_audit`). No dose, no drug names, no diagnosis claims.

## 9. Roles, safety gates & withdrawal enforcement

| Capability | Worker | Breeding mgr | Farm mgr | Owner | Vet (external) |
|---|---|---|---|---|---|
| Record health event/score/task complete | ✓ | ✓ | ✓ | ✓ | read+advise |
| Attach formulary product to treatment | — (see stocked name, withdrawal) | — | ✓ (pack-gated classes) | ✓ | advise |
| Antibiotic / NSAID / hormone class attach | ✗ | ✗ | vet-pack-gated | vet-pack-gated | ✓ |
| Accept/adapt protocol pack | ✗ | ✗ | propose | ✓ (vet named) | ✓ |
| Copilot settings / keys | use only | use only | endpoint/model | all | ✗ |
| Dismiss critical anomaly | ✗ | ✗ | ✓ | ✓ | — |

Hard gates (enforced at mutation call sites, handbook Ch.10):

1. Treatment save requires `formulary_items.id`; UI offers no free text.
2. Sale/slaughter/milk-pickup/egg-sale flows query `withdrawal_board`; active clock = hard block with reason screen.
3. AI outputs are drafts; nothing clinical mutates without human Accept.
4. Notifiable diseases open SOP contact info; never auto-report.

## 10. Offline behaviour

- Seeds bundled in APK assets → Room mirror at first run; Supabase refresh when online (version check).
- Tip selection, Lane A detection, KPI computation: fully on-device from Room mirror.
- Copilot: offline ⇒ NL disabled; charts/anomaly drafts still work (AI spec §5 empty-URL mode).
- Forms/outbox per master plan sync; withdrawal board mirrored to Room so sale-blocking works in a dead-zone barn.

## 11. Seeding plan (day-1 content)

| Seed set | Rows | Source |
|---|---|---|
| `disease_catalog` | all codes from health spec §4.1–4.4 (~40 entries) | health spec tables |
| `score_definitions` | famacha_goat, bcs_ruminant_15, bcs_beef_19, locomotion_cattle, flystrike_awi | vet pack §0.2/§8 citations |
| `kpi_definitions` | ICAR set: kid/lamb survival, scan%, hen-housed eggs, calving interval, SCC flag, FCR, hatchability | vet pack §8 ICAR links |
| `action_catalog` | ~25 codes from AI spec §4.2 + outbreak path actions | AI spec §4.2 |
| `health_tips` | 60+, tagged module/season incl. weather-reactive | health spec §7 |
| protocol templates | CDT (goat/sheep), dairy/beef cattle, ND/IB/IBD/Marek by kind, RHDV/myxo where endemic | health spec §3 |
| `copilot_skills` | 5 module briefings | vet pack §6 |

Seed updates: version bump + Edge seed function; app pulls new content without APK release.

## 12. Acceptance tests & golden fixtures

1. **CDT pack expansion fixture**: doe kidded 01 Mar ⇒ tasks: prepartum CDT (if accepted late, retro-dated), kid primer at 30 d ±template choice, booster +4 wk — exactly one template fires (health spec §3.1 rule).
2. **Withdrawal block fixture**: milk withdrawal 4 d ⇒ sale of milk blocked day −1, allowed day 0; FEFO decremented on treatment save.
3. **Lane A growth fixture**: synthetic cohort (n≥8) with one animal at −2.2σ ⇒ exactly one negative anomaly + reweigh/vet-check drafts, no drug actions.
4. **Tip stability fixture**: same farm+date across devices picks identical tip; impression exclusion within 14 d.
5. **RLS fixtures**: wrong-farm token sees zero rows in every new table; missing claim fails closed.
6. **Outbreak fixture**: mortality spike ⇒ quarantine group status + 7 daily tasks + notifiable card opens SOP, sends nothing.

## 13. Traceability

| Feature area | Source |
|---|---|
| Knowledge pipeline sinks | vet pack §0–§8; health spec §2–§7; AI spec §3–§6 |
| Withdrawal/formulary law | health spec §5 |
| Tip algorithm | health spec §7 |
| Anomaly/action law | AI spec §4; action catalog §2.3 here |
| Copilot constraints | vet pack §6 must/must-not list; AI spec §6 |
| Pack acceptance gate | health spec §3; vet pack §7 |
| Handbook gates | FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md Ch.9–10 |

*End of vet intelligence feature implementation spec.*

