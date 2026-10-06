# MULTI-SPECIES FARM INTELLIGENCE PLATFORM
## Technical Implementation Master Plan
### Goats · Rabbits · Poultry · Sheep · Cattle

**Based on:** DOC-20260818-WA0002.pdf (Master Product, Lifecycle & Technical Design)
**Enhanced:** 19 August 2026
**Revised:** 20 August 2026 — Android-only. Species products on a shared platform. **Technical pass:** CQRS (event ledger vs read models), modular monolith / Supabase backend, RLS tenancy, flock vs individual grain, poultry kinds inside one module. **AI pass:** embedded analytics in the APK + Supabase only (no farm AI sidecars); BYO Model API in Settings — see §12a + `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`.
**Species scope:** Goat and rabbit remain the **Phase 1–8 system of record**. Poultry, sheep, and cattle are **first-class products** with dedicated Gradle modules, biology, housing, recording, and KPIs — not filters, gestation-number tweaks, or copied goat/rabbit screens.
**Purpose:** Technical blueprint aligned with species-specific modules, hybrid methods where each animal keeps the right UX, and a path to add animal modules without flattening them into one list.

### Alignment rules (non-negotiable)

1. **No generic Animals home.** The only home is the module dashboard, then a species (or shared-tool) graph.
2. **Goat UX is designed for goats** (kidding, paddocks, FAMACHA, full family tree, goat KPIs).
3. **Rabbit UX is designed for rabbits** (kindling, cages, litters, nest boxes, compact pedigree, rabbit KPIs).
4. **Poultry UX is designed for poultry** (flock/house first, placement, hatch/incubation, lay or grow-out, biosecurity, egg/FCR KPIs). **Kinds** (chicken, duck, guinea fowl, turkey, goose, quail, …) live **inside** `:feature-poultry` with kind-specific biology, language, and housing — not as extra dashboard livestock tiles and not as a goat-style mixed bird list.
5. **Sheep UX is designed for sheep** (joining, scanning, lambing, mobs/paddocks, wool/shearing where used, footrot/flystrike, lambing % KPIs). Not “goats with 147-day gestation.”
6. **Cattle UX is designed for cattle** (joining/AI/ET, pregnancy check, calving, herd/mob/lot, dairy parlour or beef weaning, NLIS/EID, calving-interval KPIs). Beef vs dairy are **capabilities inside the cattle module**, not a mixed livestock home.
7. **Shared tables are infrastructure** (`animals`, events, measurements, `animal_groups`). They must not become a shared “animal list” product.
8. **RFID/search** identifies the record (or flock/lot), then **opens that species’ module**.
9. **New species** = new feature module (`:feature-poultry`, `:feature-sheep`, `:feature-cattle`), not a species filter on an existing module.
10. **Donor patterns** (farmOS assets, LiteFarm animals, Cattleitics, ICAR sheep/goat/cattle) inform data, not a one-screen livestock IA.
11. **Working object follows biology.** Goats/cattle/stud sheep: individual. Rabbits: litter then individual. Poultry commercial: flock/house/placement. Cattle feedlot: lot. Do not force one object onto every species.

---

## Table of Contents

1. [Technology Stack Decision Matrix](#1-technology-stack-decision-matrix)
2. [System Architecture](#2-system-architecture)
3. [Service Decomposition & Boundaries](#3-service-decomposition--boundaries)
4. [Core Data Model (PostgreSQL Schema)](#4-core-data-model-postgresql-schema)
5. [Event Sourcing Implementation](#5-event-sourcing-implementation)
6. [Offline-First & Sync Architecture](#6-offline-first--sync-architecture)
7. [Species modules (per animal)](#7-species-modules-designed-per-animal)
    - [7.1 Goat](#71-goat-module-goatlifecycleengine)
    - [7.2 Rabbit](#72-rabbit-module-rabbitlifecycleengine)
    - [7.3 Poultry](#73-poultry-module-poultrylifecycleengine) (chicken, duck, guinea fowl, …)
    - [7.4 Sheep](#74-sheep-module-sheeplifecycleengine)
    - [7.5 Cattle](#75-cattle-module-cattlelifecycleengine)
8. [Genetics calculations & per-module pedigree](#8-genetics--pedigree-engine)
9. [Dynamic Task Engine](#9-dynamic-task-engine)
10. [Analytics & KPI Pipeline](#10-analytics--kpi-pipeline)
11. [AI & Farm Copilot](#11-ai--farm-copilot)
12. [Farm Copilot (in-app; Hermes deferred)](#12-farm-copilot-in-app-hermes-deferred)
12a. [Embedded AI module (charts, anomalies, Model API)](#12a-embedded-ai-module-charts-anomalies-model-api)
13. [Module dashboard & new animal modules](#13-module-dashboard--new-animal-modules)
14. [IoT & Hardware Integration](#14-iot--hardware-integration)
15. [Security, Auth & Multi-Tenancy](#15-security-auth--multi-tenancy)
16. [DevOps & Infrastructure](#16-devops--infrastructure)
17. [Open-Source Tools & Repos Reference](#17-open-source-tools--repos-reference)
18. [Phased Delivery Plan](#18-phased-delivery-plan)
19. [Risk Register](#19-risk-register)
20. [Technical invariants](#20-technical-invariants)
21. [Research status by branch](#21-research-status-by-branch)

---

## 1. Technology Stack Decision Matrix

**Client policy:** Android only. Phone for barn/paddock capture. Tablet for manager dashboards, reports, and breeding plans. No iOS. No React Native. No Compose Multiplatform until iOS is a real requirement.

| Layer | Technology | Justification |
|-------|-----------|---------------|
| **Field + manager app** | Kotlin + Jetpack Compose + Material 3 | Native Android UI, RFID/BLE without a JS bridge, one APK for phone and tablet |
| **Architecture** | Clean Architecture + MVVM | `data` / `domain` / `ui`; ViewModels survive rotation; testable engines |
| **Navigation** | Navigation Compose | Dashboard → `:feature-goat` / `:feature-rabbit` / `:feature-poultry` / `:feature-sheep` / `:feature-cattle` / shared tools. No shared Animals graph. |
| **API** | Kotlin + Ktor | Same language as the app; coroutines end-to-end |
| **Event sourcing** | PostgreSQL append-only event store (Kotlin) | Immutable **ledger**; read models in the same DB. Modular monolith — not a fleet of microservices for MVP |
| **Primary database** | PostgreSQL 16+ | Event store, relational read models, JSONB. **PostGIS optional** from grazing plans (Phase 5+), not a Phase 1 dependency |
| **PDF reports** | Android PrintManager (MVP); OpenPDF if needed | Avoid iText 7 AGPL unless a commercial license is purchased |
| **On-device database** | Room (SQLite) | Official Android persistence; encrypted with SQLCipher if required |
| **Offline sync** | Room outbox + WorkManager | Durable local writes; sync when network returns; domain conflict rules |
| **Optional sync engine** | Synchro Kotlin client | PostgreSQL WAL sync if a custom outbox becomes too costly |
| **Search** | Android Room FTS5 locally; Meilisearch on server | Instant animal lookup offline; farm-wide search online |
| **File storage** | MinIO (S3) | Photos, lab PDFs, certificates |
| **Cache / queue (server)** | Redis + Kotlin coroutines | Jobs, notifications |
| **AI/ML** | Kotlin rules in `:domain-ai` (+ optional LiteRT/ONNX in APK) | Anomalies & charts offline-first; no farm ML VM |
| **On-device AI** | LiteRT and/or ONNX Runtime Mobile | Optional compressed assets; downloadable from Supabase Storage |
| **LLM (Farm Copilot)** | In-app tool-loop + **BYO** OpenAI-compatible Model API | App executes allow-listed tools via Supabase (RLS). **Not Hermes.** |
| **Model API (Settings)** | OpenAI-compatible HTTP client (OkHttp SSE); optional Supabase Edge proxy | User’s Groq/OpenRouter/OpenAI/etc. key; keys in Keystore or Vault |
| **Backend hosting** | **Supabase** (Postgres, Auth, Storage, Edge Functions) | Product AI/data footprint stays inside Supabase + app — no AI sidecars |
| **Pedigree math** | PyAGH (Python) | Inbreeding, relationship matrices |
| **Charts** | Vico | Compose charts as **additional** representation on growth/milk/eggs/KPI pages |
| **Auth** | Keycloak (OIDC) + AppAuth | **One realm**; `farm_id` in token/claims. Not a Keycloak realm per farm |
| **DI** | Hilt | Standard Android injection |
| **Images** | Coil | Animal photos |
| **CI/CD** | GitHub Actions | Unit tests, Compose screenshot tests, APK/AAB |
| **Containers** | Docker Compose → Kubernetes | API + Postgres + MinIO |
| **Monitoring** | Grafana + Prometheus + Loki | Server health |

---

### Why this beats React Native for *this* product

RFID readers, Bluetooth scales, USB/serial milk meters, and barn Wi‑Fi drops are Android hardware problems. Compose talks to vendor AARs and `BluetoothGatt` directly. Room + WorkManager is the Android offline stack. Tablet layouts use `WindowSizeClass` instead of a second web app.

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EXPERIENCE LAYER (Android only)             │
│  ┌──────────────────────────┐  ┌────────────────────────────────┐ │
│  │ Module Dashboard         │  │ Same dashboard, denser tablet  │ │
│  │ Goats | Rabbits | Poultry │  │ + more KPI tiles               │ │
│  │ Sheep | Cattle | Farm    │  │                                │ │
│  └────────────┬─────────────┘  └───────────────┬────────────────┘ │
│               │ Room + outbox                  │ REST sync; WS alerts only │
│               └────────────────┬───────────────┘                  │
│  Hardware: RFID (UHF/LF EID), BLE scales, camera QR, USB/serial meters, NLIS wands │
└────────────────────────────────┼──────────────────────────────────┘
                                 │
┌────────────────────────────────▼──────────────────────────────────┐
│                        API GATEWAY (Ktor)                         │
│  OIDC (Keycloak) │ Rate limiting │ Sync endpoints │ WebSocket     │
└────────────────────────────────┬──────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────────────────────────────┐
│                      APPLICATION SERVICES                         │
│  Platform: identity store, events, health records, inventory,     │
│            finance posting, sync, tasks merge                     │
│  Goat module API: kidding, lactation, FAMACHA, goat pedigree      │
│  Rabbit module API: kindling, litters, cages, rabbit pedigree     │
│  Poultry module API: kinds, flocks, houses, hatch, eggs, poultry KPIs │
│  Sheep module API: joining, scanning, lambing, wool, sheep pedigree│
│  Cattle module API: calving, AI/ET, dairy/beef, NLIS, cattle pedigree│
└─────────┬─────────────────────────────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────────────────────────────┐
│                      DOMAIN CORE                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Event Store (PostgreSQL) — Immutable Event Ledger        │    │
│  │ Aggregates: Animal, Breeding, Health, Production, Finance│    │
│  └──────────────────────────────────────────────────────────┘    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────┐│
│  │ GoatModule   │ │ RabbitModule │ │ PoultryModule│ │ Sheep /   ││
│  │              │ │              │ │ (flock-first)│ │ Cattle    ││
│  └──────────────┘ └──────────────┘ └──────────────┘ └───────────┘│
└─────────┬─────────────────────────────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────────────────────────────┐
│                    INTELLIGENCE LAYER                              │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────────┐    │
│  │ KPI by module│ │ Embedded AI  │ │ Farm Copilot           │    │
│  │ (5 scorecards│ │ charts+      │ │ (in-app + BYO Model    │    │
│  │  not mixed)  │ │ anomalies    │ │  API; Supabase tools)  │    │
│  │              │ │ in APK       │ │                        │    │
│  └──────────────┘ └──────────────┘ └────────────────────────┘    │
└─────────┬─────────────────────────────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────────────────────────────┐
│                      PERSISTENCE                                  │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌────────┐            │
│  │PostgreSQL│  │  Redis   │  │ MinIO  │  │Meili-  │            │
│  │(primary) │  │(cache/q) │  │(files) │  │search  │            │
│  └──────────┘  └──────────┘  └────────┘  └────────┘            │
└───────────────────────────────────────────────────────────────────┘
```

---

## 3. Service Decomposition & Boundaries

**Platform services** persist identity and events. They are not a livestock product. **Module packages** own species language, validation, and HTTP routes the feature apps call.

**Deploy shape (MVP):** Supabase (Postgres + Auth + Storage + Edge) as the hosted backend; Android APK as the field client. Bounded contexts in this table are **logical packages**, not separately deployed AI/ML hosts. Do not add Hermes/Ollama/Python ML sidecars to the product footprint.

| Service | Scope | Key events |
|---------|--------|------------|
| `identity-platform` | Shared row in `animals` + identifiers | AnimalRegistered, AnimalStatusChanged, AnimalDied, AnimalSold |
| `event-platform` | Immutable ledger + quantities | (all domain events appended here) |
| `goat-module` | Goat product | KiddingRecorded, LactationStarted, FamachaRecorded, GoatPedigreeViewed (audit) |
| `rabbit-module` | Rabbit product | KindlingRecorded, LitterCreated, NestBoxScheduled, KitIndividualized |
| `poultry-module` | Poultry product (all bird kinds) | FlockPlaced, EggsSet, HatchRecorded, DailyEggCountRecorded, FlockMortalityRecorded, HouseMoved, PoultryKindEnabled |
| `sheep-module` | Sheep product | JoiningStarted, PregnancyScanned, LambingRecorded, ShearingRecorded, CrutchingRecorded, FootrotScored |
| `cattle-module` | Cattle product | HeatDetected, AiRecorded, PregnancyChecked, CalvingRecorded, WeaningRecorded, MilkRecorded, NlisMovementRecorded |
| `pedigree-platform` | Relations only | ParentageRecorded, ParentageVerified |
| `health-platform` | Clinical records, **schedules, disease library, formulary**; forms by species | ObservationRecorded, TreatmentStarted, WithdrawalActivated, VaccinationScheduled, ProtocolPackAccepted |
| `production-platform` | Measurements | WeightRecorded, MilkRecorded (entry from goat/cattle dairy), EggCountRecorded (poultry), FleeceRecorded (sheep) |
| `location-platform` | Places; paddock vs cage vs house vs parlour is type, not a generic pen UI | AnimalMoved, GroupMoved |
| `inventory-service` | Feed/vet stock | StockReceived, StockConsumed |
| `task-engine` | Merge queues; **payloads authored by each module** | TaskGenerated, TaskCompleted |
| `finance-service` | Posting; cost object may be animal, litter, flock, mob, lot, paddock, house | ExpenseRecorded, RevenueRecorded |
| `sync-service` | Outbox | SyncCompleted |
| `audit-service` | All | (subscribe) |

Android `:feature-goat` talks to goat-module + platform. It never lists other species. Each feature module is closed over its `species_code` (poultry also over `animal_groups` of type flock). Dashboard RFID uses identity-platform lookup then **navigates to the matching feature module** (individual profile, litter profile, or flock/lot home).

---

## 4. Core Data Model (PostgreSQL Schema)

Platform tables hold rows for every species. **They are not a product surface.** Each module queries `WHERE species_code = 'goat'|'rabbit'|'poultry'|'sheep'|'cattle'`. Do not build an API `GET /animals` that the UI uses as a mixed herd. Poultry commercial UIs query **flocks** first; individuals exist when the poultry module creates them (breeders, pedigree bands).

### 4.1 Identity store (platform)

```sql
CREATE TABLE farms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    country_code CHAR(2),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    timezone TEXT DEFAULT 'UTC',
    currency CHAR(3) DEFAULT 'USD',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE species_modules (
    code TEXT PRIMARY KEY,              -- 'goat', 'rabbit', 'poultry', 'sheep', 'cattle'
    display_name TEXT NOT NULL,
    enabled BOOLEAN DEFAULT true,
    capability JSONB NOT NULL DEFAULT '{}',
    -- capability examples:
    -- goat:    pedigreeView, famacha, lactation, housing paddock
    -- rabbit:  litterFirstClass, nestBox, housing cage
    -- poultry: flockFirstClass, eggs, hatchery, housing house, kinds[]
    -- sheep:   scanning, wool, flystrike, housing paddock
    -- cattle:  dairy, beef, nlis, aiEt, housing paddock|feedlot|parlour
    created_at TIMESTAMPTZ DEFAULT now()
);

INSERT INTO species_modules (code, display_name, capability) VALUES
('goat', 'Goats', '{"pedigreeView":"full_tree","workingObject":"individual","litterFirstClass":false,"famacha":true,"lactation":true,"housing":"paddock"}'),
('rabbit', 'Rabbits', '{"pedigreeView":"compact_sibs","workingObject":"litter","litterFirstClass":true,"famacha":false,"lactation":false,"housing":"cage"}'),
('poultry', 'Poultry', '{"pedigreeView":"breeder_only","workingObject":"flock","flockFirstClass":true,"eggs":true,"hatchery":true,"lactation":false,"housing":"house"}'),
('sheep', 'Sheep', '{"pedigreeView":"full_tree","workingObject":"individual_or_mob","scanning":true,"wool":true,"flystrike":true,"famacha":true,"housing":"paddock"}'),
('cattle', 'Cattle', '{"pedigreeView":"full_tree","workingObject":"individual_or_lot","dairy":true,"beef":true,"nlis":true,"aiEt":true,"lactation":true,"housing":["paddock","feedlot","parlour"]}');

CREATE TABLE farm_enabled_modules (
    farm_id UUID NOT NULL REFERENCES farms(id),
    module_id TEXT NOT NULL,            -- species code OR shared module: 'health','finance','tasks'
    sort_order INT DEFAULT 0,
    PRIMARY KEY (farm_id, module_id)
);

-- Poultry kinds are not livestock dashboard tiles. They are catalogs inside :feature-poultry.
CREATE TABLE poultry_kinds (
    code TEXT PRIMARY KEY,              -- chicken, duck, muscovy, guinea_fowl, turkey, goose, quail, pigeon, other
    display_name TEXT NOT NULL,
    class TEXT NOT NULL CHECK (class IN ('landfowl', 'waterfowl', 'gamebird')),
    biology JSONB NOT NULL,
    language JSONB NOT NULL,
    -- biology: incubationDays, candlingDay, lockDownLeadDays, broodDays, meatTargetDays, pulletToLayDays
    -- language: female, male, young, groupNoun (flock/drove), eggLabel
    is_system BOOLEAN DEFAULT true
);

INSERT INTO poultry_kinds (code, display_name, class, biology, language) VALUES
('chicken', 'Chickens', 'landfowl',
 '{"incubationDays":21,"candlingDay":7,"lockDownLeadDays":3,"broodDays":14,"meatTargetDays":42,"pulletToLayDays":126}',
 '{"female":"hen","male":"rooster","young":"chick","groupNoun":"flock","eggLabel":"eggs"}'),
('duck', 'Ducks', 'waterfowl',
 '{"incubationDays":28,"candlingDay":10,"lockDownLeadDays":3,"broodDays":21,"meatTargetDays":56,"pulletToLayDays":140,"needsWater":true}',
 '{"female":"duck","male":"drake","young":"duckling","groupNoun":"flock","eggLabel":"eggs"}'),
('muscovy', 'Muscovy ducks', 'waterfowl',
 '{"incubationDays":36,"candlingDay":10,"lockDownLeadDays":5,"broodDays":21,"meatTargetDays":70,"pulletToLayDays":196,"needsWater":true}',
 '{"female":"duck","male":"drake","young":"duckling","groupNoun":"flock","eggLabel":"eggs"}'),
('guinea_fowl', 'Guinea fowl', 'landfowl',
 '{"incubationDays":28,"candlingDay":10,"lockDownLeadDays":3,"broodDays":21,"meatTargetDays":84,"pulletToLayDays":210,"rangeTypical":true}',
 '{"female":"hen","male":"cock","young":"keet","groupNoun":"flock","eggLabel":"eggs"}'),
('turkey', 'Turkeys', 'landfowl',
 '{"incubationDays":28,"candlingDay":10,"lockDownLeadDays":3,"broodDays":21,"meatTargetDays":126,"pulletToLayDays":210}',
 '{"female":"hen","male":"tom","young":"poult","groupNoun":"flock","eggLabel":"eggs"}'),
('goose', 'Geese', 'waterfowl',
 '{"incubationDays":30,"candlingDay":10,"lockDownLeadDays":5,"broodDays":21,"meatTargetDays":112,"pulletToLayDays":240,"needsWater":true,"seasonalLay":true}',
 '{"female":"goose","male":"gander","young":"gosling","groupNoun":"flock","eggLabel":"eggs"}'),
('quail', 'Quail', 'gamebird',
 '{"incubationDays":17,"candlingDay":7,"lockDownLeadDays":2,"broodDays":14,"meatTargetDays":42,"pulletToLayDays":42}',
 '{"female":"hen","male":"cock","young":"chick","groupNoun":"covey","eggLabel":"eggs"}'),
('pigeon', 'Pigeons / squab', 'landfowl',
 '{"incubationDays":17,"candlingDay":null,"lockDownLeadDays":2,"broodDays":28,"meatTargetDays":28,"pulletToLayDays":null,"squab":true}',
 '{"female":"hen","male":"cock","young":"squab","groupNoun":"loft","eggLabel":"eggs"}');

CREATE TABLE farm_enabled_poultry_kinds (
    farm_id UUID NOT NULL REFERENCES farms(id),
    kind_code TEXT NOT NULL REFERENCES poultry_kinds(code),
    purposes JSONB NOT NULL DEFAULT '["layer","meat","breeder"]',
    sort_order INT DEFAULT 0,
    PRIMARY KEY (farm_id, kind_code)
);

CREATE TABLE animals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    farm_tag TEXT NOT NULL,
    species_code TEXT NOT NULL REFERENCES species_modules(code),
    poultry_kind_code TEXT REFERENCES poultry_kinds(code),  -- required when species_code = 'poultry'
    breed TEXT,
    sex TEXT CHECK (sex IN ('male', 'female', 'unknown')),
    date_of_birth DATE,
    date_of_acquisition DATE,
    origin TEXT CHECK (origin IN ('born_on_farm', 'purchased', 'donated', 'transferred')),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'sold', 'dead', 'culled', 'transferred')),
    current_location_id UUID,
    photo_url TEXT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(farm_id, farm_tag),
    CONSTRAINT animals_poultry_kind_ck CHECK (
        (species_code = 'poultry' AND poultry_kind_code IS NOT NULL)
        OR (species_code <> 'poultry' AND poultry_kind_code IS NULL)
    )
);

-- Identifiers belong to animals. Flock/lot codes live on animal_groups, not here.
CREATE TABLE animal_identifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    animal_id UUID NOT NULL REFERENCES animals(id),
    type TEXT NOT NULL CHECK (type IN (
        'farm_id', 'official_id', 'rfid', 'eid', 'ear_tag', 'tattoo', 'registration', 'name',
        'wing_band', 'leg_band', 'nlis', 'nait', 'freeze_brand', 'herd_book'
    )),
    value TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    assigned_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (animal_id, type, value)
);

-- RFID/EID/NLIS lookup: one active tag value per farm (enforced in app + partial unique via animals join in 4.10)
CREATE INDEX idx_animal_identifiers_value ON animal_identifiers(value);
CREATE INDEX idx_animals_farm_species ON animals(farm_id, species_code);
CREATE INDEX idx_animals_farm_poultry_kind ON animals(farm_id, poultry_kind_code) WHERE poultry_kind_code IS NOT NULL;
CREATE INDEX idx_animals_status ON animals(farm_id, status);
```

### 4.2 Pedigree

```sql
CREATE TABLE pedigree_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id UUID NOT NULL REFERENCES animals(id),
    parent_id UUID REFERENCES animals(id),
    relation_type TEXT NOT NULL CHECK (relation_type IN ('sire', 'dam', 'genetic_dam', 'recipient_dam')),
    confidence TEXT DEFAULT 'recorded' CHECK (confidence IN ('recorded', 'verified', 'dna_confirmed')),
    verification_method TEXT,
    verified_at TIMESTAMPTZ,
    notes TEXT,
    CHECK (parent_id IS DISTINCT FROM animal_id),
    UNIQUE(animal_id, relation_type)  -- one sire, one dam, one genetic_dam, one recipient_dam
);
```

### 4.3 Events & Measurements

`animal_events` and `measurements` are **read models** projected from `domain_events`. Do not treat them as a second ledger. Timeline “delete” is a compensating event + `is_superseded` on the projection — never DELETE from `domain_events`.

Flock-grain facts (daily eggs, house mortality) go on `poultry_egg_records` / group projections, **not** as fake `measurements` rows on a dummy animal.

```sql
CREATE TABLE animal_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    animal_id UUID REFERENCES animals(id),
    event_type TEXT NOT NULL,
    event_date TIMESTAMPTZ NOT NULL,
    data JSONB NOT NULL DEFAULT '{}',
    recorded_by UUID,
    source_device TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    is_superseded BOOLEAN DEFAULT false
);

CREATE TABLE measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    animal_id UUID REFERENCES animals(id),
    type TEXT NOT NULL CHECK (type IN (
        'weight', 'bcs', 'height', 'temperature', 'famacha',
        'milk_yield', 'fat_pct', 'protein_pct', 'scc',
        'fleece_weight', 'wool_micron', 'staple_length',
        'locomotion', 'footrot_score', 'flystrike_score'
    )),
    value DECIMAL NOT NULL,
    unit TEXT,
    measured_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID,
    method TEXT,
    notes TEXT
);

CREATE INDEX idx_measurements_animal_type ON measurements(animal_id, type, measured_at DESC)
    WHERE animal_id IS NOT NULL;
```

`group_id` on timeline/measurements/health is added in 4.5 after `animal_groups` exists (migration order).

### 4.4 Breeding & Reproduction

Platform columns are species-neutral (`female_id` = dam/ewe/cow/doe/hen as the **module** labels it). Poultry commercial reproduction uses **flock + hatchery events**, not this pregnancy table.

```sql
CREATE TABLE breeding_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    female_id UUID NOT NULL REFERENCES animals(id),  -- module UI: doe / ewe / cow / hen
    sire_id UUID REFERENCES animals(id),
    event_date DATE NOT NULL,
    method TEXT CHECK (method IN ('natural', 'ai', 'et', 'hand_mating', 'pen_mating')),
    attempts INT DEFAULT 1,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE pregnancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    breeding_event_id UUID REFERENCES breeding_events(id),
    female_id UUID NOT NULL REFERENCES animals(id),
    status TEXT NOT NULL DEFAULT 'suspected' CHECK (status IN ('suspected', 'confirmed', 'failed', 'aborted', 'delivered')),
    expected_birth_date DATE,
    confirmed_at DATE,
    confirmation_method TEXT, -- ultrasound_scan, blood, rectal, observation
    fetal_count INT,
    fetal_sex_split TEXT,     -- sheep/cattle scanning: e.g. twins
    actual_birth_date DATE,
    outcome_notes TEXT
);

CREATE TABLE litters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_id UUID REFERENCES pregnancies(id),
    female_id UUID NOT NULL REFERENCES animals(id),
    sire_id UUID REFERENCES animals(id),
    species TEXT NOT NULL, -- copy of dam species_code at write; must match female’s species
    birth_date DATE NOT NULL,
    total_born INT,
    live_born INT,
    stillborn INT,
    abnormalities TEXT,
    assistance_level TEXT,
    notes TEXT
);
```

`doe_id` in older drafts is an alias of `female_id`. Goat/rabbit modules keep “doe” in copy only.

### 4.5 Groups, flocks, mobs, lots (platform)

Used as the **primary working object** by poultry (flock/placement) and as optional grouping by sheep (mob) and cattle (mob/lot). Rabbit **litters** stay on `litters`. Never expose this table as a mixed-species “groups” home.

```sql
CREATE TABLE animal_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    species_code TEXT NOT NULL REFERENCES species_modules(code),
    poultry_kind_code TEXT REFERENCES poultry_kinds(code),
    type TEXT NOT NULL CHECK (type IN ('flock', 'mob', 'lot', 'pen_group', 'hatch', 'placement')),
    name TEXT NOT NULL,
    code TEXT, -- flock/lot code for QR/search (not animal_identifiers)
    purpose TEXT,
    current_location_id UUID,
    parent_group_id UUID REFERENCES animal_groups(id), -- hatch → placement; split mobs
    placed_at DATE,
    closed_at DATE,
    -- Census vs roster (exactly one SoR per group):
    -- census: commercial poultry — head_count maintained by placements/mortality/culls; memberships empty
    -- roster: stud/breeders/mobs of tagged animals — head_count is derived from memberships
    accounting TEXT NOT NULL DEFAULT 'census' CHECK (accounting IN ('census', 'roster')),
    head_count INT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT groups_poultry_kind_ck CHECK (
        (species_code = 'poultry' AND poultry_kind_code IS NOT NULL)
        OR (species_code <> 'poultry' AND poultry_kind_code IS NULL)
    )
);

ALTER TABLE animal_events
    ADD COLUMN group_id UUID REFERENCES animal_groups(id),
    ADD CONSTRAINT animal_events_subject_ck CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL);

ALTER TABLE measurements
    ADD COLUMN group_id UUID REFERENCES animal_groups(id),
    ADD CONSTRAINT measurements_subject_ck CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL);

CREATE TABLE animal_group_memberships (
    group_id UUID NOT NULL REFERENCES animal_groups(id),
    animal_id UUID NOT NULL REFERENCES animals(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    PRIMARY KEY (group_id, animal_id, joined_at)
);

CREATE TABLE poultry_egg_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flock_id UUID NOT NULL REFERENCES animal_groups(id),
    record_date DATE NOT NULL,
    eggs_collected INT NOT NULL,
    eggs_set INT,
    eggs_hatched INT,
    cracked INT,
    double_yolk INT,
    feed_kg DECIMAL,
    mortality INT,
    culls INT,
    UNIQUE (flock_id, record_date)
);

CREATE TABLE wool_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    event_date DATE NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('shearing', 'crutching', 'classing')),
    fleece_weight_kg DECIMAL,
    micron DECIMAL,
    staple_length_mm DECIMAL,
    notes TEXT,
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);
```

### 4.6 Health & Treatment

```sql
CREATE TABLE health_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    event_type TEXT NOT NULL CHECK (event_type IN (
        'observation', 'diagnosis', 'vaccination', 'treatment', 'lab_result', 'vet_visit', 'injury',
        'flock_vaccination', 'biosecurity', 'footrot', 'flystrike', 'mastitis'
    )),
    event_date TIMESTAMPTZ NOT NULL,
    description TEXT,
    severity TEXT CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    diagnosis TEXT,
    outcome TEXT,
    recorded_by UUID,
    data JSONB DEFAULT '{}',
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);

CREATE TABLE treatments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    health_event_id UUID REFERENCES health_events(id),
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    product_name TEXT NOT NULL,
    active_ingredient TEXT,
    dose DECIMAL,
    dose_unit TEXT,
    route TEXT,
    batch_number TEXT,
    start_date DATE NOT NULL,
    end_date DATE,
    meat_withdrawal_days INT,
    milk_withdrawal_days INT,
    egg_withdrawal_days INT,
    meat_withdrawal_until DATE,
    milk_withdrawal_until DATE,
    egg_withdrawal_until DATE,
    administered_by UUID,
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);
```

### 4.7 Locations & Movement

```sql
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    parent_id UUID REFERENCES locations(id),
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN (
        'farm', 'building', 'barn', 'paddock', 'pen', 'cage', 'quarantine', 'isolation',
        'poultry_house', 'coop', 'range', 'hatchery', 'brooder', 'pond', 'swim', 'loft',
        'shearing_shed', 'feedlot', 'milking_parlour', 'calving_paddock', 'lambing_paddock'
    )),
    capacity INT,
    area_sqm DECIMAL,
    qr_code TEXT,
    metadata JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT true
);

ALTER TABLE animals
    ADD CONSTRAINT animals_current_location_fk
    FOREIGN KEY (current_location_id) REFERENCES locations(id);
ALTER TABLE animal_groups
    ADD CONSTRAINT groups_current_location_fk
    FOREIGN KEY (current_location_id) REFERENCES locations(id);

CREATE TABLE movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    from_location_id UUID REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    moved_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    moved_by UUID,
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);
```

### 4.8 Inventory & Finance

```sql
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    category TEXT NOT NULL CHECK (category IN ('feed', 'veterinary', 'farm_supplies')),
    name TEXT NOT NULL,
    unit TEXT,
    current_quantity DECIMAL DEFAULT 0,
    min_stock_level DECIMAL,
    reorder_level DECIMAL,
    cost_per_unit DECIMAL
);

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    type TEXT NOT NULL CHECK (type IN ('purchase', 'consumption', 'adjustment', 'disposal')),
    quantity DECIMAL NOT NULL,
    unit_cost DECIMAL,
    batch_number TEXT,
    expiry_date DATE,
    supplier TEXT,
    allocated_to_animal UUID REFERENCES animals(id),
    allocated_to_group UUID REFERENCES animal_groups(id),
    transaction_date TIMESTAMPTZ DEFAULT now(),
    recorded_by UUID
);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    type TEXT NOT NULL CHECK (type IN ('income', 'expense')),
    category TEXT NOT NULL,
    amount DECIMAL NOT NULL,
    currency CHAR(3),
    description TEXT,
    animal_id UUID REFERENCES animals(id),
    litter_id UUID REFERENCES litters(id),
    group_id UUID REFERENCES animal_groups(id),
    related_entity_type TEXT,
    related_entity_id UUID,
    transaction_date DATE NOT NULL,
    recorded_by UUID
);
```

### 4.9 Tasks

```sql
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    module_id TEXT NOT NULL, -- goat|rabbit|poultry|sheep|cattle|health|tasks|...
    title TEXT NOT NULL,
    description TEXT,
    priority INT DEFAULT 2 CHECK (priority BETWEEN 1 AND 5),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'assigned', 'in_progress', 'completed', 'cancelled', 'overdue')),
    trigger_event_type TEXT,
    trigger_event_id UUID,
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    location_id UUID REFERENCES locations(id),
    assigned_to UUID,
    due_date TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    completion_notes TEXT,
    recurrence_rule TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);
```

### 4.10 Users, idempotency, RLS (required for the schema to be operable)

```sql
CREATE TABLE farm_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    keycloak_sub TEXT NOT NULL,
    display_name TEXT,
    role TEXT NOT NULL, -- owner, farm_manager, breeding, vet, worker, finance, buyer
    enabled_modules TEXT[] NOT NULL DEFAULT '{}',
    UNIQUE (farm_id, keycloak_sub)
);

CREATE UNIQUE INDEX uq_active_scan_id_per_farm
    ON animal_identifiers (farm_id, value)
    WHERE is_active AND type IN ('rfid', 'eid', 'nlis', 'nait');

-- Example RLS (repeat for every farm-scoped table). Enable after domain_events exists (section 5).
-- ALTER TABLE animals ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY animals_farm_isolation ON animals
--     USING (farm_id = current_setting('app.farm_id')::uuid);
```

Ktor sets `SET LOCAL app.farm_id = '...'` in the request transaction **before** any query.

### 4.11 Analytics anomalies & recommendations

Projections for the embedded AI module (§12a). Detected on-device or via Model API; not the event ledger. Accepting a recommendation may create a `tasks` row or a Health draft — biology mutations still go through normal commands → `domain_events`.

```sql
CREATE TABLE analytics_anomalies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    feature_code TEXT NOT NULL, -- growth, milk, eggs, fcr, famacha, scc, ...
    polarity TEXT NOT NULL CHECK (polarity IN ('negative', 'positive', 'watch')),
    severity TEXT NOT NULL CHECK (severity IN ('info', 'warning', 'critical')),
    species_code TEXT NOT NULL,
    poultry_kind_code TEXT,
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    title TEXT NOT NULL,
    detail TEXT,
    evidence JSONB NOT NULL DEFAULT '{}',
    lane TEXT NOT NULL CHECK (lane IN ('rules', 'on_device', 'model_api')),
    status TEXT NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'acknowledged', 'resolved', 'dismissed')),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);

CREATE TABLE analytics_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    anomaly_id UUID NOT NULL REFERENCES analytics_anomalies(id),
    action_code TEXT NOT NULL,
    title TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'proposed'
        CHECK (status IN ('proposed', 'accepted', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Non-secret AI defaults may live on farms.config JSONB
-- (analytics_endpoint template, model_id, mode). API keys stay on-device (Keystore).
```

---

## 5. Event Sourcing Implementation

### Approach: PostgreSQL event store in Kotlin (Ktor)

Stay on Postgres. Do not add Kafka or EventStoreDB for MVP. Append domain events in one table; project into the read models in Section 4 inside the same transaction (or via an outbox worker).

**Why not Alvyn:** Alvyn is Node/TypeScript. The Android-only stack is Kotlin end-to-end.

**Optional later:** [Axon Framework](https://github.com/AxonFramework/AxonFramework) if aggregates and sagas outgrow a thin store.

```sql
CREATE TABLE domain_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    stream_id UUID NOT NULL,
    stream_type TEXT NOT NULL, -- animal, group, breeding, health, finance, farm
    version INT NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}', -- user_id, device_id, species_code
    mutation_id UUID,
    recorded_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (stream_id, version)
);

CREATE UNIQUE INDEX uq_domain_events_farm_mutation
    ON domain_events(farm_id, mutation_id)
    WHERE mutation_id IS NOT NULL;
CREATE INDEX idx_domain_events_farm_time ON domain_events(farm_id, recorded_at, id);
```

`mutation_id` on the row is the client idempotency key (also echoed in metadata for audit). Append **once** per command; project into section 4 tables in the **same transaction** (or transactional outbox → projector). The Android app never replays `domain_events`.

### Aggregates (do not dump flock events onto Animal)

| Stream type | Owns | Example events |
|-------------|------|----------------|
| `animal` | One tagged/identified animal | Registered, Moved, WeightRecorded, MatingRecorded, Kidding/Lambing/Calving, Sold, Died |
| `group` | Flock/mob/lot/hatch | FlockPlaced, HeadCountAdjusted, GroupMoved, EggsSet, HatchRecorded, DailySheetRecorded |
| `breeding` | Mating/joining/AI cycle | optional if kept on animal/group |

```kotlin
sealed interface AnimalEvent {
    data class Registered(
        val farmId: String, val farmTag: String, val species: Species,
        val poultryKind: String?, val breed: String?, val sex: Sex, val dateOfBirth: LocalDate?,
    ) : AnimalEvent
    data class Moved(val fromLocationId: String?, val toLocationId: String, val reason: String?) : AnimalEvent
    data class WeightRecorded(val value: Double, val unit: String, val measuredAt: Instant) : AnimalEvent
    data class MatingRecorded(val sireId: String, val method: MatingMethod, val eventDate: LocalDate) : AnimalEvent
    data class PregnancyConfirmed(val expectedBirthDate: LocalDate, val fetalCount: Int?) : AnimalEvent
    data class KiddingRecorded(val litterId: String, val liveBorn: Int, val stillborn: Int) : AnimalEvent
    data class LambingRecorded(val litterId: String, val liveBorn: Int, val stillborn: Int) : AnimalEvent
    data class CalvingRecorded(val calfIds: List<String>, val assistance: String?) : AnimalEvent
    data class Sold(val buyerId: String?, val salePrice: BigDecimal, val saleDate: LocalDate) : AnimalEvent
    data class Died(val reason: String, val date: LocalDate, val salvageValue: BigDecimal?) : AnimalEvent
}

sealed interface GroupEvent {
    data class FlockPlaced(val kindCode: String, val purpose: String, val headCount: Int, val locationId: String) : GroupEvent
    data class EggsSet(val kindCode: String, val eggsSet: Int, val setDate: LocalDate) : GroupEvent
    data class HatchRecorded(val eggsSet: Int, val hatched: Int, val placementGroupId: String?) : GroupEvent
    data class DailySheetRecorded(val date: LocalDate, val eggs: Int?, val mortality: Int, val feedKg: Double?) : GroupEvent
    data class HeadCountAdjusted(val delta: Int, val reason: String) : GroupEvent
    data class GroupMoved(val toLocationId: String) : GroupEvent
}
```

data class AnimalState(
    val id: String = "",
    val farmId: String = "",
    val species: Species? = null,
    val status: AnimalStatus = AnimalStatus.ACTIVE,
    val currentLocationId: String? = null,
    val lastWeight: Double? = null,
    val reproductiveState: ReproductiveState? = null,
    val expectedBirthDate: LocalDate? = null,
)

fun AnimalState.evolve(event: AnimalEvent): AnimalState = when (event) {
    is AnimalEvent.Registered -> copy(farmId = event.farmId, species = event.species, status = AnimalStatus.ACTIVE)
    is AnimalEvent.Moved -> copy(currentLocationId = event.toLocationId)
    is AnimalEvent.WeightRecorded -> copy(lastWeight = event.value)
    is AnimalEvent.PregnancyConfirmed -> copy(
        reproductiveState = ReproductiveState.PREGNANT,
        expectedBirthDate = event.expectedBirthDate,
    )
    is AnimalEvent.KiddingRecorded, is AnimalEvent.LambingRecorded, is AnimalEvent.CalvingRecorded ->
        copy(reproductiveState = ReproductiveState.POST_PARTUM, expectedBirthDate = null)
    is AnimalEvent.Sold -> copy(status = AnimalStatus.SOLD)
    is AnimalEvent.Died -> copy(status = AnimalStatus.DEAD)
    else -> this
}
```

The Android app does **not** replay the full event store. It writes commands into a local outbox; the server appends events and returns updated read models. The animal timeline screen is a query of `animal_events` / projections.

---

## 6. Offline-First & Sync Architecture

### Primary: Room + outbox + WorkManager

```
┌─────────────────────────────┐
│  Jetpack Compose app        │
│  ┌───────────────────────┐  │         ┌──────────────────────┐
│  │ Room (SQLite)         │◄─┼────────►│  Ktor /sync          │
│  │ - local source of     │  │  HTTP   │  POST mutations      │
│  │   truth for UI        │  │         │  GET changes since   │
│  │ - Flow observers      │  │         │  conflict rules      │
│  └───────────────────────┘  │         └──────────┬───────────┘
│  ┌───────────────────────┐  │                    │
│  │ Outbox table          │  │                    ▼
│  │ WorkManager sync      │  │         PostgreSQL + MinIO
│  │ Photo upload queue    │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

### Sync protocol

1. **Write locally first.** Every field action inserts into Room and `sync_outbox` with a UUID **mutation_id** (idempotency key).
2. **Push:** `POST /sync/mutations` with `{ mutationId, farmId, type, payload }`. Server appends `domain_events` if `mutation_id` is new; otherwise returns the existing projection (idempotent). Optimistic concurrency: `stream_id` + expected `version`.
3. **Pull:** `GET /sync?farm_id={jwt}&after_ts={recorded_at}&after_id={id}` returns a **page** of projected rows (animals, groups, events, …) whose originating event is after the cursor. Cursor is `(recorded_at, id)` on `domain_events`, not a wall clock on the phone. Device timezone must not define order.
4. **Conflicts (domain rules, not last-write-wins):**
   - Weights: latest `measuredAt` (device clock, clamped to ±24h of server receive) wins
   - Animal/group status (sold/dead/closed): server wins
   - Health events: merge both; flag for review
   - Flock daily sheet: unique `(flock_id, record_date)` — second writer updates if payload differs and is flagged
   - Finance: server wins; notify device
5. **Photos:** WorkManager unique work + resumable MinIO multipart; object key includes `farm_id`
6. **Auth:** JWT `farm_id` is the only farm in the request. Workers’ pull is filtered by granted `species_code`s.
7. **Triggers:** app foreground, `ConnectivityManager` callback, manual refresh, periodic 15 min when online

Encrypt the local DB with SQLCipher if devices leave the farm.

### Room entities (Android)

Platform identity table in Room (`animals` includes `speciesCode`). Feature modules query by species. Do not bind a mixed animal list to this table.

```kotlin
@Entity(tableName = "animals")
data class AnimalEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val farmTag: String,
    val speciesCode: String,
    val poultryKindCode: String?, // required for poultry
    val breed: String?,
    val sex: String,
    val dateOfBirthEpochDay: Long?,
    val status: String,
    val currentLocationId: String?,
    val updatedAt: Long,
)

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val mutationId: String,
    val farmId: String,
    val speciesCode: String?,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
```

### Alternative: Synchro Kotlin client

[trainstar/synchro](https://github.com/trainstar/synchro) has a **first-class Kotlin** engine (not a React Native bridge). Use it if the custom outbox becomes a bottleneck. PostgreSQL WAL, local SQLite, configurable conflicts.
---

## 7. Species modules (designed per animal)

**Product rule:** Each animal module is a **purpose-built app for that species**, not a generic livestock screen with toggles. Goats get a goat product. Rabbits get a rabbit product. Poultry, sheep, and cattle each get their own product — not “goats with a different gestation number,” not a flock filter on rabbits, and not a cattle clone of the sheep lambing board.

**Platform rule:** Identity, event ledger, quantities, groups, sync, users, and finance *posting* are shared infrastructure. They do not dictate labels or workflows. A module may reuse a Compose *component* (e.g. an interactive family-tree widget) only when that component is the right method **for that animal**. Reuse is optional; copying one species’ IA into another is not allowed.

### What “designed for the target animal” means

| Layer | Goat | Rabbit | Poultry | Sheep | Cattle |
|-------|------|--------|---------|-------|--------|
| Language | Doe, buck, kid, kidding, lactation, paddock | Doe, buck, kit, kindling, nest box, cage, litter | **Kind pack:** hen/rooster/chick; duck/drake/duckling; guinea hen/cock/keet; tom/poult; goose/gander/gosling — plus flock, house, hatch, lay | Ewe, ram, lamb, joining, scanning, lambing, mob, wool | Cow, bull, heifer, calf, joining/AI, PD, calving, herd, lot, parlour |
| Home inside the module | Herd, kidding board, FAMACHA, milk if dairy | Colony, kindling board, nest-box due, litter weights | **Kind switcher** + house/flock board; chicken vs duck vs guinea (etc.) queues stay in poultry | Flock/mob, joining calendar, scan results, lambing paddock, shearing if wool | Herd/mob, PD board, calving, dairy parlour **or** beef weaning (capability) |
| Working object | Individual kids at birth | Litter first; individuals when breeding/sale requires it | **Flock/placement first**; individuals for breeders / pedigree bands only | Individual for stud; **mob** for commercial mustering | Individual for seedstock/dairy; **lot/mob** for feedlot/commercial beef |
| Pedigree | Full family tree as primary | Compact sib/litter as primary; full tree for a selected breeder | Compact / none for commercial; full tree **inside poultry** for selected breeders | Full tree for stud; lambing groups if the sheep product needs them | Full tree for seedstock; simpler dam-sire on commercial |
| Places | Paddock / barn / pen, grazing rest | Cage map, QR on cage | House, coop, range, hatchery, brooder; **pond/swim for waterfowl**; loft for pigeons | Paddock, lambing paddock, shearing shed | Paddock, calving paddock, feedlot, milking parlour |
| Health emphasis | Parasites, FAMACHA, milk withdrawal | Pre-wean mortality, doe condition, colony disease | Flock vaccination calendar, mortality/cull %, salmonella/biosecurity | Worms, footrot, flystrike, pregnancy toxaemia, scanning | Mastitis/metabolic (dairy), BVD/IBR, lameness, calf scours |
| Reproduction model | Mating → ~150d → kidding | Mating → nest box → kindling 31–33d | Set eggs → incubation **by kind** (chicken 21d, duck 28d, guinea 28d, turkey 28d, goose ~30d, quail 17d, muscovy 35d) → hatch → brood → lay or grow-out | Joining → scan (~d 40–90) → lambing ~147d | Heat/AI/ET → PD → calving ~283d |
| KPIs | Kidding interval, kid survival, milk, ADG | Kits/doe/year, FCR, days to market, litter size | Eggs/hen housed, peak %, hatchability, FCR, livability, days to market | Lambing %, scanning %, lambs/ewe, weaning %, wool kg/micron | Calving interval, conception %, weaning weight, milk (dairy), ADG, days on feed |

A mixed farm opens **Goats** and works only in goat terms until they leave to the dashboard and open another species tile. Shared **Health** / **Finance** tiles still filter by species and use that species’ forms when an animal or flock is in context.

**Poultry is not a mammal with extra fields.** Do not reuse pregnancy, FAMACHA, or lactation screens. **Chicken is not the only poultry kind.** Ducks, guinea fowl, turkeys, geese, quail (and farm-defined kinds) share the poultry module but get **kind biology + copy + housing**, not a “chicken flock with a duck label.” **Sheep is not a small goat.** Scanning, flystrike, wool, and mob mustering are sheep product surfaces. **Cattle is not a large sheep.** NLIS/traceability, AI/ET, dairy parlour, and feedlot lots are cattle product surfaces. Dairy vs beef is a **cattle capability pack**; poultry kinds are a **poultry kind catalog** — neither is a second dashboard species.

### Hybrid method (same toolkit, different product)

- If a **family tree** is right for goats, sheep stud, or cattle seedstock, that module **implements it** as a first-class screen.
- If a **compact litter pedigree** is right for rabbits (or poultry breeders), that module **implements that**.
- If a **flock daily sheet** is right for poultry, the poultry module implements it; goats never see it.
- Both widgets may live in a shared UI kit. Each module chooses **its** primary method. Neither species is denied a feature because another cannot use it the same way.

### SpeciesModule contract (`:shared` + dedicated UI package)

Each animal is a Gradle feature module: `:feature-goat`, `:feature-rabbit`, `:feature-poultry`, `:feature-sheep`, `:feature-cattle`.

**Package split (required for KMP later and for Ktor reuse):**

| Module | Contents | Android APIs? |
|--------|----------|---------------|
| `:domain` | `SpeciesLifecycleEngine`, DTOs, validation, KPI formulas | No |
| `:feature-*` | `navGraph()`, Compose screens, `SpeciesModule` UI binding | Yes |
| `:app` | Dashboard, RFID router, Hilt, WorkManager | Yes |
| `:server` | Same `:domain` JAR; HTTP; projections | No |

`SpeciesModule.navGraph()` must **not** live in `:domain`. Engines in `:domain` are called by Ktor and by feature ViewModels.

```kotlin
interface SpeciesModule {
    val code: String
    val displayName: String
    /** Biology, tasks, validation — not UI. */
    val lifecycle: SpeciesLifecycleEngine
    /** Which screens this module owns (Compose graph). */
    fun navGraph(): SpeciesNavGraph
    /** Module-home Today board: species-specific, not a generic task list. */
    fun moduleHome(): ModuleHomeSpec
    val kpiScorecard: KpiScorecard
    val protocolPackId: String
}
```

The shell (`DashboardScreen`) only launches `module.navGraph()`. It does not render a shared “AnimalList” as the species experience. Shared lists are primitives the **module** embeds if they fit.

### 7.1 Goat module (GoatLifecycleEngine)

```kotlin
class GoatLifecycleEngine(
    private val averageGestationDays: Long = 150,
    private val lateGestationStartDay: Long = 108,
    private val pregnancyConfirmationDueDay: Long = 45,
) {
    fun onMatingRecorded(event: MatingRecorded): List<TaskDraft> {
        val expectedKidding = event.date.plusDays(averageGestationDays)
        return listOf(
            TaskDraft("Pregnancy check", event.date.plusDays(pregnancyConfirmationDueDay), event.doeId),
            TaskDraft("Late-gestation nutrition review", event.date.plusDays(lateGestationStartDay), event.doeId),
            TaskDraft("Pre-kidding preparation", expectedKidding.minusDays(7), event.doeId),
            TaskDraft("Expected kidding", expectedKidding, event.doeId, priority = 1),
        )
    }
}
```

Engines live in `:domain` (pure Kotlin, no Android). Ktor and the app both call them. Config (gestation days, nest-box day) is farm-level, not hardcoded.

### 7.2 Rabbit module (RabbitLifecycleEngine)

Dedicated rabbit product: colony/cage map, mating → nest box → kindling → **litter** as the working object → weaning / grow-out / selection. Kindling language, litter KPIs, and compact pedigree are designed here — not adapted from the goat kidding form.

**Breeding programme (full MVP):** See `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md` and `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md`. **All** programme features in that spec ship in rabbit MVP (schedule, nest boxes, cage pairing, COI, palpation, foster, scorecards, colour genetics, market planner, waitlist/sales, photos, QR cards, ICS, multi-breed packs) — no deferred “later” bucket for those items.

| Capability | Behaviour |
|------------|-----------|
| Purpose pack | Default `kudbat_semi_intensive_excel`; multi-breed pack bindings supported |
| Cage unit | 11 does + 1 buck; buck mates **2/day** then **rest 1 day**; buck borrow OK |
| Startup | **3 cages**, each with **dedicated 11 named nest boxes** (1:11) |
| Expansion | **Cage pairing** → shared pool **2:11** with concurrency simulator |
| Nest boxes | Named + QR + status + **sanitation log**; assigned per mating |
| Notifications | Full task set (palpate, nest, kindling, sanitize, rebreed, wean, weights, market, foster check) |
| Pairings | COI + relationship gate + **colour prediction** |
| Litters | Kindling, foster (3-day window), photo journal, kit promote, retention |
| Commercial | Market/butcher planner, sales waitlist + contracts, cage/box PDF cards, ICS export |
| Scorecards | Doe + buck + programme KPIs |
| Working object | Litter first; kits individualized when kept as breeders |

Default clinical config remains farm-overridable; Excel pack drives the task calendar for this programme.

### 7.3 Poultry module (PoultryLifecycleEngine)

Dedicated poultry product. **One dashboard tile. Many bird kinds.** Chickens, ducks, guinea fowl, turkeys, geese, quail, pigeons/squab, and farm-defined kinds are managed **inside** `:feature-poultry`. Do not add a Ducks or Guineas tile next to Goats. Do not store ducks as `species_code = 'goat'` or as rabbits.

**Flock and house are the default working objects** (per kind). Individual `animals` rows are for breeding stock, show birds, and any bird that needs a wing/leg band. A house of 5,000 meat chickens is not 5,000 goat-style profiles. A duck pond flock is still a flock, not a mammal pregnancy.

Two independent axes on every poultry flock:

| Axis | Values | Rule |
|------|--------|------|
| **Kind** (`poultry_kind_code`) | `chicken`, `duck`, `muscovy`, `guinea_fowl`, `turkey`, `goose`, `quail`, `pigeon`, `other` (+ farm-defined row in `poultry_kinds`) | Biology, language, housing, vaccine pack, KPI cohort |
| **Purpose** | `layer`, `meat`, `breeder`, `dual`, `hatchery`, `squab` | Tasks and scorecard (eggs vs FCR vs hatch) |

A farm **enables kinds** in `farm_enabled_poultry_kinds` (e.g. chickens + guinea fowl only). The poultry home shows a kind filter/chips; Today and KPIs never mix chicken ADG with duck FCR or guinea keet mortality with broiler chicks.

**Shipped kind catalog (defaults; farm-overridable days):**

| Kind | Class | Incubation | Young | Housing extras | Notes |
|------|-------|------------|-------|----------------|-------|
| Chicken | Landfowl | 21d | Chick | House / coop / range | Default layer/meat/breeder packs |
| Duck | Waterfowl | 28d | Duckling | Pond / swim + dry shelter | Higher humidity incubation; wet litter |
| Muscovy | Waterfowl | 35d | Duckling | Pond / swim | Longer hatch than common ducks |
| Guinea fowl | Landfowl | 28d | Keet | Range typical; broody | Keet heat critical; different vax pack |
| Turkey | Landfowl | 28d | Poult | House / range | Slower meat target; separate vax |
| Goose | Waterfowl | ~28–32d | Gosling | Pond / pasture | Often seasonal lay |
| Quail | Gamebird | 16–18d | Chick | Battery/colony cages or pens | Fast lay; small-egg counts |
| Pigeon | Landfowl | ~18d | Squab | Loft | Pair/loft object may sit beside flock |

Farms may INSERT a custom kind (`other` or `peacock`, etc.) with biology JSON. New **livestock** (sheep, cattle) still get their own feature modules; a new **bird** is a poultry kind unless biology is not avian hatch/flock (then it is a new species module).

```kotlin
data class PoultryKindBiology(
    val incubationDays: Long,
    val candlingDay: Long?,
    val lockDownLeadDays: Long,
    val broodDays: Long,
    val meatTargetDays: Long?,
    val pulletToLayDays: Long?,
    val needsWater: Boolean = false,
)

class PoultryLifecycleEngine(
    private val kinds: Map<String, PoultryKindBiology>,
) {
    fun onFlockPlaced(event: FlockPlaced): List<TaskDraft> {
        val bio = kinds.getValue(event.kindCode)
        val tasks = mutableListOf(
            TaskDraft("Placement inspection / biosecurity", event.date, event.flockId, priority = 1),
            TaskDraft("Starter vaccination check (${event.kindCode})", event.date.plusDays(1), event.flockId),
        )
        if (bio.needsWater) {
            tasks += TaskDraft("Water access / swim check", event.date, event.flockId)
        }
        when (event.purpose) {
            "meat" -> bio.meatTargetDays?.let {
                tasks += TaskDraft("Target catch / processing", event.date.plusDays(it), event.flockId, priority = 1)
            }
            "layer", "dual" -> bio.pulletToLayDays?.let {
                tasks += TaskDraft("Light program / first egg watch", event.date.plusDays(it), event.flockId)
            }
            "breeder" -> tasks += TaskDraft("Peak production review", event.date.plusDays(210), event.flockId)
            "squab" -> tasks += TaskDraft("Squab harvest window", event.date.plusDays(bio.meatTargetDays ?: 28), event.flockId)
        }
        return tasks
    }

    fun onEggsSet(event: EggsSet): List<TaskDraft> {
        val bio = kinds.getValue(event.kindCode)
        val hatch = event.date.plusDays(bio.incubationDays)
        val young = event.language.young // chick / duckling / keet / poult / gosling / squab
        return listOfNotNull(
            bio.candlingDay?.let { TaskDraft("Candling", event.date.plusDays(it), event.hatchId) },
            TaskDraft("Transfer / lock-down", hatch.minusDays(bio.lockDownLeadDays), event.hatchId)
                .takeIf { bio.lockDownLeadDays > 0 },
            TaskDraft("Expected hatch", hatch, event.hatchId, priority = 1),
            TaskDraft("$young processing / placement", hatch, event.hatchId, priority = 1),
        )
    }
}
```

Hatchery setters **must** choose kind (and purpose). Do not default every set of eggs to chicken 21-day incubation.

**Poultry screens (owned by this module):** kind-aware house/pond/loft map + QR; daily flock sheet (eggs, feed kg, mortality, culls, water); **vaccination calendar by kind + flock**; hatchery set/candling/hatch with kind incubation; grow-out FCR; layer HDEP / eggs per female housed (copy: hen vs duck vs guinea). No kidding board. No FAMACHA. No milk withdrawal unless a farm also has goats/cattle (those stay in those modules).

**Cohorts:** same `species_code = poultry` **and** same `poultry_kind_code` **and** same purpose. Never overlay chicken broilers with duck grow-out as one growth chart.

**Identifiers:** wing band, leg band, flock code; loft/pair ID for pigeons. RFID is uncommon; QR on house, crate, and pond gate is the field default.

**Adding a kind the farm does not ship yet:** insert `poultry_kinds` + enable on the farm + protocol pack (vax, incubation). Do not clone `:feature-goat`. Do not ship an empty “Ducks” dashboard species.

### 7.4 Sheep module (SheepLifecycleEngine)

Dedicated sheep product. Gestation ~147 days is **config**, not a reason to clone goats. Scanning (fetal count / multiples) is a first-class sheep event. Wool farms get shearing/crutching/classing; meat-only farms hide wool via capability.

```kotlin
class SheepLifecycleEngine(
    private val averageGestationDays: Long = 147,
        private val scanningDay: Long = 70,             // ICAR ~d 70 (60–90)
    private val preLambingDay: Long = 140,
    private val markingOffsetDays: Long = 14,       // after lambing start
) {
    fun onJoiningRecorded(event: JoiningRecorded): List<TaskDraft> {
        val expected = event.date.plusDays(averageGestationDays)
        return listOf(
            TaskDraft("Ram out / joining end", event.joiningEndDate, event.mobId),
            TaskDraft("Pregnancy scanning", event.date.plusDays(scanningDay), event.mobId, priority = 1),
            TaskDraft("Pre-lambing vaccination / nutrition", expected.minusDays(averageGestationDays - preLambingDay), event.mobId),
            TaskDraft("Lambing paddock set-up", expected.minusDays(7), event.mobId, priority = 1),
            TaskDraft("Expected lambing start", expected, event.mobId, priority = 1),
        )
    }
}
```

**Sheep screens:** mob/muster list; joining calendar (rains / ram harness optional); **scan results board** (dry / single / twin / triplet → feed and lambing paddock); lambing; marking/tailing; weaning; FAMACHA **or** dag/footrot scores as sheep health forms; shearing shed workflow when `wool: true`. Pedigree: full tree for stud; commercial farms live in mob + lambing %.

Do not reuse goat kidding copy (“doe”, “kidding interval”) or rabbit nest boxes.

### 7.5 Cattle module (CattleLifecycleEngine)

Dedicated cattle product. Gestation ~283 days. **Dairy** and **beef** are capability packs in the same module: dairy enables parlour, milk recording, SCC, lactation curves, milk withdrawal; beef enables weaning weights, lots, days on feed. A farm may enable both.

```kotlin
class CattleLifecycleEngine(
        private val averageGestationDays: Long = 280,
        private val pregnancyCheckDay: Long = 32,       // ultrasound/PAG often ~28–32; palpation later; farm config
    private val dryOffLeadDays: Long = 60,          // dairy
    private val weaningDays: Long = 205,            // beef approx; farm config
) {
    fun onAiRecorded(event: AiRecorded): List<TaskDraft> {
        val expected = event.date.plusDays(averageGestationDays)
        return listOf(
            TaskDraft("Pregnancy diagnosis (PD)", event.date.plusDays(pregnancyCheckDay), event.cowId, priority = 1),
            TaskDraft("Expected calving", expected, event.cowId, priority = 1),
            TaskDraft("Calving paddock / close-up pen", expected.minusDays(21), event.cowId),
        )
    }

    fun onCalvingRecorded(event: CalvingRecorded): List<TaskDraft> {
        val tasks = mutableListOf(
            TaskDraft("Calf ID / NLIS tag", event.date, event.calfId, priority = 1),
            TaskDraft("Dam post-calving check", event.date.plusDays(1), event.cowId),
        )
        if (event.dairyEnabled) {
            tasks += TaskDraft("Peak / 60-day milk review", event.date.plusDays(60), event.cowId)
            // Dry-off is owned by the *next* expected calving (or a lactation record), not calving+223.
        } else {
            tasks += TaskDraft("Expected weaning", event.date.plusDays(weaningDays), event.calfId)
        }
        return tasks
    }
}
```

**Cattle screens:** herd/mob; heat/AI/ET board; PD board; calving; NLIS/EID movements (jurisdiction pack: NLIS AU, NAIT NZ, or generic official ID); dairy parlour session + milk meters; beef lot / feedlot close-out. Pedigree full tree for seedstock. Health: mastitis and milk withdrawal on dairy; calf protocols on both.

Do not reuse sheep scanning as cattle PD, or goat FAMACHA as the cattle home health widget (BCS + locomotion instead unless the farm also runs sheep/goats).

---

## 8. Genetics calculations & per-module pedigree

PyAGH is a **platform calculator**. Pedigree **screens and breeding plans live in each species module**. Do not ship one Genetics app that lists mixed species. Poultry commercial flocks skip pedigree UI entirely.

### Calculation Libraries

| Library | Language | Capabilities | Repo |
|---------|----------|-------------|------|
| **PyAGH** | Python | Inbreeding coefficients, A/G/H matrices, pedigree sorting, error detection, visualization | [zhaow-01/PyAGH](https://github.com/zhaow-01/PyAGH) |
| **ribd** (pedsuite) | R | Kinship, inbreeding, IBD coefficients, X-linked, inbred founders | [magnusdv/ribd](https://github.com/magnusdv/ribd) |
| **visPedigree** | R | C++ high-performance inbreeding, relationship matrices, pedigree visualization, scales to 1M+ animals | [luansheng/visPedigree](https://github.com/luansheng/visPedigree) |

### Pedigree visualization (per-module method)

The pedigree **store** is shared (sire/dam, confidence, DNA). The pedigree **product** is owned by the species module:

- **Goat module:** interactive family tree and descendant tree are the default pedigree experience (3–6+ generations). Required for goats.
- **Rabbit module:** compact sib/litter pedigree is the default. A selected breeding rabbit may open a full tree *inside the rabbit module* when identity exists.
- **Poultry module:** no pedigree home for commercial flocks. Breeder birds **of that kind**: compact sib view; selected birds open a full tree *inside poultry*. Do not mix chicken and duck pedigrees. Hatch cohorts are not pedigrees.
- **Sheep module:** full family tree for stud; lambing-drop / sire groups for commercial. Do not inherit rabbit compactness or goat dairy assumptions.
- **Cattle module:** full family tree for seedstock; dam–sire + registration IDs for commercial/dairy. ET uses `genetic_dam` vs `recipient_dam` on `pedigree_relations`.

PyAGH / visPedigree are calculation backends for all modules.

```
Ktor API ──HTTP──► Python Genetics Microservice
                          │
                          ├── PyAGH: inbreeding_coefficient(pedigree)
                          ├── PyAGH: relationship_matrix(candidates)
                          ├── Breeding index calculation
                          └── Mating recommendation ranking
```

### Breeding Recommender Logic

```python
def recommend_matings(female_id: str, candidate_sires: list, config: BreedingObjective, species_code: str):
    # Called only from the owning module; sires must match species_code
    # Poultry breeders may call this; commercial flocks do not.
    results = []
    for sire in candidate_sires:
        coi = calculate_inbreeding(female_id, sire.id, pedigree_depth=4)
        offspring_performance = predict_offspring_performance(female_id, sire.id)
        index = config.calculate_index(
            inbreeding_risk=coi,
            growth_score=offspring_performance.growth,
            fertility_score=offspring_performance.fertility,
            survival_score=offspring_performance.survival,
            maternal_score=offspring_performance.maternal,
        )
        results.append({
            'sire_id': sire.id,
            'expected_coi': coi,
            'performance_index': index,
            'decision': classify_decision(coi, index, config.thresholds),
            'evidence': explain_recommendation(female_id, sire.id),
        })
    return sorted(results, key=lambda r: r['performance_index'], reverse=True)
```

---

## 9. Dynamic Task Engine

Each **species module authors** its tasks. The platform task engine **stores, assigns, and merges** them for the shared Tasks tile. Module homes show **only that species’ queue**.

### Event-to-task mapping (owned by the module)

| Trigger Event | Generated Tasks |
|--------------|----------------|
| MatingRecorded (goat) | Pregnancy check (day 45), late-gestation review (day 108), pre-kidding prep (day 143), expected kidding (day 150) |
| MatingRecorded (rabbit) | Nest box (day 28), pre-kindling inspection (day 30), expected kindling (day 32) |
| JoiningRecorded (sheep) | Ram out, pregnancy scanning, pre-lambing vax/nutrition, lambing paddock, expected lambing |
| AiRecorded / MatingRecorded (cattle) | PD, close-up pen, expected calving; dairy: dry-off candidate |
| FlockPlaced (poultry) | Placement/biosecurity, **kind** vaccination pack; meat catch date **or** first-egg; waterfowl swim check |
| EggsSet (poultry) | Candling/lock-down/hatch using **that kind’s** incubation (not a global 21 days) |
| WeightRecorded | Growth anomaly check (if deviation > threshold; cohort = same species **and** purpose) |
| VaccinationCompleted | Schedule booster (protocol-defined interval); poultry: next flock calendar shot |
| TreatmentStarted | Withdrawal monitoring, follow-up check |
| StockBelowThreshold | Feed/medicine reorder review |
| AnimalMoved / GroupMoved (to quarantine) | Release evaluation at configured interval |
| ShearingDue (sheep, wool pack) | Shed booking, crutching if configured |

### Queues (not one undifferentiated Today)

```
GET /api/modules/goat/today      → kidding, FAMACHA, goat weigh-ins
GET /api/modules/rabbit/today    → kindling, nest boxes, litter weights
GET /api/modules/poultry/today   → flock sheets, vax, hatch, catch **by enabled kinds**
GET /api/modules/poultry/today?kind=guinea_fowl
GET /api/modules/sheep/today     → scanning, lambing, crutching/shearing, drench
GET /api/modules/cattle/today    → PD, calving, parlour exceptions, weaning
GET /api/tasks/today             → merged farm queue for the shared Tasks tool (each row has moduleId)
```

---

## 10. Analytics & KPI Pipeline

KPI **definitions and dashboards are per module**. Shared SQL helpers (ADG) are platform. Do not present a single mixed-species “herd KPI” as any species home.

Materialized views must be **split** (`mv_goat_kpis`, `mv_rabbit_kpis`, `mv_poultry_kpis`, `mv_sheep_kpis`, `mv_cattle_kpis`), not one `mv_animal_kpis` with a species column driving a generic UI.

Poultry KPIs are **flock-grain** (eggs per female housed, FCR, livability) **split by poultry_kind_code**. Never one “poultry KPI” that averages chickens with ducks.

### KPI Calculation Functions

```sql
-- Average Daily Gain: last weight minus first weight in range, over elapsed days between those points
CREATE OR REPLACE FUNCTION calc_adg(p_animal_id UUID, p_from TIMESTAMPTZ, p_to TIMESTAMPTZ)
RETURNS DECIMAL AS $$
  WITH w AS (
    SELECT value, measured_at
    FROM measurements
    WHERE animal_id = p_animal_id AND type = 'weight'
      AND measured_at BETWEEN p_from AND p_to
  ),
  bounds AS (
    SELECT
      (SELECT value FROM w ORDER BY measured_at ASC LIMIT 1) AS first_w,
      (SELECT measured_at FROM w ORDER BY measured_at ASC LIMIT 1) AS first_at,
      (SELECT value FROM w ORDER BY measured_at DESC LIMIT 1) AS last_w,
      (SELECT measured_at FROM w ORDER BY measured_at DESC LIMIT 1) AS last_at
  )
  SELECT CASE
    WHEN first_at IS NULL OR last_at IS NULL OR last_at = first_at THEN NULL
    ELSE (last_w - first_w) / GREATEST(1.0, EXTRACT(EPOCH FROM (last_at - first_at)) / 86400.0)
  END
  FROM bounds;
$$ LANGUAGE SQL STABLE;

-- Conception rate: one pregnancy row per breeding_event (latest), not a multiplying join
CREATE OR REPLACE FUNCTION calc_conception_rate(p_sire_id UUID)
RETURNS DECIMAL AS $$
  SELECT COUNT(*) FILTER (WHERE p.status IN ('confirmed','delivered'))::DECIMAL
       / NULLIF(COUNT(*), 0) * 100
  FROM breeding_events be
  LEFT JOIN LATERAL (
    SELECT status FROM pregnancies p
    WHERE p.breeding_event_id = be.id
    ORDER BY COALESCE(p.confirmed_at, p.actual_birth_date) DESC NULLS LAST
    LIMIT 1
  ) p ON true
  WHERE be.sire_id = p_sire_id;
$$ LANGUAGE SQL STABLE;
```

### Materialized KPI Views

```sql
CREATE MATERIALIZED VIEW mv_goat_kpis AS
SELECT a.id AS animal_id, ... goat-only metrics ...
FROM animals a WHERE a.species_code = 'goat' AND a.status = 'active';

CREATE MATERIALIZED VIEW mv_rabbit_kpis AS
SELECT a.id AS animal_id, ... rabbit/litter metrics ...
FROM animals a WHERE a.species_code = 'rabbit' AND a.status = 'active';

CREATE MATERIALIZED VIEW mv_poultry_kpis AS
SELECT g.id AS flock_id, g.poultry_kind_code, ... eggs, FCR, livability, hatchability ...
FROM animal_groups g WHERE g.species_code = 'poultry' AND g.closed_at IS NULL;

CREATE MATERIALIZED VIEW mv_sheep_kpis AS
SELECT a.id AS animal_id, ... lambing_pct, scan_result, wool ...
FROM animals a WHERE a.species_code = 'sheep' AND a.status = 'active';

CREATE MATERIALIZED VIEW mv_cattle_kpis AS
SELECT a.id AS animal_id, ... calving_interval, milk or weaning_wt ...
FROM animals a WHERE a.species_code = 'cattle' AND a.status = 'active';
```

Refresh materialized views on a schedule (e.g., hourly) or on significant event batches.

---

## 11. AI & Farm Copilot

**Coverage rule:** Every **enabled** species module gets the same intelligence *stack* (rules, KPIs, optional ML, Copilot). None of it is a mixed-herd brain. Goat and rabbit ship first (Phases 7–8). Sheep, cattle, and poultry receive the **same stack** when those products ship (Phases 9–11), with **that module’s** data, language, and scorecard — not goat models with a species filter.

| Intelligence layer | Goat | Rabbit | Poultry | Sheep | Cattle |
|--------------------|------|--------|---------|-------|--------|
| Task / lifecycle engine | Yes (kidding) | Yes (kindling) | Yes (hatch/placement **by kind**) | Yes (joining/scan/lambing) | Yes (AI/PD/calving; dairy dry-off from next lactation) |
| Module KPI scorecard + `mv_*_kpis` | Yes | Yes | Yes, **by kind** (flock grain) | Yes | Yes (dairy and/or beef packs) |
| Rule-based anomalies | Growth, kidding, FAMACHA | Growth, litter mortality | House mortality, egg drop, hatchability **by kind** | Scan %, lambing, flystrike/footrot clusters | PD miss, calving interval, mastitis/SCC (dairy), ADG (beef) |
| Withdrawal / hold watch | Meat + milk | Meat | Meat + **egg** hold | Meat | Meat + milk (dairy) |
| Pedigree + PyAGH + mating recommender | Yes (full tree) | Yes (compact; tree for breeders) | **Breeders of that kind only**; commercial flocks skip | Yes (stud); light for commercial mobs | Yes (seedstock/ET genetic vs recipient dam); light for commercial |
| Forecast / risk models | Individual ADG, kid survival | Litter/kit mortality, days to market | **Flock** FCR, livability, egg curve — **not** 5,000 bird models | Lamb survival, scanning outcomes | Calf ADG / weaning; dairy yield/SCC |
| Copilot in-module + `species_code` on every tool | Yes | Yes | Yes + optional `poultry_kind_code` | Yes | Yes |
| Module skill / daily briefing (APK Markdown + optional Supabase schedule) | `goat-kidding-briefing` | `rabbit-kindling-briefing` | `poultry-house-briefing` (kind-aware) | `sheep-lambing-briefing` | `cattle-calving-briefing` (dairy vs beef copy) |
| Insights farm-wide Copilot | Only with **explicit module picker**; never one answer mixing species | Same | Same | Same | Same |

**Hosting:** Intelligence runs in the **APK** + **Supabase** (Postgres views, RLS, optional Edge proxy). Skills are authored per module. Poultry commercial intelligence is flock/house/kind. Do not run goat ADG models on broiler flocks. **No Hermes/Ollama/ML sidecars** in the product deploy.

### Phase 1: Rule-Based Intelligence (no ML required)

- **Growth anomaly**: vs **same-species** cohort (never mix goat ADG with broiler FCR)
- **Reproductive anomaly**: separate rules per module (kidding vs kindling vs scan% vs PD% vs hatchability)
- **Health pattern**: cluster within a location **and** species (poultry: house-level mortality spike)
- **Feed cost alert**: optional farm-wide tool, not a species home widget

### Phase 2: Richer analytics (still no farm ML host)

Prefer Kotlin cohort/MAD/z-score and optional on-device LiteRT. If a farm wants LLM-enriched explanation, use **BYO Model API** (Settings). Do **not** deploy XGBoost/Chronos as a farm-owned service. Optional later: Edge Function that only **proxies** a vendor API — still not a sidecar ML runtime.

### Phase 3: Farm Copilot (LLM)

Copilot opens **from the current module** (or Insights with explicit module scope). Every tool call carries `species_code`. The model must not invent SQL.

- **Goat / rabbit / poultry / sheep / cattle:** same scoping rules as before — only that module’s records; poultry answers by kind; no fake individual bird stories for commercial flocks.

**Safety:** Cite source records. No autonomous treatment, cull, or sale. Drafts return to the **owning module** for Accept/Reject.

**Runtime:** In-app `CopilotEngine` + BYO OpenAI-compatible Model API (§12). Not Hermes.

**Embedded AI (required for field UX):** See [§12a](#12a-embedded-ai-module-charts-anomalies-model-api) and `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`. Feature pages ship **table + Vico chart**, flag **positive/negative** anomalies, and propose **corrective actions**. Model API is optional BYO; on-device rules always work offline.

---

## 12. Farm Copilot (in-app; Hermes deferred)

### 12.0 Locked decisions

| Lock | Decision |
|------|----------|
| LLM backends | Configured **only** in Settings → AI & analytics (BYO OpenAI-compat). Presets: Groq, OpenRouter, Google AI Studio, OpenAI, Custom. |
| Hermes | Out of product footprint (free sidecar not viable). |
| Hermes task map | See `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` §0.2 — binding. |
| Farm data tools | **FarmToolRegistry** (in-app + Supabase). Same tool jobs as former Farm MCP; **MCP protocol not used**. |
| Empty Model API | Valid — Lane A charts/anomalies still ship. |

### 12.0a Why Farm MCP is replaced (protocol only)

Farm MCP existed so **Hermes** (a remote process) could call farm tools over the MCP wire. Copilot now runs **inside the APK**, so MCP is an unnecessary hop. The **tool surface is kept** as `FarmToolRegistry` (names, species scoping, read-mostly + `propose_recommendation`). We are not removing farm intelligence tools — only the Hermes-facing MCP adapter.

Full write-up: AI spec §0.3.

### 12.0b Hosting decision

| Option | Verdict for this product |
|--------|--------------------------|
| Hermes + Ollama on farm LAN / paid VM | **Out of footprint** |
| Hermes on a **free** sidecar | **Not viable** |
| Supabase Edge as Hermes host | **Impossible** |
| **In-app Copilot + Settings BYO Model API + FarmToolRegistry** | **Chosen** |

Hermes remains an optional personal experiment on someone’s own PC — not a Phase 8 deliverable.

### 12.1 What owns what

| Farm OS (SoR) | Copilot (advisory) |
|---------------|-------------------|
| Supabase Postgres events/projections, animals, health, finance | Natural-language Q&A with citations |
| `:domain-ai` rules, KPI SQL in Supabase, charts | Explain those numbers in farmer language |
| Task engine, withdrawal blocks, RBAC | Draft briefings / recommendations; never post treatments/culls/sales alone |
| Android Compose UI | Optional: Owner-only notification of briefing tasks |

If Model API is unset or offline, recording still works; Copilot degrades to rule-evidence text / queued questions.

### 12.2 Topology (product)

```
Android Copilot screen (:feature-ai)
        │  Supabase JWT (farm_id claim)
        ▼
CopilotEngine (Kotlin tool-loop in APK)
        │
        ├── FarmToolRegistry → Supabase client / RPC (RLS)
        ├── Chat → BYO OpenAI-compat URL from Settings
        │          OR Supabase Edge ai-proxy (secret in Vault)
        └── Skills → bundled Markdown per module (APK or Storage)
```

**Do not** expose a remote agent with a shell. The LLM never gets `execute_sql` or terminal tools.

### 12.3 FarmToolRegistry (locked; successor to Farm MCP tool surface)

| Tool | Purpose |
|------|---------|
| `search_in_module` | Tag / RFID / name / flock code within session species |
| `get_animal` / `get_group` | Reject if species ≠ session module |
| `get_timeline` | Events (capped) |
| `get_weight_series` / `get_kpis` | Series and scorecard — never raw SQL |
| `list_module_today` | That species’ Today |
| `get_withdrawal_status` | Meat/milk/egg hold |
| `list_open_anomalies` | From `analytics_anomalies` |
| `search_health_library` | Read-only first-aid / catalog |
| `propose_recommendation` | Draft for Accept/Reject |

### 12.4 Skills & briefings

Ship versioned Markdown in-repo (`skills/goat-kidding-briefing.md`, …). Morning briefing: Supabase `pg_cron` or scheduled Edge Function writes summary tasks / push — **not** Hermes cron.

### 12.5 Android UX

Copilot screen inside each species module (same component, different `species_code`). Citation chips open that module’s profile or flock sheet. Offline: queue the question or show Lane A evidence only.

### 12.6 Hard rules

1. Structured farm data remains authoritative.  
2. Copilot cannot mutate biology/finance except `propose_recommendation` → human Accept.  
3. Isolation: `farm_id` + `species_code` (+ poultry kind when in flock context).  
4. Audit: `ai_audit` row (prompt hash, model, farm, module) — minimise PII.  
5. No Hermes URL in Settings — LLM backends only via Settings Model API fields (§12.0).

---

## 12a. Embedded AI module (charts, anomalies, Model API)

**Full specification:** `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` (companion — **Supabase + app only**).

### 12a.1 Product requirement

Compatible feature pages (growth, milk, eggs, FCR, FAMACHA, SCC, etc.) must offer:

1. **Charts as an additional representation** — Table | Chart | Both (Vico).  
2. **Anomaly flags** — negative / positive / watch.  
3. **Corrective-action recommendations** — drafts only; never auto-treat/cull or invent doses.  
4. **Optional BYO Model API in Settings** — Groq/OpenRouter/OpenAI/custom; empty = full on-device product.

### 12a.2 Three compute lanes

| Lane | Where | Role |
|------|--------|------|
| **A — Rules** | `:domain-ai` on device | Always-on baselines, z-scores, growth bands, egg drop %, FAMACHA — offline |
| **B — Small models** | LiteRT / ONNX in APK | Optional packs from Supabase Storage |
| **C — BYO Model API** | Settings URL (+ optional Edge proxy) | LLM enrich / explain; **not** a farm-hosted Chronos/XGBoost service |

Lanes A–C power **inline analytics**. §12 Copilot shares the same BYO Model API when configured.

### 12a.3 Android modules

```
:domain-ai          Series, anomalies, recommendations, Copilot tool schemas
:feature-ai         Charts, anomaly UI, Copilot, Settings AI, review queue
:ai-client          OpenAI-compat + optional Supabase ai-proxy client
:ai-runtime-litert  Optional
:ai-runtime-onnx    Optional
```

### 12a.4 Settings (LLM / Model API) — locked

- **Only** place to configure LLM backends  
- Mode: Hybrid (default) | On-device only | API preferred  
- Presets: Groq · OpenRouter · Google AI Studio · OpenAI · Custom  
- Keystore key **or** Supabase Edge proxy  
- `share_animal_ids` default off  
- No Hermes / Ollama / farm-analytics-microservice fields  

Full field list: AI spec §5 / §0.1.

### 12a.5 Tooling (footprint-aligned)

| Need | Tool | Notes |
|------|------|-------|
| Charts | Vico | In-app |
| On-device | LiteRT / ONNX Runtime Mobile | Optional |
| LLM | BYO OpenAI-compat | User free tiers OK |
| Proxy | Supabase Edge Functions | Secrets in Vault |
| Agent host | **None** | Hermes deferred — free sidecar not viable |

### 12a.6 Safety

- Species / poultry-kind isolation  
- No prescribing from anomalies or Copilot  
- Health formulary gates treatments  
- Minimise PII to third-party Model APIs  

---

## 13. Module dashboard & new animal modules

The first screen after login is a **module launcher**. Opening a species tile enters that animal’s own product — not a filtered generic animal list.

Shared tiles (Health, Finance, Tasks, Farm ops) are farm-wide tools. They must not replace the species module as the place where breeding, housing, and pedigree are designed.

Farms **enable** species tiles (`farm_enabled_modules`). A goat-only farm never sees empty poultry/sheep/cattle tiles.

### 13.1 Dashboard information architecture

```
┌─────────────────────────────────────────────┐
│  Farm: [name]     🌤 24°  7-day ▸  [sync]  │
│  Tip: Score FAMACHA in the shade, not heat │
│  Alerts: kidding · hold · vax due           │
├─────────────┬─────────────┬─────────────────┤
│ 🐐 Goats    │ 🐇 Rabbits  │ 🐔 Poultry      │
│ goat product│ rabbit      │ chicken/duck/   │
│             │ product     │ guinea…         │
├─────────────┼─────────────┼─────────────────┤
│ 🐑 Sheep    │ 🐄 Cattle   │ 🌾 Farm ops     │
│ sheep       │ dairy/beef  │ shared places   │
│ product     │ packs       │                 │
├─────────────┼─────────────┼─────────────────┤
│ ❤ Health    │ ☑ Tasks     │ ⚖ Production    │
│ farm-wide   │ farm-wide   │ farm-wide       │
├─────────────┼─────────────┼─────────────────┤
│ 💰 Finance  │ 📊 Insights │                 │
└─────────────┴─────────────┴─────────────────┘
        [ + Enable animal module ]
```

- **Goat tile → Goat module home:** herd, kidding board, family tree, FAMACHA, lactation if dairy.
- **Rabbit tile → Rabbit module home:** colony, cages, kindling, litters, nest boxes.
- **Poultry tile → Poultry module home:** kind chips (chicken, duck, guinea fowl, …), houses/ponds/lofts, flocks, daily sheets, hatchery, biosecurity — flock-first, **kind-aware**.
- **Sheep tile → Sheep module home:** mobs, joining, scanning, lambing, wool if enabled.
- **Cattle tile → Cattle module home:** herd, PD/calving, NLIS; parlour and/or lots per capability.
- Scan-from-dashboard resolves the animal **or flock/lot**, then **routes into that species’ module**.
- **Weather widget** (current temp + condition): tap → 7-day forecast page (Open-Meteo; farm lat/lon). Livestock notes for **enabled** modules only (heat/freeze/rain). Spec: `FARM_OS_HEALTH_MODULE_SPEC.md` §8.
- **Health tip of the day** under the farm header: one rotating, module- and weather-aware tip; not a diagnosis. Spec §7.
- Phone: 2-column grid. Tablet: 3–4 columns.

Genetics lives **inside** each species module, not as one shared Genetics tile that flattens species into one UI.

### 13.2 Module catalog

| Module id | Kind | What the user gets |
|-----------|------|-------------------|
| `goat` | Species product | Full goat OS |
| `rabbit` | Species product | Full rabbit OS |
| `poultry` | Species product | Full poultry OS: **kinds** (chicken, duck, guinea fowl, turkey, goose, quail, pigeon, custom) × purpose packs (layer/meat/breeder/hatchery) |
| `sheep` | Species product | Full sheep OS (wool pack optional) |
| `cattle` | Species product | Full cattle OS (dairy and/or beef packs) |
| `health` | Shared tool | Calendar of **vet-accepted** vaccine slots; disease library; formulary + withdrawals (meat/milk/egg); outbreak view. Species forms in context. See `FARM_OS_HEALTH_MODULE_SPEC.md` |
| `weather` | Shared tool | Dashboard widget + **7-day** forecast; not a species home |
| `tasks` | Shared tool | Merged queue, labelled by module |
| `production` | Shared tool | Weights/milk/eggs/wool with species-aware charts |
| `farm_ops` | Shared tool | Locations; paddock vs cage vs house vs parlour by place type |
| `finance` | Shared tool | Cost objects: animal, litter, flock, mob, lot, paddock, house, farm |
| `insights` | Shared tool | Copilot scoped to enabled modules |

### 13.3 Adding a new animal module

Treat it as a **new product**, not a config row on an existing species:

1. Research that species’ biology, housing, recording (ICAR / national ID schemes where they apply).
2. Gradle `:feature-{species}` with its own home, reproduction flow, working object, pedigree method, KPI scorecard, protocol pack.
3. Implement `SpeciesModule` and register on the dashboard.
4. Reuse shared *infrastructure* (events, sync, RFID lookup, `animal_groups`) and optional *widgets* only if they fit.
5. Do not enable the tile until those screens exist. An empty filter on goats is not a sheep module. A flock filter on rabbits is not poultry.

**Build order after goat+rabbit MVP:** sheep (closest mammal recording to goats, but still its own IA) → cattle (NLIS + dairy/beef packs) → poultry (requires flock-first UX and hatchery; most different from the mammal modules). Platform `animal_groups` should land before poultry; sheep/cattle can use it for mobs/lots in the same drop.

### 13.4 Android navigation

- `DashboardScreen` → `module.navGraph()` for that species.
- Deep link: `farm://module/goat/...`, `farm://module/health/...`, `farm://weather`, `farm://tips/{id}`.
- RFID/QR: identify species from the animal or group record, then open **that** module’s profile — never a species-neutral sheet as the main experience.

---

## 14. IoT & Hardware Integration

| Device | Integration Method | Library/Protocol |
|--------|-------------------|-----------------|
| **UHF RFID (Chainway)** | Vendor Android AAR + `RfidScanner` interface | Chainway SDK (not RN wrappers) |
| **RFID (Zebra)** | Zebra RFID Android SDK | Official AAR; implement `RfidScanner` |
| **RFID (Urovo DT50)** | Urovo Android SDK | Official AAR; implement `RfidScanner` |
| **Bluetooth Scales** | Android BLE (`BluetoothGatt`) | Scale-specific GATT profile behind `ScaleReader` |
| **QR / barcode** | CameraX + ML Kit Barcode | Houses, cages, crates, paddock posts |
| **NLIS / official EID wands** | Same `RfidScanner`; cattle/sheep modules interpret NLIS/NAIT format | Jurisdiction pack in cattle/sheep |
| **Milk meters** | USB host / serial | Goat dairy **and** cattle dairy; module session owns the save |
| **Poultry house sensors** | MQTT (temp, humidity, NH3, water) | House-level, poultry module dashboard |
| **Environmental sensors** | MQTT | Mosquitto + Ktor subscriber (paddock/barn too) |
| **Weather** | REST | **Open-Meteo** current + `forecast_days=7` daily; cache in Room; farm lat/lon required |

Use one adapter interface so the UI never imports a vendor SDK:

```kotlin
interface RfidScanner {
    fun start(): Flow<String> // EPC / EID
    fun stop()
}
```

### RFID Workflow (Field)

```
Worker scans RFID/QR → identity-platform resolves animal **or group** + species_code
  → Navigate into **that species module** (goat profile vs rabbit/litter vs poultry flock vs sheep/cattle)
  → Module quick actions only (goat: weight, FAMACHA…; poultry: daily sheet, mortality…)
  → Save to Room + outbox
```

---

## 15. Security, Auth & Multi-Tenancy

### Auth: Keycloak (Recommended)

- Open source, self-hosted
- RBAC with fine-grained permissions
- **One realm for the product.** Tenancy is `farm_id` on the user (group/attribute) + PostgreSQL RLS. Do **not** create a Keycloak realm per farm (ops cost and SSO break).
- OAuth 2.0 / OIDC for the Android app (AppAuth)
- GitHub: [keycloak/keycloak](https://github.com/keycloak/keycloak)

### Role-Permission Matrix

| Permission | Owner | Farm Manager | Breeding Mgr | Vet/Health | Worker | Finance | Buyer (read-only) |
|-----------|-------|-------------|-------------|-----------|--------|---------|-------------------|
| Farm config | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| AI / Model API settings | ✅ | ✅* | ❌ | ❌ | ❌ | ❌ | ❌ |
| User management | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Goat module | ✅ | ✅ | ✅ | Read | Assigned goats | Read | Goat passport |
| Rabbit module | ✅ | ✅ | ✅ | Read | Assigned rabbits | Read | Rabbit/litter profile |
| Poultry module | ✅ | ✅ | ✅ | Read | Assigned houses/flocks | Read | Flock/breeder passport |
| Sheep module | ✅ | ✅ | ✅ | Read | Assigned mobs | Read | Sheep passport |
| Cattle module | ✅ | ✅ | ✅ | Read | Assigned cattle | Read | Cattle passport / NLIS view |
| Breeding (in-module) | ✅ | ✅ | ✅ | Read | ❌ | ❌ | ❌ |
| Health records | ✅ | ✅ | Read | ✅ | Assigned | ❌ | ❌ |
| Financials | ✅ | Summary | ❌ | ❌ | ❌ | ✅ | ❌ |
| Reports | ✅ | ✅ | Breeding | Health | ❌ | ✅ | Certificate |
| Audit trail | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

\*Farm Manager may set Model API endpoint/model for the farm; only Owner rotates or clears API keys.

### Multi-Tenancy

- Row-level security: every farm table has `farm_id`; policies use `current_setting('app.farm_id')` set by Ktor per request from the JWT
- Module APIs additionally filter `species_code`
- Workers can be granted any subset of species modules (goat-only, poultry+cattle, etc.)
- Meilisearch indexes must include `farm_id` and filter on every query; never a global animal search

---

## 16. DevOps & Infrastructure

### Docker Compose (MVP)

```yaml
services:
  api:
    build: ./server
    ports: ["8080:8080"]
    depends_on: [postgres, redis, minio]
    environment:
      DATABASE_URL: postgresql://farm:farm@postgres:5432/farmdb
      REDIS_URL: redis://redis:6379

  postgres:
    image: postgres:16
    volumes: [pgdata:/var/lib/postgresql/data]
    environment:
      POSTGRES_DB: farmdb

  redis:
    image: redis:7-alpine

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    volumes: [minio_data:/data]

  meilisearch:
    image: getmeili/meilisearch:v1.10
    volumes: [meili_data:/meili_data]

  genetics:
    build: ./genetics-service
    ports: ["5000:5000"]

  ollama:
    image: ollama/ollama
    volumes: [ollama_models:/root/.ollama]
    environment:
      OLLAMA_CONTEXT_LENGTH: "64000"

  hermes:
    image: nousresearch/hermes-agent
    command: gateway run
    restart: unless-stopped
    volumes: [hermes_data:/opt/data]
    ports:
      - "127.0.0.1:8642:8642"
      - "127.0.0.1:8644:8644"
    environment:
      API_SERVER_ENABLED: "true"
      API_SERVER_HOST: "0.0.0.0"
      API_SERVER_KEY: ${HERMES_API_KEY}
      WEBHOOK_ENABLED: "true"
      WEBHOOK_SECRET: ${HERMES_WEBHOOK_SECRET}
      TERMINAL_ENV: docker
    depends_on: [ollama, api]

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    command: start-dev

volumes:
  pgdata:
  minio_data:
  meili_data:
  ollama_models:
  hermes_data:
```

### CI/CD Pipeline

```
Push → GitHub Actions:
  1. ktlint + detekt
  2. Android unit tests (JUnit, lifecycle engines)
  3. Ktor integration tests (Testcontainers + PostgreSQL)
  4. Assemble debug APK (phone + tablet resource configs)
  5. Build API Docker image
  6. Deploy API to staging
  7. Compose UI tests (optional Maestro / Espresso)
  8. Release AAB (manual approval)
```

---

## 17. Open-Source Tools & Repos Reference

### Core Framework & Infrastructure

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Jetpack Compose** | Android UI | [androidx.compose](https://developer.android.com/jetpack/compose) | Apache-2 |
| **Ktor** | HTTP API | [ktorio/ktor](https://github.com/ktorio/ktor) | Apache-2 |
| **Hilt** | DI | [google/dagger](https://github.com/google/dagger) | Apache-2 |
| **Room** | On-device SQLite | AndroidX Room | Apache-2 |
| **WorkManager** | Background sync | AndroidX Work | Apache-2 |
| **CameraX / ML Kit** | QR and photos | Google | Apache-2 |
| **Vico** | Compose charts | [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico) | Apache-2 |
| **PostgreSQL** | Primary database | [postgresql.org](https://www.postgresql.org/) | PostgreSQL |
| **Redis** | Cache / jobs | [redis/redis](https://github.com/redis/redis) | BSD-3 |

### Offline & Sync

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Room + outbox** | Local-first writes | AndroidX | Apache-2 |
| **SQLCipher (optional)** | Encrypted SQLite | [sqlcipher/android-database-sqlcipher](https://github.com/sqlcipher/android-database-sqlcipher) | BSD |
| **Synchro Kotlin client** | WAL-based Postgres sync | [trainstar/synchro](https://github.com/trainstar/synchro) | — |

### Event Sourcing

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Custom Postgres event table** | MVP event ledger | In-repo | — |
| **Axon Framework** (optional later) | JVM aggregates/sagas | [AxonFramework/AxonFramework](https://github.com/AxonFramework/AxonFramework) | Apache-2 |

### Genetics & Pedigree

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **PyAGH** | Inbreeding, relationship matrices | [zhaow-01/PyAGH](https://github.com/zhaow-01/PyAGH) | MIT |
| **ribd** (pedsuite) | Pedigree relatedness coefficients (R) | [magnusdv/ribd](https://github.com/magnusdv/ribd) | GPL-3 |
| **visPedigree** | Pedigree visualization + high-perf inbreeding (R) | [luansheng/visPedigree](https://github.com/luansheng/visPedigree) | MIT |

### IoT & Hardware

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **CameraX** | Camera + QR | AndroidX Camera | Apache-2 |
| **ML Kit Barcode** | Barcode/QR decode | Google ML Kit | Apache-2 |
| **Vendor RFID SDKs** | Chainway / Zebra / Urovo | Vendor developer portals | Proprietary AARs |
| **Mosquitto** | MQTT broker for sensors | [eclipse/mosquitto](https://github.com/eclipse/mosquitto) | EPL-2 |

### Search, Storage & Auth

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Meilisearch** | Animal/farm search | [meilisearch/meilisearch](https://github.com/meilisearch/meilisearch) | MIT |
| **MinIO** | S3-compatible file storage | [minio/minio](https://github.com/minio/minio) | AGPL-3 (self-host or licensed alternative) |
| **Keycloak** | Identity & access management | [keycloak/keycloak](https://github.com/keycloak/keycloak) | Apache-2 |

### Reference Farm/Livestock Platforms

| Tool | Purpose | Repo / Link | Notes |
|------|---------|-------------|-------|
| **farmOS** | Web-based farm record keeping | [farmOS/farmOS](https://github.com/farmOS/farmOS) | Drupal-based; reference for data models |
| **Penzi Farm ERP** | Laravel livestock management | [maurice2428/PenziFarm](https://github.com/maurice2428/PenziFarm) | Reference for breeding/health workflows |
| **Cattleitics** | Cattle herd management | [ToastSeagers/Cattleitics](https://github.com/ToastSeagers/Cattleitics) | Reference for pedigree + pasture UX |
| **GENMON** | Livestock population monitoring | [Listed in awesome-agriculture](https://github.com/brycejohnston/awesome-agriculture) | Goat/sheep/cattle population genetics |
| **ICAR** | Recording guidelines (sheep, goats, cattle, poultry where published) | [icar.org](https://www.icar.org/) | Inform KPI definitions; do not copy web IA |
| **awesome-agriculture** | Curated ag-tech list | [brycejohnston/awesome-agriculture](https://github.com/brycejohnston/awesome-agriculture) | Discovery resource |

### AI/ML & embedded analytics

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Kotlin `:domain-ai`** | Rules, MAD/z-score anomalies, recommendations | In-repo | — |
| **LiteRT** | Optional on-device `.tflite` | [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) | Apache-2 |
| **ONNX Runtime Mobile** | Optional on-device / exported models | [onnxruntime.ai](https://onnxruntime.ai/docs/tutorials/mobile/) | MIT |
| **Vico** | Charts (also listed above) | [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico) | Apache-2 |
| **BYO OpenAI-compat** | Copilot / Lane C via Settings (Groq, OpenRouter, OpenAI, …) | Vendor docs | Vendor |
| **Supabase Edge Functions** | Optional `ai-proxy` + scheduled briefings | [supabase.com/docs/guides/functions](https://supabase.com/docs/guides/functions) | Apache-2 |
| **Kompletions-style client** | Design ref for thin OkHttp SSE client | [s1mar/Kompletions](https://github.com/s1mar/Kompletions) | — |
| **PocketSage** | Optional on-device RAG pattern later | [umarpazir11/pocketsage](https://github.com/umarpazir11/pocketsage) | — |
| **Hermes / Ollama** | **Deferred** — not in product hosting footprint | [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | MIT |

**Spec:** `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` (§12a). No farm-hosted Chronos/XGBoost/scikit-learn service.

### Monitoring & DevOps

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Grafana** | Dashboards & alerting | [grafana/grafana](https://github.com/grafana/grafana) | AGPL-3 |
| **Prometheus** | Metrics collection | [prometheus/prometheus](https://github.com/prometheus/prometheus) | Apache-2 |
| **Loki** | Log aggregation | [grafana/loki](https://github.com/grafana/loki) | AGPL-3 |

---

## 18. Phased Delivery Plan

### Phase 1: Foundation (Weeks 1–8)
**Goal:** Reliable system of record

- [ ] Gradle: `:app`, `:domain` (JVM engines), `:feature-goat`, `:feature-rabbit`, `:server` (add `:domain-ai` / `:feature-ai` stubs early if charts land in Phase 3)
- [ ] PostgreSQL: farms, farm_users, species_modules, farm_enabled_modules, animals, identifiers, locations, domain_events (`farm_id` + `mutation_id`), RLS skeleton
- [ ] `animal_groups` table created unused (needed before finance/poultry/sheep/cattle allocate to groups)
- [ ] Event store + Animal aggregate in Kotlin; project in the same transaction
- [ ] Keycloak + AppAuth (Owner + Worker)
- [ ] **Module dashboard** launching dedicated species products
- [ ] `:feature-goat`: kidding, paddocks, FAMACHA, **full family tree**, goat KPIs
- [ ] `:feature-rabbit`: kindling, cages, litters, nest boxes, **compact pedigree**, rabbit KPIs
- [ ] Shared infrastructure only: locations core, timeline, QR, sync — not a generic animal UX
- [ ] Room + outbox + WorkManager sync
- [ ] RFID/QR scan from dashboard strip

**MVP Deliverable:** Dashboard + **goat product** + **rabbit product** on shared identity/events/sync. No mixed Animals list. No iOS, no web. Poultry/sheep/cattle tiles stay **disabled** until Phases 9–11.

### Phase 2: Reproduction (Weeks 9–14)
**Goal:** Lifecycle automation **inside each module**

- [ ] Goat module: heat/mating → pregnancy → kidding → kid records → lactation tasks
- [ ] Rabbit module: mating → nest box → kindling → **litter** → kit IDs when needed
- [ ] Rabbit breeding programme **full MVP** per `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`: 3×11 boxes, waves, notifications, nest sanitize/QR, cage pairing 2:11, COI+colour pairing, palpation/false-pregnancy, foster, photo journal, kit promote/retention, doe/buck scorecards, market planner, waitlist/contracts, multi-breed packs, cage cards PDF, ICS export, inventory bedding hooks
- [ ] No shared “reproductive calendar” that mixes kidding and kindling as one grid
- [ ] Each module’s expected-birth board on **that** module home

### Phase 3: Growth & Production (Weeks 15–18)
**Goal:** Performance **per species** + chart/anomaly UX foundations

- [ ] Goat: weights, ADG, optional milk/lactation curves
- [ ] Rabbit: litter/grow-out weights, FCR, days to market
- [ ] Bluetooth scale in the **active module** session
- [ ] Cohorts = same species (never mixed-species growth overlay as default)
- [ ] Gradle: `:domain-ai`, `:feature-ai` (chart shells + Lane A rules)
- [ ] Growth (and milk/eggs where present): **Table | Chart | Both** (Vico)
- [ ] Lane A anomalies: negative / positive / watch + corrective-action drafts (Accept → task)
- [ ] No Model API required yet (offline rules only)

### Phase 4: Health (Weeks 19–23)
**Goal:** Shared health *records*, species *forms*, farm-wide **schedules + weather/tips**

- [ ] Platform: vaccines, treatments, withdrawals (meat/milk/**egg**), lab, vet visits
- [ ] Protocol packs `vet_accepted` → due tasks (CDT, cattle respiratory, poultry ND/IB/IBD, rabbit RHD/myxo by jurisdiction)
- [ ] Disease library + first-aid copy (no farmer dose tables)
- [ ] Formulary: only vet-approved products; treatments reference formulary ids
- [ ] Goat forms: FAMACHA, milk withdrawal
- [ ] Rabbit forms: GI stasis red flag, colony outbreak
- [ ] Later species add forms in the same Health platform
- [ ] **Dashboard: health tip of the day** (enabled modules + weather season)
- [ ] **Dashboard: weather widget → 7-day Weather page** (Open-Meteo)
- [ ] Health tile can filter; opening an animal still lands in context of its module
- [ ] VCPR / disclaimer on first Health open

### Phase 5: Farm Operations (Weeks 24–28)
**Goal:** Field execution without a generic livestock IA

- [ ] Goat module: paddock/grazing plans
- [ ] Rabbit module: cage map + QR
- [ ] Shared Tasks tool = merged queue with `moduleId`
- [ ] Each module Today = species queue only
- [ ] RFID → module routing
- [ ] FEFO inventory (platform)

### Phase 6: Finance (Weeks 29–33)
**Goal:** Cost objects that match biology

- [ ] Allocate to animal, litter, paddock, or farm in goat/rabbit MVP
- [ ] Flock/mob/lot cost objects when those modules (Phases 9–11) write `animal_groups`
- [ ] Sales/cull in the **module that owns the animal**
- [ ] P&L can roll up farm-wide; drill-down stays per module

### Phase 7: Genetics & Breeding Intelligence (Weeks 34–38)
**Goal:** Intelligence inside each module

- [ ] PyAGH platform service
- [ ] Goat module: full tree 6+ gen, inbreeding warnings on **goat** mating plans
- [ ] Rabbit module: compact pedigree + breeder full-tree; rabbit breeding index
- [ ] DNA/parentage verification in the module that recorded the animal

### Phase 8: Intelligence & Copilot (Weeks 39–46)
**Goal:** In-app Copilot + hybrid analytics **without** farm AI hosts

- [ ] Anomaly rules **per species** (and poultry **kind**); sync `analytics_anomalies` to Supabase
- [ ] Settings → **AI & analytics**: BYO Model API URL, model id, Keystore key **or** Supabase Edge proxy
- [ ] Modes: Hybrid / On-device only / API preferred
- [ ] In-app `CopilotEngine` tool-loop; allow-listed Supabase tools; module Markdown skills
- [ ] `ai_audit` rows; Accept/Reject recommendation drafts in owning module
- [ ] Optional LiteRT/ONNX packs from Supabase Storage
- [ ] **Do not** ship Hermes, Ollama, or a farm ML sidecar as part of this phase

### Phase 9: Sheep product (after goat/rabbit MVP is stable)
**Goal:** `:feature-sheep` as its own OS

- [ ] Research pack: ICAR sheep, national EID, scanning codes (dry/single/twin/triplet)
- [ ] `animal_groups` type `mob` used by sheep home (not a goat list filter)
- [ ] Joining → scan board → lambing → marking → weaning
- [ ] Wool pack optional: shearing, crutching, micron
- [ ] Sheep health forms: footrot, flystrike, FAMACHA-as-sheep (not goat copy)
- [ ] `mv_sheep_kpis`; Copilot skill `sheep-lambing-briefing`
- [ ] Do not ship until lambing + scanning screens exist

### Phase 10: Cattle product
**Goal:** `:feature-cattle` with dairy and/or beef packs

- [ ] NLIS/NAIT/official ID identifier types + movement events
- [ ] Heat/AI/ET → PD → calving; `genetic_dam` / `recipient_dam` for ET
- [ ] Dairy pack: parlour, milk meters, SCC, dry-off, milk withdrawal
- [ ] Beef pack: lots, weaning weights, days on feed
- [ ] `mv_cattle_kpis`; Copilot skill `cattle-calving-briefing`
- [ ] Do not clone sheep scanning UI for PD

### Phase 11: Poultry product
**Goal:** `:feature-poultry` flock-first, **multi-kind** (not chicken-only)

- [ ] `poultry_kinds` catalog + `farm_enabled_poultry_kinds`
- [ ] `animal_groups` type `flock` / `placement` / `hatch` with **required** `poultry_kind_code`
- [ ] Kind chips on poultry home; chicken-only farms hide unused kinds
- [ ] House / pond / loft map + daily sheet (eggs, feed, mortality, culls)
- [ ] Hatchery: set → candle → hatch using **kind incubation** (21 / 28 / 35 / 17 …)
- [ ] Purpose packs per kind: layer, meat, breeder (individuals only when banded)
- [ ] Kind-specific protocol packs (chicken vax ≠ duck ≠ guinea keet)
- [ ] `mv_poultry_kpis` partitioned by kind; Copilot `species_code` + `poultry_kind_code`
- [ ] Ban individual-bird herd lists for commercial placements
- [ ] Ban a separate dashboard tile per bird kind

Phase 9–11 may run as sequential releases. Do not start them by copying `:feature-goat` and renaming labels.

---

## 19. Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Offline sync conflicts cause data loss | High | Medium | Domain-aware conflict rules; conflict audit log; user notification |
| Room schema migrations fail in the field | High | Low | Versioned migrations; force pull if client schema too old |
| Custom event store bugs | Medium | Medium | Keep append + project in one transaction; add Axon only if needed |
| No iOS later is expensive | Medium | Medium | Keep `:shared` JVM-free of Android APIs so KMP can be added later |
| RFID hardware fragmentation | Medium | High | Abstract via device adapter pattern; support top 2-3 vendors initially |
| Genetics calculations slow at scale | Medium | Low | Pre-compute and cache; materialized views; async processing |
| Scope creep from 11 phases | High | High | Strict phase gating; MVP = goat+rabbit; 9–11 after stable |
| Dual-write ledger vs tables | High | Medium | Same transaction: append `domain_events` + project; never two writers |
| Keycloak realm per farm | High | Medium | Single realm + `farm_id` claim + RLS |
| Flock census vs roster mixed | High | Medium | `accounting` on `animal_groups`; commercial poultry = census only |
| Unique farm_tag blocks unnamed birds | Medium | Low | Commercial poultry has no `animals` rows; only flocks |
| Low connectivity in rural areas | High | High | Offline-first architecture is the primary mitigation; test on 2G/Edge |
| Farmer uses Health library as a drug cookbook | High | Medium | First aid only; treatments require `formulary_items` + vet pack; Copilot cannot name doses |
| Sheep copied from goat without a sheep product | High | Medium | Phase 9 `:feature-sheep`; scanning + mobs required; no filter-on-goats |
| Cattle treated as large sheep | High | Medium | Phase 10 cattle packs (NLIS, AI/ET, dairy vs beef); separate PD vs scan UX |
| Poultry treated as many rabbits | High | High | Phase 11 flock-first; no commercial individual list; hatchery ≠ pregnancy |
| All poultry treated as chickens | High | High | Kind catalog + incubation/language/housing packs; no global 21-day hatch |
| Duck/guinea tiles on the main dashboard | Medium | Medium | Kinds stay inside Poultry; enable via `farm_enabled_poultry_kinds` |
| `animal_groups` becomes a generic livestock home | High | Medium | Groups APIs are module-scoped; no dashboard “Groups” tile that mixes species |
| Dairy milk UI leaks into beef-only cattle farms | Medium | Medium | Capability flags; hide parlour/SCC when dairy pack off |
| Wool UI leaks into meat-only sheep farms | Medium | Medium | Sheep `wool` capability |
| Multi-species extension later | Low | Low | New feature module + `species_modules` row |
| Hermes terminal/tool loop mutates farm data | High | Low | Hermes **not deployed**; in-app tools are allow-listed read + propose only |
| Free sidecar attempted for Hermes/Ollama | High | Medium | Documented as non-viable; product uses BYO Model API + APK engines |
| Model API sends pedigree/PII to third party | High | Medium | Default: opaque series only; Owner opt-in for IDs; Keystore or Vault; Hybrid offline fallback |
| Anomaly auto-treats or invents doses | High | Medium | Recommendations = drafts only; Health formulary gates treatments; no dose fields from AI |
| Charts replace structured records | Medium | Medium | Table remains SoR; Chart is additional view (Table \| Chart \| Both) |
| Mixed-species anomaly cohorts | High | Low | Engine requires `species_code` (+ poultry kind); never goat vs rabbit ADG |
| Copilot broken when no Model API key | Low | High | Lane A charts/anomalies work without any API; Copilot optional |

---

## 20. Technical invariants

These rules make the rest of the plan implementable. If a later screen contradicts them, the invariant wins.

1. **CQRS in one database.** `domain_events` is the system of record. Section 4 tables are projections. Append + project in one transaction (or transactional outbox). The app never replays the ledger. Compensating events — do not DELETE ledger rows.
2. **Backend footprint for AI.** Product AI runs in the **APK** and **Supabase** (Postgres, Auth, Storage, Edge Functions). Do not require farm-hosted Hermes, Ollama, or ML sidecars. “Services” elsewhere in this plan that imply extra hosts are **deferred** unless they fit Supabase/app.
3. **One APK, feature flags.** All `:feature-*` modules compile into the farm APK. `farm_enabled_modules` hides tiles. Do not use Play Feature Delivery for barn offline.
4. **`:domain` / `:domain-ai` are JVM-only.** Lifecycle and analytics engines and DTOs. Compose navigation stays in `:feature-*`.
5. **Subject grain.** Tagged animal → `animals`. Commercial poultry / untagged census → `animal_groups` with `accounting = 'census'`. Do not create dummy animal rows to store egg counts.
6. **Poultry kinds ≠ livestock species.** `species_code = 'poultry'` plus `poultry_kind_code`. CHECK constraints enforce kind nullability.
7. **Identity uniqueness.** `(farm_id, farm_tag)` on animals. Active scan IDs unique per `(farm_id, value)`. Flock codes on `animal_groups.code`, not `animal_identifiers`.
8. **Pedigree.** At most one row per `(animal_id, relation_type)`. Parent ≠ child. Sire/dam must match `species_code` (and poultry kind).
9. **Tenancy.** JWT `farm_id` (Supabase Auth / RLS). Meilisearch or search always filtered by `farm_id` if used.
10. **Sync.** Idempotent `mutation_id`. Pull cursor on events/projections as designed. REST/Supabase for sync; WebSocket only for optional live alerts.
11. **Health/treatments/tasks** may attach to `animal_id` and/or `group_id`. Flock vaccination must not require a fake bird.
12. **Egg withdrawal** is a poultry treatment field; milk withdrawal is goat/cattle dairy only.
13. **Copilot** is not SoR. In-app tool-loop; allow-listed tools only; no remote agent shell.
14. **Embedded AI** (§12a) is advisory. Charts are additional representations. Anomalies never auto-treat, auto-cull, or invent doses. Model API is BYO; Lane A works offline with zero API.
15. **Licenses.** MinIO is AGPL if still used (or prefer Supabase Storage). Do not ship iText 7 without a commercial license. SQLCipher is optional and must use a Room-compatible integration.
16. **KPI math.** ADG uses first/last timed weights, not min/max. Conception rate uses one pregnancy per breeding event. Poultry KPIs never average across kinds.

**Companion research:** `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md` (clinical recording), `FARM_OS_HEALTH_MODULE_SPEC.md` (vaccination templates, disease library, formulary, tip-of-day, weather), `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` (charts, anomalies, BYO Model API), `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md` + `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` (KudBat waves, nest boxes, cage pairing, COI). **Not a formulary of doses.**

---

## 21. Research status by branch

**Veterinary expert packs** live in `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md` (same folder as this plan). That document is the species clinical/recording specification: ICAR traits, FAMACHA, BCS scales, rabbit handling, sheep scan vs cattle PD, poultry incubation by kind, Copilot limits.

| Branch | Status after research pack |
|--------|----------------------------|
| **Goat** | Reproduction, FAMACHA, BCS 1–5, ICAR kid weights/scan family, dairy milk/SCC pointer, red flags — **ready to implement forms**. Farm vet still signs vaccine/drench pack. |
| **Rabbit** | Merck handling/temp, 31–33 d, nest 28–29, foster window, pasteurella/GI stasis catalogs — **ready**. Intensive rebreed is a purpose pack, not default. |
| **Sheep** | 147 d, scan ~d 70 litter size (ICAR/MLA), FAMACHA, BCS, flystrike visual scores, footrot notifiable hook, wool micron — **ready**. Jurisdiction EID still a plug-in. |
| **Cattle** | ~280 d, PD methods + reconfirm early PD, ICAR calving ease/stillbirth dummy ID, dairy SCC/200k flag/DIM&lt;5, beef BCS 1–9, d 82 postpartum window — **ready**. Official ID = jurisdiction pack. |
| **Poultry kinds** | MSU incubation table (chicken 21 / duck 28 / muscovy 35–37 / guinea 28 / turkey 28 / goose 28–34 / coturnix 17 / pigeon 17), hen-housed vs hen-day, flock biosecurity, turkey–chicken histomonas warning — **ready for hatchery engine**. Strain broiler handbooks remain farm config. |
| **Still farm-local** | Named products, doses, withdrawal days, notifiable lists, VCPR, Keycloak roles |

**Rule:** Architecture + research packs may proceed. **No tile ships health treatments** until an attending vet (or licensed advisor) accepts a protocol pack JSON for that farm. Copilot never prescribes.

**Sources:** listed in the research pack §8 (ICAR, Merck, WormBoss/UT FAMACHA, MLA/AWI, MSU hatchery, Alabama/Purdue BCS, etc.).

---

## Appendix: Repository Structure

```
farm-os/
├── app/                          # Shell: dashboard, auth, RFID/QR router, Settings
├── domain/                       # JVM: engines, DTOs, validation (no Android)
├── domain-ai/                    # JVM: AnalyticsFeature, AnomalyEngine, recommendations
├── feature-goat/                 # Goat product (Compose)
├── feature-rabbit/               # Rabbit product (Compose)
├── feature-poultry/              # Poultry product (kinds × flocks/houses)
├── feature-sheep/                # Sheep product (mobs, scanning, lambing)
├── feature-cattle/               # Cattle product (dairy/beef packs, NLIS)
├── feature-health/               # Shared health: schedules, library, formulary
├── feature-ai/                   # Charts, anomaly UI, Copilot, Model API Settings
├── ai-runtime-litert/            # Optional on-device LiteRT
├── ai-runtime-onnx/              # Optional on-device ONNX
├── ai-client/                    # OpenAI-compat + optional Supabase ai-proxy client
├── feature-weather/              # 7-day forecast (optional; or screens in :app)
├── feature-tasks/                # Merged queue
├── feature-finance/
├── supabase/                     # Migrations, RLS, Edge Functions (ai-proxy, briefings)
│   ├── migrations/
│   └── functions/
│       └── ai-proxy/
├── skills/                       # Copilot Markdown skills per module (bundled in APK)
├── infra/
├── settings.gradle.kts
└── README.md
```

*Hermes/Ollama compose stacks and `server/modules/analytics` as a separate host are **not** part of this tree.*

---

*Android-only. Each species module is its own product. Shared platform = identity, events, groups, sync, finance posting — never a generic livestock home. Ledger = `domain_events`; UI reads projections.*
