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

