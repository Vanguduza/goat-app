# Farm OS — Rabbit breeding programme module
## Full MVP implementation (complete feature set)

**Companion to:** master plan §7.2 · `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` · vet pack §2  
**Date:** 20 August 2026 (full-MVP pass)  
**Android:** `:feature-rabbit` · `:domain` engines · Room mirrors · Supabase sync  
**Purpose pack id:** `kudbat_semi_intensive_excel` (default); multi-breed packs supported in MVP

### MVP scope lock

**Everything below ships in rabbit MVP.** There is no “Phase 2+ / later” deferral for programme features listed here. Only explicitly out-of-scope items (§18) stay out.

| Former bucket | Now |
|---------------|-----|
| Must-have | MVP |
| Should-have (palpation, foster, scorecards, sanitation, inventory, QR, ICS, …) | **MVP** |
| Nice-to-have (colour genetics, market planner, waitlist/sales, multi-breed templates, photo journal) | **MVP** |

---

## 0. Locked farm startup profile

| Setting | Value |
|---------|--------|
| Initial breeding cages | **3** (A, B, C) |
| Does / bucks per cage | **11 / 1** |
| Nest boxes at start | **11 per cage**, dedicated pools |
| Ratio start | **1 : 11** |
| Expansion | Cage **pairing** → shared pool **2 : 11** |
| Schedule | Excel offsets — nest-box schedule doc |
| Shared buck pool | Supported in MVP (borrow + COI) |

---

## 1. Navigation & screens (`:feature-rabbit`)

```
RabbitNavGraph
├── RabbitHome
│   ├── Today (cage filter chips)
│   ├── Alerts strip (overdue nest / kindling / dirty boxes / low bedding)
│   └── Quick actions: Mate · Place nest · Kindling · Scan QR
├── ColonyMapScreen          // cages grid, occupancy, pair badges
├── CageDetailScreen         // does, buck, current wave, nest demand chart
├── BreedingProgrammeScreen  // waves list + calendar month
├── WaveDetailScreen
├── WaveGenerateWizard
├── MatingDetailScreen
├── PairingPlannerScreen     // doe × buck + COI + colour predict
├── NestBoxInventoryScreen
├── NestBoxDetailScreen      // history, clean log, QR
├── NestSanitizeFlow
├── LitterDetailScreen       // kits, foster, photos, weights
├── FosterMatchScreen
├── KitPromoteScreen         // individualize + ear tag
├── RetentionPlannerScreen   // keep / cull / sale / waitlist
├── DoeScorecardScreen
├── BuckScorecardScreen
├── MarketPlannerScreen      // butcher / sale dates from weights
├── SalesWaitlistScreen
├── SalesContractScreen
├── CageCardPrintScreen      // PDF / share cage + box cards
├── CalendarExportScreen     // ICS
├── ColourGeneticsScreen     // loci config + hypothetical litter
├── PhotoJournalScreen       // litter gallery
├── PedigreeCompactScreen / PedigreeFullScreen
├── ProgrammeKpiScreen
└── ProgrammeSettingsScreen  // packs, COI thresholds, palpation on/off, breeds
```

Hilt: each screen → ViewModel → use cases in `:domain` → repositories (Room + Supabase).

---

## 2. Purpose packs & multi-breed templates (MVP)

```kotlin
data class RabbitPurposePack(
    val id: String,
    val displayName: String,
    val nestInDaysAfterMating: Int,      // 28
    val kindlingDaysAfterMating: Int,    // 32
    val rebreedDaysAfterMating: Int,     // 43
    val nestOutDaysAfterKindling: Int,   // 21
    val weanDaysAfterKindling: Int,      // 35
    val palpateDaysAfterMating: Int?,    // 14 or null if disabled
    val kindlingOverdueDays: Int,        // 34
    val kitWeightDaysAfterKindling: List<Int>, // 14, 28, wean
    val marketTargetLiveWeightKg: Double?,
    val marketMinAgeDays: Int?,
    val breedCode: String?,              // null = farm default
)

object KudbatSemiIntensiveExcel : RabbitPurposePack(
    id = "kudbat_semi_intensive_excel",
    displayName = "KudBat semi-intensive (Excel)",
    nestInDaysAfterMating = 28,
    kindlingDaysAfterMating = 32,
    rebreedDaysAfterMating = 43,
    nestOutDaysAfterKindling = 21,
    weanDaysAfterKindling = 35,
    palpateDaysAfterMating = 14,
    kindlingOverdueDays = 34,
    kitWeightDaysAfterKindling = listOf(14, 28, 35),
    marketTargetLiveWeightKg = 2.5,
    marketMinAgeDays = 70,
    breedCode = null,
)
```

**Multi-breed:** `rabbit_breed_packs` maps `breed_code` → purpose pack overrides (e.g. different wean day). Wave generate picks pack from majority breed in cage or explicit pack selection. Task templates clone from pack; custom farm tasks append via `rabbit_task_template_extras`.

```sql
CREATE TABLE rabbit_purpose_packs (
    id TEXT PRIMARY KEY,
    farm_id UUID REFERENCES farms(id), -- null = system pack
    definition JSONB NOT NULL
);

CREATE TABLE rabbit_breed_pack_bindings (
    farm_id UUID NOT NULL REFERENCES farms(id),
    breed_code TEXT NOT NULL,
    purpose_pack_id TEXT NOT NULL REFERENCES rabbit_purpose_packs(id),
    PRIMARY KEY (farm_id, breed_code)
);

CREATE TABLE rabbit_task_template_extras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    purpose_pack_id TEXT NOT NULL,
    task_code TEXT NOT NULL,
    title TEXT NOT NULL,
    offset_anchor TEXT NOT NULL CHECK (offset_anchor IN ('mating', 'kindling', 'wean', 'rebreed')),
    offset_days INT NOT NULL,
    priority INT NOT NULL DEFAULT 2,
    role_hint TEXT
);
```

---

## 3. Complete data model (MVP)

### 3.1 Cages, pairs, occupancy

```sql
CREATE TABLE rabbit_cages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    capacity_does INT NOT NULL DEFAULT 11,
    capacity_bucks INT NOT NULL DEFAULT 1,
    location_id UUID REFERENCES locations(id),
    qr_value TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    notes TEXT,
    UNIQUE (farm_id, code)
);

CREATE TABLE rabbit_cage_occupancy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    animal_id UUID NOT NULL REFERENCES animals(id),
    role TEXT NOT NULL CHECK (role IN ('doe', 'buck', 'kit_growout', 'temporary')),
    slot_label TEXT,                 -- 'A2', 'A3' matching sheet
    entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    exited_at TIMESTAMPTZ,
    UNIQUE (cage_id, animal_id) WHERE exited_at IS NULL
);

CREATE TABLE rabbit_cage_pairs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    name TEXT NOT NULL,
    cage_a_id UUID NOT NULL REFERENCES rabbit_cages(id),
    cage_b_id UUID NOT NULL REFERENCES rabbit_cages(id),
    nest_pool_id UUID NOT NULL REFERENCES rabbit_nest_pools(id),
    phase_offset_days INT NOT NULL DEFAULT 34,
    active BOOLEAN NOT NULL DEFAULT true,
    CHECK (cage_a_id <> cage_b_id),
    UNIQUE (farm_id, cage_a_id),
    UNIQUE (farm_id, cage_b_id)
);

CREATE TABLE rabbit_buck_borrows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    buck_id UUID NOT NULL REFERENCES animals(id),
    from_cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    to_cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    mating_id UUID REFERENCES rabbit_matings(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    notes TEXT
);
```

### 3.2 Nest pools, boxes, sanitation

```sql
CREATE TABLE rabbit_nest_pools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    ownership TEXT NOT NULL CHECK (ownership IN ('dedicated', 'shared')),
    target_box_count INT NOT NULL DEFAULT 11,
    dedicated_cage_id UUID REFERENCES rabbit_cages(id),
    UNIQUE (farm_id, code)
);

CREATE TABLE rabbit_nest_boxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    pool_id UUID NOT NULL REFERENCES rabbit_nest_pools(id),
    code TEXT NOT NULL,
    display_name TEXT,
    qr_value TEXT,
    status TEXT NOT NULL DEFAULT 'available'
        CHECK (status IN ('available','assigned','in_cage','dirty','quarantine','retired')),
    condition_notes TEXT,
    last_cleaned_at TIMESTAMPTZ,
    UNIQUE (farm_id, code)
);

CREATE TABLE rabbit_nest_box_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    nest_box_id UUID NOT NULL REFERENCES rabbit_nest_boxes(id),
    doe_id UUID NOT NULL REFERENCES animals(id),
    cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    mating_id UUID NOT NULL REFERENCES rabbit_matings(id),
    planned_in_at DATE NOT NULL,
    planned_out_at DATE NOT NULL,
    actual_in_at TIMESTAMPTZ,
    actual_out_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'planned'
        CHECK (status IN ('planned','active','completed','cancelled')),
    UNIQUE (nest_box_id, mating_id)
);

CREATE TABLE rabbit_nest_sanitation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    nest_box_id UUID NOT NULL REFERENCES rabbit_nest_boxes(id),
    cleaned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cleaned_by UUID,
    method TEXT,                     -- scrape, wash, disinfect, dry
    disinfectant_product_id UUID,    -- formulary / inventory item optional
    checklist JSONB NOT NULL DEFAULT '{}',
    passed BOOLEAN NOT NULL DEFAULT true,
    notes TEXT,
    photo_ids UUID[] DEFAULT '{}'
);
```

**Status machine:**  
`available` → (reserve) `assigned` → (place) `in_cage` → (remove) `dirty` → (sanitize pass) `available`  
Any state → `quarantine` / `retired` with reason.

### 3.3 Waves & matings

```sql
CREATE TABLE rabbit_breeding_waves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    code TEXT NOT NULL,
    wave_start_date DATE NOT NULL,
    purpose_pack_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'planned'
        CHECK (status IN ('planned','active','completed','cancelled')),
    concurrency_ok BOOLEAN,
    notes TEXT,
    UNIQUE (farm_id, code)
);

CREATE TABLE rabbit_matings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    wave_id UUID REFERENCES rabbit_breeding_waves(id),
    cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    doe_id UUID NOT NULL REFERENCES animals(id),
    buck_id UUID NOT NULL REFERENCES animals(id),
    mated_on DATE NOT NULL,
    cohort_index INT,
    purpose_pack_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'mated'
        CHECK (status IN (
            'planned','mated','palpated_pregnant','palpated_open',
            'confirmed','false_pregnant','kindled','failed','cancelled'
        )),
    expected_kindling_on DATE NOT NULL,
    nest_in_on DATE NOT NULL,
    nest_out_on DATE NOT NULL,
    rebreed_on DATE NOT NULL,
    wean_on DATE,
    palpate_on DATE,
    actual_kindling_on DATE,
    coi_percent NUMERIC(6,3),
    relationship_code TEXT,          -- full_sib, half_sib, parent_child, cousin, unrelated, ...
    pedigree_depth_checked INT,
    inbreeding_decision TEXT NOT NULL DEFAULT 'pending'
        CHECK (inbreeding_decision IN ('allow','warn_accepted','block','pending')),
    inbreeding_ack_by UUID,
    inbreeding_ack_at TIMESTAMPTZ,
    predicted_colours JSONB,         -- from colour engine snapshot
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE rabbit_palpation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    mating_id UUID NOT NULL REFERENCES rabbit_matings(id),
    palpated_on DATE NOT NULL,
    result TEXT NOT NULL CHECK (result IN ('pregnant','open','uncertain')),
    recorded_by UUID,
    notes TEXT
);
```

### 3.4 Litters, kits, foster, photos

```sql
-- Platform litters table remains SoR projection; rabbit module adds:
CREATE TABLE rabbit_litter_details (
    litter_id UUID PRIMARY KEY REFERENCES litters(id),
    farm_id UUID NOT NULL REFERENCES farms(id),
    mating_id UUID NOT NULL REFERENCES rabbit_matings(id),
    cage_id UUID NOT NULL REFERENCES rabbit_cages(id),
    nest_box_id UUID REFERENCES rabbit_nest_boxes(id),
    kindled_at TIMESTAMPTZ,
    assisted BOOLEAN DEFAULT false,
    doe_ate_kits BOOLEAN DEFAULT false,
    notes TEXT
);

CREATE TABLE rabbit_kits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    litter_id UUID NOT NULL REFERENCES litters(id),
    animal_id UUID REFERENCES animals(id), -- null until individualized
    temp_label TEXT,                     -- 'Kit 1' before ear tag
    sex TEXT CHECK (sex IN ('male','female','unknown')),
    colour_phenotype TEXT,
    colour_genotype JSONB,
    status TEXT NOT NULL DEFAULT 'alive'
        CHECK (status IN ('alive','dead','fostered_out','missing')),
    death_on DATE,
    death_reason TEXT,
    retention TEXT CHECK (retention IN ('undecided','keep_breeder','grow_meat','sale_pet','cull')),
    ear_tag TEXT,
    individualized_at TIMESTAMPTZ,
    UNIQUE (farm_id, ear_tag)
);

CREATE TABLE rabbit_foster_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    kit_id UUID NOT NULL REFERENCES rabbit_kits(id),
    from_litter_id UUID NOT NULL REFERENCES litters(id),
    to_litter_id UUID NOT NULL REFERENCES litters(id),
    to_doe_id UUID NOT NULL REFERENCES animals(id),
    fostered_on DATE NOT NULL,
    reason TEXT,
    within_window BOOLEAN NOT NULL,      -- true if ≤3 days from either kindling
    recorded_by UUID,
    notes TEXT
);

CREATE TABLE rabbit_litter_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    litter_id UUID NOT NULL REFERENCES litters(id),
    kit_id UUID REFERENCES rabbit_kits(id),
    storage_path TEXT NOT NULL,          -- Supabase Storage
    caption TEXT,
    taken_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    taken_by UUID,
    tags TEXT[] DEFAULT '{}'             -- health, show, nest, weigh
);
```

**Foster rules (engine):**

1. Both litters kindled within **3 days** (Merck window) → `within_window = true`.  
2. Outside window → warn; Owner/Breeding Mgr ack required.  
3. Update kit `status`; adjust litter live counts via compensating events.  
4. Task: check fostered kits next morning.

### 3.5 False pregnancy / failed kindling

```sql
CREATE TABLE rabbit_mating_outcomes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    mating_id UUID NOT NULL REFERENCES rabbit_matings(id) UNIQUE,
    outcome TEXT NOT NULL CHECK (outcome IN (
        'kindled','false_pregnant','failed_no_kindling','aborted','doe_died'
    )),
    recorded_on DATE NOT NULL,
    cancel_nest_assignment BOOLEAN NOT NULL DEFAULT true,
    reschedule_rebreed_on DATE,
    notes TEXT
);
```

**Flow `markFalsePregnant(matingId)`:**

1. Set mating `status = false_pregnant`.  
2. Cancel open tasks: `EXPECTED_KINDLING`, `NEST_BOX_PLACE` (if not placed), `NEST_BOX_REMOVE`.  
3. If box `in_cage`/`assigned` → create remove-now task; release reservation.  
4. Set `rebreed_on = today + farm.false_preg_rebreed_delay_days` (default **7**).  
5. Create `REBREED` task; log outcome.

**Flow `markKindlingOverdue` / `failed_no_kindling`:** same cancellations; prompt vet note (Merck); optional induction is **vet-only** — app opens Health, does not prescribe.

### 3.6 Line tags, colour genetics

```sql
CREATE TABLE rabbit_family_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    code TEXT NOT NULL,                 -- 'Blue', 'Line-3'
    display_name TEXT NOT NULL,
    colour_hex TEXT,
    notes TEXT,
    UNIQUE (farm_id, code)
);

ALTER TABLE animals ADD COLUMN IF NOT EXISTS rabbit_line_id UUID REFERENCES rabbit_family_lines(id);

CREATE TABLE rabbit_colour_loci (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    locus_code TEXT NOT NULL,           -- 'A','B','C','D','E',... farm-configured
    display_name TEXT NOT NULL,
    alleles JSONB NOT NULL,             -- [{"code":"A","dom":1},...]
    UNIQUE (farm_id, locus_code)
);

CREATE TABLE rabbit_animal_genotypes (
    animal_id UUID NOT NULL REFERENCES animals(id),
    farm_id UUID NOT NULL REFERENCES farms(id),
    locus_code TEXT NOT NULL,
    allele_a TEXT NOT NULL,
    allele_b TEXT NOT NULL,
    confidence TEXT DEFAULT 'assumed' CHECK (confidence IN ('tested','pedigree','assumed','phenotype_only')),
    PRIMARY KEY (animal_id, locus_code)
);
```

**Colour engine:** Punnett per locus for doe × buck → probability table of phenotypes (farm phenotype map JSON). Snapshot saved on mating as `predicted_colours`. UI: `ColourGeneticsScreen` + embed on `PairingPlannerScreen`.

### 3.7 Retention, sales waitlist, contracts

```sql
CREATE TABLE rabbit_retention_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    kit_id UUID NOT NULL REFERENCES rabbit_kits(id),
    decision TEXT NOT NULL CHECK (decision IN ('keep_breeder','grow_meat','sale_pet','cull','undecided')),
    decided_on DATE NOT NULL,
    decided_by UUID,
    notes TEXT
);

CREATE TABLE rabbit_sales_waitlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    contact_name TEXT NOT NULL,
    contact_phone TEXT,
    contact_email TEXT,
    desired_sex TEXT,
    desired_colour TEXT,
    desired_breed TEXT,
    qty INT NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'open'
        CHECK (status IN ('open','matched','fulfilled','cancelled')),
    matched_kit_id UUID REFERENCES rabbit_kits(id),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE rabbit_sales_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    waitlist_id UUID REFERENCES rabbit_sales_waitlist(id),
    buyer_name TEXT NOT NULL,
    buyer_contact TEXT,
    animal_ids UUID[] NOT NULL,
    price_total NUMERIC(12,2),
    currency TEXT DEFAULT 'USD',
    status TEXT NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft','agreed','paid','delivered','cancelled')),
    terms_text TEXT,
    pdf_storage_path TEXT,
    agreed_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);
```

Promote to breeder: `KitPromoteScreen` creates `animals` row, `pedigree_relations`, ear tag uniqueness, occupancy move; requires pedigree parents known unless foundation override.

### 3.8 Market / butcher planner

```sql
CREATE TABLE rabbit_market_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    litter_id UUID REFERENCES litters(id),
    kit_id UUID REFERENCES rabbit_kits(id),
    target_weight_kg NUMERIC(6,3) NOT NULL,
    target_date DATE,
    projected_date DATE,             -- from ADG
    purpose TEXT CHECK (purpose IN ('meat','pet_sale','show')),
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','ready','sold','butchered','cancelled')),
    notes TEXT
);
```

Projection: use litter/kit weights → ADG → days to `target_weight_kg`; create task `MARKET_READY` on projected date; notify.

### 3.9 Inventory hooks (bedding / feed)

Reuse platform `inventory_items` / transactions. Rabbit programme settings bind:

```sql
CREATE TABLE rabbit_programme_inventory_links (
    farm_id UUID PRIMARY KEY REFERENCES farms(id),
    nest_bedding_item_id UUID REFERENCES inventory_items(id),
    nest_bedding_qty_per_place NUMERIC(10,3) DEFAULT 1,
    doe_feed_item_id UUID REFERENCES inventory_items(id),
    low_stock_notify BOOLEAN DEFAULT true
);
```

On `NEST_BOX_PLACE` complete → optional auto issue bedding qty; if below reorder → alert.

### 3.10 Cage cards & calendar export

```sql
CREATE TABLE rabbit_cage_card_templates (
    farm_id UUID PRIMARY KEY REFERENCES farms(id),
    show_qr BOOLEAN DEFAULT true,
    show_next_tasks BOOLEAN DEFAULT true,
    show_pedigree_snippet BOOLEAN DEFAULT false,
    layout JSONB NOT NULL DEFAULT '{}'
);

-- ICS: generated client-side or Edge; no extra table required.
-- Optional audit:
CREATE TABLE rabbit_calendar_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    exported_at TIMESTAMPTZ DEFAULT now(),
    range_start DATE,
    range_end DATE,
    task_count INT
);
```

---

## 4. Domain engines (full API)

```kotlin
interface RabbitBreedingProgrammeEngine {
    fun setupStartupFarm(cmd: StartupThreeCageCmd): StartupResult
    fun generateWave(cmd: GenerateWaveCmd): WavePlan
    fun confirmMating(cmd: ConfirmMatingCmd): MatingResult
    fun tasksForMating(matingId: Uuid): List<TaskDraft>
    fun evaluatePairing(doeId: Uuid, buckId: Uuid, policy: CoiPolicy): PairingVerdict
    fun predictColours(doeId: Uuid, buckId: Uuid): ColourPrediction
    fun reserveNestBoxes(matingId: Uuid): List<Assignment>
    fun placeNestBox(cmd: PlaceNestCmd): NestBox
    fun removeNestBox(cmd: RemoveNestCmd): NestBox
    fun sanitizeNestBox(cmd: SanitizeCmd): SanitationLog
    fun recordPalpation(cmd: PalpationCmd): Mating
    fun recordKindling(cmd: KindlingCmd): Litter
    fun markFalsePregnant(cmd: FalsePregCmd): Mating
    fun markFailedKindling(cmd: FailedKindlingCmd): Mating
    fun fosterKits(cmd: FosterCmd): List<FosterEvent>
    fun individualizeKit(cmd: PromoteKitCmd): Animal
    fun setRetention(cmd: RetentionCmd)
    fun matchWaitlist(cmd: WaitlistMatchCmd)
    fun createSalesContract(cmd: ContractCmd): SalesContract
    fun projectMarketDates(litterId: Uuid): List<MarketPlan>
    fun simulatePairConcurrency(pairId: Uuid, waves: List<Uuid>): ConcurrencyReport
    fun pairCages(cmd: PairCagesCmd): CagePair
    fun borrowBuck(cmd: BorrowBuckCmd)
    fun buildCageCard(cageId: Uuid): CageCardModel
    fun exportIcs(range: DateRange, cageIds: List<Uuid>?): ByteArray
    fun doeScorecard(doeId: Uuid, window: DateRange): DoeScorecard
    fun buckScorecard(buckId: Uuid, window: DateRange): BuckScorecard
    fun programmeKpis(farmId: Uuid, window: DateRange): ProgrammeKpis
}

object NestAssigner {
    /** Prefer available in pool, longest idle (last_cleaned_at / last out), not quarantine. */
    fun pick(pool: NestPool, boxes: List<NestBox>, need: Int): List<NestBox>
}

object WaveScheduler {
    /** Cohorts at offsets 0,2,4,6,8,10; 2 does/day except last 1; enforce buck rest. */
    fun plan(start: LocalDate, does: List<Doe>, buck: Buck): List<CohortPlan>
}

object NestConcurrency {
    fun peak(windows: List<ClosedRange<LocalDate>>): Int
    fun firstSafeStart(aWaves: List<Wave>, offsetHint: Int, poolSize: Int): LocalDate
}
```

### 4.1 COI policy (MVP defaults)

```kotlin
data class CoiPolicy(
    val warnAtPercent: Double = 6.25,
    val blockAtPercent: Double = 12.5,
    val blockRelationships: Set<Relationship> = setOf(
        Relationship.PARENT_CHILD, Relationship.FULL_SIB
    ),
    val warnRelationships: Set<Relationship> = setOf(Relationship.HALF_SIB),
    val minPedigreeDepth: Int = 2,
    val allowFoundationOverride: Boolean = true, // Breeding Mgr+
)
```

Computation: Kotlin Wright COI on pedigree graph (same formulas as platform genetics); optional server verify later — **MVP computes on-device** from synced `pedigree_relations`.

### 4.2 Task generation (complete list)

| Code | Anchor | When | On complete |
|------|--------|------|-------------|
| `PALPATE` | mating | +14 (if pack enabled) | write palpation record; branch open/pregnant |
| `NEST_BOX_PLACE` | mating | +28 | placeNestBox; issue bedding |
| `EXPECTED_KINDLING` | mating | +32 | open kindling form |
| `KINDLING_OVERDUE` | mating | +34 if no litter | escalate |
| `NEST_BOX_REMOVE` | kindling | +21 | removeNestBox → dirty |
| `NEST_SANITIZE` | remove | +0 (same day) or +1 | sanitize → available |
| `REBREED` | mating | +43 (or rescheduled) | open pairing planner |
| `WEAN` | kindling | +35 | wean flow; retention prompts |
| `KIT_WEIGHT` | kindling | pack days | measurement rows |
| `FOSTER_CHECK` | foster | +1 | checklist |
| `MARKET_READY` | projection | projected date | market planner |
| `BUCK_LOAD_REVIEW` | weekly | cron/WorkManager | buck scorecard alert if over farm max matings/week |
| `WAITLIST_MATCH` | retention sale_pet | on decision | notify Mgr |

All tasks: `module_id=rabbit`, `animal_id` and/or `group` litter ref in metadata JSON, deep link to screen.

---

## 5. Notifications & WorkManager

| Trigger | Local notif | Push (if online) | Roles |
|---------|-------------|------------------|-------|
| Task due tomorrow | ✓ | ✓ | Worker (cage), Mgr |
| Task overdue | ✓ | ✓ | Mgr; +4h Owner for nest/kindling |
| Dirty box > 48h | ✓ | ✓ | Worker |
| Low bedding / feed | ✓ | ✓ | Mgr |
| COI block attempted | — | ✓ | Breeding Mgr |
| Waitlist match | ✓ | ✓ | Mgr |
| Market ready | ✓ | ✓ | Mgr |
| Pair concurrency conflict | — | in-app only | Owner |

Implementation: Room `rabbit_task_local` + `RabbitNotifyWorker` (Periodic + OneTime on sync). Channel ids: `rabbit_critical`, `rabbit_tasks`, `rabbit_inventory`.

---

## 6. Screen-level implementation notes

### 6.1 Setup wizard (`StartupThreeCageCmd`)

1. Names A/B/C → insert cages + QR codes.  
2. For each cage: pool `POOL-{code}`, boxes `NB-{code}-01`…`11`, QR per box.  
3. Optional CSV/manual assign does to slots A2…A12 pattern.  
4. Assign buck; warn if COI unknown.  
5. Bind inventory bedding item.  
6. Enable palpation default on.  
7. Offer “Plan Wave 1” CTA.

### 6.2 Pairing planner

Compose sections: animal pickers → relationship badge → COI gauge → colour probability table → sire history on doe → Acknowledge warn → Confirm.  
Blocks navigation to save if `block`.

### 6.3 Litter + photo journal

Kindling form: live/stillborn, abnormalities, assisted, nest box confirm.  
Creates N `rabbit_kits` rows.  
Photo: CameraX → compress → Supabase Storage path `farm/{id}/litters/{litterId}/…` → `rabbit_litter_photos`. Gallery on litter tab; tag filters.

### 6.4 Foster match

Lists litters kindled ≤3 days apart with capacity hint (doe milking load = live kits). Drag/select kits → confirm → events + tasks.

### 6.5 Scorecards

**Doe:** matings, kindling rate, avg live born, weaned, prewean mortality, mean interval, last COI of litters, nest on-time %.  
**Buck:** matings count, unique does, litters sired, mean litter size, mean offspring COI, borrow count.  
SQL views `mv_rabbit_doe_scorecard`, `mv_rabbit_buck_scorecard` filtered by `farm_id`.

### 6.6 Cage cards

Generate PDF (Android Print / OpenPDF): cage name, QR, occupant list, next 3 tasks, active nest box codes. Share sheet. Box card: code, QR, pool, status.

### 6.7 ICS export

RFC5545 VEVENT per task in range; `UID = taskId@farm-os-rabbit`; download/share `.ics`.

### 6.8 Sales

Waitlist board → Match kit (retention `sale_pet`) → Contract draft → mark paid/delivered → finance posting optional via finance module event.

---

## 7. Nest concurrency & cage pairing (MVP)

```text
on PairCages(A,B):
  create or select pool of 11
  move boxes from dedicated pools into shared pool (or archive extras as spare pool)
  set ownership=shared
  re-run simulate on all planned/active waves
  if peak > 11 → show firstSafeStart; refuse activate pair until resolved

on GenerateWave for paired cage:
  auto-suggest start = other.wave_start + phase_offset_days
  simulate → must pass
```

UI chart: daily stacked nest demand for pair (0–11 line).

---

## 8. Events (append to domain_events)

| event_type | payload highlights |
|------------|-------------------|
| `RabbitWaveGenerated` | wave_id, cage_id, mating_ids |
| `RabbitMatingRecorded` | mating_id, doe, buck, coi, decision |
| `RabbitPalpationRecorded` | result |
| `RabbitNestBoxPlaced` / `Removed` / `Sanitized` | box_id, doe_id |
| `RabbitKindlingRecorded` | litter_id, live, still |
| `RabbitFosterPerformed` | kit_ids, litters |
| `RabbitKitIndividualized` | animal_id, ear_tag |
| `RabbitFalsePregnantMarked` | mating_id |
| `RabbitCagePaired` | pair_id, pool_id |
| `RabbitSaleContractAgreed` | contract_id |

Project into tables in same transaction (Supabase RPC or app outbox).

---

## 9. Offline & sync

- Room entities mirror all rabbit_* tables used barn-side.  
- Mutations: outbox with `mutation_id`; sync to Supabase.  
- Photos: queue file upload.  
- COI: works offline if pedigree synced.  
- Conflict: last-write on sanitation logs; mating status use domain rules (kindled wins over false_pregnant if both pending — surface conflict UI).

---

## 10. Permissions

| Action | Worker | Breeding Mgr | Owner |
|--------|--------|--------------|-------|
| Complete nest/weigh/kindling tasks | ✓ | ✓ | ✓ |
| Generate wave / confirm mating | ✗ | ✓ | ✓ |
| Ack COI warn | ✗ | ✓ | ✓ |
| Override foundation pedigree | ✗ | ✓ | ✓ |
| Pair cages / change pack | ✗ | ✗ | ✓ |
| Sales contract price | ✗ | ✓ | ✓ |
| Retire nest box | ✗ | ✓ | ✓ |

---

## 11. KPIs & anomalies (MVP)

KPIs as § prior +: foster rate, on-time sanitize %, mean days dirty, waitlist fulfillment days, % litters with photos, market projection accuracy.

Lane A anomalies: missed nest place, kindling overdue, litter mortality spike, dirty box SLA breach, buck overload, pool concurrency near 11.

---

## 12. Package structure

```
:domain
  rabbit/
    RabbitBreedingProgrammeEngine.kt
    WaveScheduler.kt
    NestAssigner.kt
    NestConcurrency.kt
    CoiEvaluator.kt
    ColourGeneticsEngine.kt
    MarketProjector.kt
    scorecard/
    packs/KudbatSemiIntensiveExcel.kt

:feature-rabbit
  ui/… (screens §1)
  notify/RabbitNotifyWorker.kt
  qr/RabbitQrRouter.kt
  pdf/CageCardRenderer.kt
  ics/IcsExporter.kt
```

---

## 13. Test plan (MVP acceptance)

- [ ] Wizard creates 3×11 boxes with unique codes/QR  
- [ ] Wave: 6 cohorts, 11 matings, buck never >2/day and rests between  
- [ ] Tasks fire at Excel offsets; notifications local offline  
- [ ] Nest assign never exceeds pool; place/remove/sanitize cycle  
- [ ] Pair A–B: shared 11; unsafe wave blocked; safe offset accepted  
- [ ] COI block parent–child; warn half-sib with ack  
- [ ] Palpation open → false pregnancy path cancels nest  
- [ ] Kindling + foster ≤3d; >3d requires ack  
- [ ] Kit promote creates animal + pedigree  
- [ ] Colour prediction returns probabilities when genotypes present  
- [ ] Market plan from weights creates MARKET_READY  
- [ ] Waitlist match → contract PDF path  
- [ ] Cage card PDF + ICS export non-empty  
- [ ] Bedding issue on place + low stock alert  
- [ ] Photo upload queued offline then syncs  
- [ ] Doe/buck scorecards match fixture SQL  

---

## 14. Delivery checklist (single MVP)

- [ ] Startup 3-cage wizard + dedicated 11 boxes  
- [ ] Waves, matings, full task/notification set (incl. palpation, sanitize, market, foster check)  
- [ ] Nest inventory, assignment, sanitation log, QR  
- [ ] Cage pairing + concurrency simulator  
- [ ] Pairing planner: COI, relationship, colour genetics  
- [ ] Kindling, litter, kits, foster, photo journal  
- [ ] False pregnancy / failed kindling flows  
- [ ] Kit individualize + retention planner  
- [ ] Doe/buck scorecards + programme KPIs  
- [ ] Inventory bedding/feed links  
- [ ] Cage/box cards PDF  
- [ ] ICS calendar export  
- [ ] Market/butcher planner  
- [ ] Sales waitlist + contracts  
- [ ] Multi-breed purpose pack bindings + template extras  
- [ ] Family line tags + buck borrow  
- [ ] Offline Room sync  

---

## 15. Out of scope (still)

- Goat/cattle UI inside rabbit module  
- Drug prescribing / dose invention  
- Hermes sidecar  
- Automatic financial ledger beyond optional posting hook  
- DNA lab integrations (manual parentage verify flag only)

---

## 16. Summary locks

| Decision | Lock |
|----------|------|
| MVP completeness | **All** former next/later programme features included |
| Start | 3 cages × 11 dedicated named boxes |
| Sharing | Cage pair → 2:11 with simulator |
| Schedule | KudBat Excel pack + multi-breed overrides |
| Inbreeding | COI + relationship gate on every mating |
| Nest boxes | Named, QR, sanitation history |
| Implementation | Engines in `:domain`; UI in `:feature-rabbit`; schema §3; tests §13 |

*This document is the implementation contract for the rabbit breeding programme MVP.*
