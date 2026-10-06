# Farm OS — Operations & Economics Layer

**Version:** 1.0 · **Date:** 22 August 2026
**Status:** Scope EXPANSION approved by owner (22 Aug 2026) — supersedes "no financial ledger / inventory hooks only" locks per EDR-0003.
**Owner decisions locked this day:** full cost accounting · standard stock management + feed planning & simple ration checks · paddock records + rotation planner · farm-owner business cockpit.
**Governs:** WHAT ships for operations and money. HOW: `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.
**Unchanged:** app + Supabase hosting law; offline-first; RLS tenancy; species-native UX; advisory-only AI.

---

## Contents

| § | Title |
|---|-------|
| 1 | Design stance: money as events |
| 2 | Backend schema — cost accounting |
| 3 | Backend schema — inventory & supply |
| 4 | Backend schema — pasture & rotation |
| 5 | Domain engines |
| 6 | UX flows — money capture |
| 7 | UX flows — inventory operations |
| 8 | UX flows — feed planning & ration checks |
| 9 | UX flows — paddock rotation planner |
| 10 | UX flows — owner business cockpit |
| 11 | Unit economics: formulas |
| 12 | Roles & safety |
| 13 | Offline behaviour |
| 14 | Phasing & gates |
| 15 | Traceability |

---

## 1. Design stance: money as events

Money never lives in a shadow spreadsheet-in-the-database. All financial facts are `domain_events` of class `commercial`:

- `money.recorded` (expense or income, with `category_code`, optional link to animals/group/litter/paddock/enterprise)
- `money.allocated` (derived projection rows only; allocation is recomputable)

Consequences:

- Append-only, replayable, RLS-scoped like every other event (handbook Ch.7).
- No double-entry GL. This is **farm unit economics**, not statutory accounting. Exports exist for accountants; we do not become one.
- Every stock movement that has value can optionally carry a cost line (`inventory_issue` → expense), which is what makes cost-per-kg computable without manual entry.

## 2. Backend schema — cost accounting

```sql
CREATE TABLE money_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('expense','income')),
    enterprise_allocatable BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (farm_id, code)
);

CREATE TABLE money_records (
    id UUID PRIMARY KEY,                      -- client-generated UUIDv7
    farm_id UUID NOT NULL REFERENCES farms(id),
    mutation_id UUID NOT NULL,                -- handbook Ch.7 idempotency
    occurred_on DATE NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('expense','income')),
    category_id UUID NOT NULL REFERENCES money_categories(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL DEFAULT 'USD',
    quantity NUMERIC(12,3),                   -- e.g. litres, kg for price tracking
    unit TEXT,
    counterparty TEXT,                        -- buyer/supplier free text
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    litter_id UUID REFERENCES litters(id),
    paddock_id UUID,                          -- FK §4
    inventory_movement_id UUID,               -- FK §3 when auto-posted
    breeding_wave_id UUID REFERENCES rabbit_breeding_waves(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (farm_id, mutation_id)
);

-- Derived, rebuildable (handbook Ch.7): allocations spread a record across enterprises
CREATE TABLE money_allocations (
    farm_id UUID NOT NULL REFERENCES farms(id),
    record_id UUID NOT NULL REFERENCES money_records(id) ON DELETE CASCADE,
    enterprise_code TEXT NOT NULL,            -- 'goat-dairy','rabbit-meat','poultry-layer'...
    amount NUMERIC(14,2) NOT NULL,
    basis TEXT NOT NULL CHECK (basis IN ('direct','head_days','weight_gain','feed_kg','manual')),
    PRIMARY KEY (farm_id, record_id, enterprise_code)
);

CREATE TABLE sales_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    item_kind TEXT NOT NULL,                  -- live_goat, milk_l, rabbit_meat_kg, eggs_tray...
    price NUMERIC(12,2) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    effective_on DATE NOT NULL,
    source TEXT,                              -- manual | contract ref
    UNIQUE (farm_id, item_kind, effective_on)
);
```

## 3. Backend schema — inventory & supply

```sql
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    name TEXT NOT NULL,
    phone TEXT, email TEXT,
    lead_time_days INT DEFAULT 0,
    notes TEXT
);

ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES suppliers(id),
    ADD COLUMN IF NOT EXISTS reorder_point NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS reorder_qty NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12,4),
    ADD COLUMN IF NOT EXISTS is_cold_chain BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,                      -- UUIDv7 client-side
    farm_id UUID NOT NULL REFERENCES farms(id),
    mutation_id UUID NOT NULL,
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    moved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    direction TEXT NOT NULL CHECK (direction IN ('in','out','adjust','waste')),
    qty NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    reason TEXT NOT NULL CHECK (reason IN (
        'purchase','treatment_use','feed_issue','bedding_issue','egg_sale',
        'milk_sale','stocktake','expiry','damage','transfer')),
    batch_no TEXT, expires_on DATE,           -- FEFO keys; vaccines cold chain
    unit_cost NUMERIC(12,4),                  -- purchase price → money link
    linked_treatment_id UUID, linked_event_id UUID,
    note TEXT,
    UNIQUE (farm_id, mutation_id)
);

-- Stock level projection (rebuildable from movements)
CREATE TABLE inventory_levels (
    farm_id UUID NOT NULL REFERENCES farms(id),
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    qty_on_hand NUMERIC(12,3) NOT NULL DEFAULT 0,
    avg_unit_cost NUMERIC(12,4),
    earliest_expiry DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (farm_id, item_id)
);

CREATE TABLE stocktakes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    taken_on DATE NOT NULL,
    status TEXT NOT NULL DEFAULT ('open') CHECK (status IN ('open','posted')),
    notes TEXT
);

CREATE TABLE stocktake_lines (
    stocktake_id UUID NOT NULL REFERENCES stocktakes(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    counted_qty NUMERIC(12,3) NOT NULL,
    PRIMARY KEY (stocktake_id, item_id)
);
```

Movement rules: every `out` with `reason='treatment_use'` is created by WithdrawalClock/formulary flow (vet spec §6); `feed_issue` by the ration engine (§8); posting a stocktake emits one `adjust` per variance line. Purchases auto-create `money_records` (category from item class).

## 4. Backend schema — pasture & rotation

```sql
CREATE TABLE paddocks (
    id UUID PRIMARY KEY,                      -- UUIDv7
    farm_id UUID NOT NULL REFERENCES farms(id),
    code TEXT NOT NULL,                       -- slug rule: P1..Pn, unique per farm
    display_name TEXT NOT NULL,
    area_ha NUMERIC(10,4),
    soil_type TEXT,
    water_source TEXT CHECK (water_source IN ('none','trough','stream','dam','pipeline')),
    shade BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    geo JSONB,                                -- optional polygon, later map render
    UNIQUE (farm_id, code)
);

CREATE TABLE grazing_sessions (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms(id),
    mutation_id UUID NOT NULL,
    paddock_id UUID NOT NULL REFERENCES paddocks(id),
    group_id UUID NOT NULL REFERENCES animal_groups(id),
    species_code TEXT NOT NULL,
    entered_on DATE NOT NULL,
    exited_on DATE,                            -- null = currently in
    head_count INT NOT NULL,
    rest_target_days INT,                      -- from farm config / pack
    notes TEXT,
    UNIQUE (farm_id, mutation_id),
    CHECK (exited_on IS NULL OR exited_on >= entered_on)
);

-- Derived: paddock rest state (rebuildable)
CREATE TABLE paddock_state (
    farm_id UUID NOT NULL REFERENCES farms(id),
    paddock_id UUID NOT NULL REFERENCES paddocks(id),
    status TEXT NOT NULL CHECK (status IN ('resting','grazing','locked','quarantine')),
    occupied_by_group UUID REFERENCES animal_groups(id),
    days_rest INT NOT NULL DEFAULT 0,
    last_grazed_on DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (farm_id, paddock_id)
);
```

One group grazes one paddock at a time; overlapping open sessions for a paddock are rejected at the engine (concurrency rule like nest boxes). Quarantine status integrates with health outbreak path.

## 5. Domain engines

| Engine | Module | Responsibility |
|---|---|---|
| `MoneyRecorder` | `:domain-finance` | Validate + emit `money.recorded`; idempotent by `mutation_id`; auto-post purchase/expense links from inventory & treatments |
| `AllocationEngine` | `:domain-finance` | Spread records to enterprises by basis (direct / head_days / weight_gain / feed_kg / manual); rebuild `money_allocations` from ledger |
| `UnitEconomicsEngine` | `:domain-finance` | §11 formulas → `analytics_*`-style projection rows for cockpit & module KPIs |
| `InventoryProjector` | `:domain-inventory` | movements → `inventory_levels` (qty, avg cost, FEFO earliest expiry); low-stock/reorder detection vs `reorder_point` |
| `RationPlanner` | `:domain-inventory` | §8: demand forecast from groups + purpose packs; simple ration check (energy/protein/DM vs species tables); feed_issue movements |
| `RotationEngine` | `:domain-pasture` | sessions → `paddock_state`; rest-day counters; rotation suggestion (ready paddocks ranked by days_rest + shade/water fit); quarantine interlock |
| `CockpitAggregator` | `:domain-finance` | §10 read model: KPIs, money, stock, compliance, exports |

All pure Kotlin; projections rebuildable; every engine emits through its owning module's event emitter (handbook Ch.8).

## 6. UX flows — money capture

**Principle: capture at the point of action, manual entry is the fallback.**

Auto-posted (no typing): purchase received (inventory `in` → expense), treatment product used (cost of goods), rabbit sales contract marked paid (income, links litter/kit), egg/milk sale logged.

Manual entry screen (`Money > +`):

```
[Expense|Income]  Date (today)  Amount [  ]  Category [Feed ▾]
Link to: (optional) [Group ▾] [Animal ▾] [Litter ▾] [Paddock ▾]
Qty/unit (optional, enables price history)   Note
[Save]  → outbox; works offline; duplicate-safe
```

- Categories are farm-seeded defaults (feed, vet, meds, fuel, labour, bedding, sales-income…) editable by Owner.
- Price history: any record with qty creates a `sales_prices`/purchase-price point; market planner and reorder hints read them.

## 7. UX flows — inventory operations

```
Stock screen (per module + shared)
  [Feed maize 18%]  240 kg   exp: 2026-11-02   [Issue to group] [Receive] [Adjust]
  [CDT vaccine]     12 doses cold-chain  exp 2026-09-14  FEFO
  ⚠ Low stock: layer mash (below reorder point) → [Create purchase]
```

- **Receive**: supplier, batch, expiry, unit cost → movement `in` + money record + FEFO update. Cold-chain items flagged (fridge location hint from health spec).
- **Issue**: pick reason (treatment/feed/bedding/egg sale…) → movement `out`; linked flows auto-issue.
- **Stocktake**: count sheet per location; posting creates variance `adjust` movements with note.
- **Low-stock worker**: daily check vs `reorder_point` → notification (existing matrix), one-tap purchase draft.

## 8. UX flows — feed planning & ration checks

**Simple, transparent, vet/extension-aligned — not a nutrition lab.**

```
Feed plan (week of 24 Aug)
  Goat dairy herd (24 head): need 8.1 kg DM/day total → maize 45 kg · hay 60 kg · mineral 0.5 kg
  Coverage: maize 12 days left ⚠ reorder · hay 9 days ⚠ · mineral OK
Ration check — lactating doe 55 kg: energy 82% of target, protein 104%, Ca:P 1.6:1 ✓
  → suggestion: +0.4 kg maize/head/day  [Apply to plan] (draft; Owner confirms)
```

1. Demand = Σ(group head_count × per-head DM by class/purpose from purpose-pack tables; pregnancy/lactation multipliers from vet pack reproduction sections).
2. Coverage = `inventory_levels` ÷ daily demand → days-left chips and reorder tasks.
3. Ration check: compare planned mix against species reference tables (bundled, cited) → % of target per nutrient; suggestions are drafts.
4. Feeding a plan day = `feed_issue` movements (FEFO) + optional money allocation basis `feed_kg`.

## 9. UX flows — paddock rotation planner

```
Paddocks
  P1 Grazing (Goat herd A, day 3/5)   P2 Resting (11 d) ✓ ready
  P3 Resting (4 d) · P4 Quarantine (footrot watch — locked)
  [Move herd A →]  suggests: P2 (most rest, water ✓ shade ✓)
Move flow: pick group → pick paddock (ready ones first; blocked ones explain why)
  → closes old session, opens new, head count confirm, rest timers reset
```

- Rest target per paddock/group from farm config (default 30 d, editable); ready badge when days_rest ≥ target.
- Quarantine interlock: quarantined group cannot enter a non-quarantine paddock; engine rejects with reason (health outbreak integration).
- History per paddock: grazing sessions, rest days, occupants — answers "was P2 grazed in the last 21 days?" for parasite reasoning (supports FAMACHA/refugia playbook, not a diagnosis).

## 10. UX flows — owner business cockpit

One screen, monthly-first:

```
August 2026                    [Month ▾]        [Export PDF] [Export CSV]
Net position: −1,240  (income 8,900 / expenses 10,140)
Per enterprise: goat-dairy +2,100 · rabbit-meat +860 · poultry-layer −4,200 ⚠
KPIs: kid survival 91% ✓ · hen-housed eggs 218 ✓ · calving interval 383 d ⚠
Costs: feed 46% of expenses · cost/kg rabbit gain 2.90 · milk cost/l 0.41
Ops: 2 paddocks resting ✓ · 3 stock alerts ⚠ · 1 withdrawal active · 0 notifiable
[Ask Copilot: "why is poultry negative?"]  → tool-grounded answer + action drafts
```

- Every number is a projection row with drill-down to its source records (tap → filtered list → record detail). No number without provenance.
- Copilot uses `get_kpis`/`get_timeline`-class tools only; money answers cite record IDs; suggestions are action-catalog drafts.

## 11. Unit economics: formulas

| Metric | Formula | Grain |
|---|---|---|
| Cost per kg gain | (feed cost + health cost + allocated overhead for cohort) ÷ kg weight gain | rabbit grow-out, beef, goat meat |
| Milk cost per litre | (feed + health + parlour supplies allocated) ÷ litres shipped | dairy cow/goat |
| Cost per egg / tray | (feed + health allocated to layer enterprise) ÷ eggs | layers by kind |
| Margin per litter | litter income (sales contracts) − doe feed share − litter costs | rabbit programme |
| Enterprise net | Σ allocations(income) − Σ allocations(expense) | per enterprise_code |
| Feed cost share | feed expense ÷ total expense | farm, monthly |
| Price received vs market | `sales_prices` series vs manual benchmark entry | optional |

Allocation bases: `direct` (linked at capture), `head_days` (Σ animals × days in enterprise), `weight_gain` (from measurements), `feed_kg` (ration issues). Unallocated remainder shows as "farm overhead" — never silently dropped.

## 12. Roles & safety

| Capability | Worker | Farm mgr | Owner |
|---|---|---|---|
| Record money entry | ✗ | ✓ | ✓ |
| Receive/issue stock, stocktake count | ✓ | ✓ | ✓ |
| Post stocktake / edit reorder points | ✗ | ✓ | ✓ |
| Move grazing group | ✓ | ✓ | ✓ |
| Lock paddock / set quarantine | ✗ | ✓ | ✓ |
| Edit categories, enterprises, allocation rules | ✗ | propose | ✓ |

Money records are immutable once synced; corrections are reversing entries (`money.recorded` negative-mirror with link) — consistent with append-only law.

## 13. Offline behaviour

- All captures (money, movements, sessions, stocktake counts) queue in outbox with `mutation_id`; projections recompute on device from Room mirror.
- Cockpit reads are projection-first: month view renders offline with last-synced banner.
- Rotation engine and ration checks run fully on-device; purchase drafts send when online.

## 14. Phasing & gates

| Phase slice | Ships | Gate (handbook Ch.10 style) |
|---|---|---|
| O1 Inventory core | items+movements+levels, receive/issue/stocktake UI, low-stock worker | FEFO fixture: earliest-expiry batch issued first; variance adjust on stocktake post; RLS fixtures |
| O2 Money core | categories, records, auto-post links, price history | idempotent replay fixture (double-purchase never double-posts); reversal flow test |
| O3 Pasture | paddocks, sessions, rotation planner, quarantine interlock | overlap-rejection call-site named; quarantine interlock adversarial check |
| O4 Feed & rations | demand forecast, coverage chips, ration check drafts | demand math golden fixture (24-head dairy herd example above); suggestion-is-draft gate |
| O5 Allocations & unit economics | AllocationEngine, §11 metrics | rebuild-from-ledger proof: drop allocations, rebuild, byte-equal |
| O6 Cockpit & exports | cockpit screen, PDF/CSV export | every tile provenance drill-down works; export determinism fixture |

## 15. Traceability

| Area | Source / relation |
|---|---|
| Event/idempotency/RLS law | Handbook Ch.6–7; master plan CQRS |
| Treatment→inventory auto-issue | Vet intelligence spec §4 WithdrawalClock; health spec §5 |
| Sales contracts → income | Rabbit breeding spec (contracts/waitlist) |
| Market planner targets | Rabbit breeding spec (market plans) ↔ cost/kg gain here |
| Weather inputs | Health spec §8 widget feeds rotation/feed heat hints |
| KPI formulas | Vet pack §8 ICAR sources; kpi_definitions table |
| Scope change authority | EDR-0003 (owner decision 22 Aug 2026) |

*End of operations & economics layer spec.*


