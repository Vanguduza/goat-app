# Farm OS — Consolidated Project Documentation

**Compiled:** 20 August 2026
**Purpose:** Single markdown binder of all Farm OS technical, veterinary, health, AI, and rabbit breeding programme documents.

### Source documents

| Part | Source file |
|------|-------------|
| A | `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md` |
| B | `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md` |
| C | `FARM_OS_HEALTH_MODULE_SPEC.md` |
| D | `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` |
| E | `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` |
| F | `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md` |
| G | `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` |
| H | `FARM_OS_VET_INTELLIGENCE_FEATURE_IMPLEMENTATION_SPEC.md` |
| I | `FARM_OS_OPERATIONS_ECONOMICS_LAYER_SPEC.md` |
| J | `FARM_OS_DESIGN_SYSTEM_SPEC.md` |

### Contents

1. [Part A — Technical Implementation Master Plan](#part-a--technical-implementation-master-plan)
2. [Part B — Veterinary Expert Research Pack](#part-b--veterinary-expert-research-pack)
3. [Part C — Health Module Spec](#part-c--health-module-spec)
4. [Part D — Embedded AI Module Spec](#part-d--embedded-ai-module-spec)
5. [Part E — Rabbit Nest-Box Ratio & Schedule](#part-e--rabbit-nest-box-ratio--schedule)
6. [Part F — Rabbit Breeding Programme Spec (Full MVP)](#part-f--rabbit-breeding-programme-spec-full-mvp)
7. [Part G — Technical Implementation Handbook (DDE-aligned)](#part-g--technical-implementation-handbook-dde-aligned)
8. [Part H — Vet Intelligence Feature Implementation Spec](#part-h--vet-intelligence-feature-implementation-spec)
9. [Part I — Operations & Economics Layer](#part-i--operations--economics-layer)
10. [Part J — Design System Spec (Field-first visual language)](#part-j--design-system-spec-field-first-visual-language)

---

> **Note:** Individual source files remain the editable originals. This binder is a compiled snapshot for reading/sharing. If sources diverge later, recompile from the originals.


---

<a id="part-a"></a>

# Part A — Technical Implementation Master Plan

*Source: `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md`*

---

## MULTI-SPECIES FARM INTELLIGENCE PLATFORM
### Technical Implementation Master Plan
#### Goats · Rabbits · Poultry · Sheep · Cattle

**Based on:** DOC-20260818-WA0002.pdf (Master Product, Lifecycle & Technical Design)
**Enhanced:** 19 August 2026
**Revised:** 20 August 2026 — Android-only. Species products on a shared platform. **Technical pass:** CQRS (event ledger vs read models), modular monolith / Supabase backend, RLS tenancy, flock vs individual grain, poultry kinds inside one module. **AI pass:** embedded analytics in the APK + Supabase only (no farm AI sidecars); BYO Model API in Settings — see §12a + `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`.
**Species scope:** Goat and rabbit remain the **Phase 1–8 system of record**. Poultry, sheep, and cattle are **first-class products** with dedicated Gradle modules, biology, housing, recording, and KPIs — not filters, gestation-number tweaks, or copied goat/rabbit screens.
**Purpose:** Technical blueprint aligned with species-specific modules, hybrid methods where each animal keeps the right UX, and a path to add animal modules without flattening them into one list.

#### Alignment rules (non-negotiable)

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

### Table of Contents

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

### 1. Technology Stack Decision Matrix

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

#### Why this beats React Native for *this* product

RFID readers, Bluetooth scales, USB/serial milk meters, and barn Wi‑Fi drops are Android hardware problems. Compose talks to vendor AARs and `BluetoothGatt` directly. Room + WorkManager is the Android offline stack. Tablet layouts use `WindowSizeClass` instead of a second web app.

---

### 2. System Architecture

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

### 3. Service Decomposition & Boundaries

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

### 4. Core Data Model (PostgreSQL Schema)

Platform tables hold rows for every species. **They are not a product surface.** Each module queries `WHERE species_code = 'goat'|'rabbit'|'poultry'|'sheep'|'cattle'`. Do not build an API `GET /animals` that the UI uses as a mixed herd. Poultry commercial UIs query **flocks** first; individuals exist when the poultry module creates them (breeders, pedigree bands).

#### 4.1 Identity store (platform)

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

#### 4.2 Pedigree

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

#### 4.3 Events & Measurements

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

#### 4.4 Breeding & Reproduction

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

#### 4.5 Groups, flocks, mobs, lots (platform)

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

#### 4.6 Health & Treatment

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

#### 4.7 Locations & Movement

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

#### 4.8 Inventory & Finance

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

#### 4.9 Tasks

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

#### 4.10 Users, idempotency, RLS (required for the schema to be operable)

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

#### 4.11 Analytics anomalies & recommendations

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

### 5. Event Sourcing Implementation

#### Approach: PostgreSQL event store in Kotlin (Ktor)

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

#### Aggregates (do not dump flock events onto Animal)

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

### 6. Offline-First & Sync Architecture

#### Primary: Room + outbox + WorkManager

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

#### Sync protocol

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

#### Room entities (Android)

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

#### Alternative: Synchro Kotlin client

[trainstar/synchro](https://github.com/trainstar/synchro) has a **first-class Kotlin** engine (not a React Native bridge). Use it if the custom outbox becomes a bottleneck. PostgreSQL WAL, local SQLite, configurable conflicts.
---

### 7. Species modules (designed per animal)

**Product rule:** Each animal module is a **purpose-built app for that species**, not a generic livestock screen with toggles. Goats get a goat product. Rabbits get a rabbit product. Poultry, sheep, and cattle each get their own product — not “goats with a different gestation number,” not a flock filter on rabbits, and not a cattle clone of the sheep lambing board.

**Platform rule:** Identity, event ledger, quantities, groups, sync, users, and finance *posting* are shared infrastructure. They do not dictate labels or workflows. A module may reuse a Compose *component* (e.g. an interactive family-tree widget) only when that component is the right method **for that animal**. Reuse is optional; copying one species’ IA into another is not allowed.

#### What “designed for the target animal” means

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

#### Hybrid method (same toolkit, different product)

- If a **family tree** is right for goats, sheep stud, or cattle seedstock, that module **implements it** as a first-class screen.
- If a **compact litter pedigree** is right for rabbits (or poultry breeders), that module **implements that**.
- If a **flock daily sheet** is right for poultry, the poultry module implements it; goats never see it.
- Both widgets may live in a shared UI kit. Each module chooses **its** primary method. Neither species is denied a feature because another cannot use it the same way.

#### SpeciesModule contract (`:shared` + dedicated UI package)

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

#### 7.1 Goat module (GoatLifecycleEngine)

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

#### 7.2 Rabbit module (RabbitLifecycleEngine)

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

#### 7.3 Poultry module (PoultryLifecycleEngine)

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

#### 7.4 Sheep module (SheepLifecycleEngine)

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

#### 7.5 Cattle module (CattleLifecycleEngine)

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

### 8. Genetics calculations & per-module pedigree

PyAGH is a **platform calculator**. Pedigree **screens and breeding plans live in each species module**. Do not ship one Genetics app that lists mixed species. Poultry commercial flocks skip pedigree UI entirely.

#### Calculation Libraries

| Library | Language | Capabilities | Repo |
|---------|----------|-------------|------|
| **PyAGH** | Python | Inbreeding coefficients, A/G/H matrices, pedigree sorting, error detection, visualization | [zhaow-01/PyAGH](https://github.com/zhaow-01/PyAGH) |
| **ribd** (pedsuite) | R | Kinship, inbreeding, IBD coefficients, X-linked, inbred founders | [magnusdv/ribd](https://github.com/magnusdv/ribd) |
| **visPedigree** | R | C++ high-performance inbreeding, relationship matrices, pedigree visualization, scales to 1M+ animals | [luansheng/visPedigree](https://github.com/luansheng/visPedigree) |

#### Pedigree visualization (per-module method)

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

#### Breeding Recommender Logic

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

### 9. Dynamic Task Engine

Each **species module authors** its tasks. The platform task engine **stores, assigns, and merges** them for the shared Tasks tile. Module homes show **only that species’ queue**.

#### Event-to-task mapping (owned by the module)

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

#### Queues (not one undifferentiated Today)

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

### 10. Analytics & KPI Pipeline

KPI **definitions and dashboards are per module**. Shared SQL helpers (ADG) are platform. Do not present a single mixed-species “herd KPI” as any species home.

Materialized views must be **split** (`mv_goat_kpis`, `mv_rabbit_kpis`, `mv_poultry_kpis`, `mv_sheep_kpis`, `mv_cattle_kpis`), not one `mv_animal_kpis` with a species column driving a generic UI.

Poultry KPIs are **flock-grain** (eggs per female housed, FCR, livability) **split by poultry_kind_code**. Never one “poultry KPI” that averages chickens with ducks.

#### KPI Calculation Functions

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

#### Materialized KPI Views

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

### 11. AI & Farm Copilot

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

#### Phase 1: Rule-Based Intelligence (no ML required)

- **Growth anomaly**: vs **same-species** cohort (never mix goat ADG with broiler FCR)
- **Reproductive anomaly**: separate rules per module (kidding vs kindling vs scan% vs PD% vs hatchability)
- **Health pattern**: cluster within a location **and** species (poultry: house-level mortality spike)
- **Feed cost alert**: optional farm-wide tool, not a species home widget

#### Phase 2: Richer analytics (still no farm ML host)

Prefer Kotlin cohort/MAD/z-score and optional on-device LiteRT. If a farm wants LLM-enriched explanation, use **BYO Model API** (Settings). Do **not** deploy XGBoost/Chronos as a farm-owned service. Optional later: Edge Function that only **proxies** a vendor API — still not a sidecar ML runtime.

#### Phase 3: Farm Copilot (LLM)

Copilot opens **from the current module** (or Insights with explicit module scope). Every tool call carries `species_code`. The model must not invent SQL.

- **Goat / rabbit / poultry / sheep / cattle:** same scoping rules as before — only that module’s records; poultry answers by kind; no fake individual bird stories for commercial flocks.

**Safety:** Cite source records. No autonomous treatment, cull, or sale. Drafts return to the **owning module** for Accept/Reject.

**Runtime:** In-app `CopilotEngine` + BYO OpenAI-compatible Model API (§12). Not Hermes.

**Embedded AI (required for field UX):** See [§12a](#12a-embedded-ai-module-charts-anomalies-model-api) and `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`. Feature pages ship **table + Vico chart**, flag **positive/negative** anomalies, and propose **corrective actions**. Model API is optional BYO; on-device rules always work offline.

---

### 12. Farm Copilot (in-app; Hermes deferred)

#### 12.0 Locked decisions

| Lock | Decision |
|------|----------|
| LLM backends | Configured **only** in Settings → AI & analytics (BYO OpenAI-compat). Presets: Groq, OpenRouter, Google AI Studio, OpenAI, Custom. |
| Hermes | Out of product footprint (free sidecar not viable). |
| Hermes task map | See `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` §0.2 — binding. |
| Farm data tools | **FarmToolRegistry** (in-app + Supabase). Same tool jobs as former Farm MCP; **MCP protocol not used**. |
| Empty Model API | Valid — Lane A charts/anomalies still ship. |

#### 12.0a Why Farm MCP is replaced (protocol only)

Farm MCP existed so **Hermes** (a remote process) could call farm tools over the MCP wire. Copilot now runs **inside the APK**, so MCP is an unnecessary hop. The **tool surface is kept** as `FarmToolRegistry` (names, species scoping, read-mostly + `propose_recommendation`). We are not removing farm intelligence tools — only the Hermes-facing MCP adapter.

Full write-up: AI spec §0.3.

#### 12.0b Hosting decision

| Option | Verdict for this product |
|--------|--------------------------|
| Hermes + Ollama on farm LAN / paid VM | **Out of footprint** |
| Hermes on a **free** sidecar | **Not viable** |
| Supabase Edge as Hermes host | **Impossible** |
| **In-app Copilot + Settings BYO Model API + FarmToolRegistry** | **Chosen** |

Hermes remains an optional personal experiment on someone’s own PC — not a Phase 8 deliverable.

#### 12.1 What owns what

| Farm OS (SoR) | Copilot (advisory) |
|---------------|-------------------|
| Supabase Postgres events/projections, animals, health, finance | Natural-language Q&A with citations |
| `:domain-ai` rules, KPI SQL in Supabase, charts | Explain those numbers in farmer language |
| Task engine, withdrawal blocks, RBAC | Draft briefings / recommendations; never post treatments/culls/sales alone |
| Android Compose UI | Optional: Owner-only notification of briefing tasks |

If Model API is unset or offline, recording still works; Copilot degrades to rule-evidence text / queued questions.

#### 12.2 Topology (product)

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

#### 12.3 FarmToolRegistry (locked; successor to Farm MCP tool surface)

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

#### 12.4 Skills & briefings

Ship versioned Markdown in-repo (`skills/goat-kidding-briefing.md`, …). Morning briefing: Supabase `pg_cron` or scheduled Edge Function writes summary tasks / push — **not** Hermes cron.

#### 12.5 Android UX

Copilot screen inside each species module (same component, different `species_code`). Citation chips open that module’s profile or flock sheet. Offline: queue the question or show Lane A evidence only.

#### 12.6 Hard rules

1. Structured farm data remains authoritative.  
2. Copilot cannot mutate biology/finance except `propose_recommendation` → human Accept.  
3. Isolation: `farm_id` + `species_code` (+ poultry kind when in flock context).  
4. Audit: `ai_audit` row (prompt hash, model, farm, module) — minimise PII.  
5. No Hermes URL in Settings — LLM backends only via Settings Model API fields (§12.0).

---

### 12a. Embedded AI module (charts, anomalies, Model API)

**Full specification:** `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` (companion — **Supabase + app only**).

#### 12a.1 Product requirement

Compatible feature pages (growth, milk, eggs, FCR, FAMACHA, SCC, etc.) must offer:

1. **Charts as an additional representation** — Table | Chart | Both (Vico).  
2. **Anomaly flags** — negative / positive / watch.  
3. **Corrective-action recommendations** — drafts only; never auto-treat/cull or invent doses.  
4. **Optional BYO Model API in Settings** — Groq/OpenRouter/OpenAI/custom; empty = full on-device product.

#### 12a.2 Three compute lanes

| Lane | Where | Role |
|------|--------|------|
| **A — Rules** | `:domain-ai` on device | Always-on baselines, z-scores, growth bands, egg drop %, FAMACHA — offline |
| **B — Small models** | LiteRT / ONNX in APK | Optional packs from Supabase Storage |
| **C — BYO Model API** | Settings URL (+ optional Edge proxy) | LLM enrich / explain; **not** a farm-hosted Chronos/XGBoost service |

Lanes A–C power **inline analytics**. §12 Copilot shares the same BYO Model API when configured.

#### 12a.3 Android modules

```
:domain-ai          Series, anomalies, recommendations, Copilot tool schemas
:feature-ai         Charts, anomaly UI, Copilot, Settings AI, review queue
:ai-client          OpenAI-compat + optional Supabase ai-proxy client
:ai-runtime-litert  Optional
:ai-runtime-onnx    Optional
```

#### 12a.4 Settings (LLM / Model API) — locked

- **Only** place to configure LLM backends  
- Mode: Hybrid (default) | On-device only | API preferred  
- Presets: Groq · OpenRouter · Google AI Studio · OpenAI · Custom  
- Keystore key **or** Supabase Edge proxy  
- `share_animal_ids` default off  
- No Hermes / Ollama / farm-analytics-microservice fields  

Full field list: AI spec §5 / §0.1.

#### 12a.5 Tooling (footprint-aligned)

| Need | Tool | Notes |
|------|------|-------|
| Charts | Vico | In-app |
| On-device | LiteRT / ONNX Runtime Mobile | Optional |
| LLM | BYO OpenAI-compat | User free tiers OK |
| Proxy | Supabase Edge Functions | Secrets in Vault |
| Agent host | **None** | Hermes deferred — free sidecar not viable |

#### 12a.6 Safety

- Species / poultry-kind isolation  
- No prescribing from anomalies or Copilot  
- Health formulary gates treatments  
- Minimise PII to third-party Model APIs  

---

### 13. Module dashboard & new animal modules

The first screen after login is a **module launcher**. Opening a species tile enters that animal’s own product — not a filtered generic animal list.

Shared tiles (Health, Finance, Tasks, Farm ops) are farm-wide tools. They must not replace the species module as the place where breeding, housing, and pedigree are designed.

Farms **enable** species tiles (`farm_enabled_modules`). A goat-only farm never sees empty poultry/sheep/cattle tiles.

#### 13.1 Dashboard information architecture

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

#### 13.2 Module catalog

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

#### 13.3 Adding a new animal module

Treat it as a **new product**, not a config row on an existing species:

1. Research that species’ biology, housing, recording (ICAR / national ID schemes where they apply).
2. Gradle `:feature-{species}` with its own home, reproduction flow, working object, pedigree method, KPI scorecard, protocol pack.
3. Implement `SpeciesModule` and register on the dashboard.
4. Reuse shared *infrastructure* (events, sync, RFID lookup, `animal_groups`) and optional *widgets* only if they fit.
5. Do not enable the tile until those screens exist. An empty filter on goats is not a sheep module. A flock filter on rabbits is not poultry.

**Build order after goat+rabbit MVP:** sheep (closest mammal recording to goats, but still its own IA) → cattle (NLIS + dairy/beef packs) → poultry (requires flock-first UX and hatchery; most different from the mammal modules). Platform `animal_groups` should land before poultry; sheep/cattle can use it for mobs/lots in the same drop.

#### 13.4 Android navigation

- `DashboardScreen` → `module.navGraph()` for that species.
- Deep link: `farm://module/goat/...`, `farm://module/health/...`, `farm://weather`, `farm://tips/{id}`.
- RFID/QR: identify species from the animal or group record, then open **that** module’s profile — never a species-neutral sheet as the main experience.

---

### 14. IoT & Hardware Integration

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

#### RFID Workflow (Field)

```
Worker scans RFID/QR → identity-platform resolves animal **or group** + species_code
  → Navigate into **that species module** (goat profile vs rabbit/litter vs poultry flock vs sheep/cattle)
  → Module quick actions only (goat: weight, FAMACHA…; poultry: daily sheet, mortality…)
  → Save to Room + outbox
```

---

### 15. Security, Auth & Multi-Tenancy

#### Auth: Keycloak (Recommended)

- Open source, self-hosted
- RBAC with fine-grained permissions
- **One realm for the product.** Tenancy is `farm_id` on the user (group/attribute) + PostgreSQL RLS. Do **not** create a Keycloak realm per farm (ops cost and SSO break).
- OAuth 2.0 / OIDC for the Android app (AppAuth)
- GitHub: [keycloak/keycloak](https://github.com/keycloak/keycloak)

#### Role-Permission Matrix

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

#### Multi-Tenancy

- Row-level security: every farm table has `farm_id`; policies use `current_setting('app.farm_id')` set by Ktor per request from the JWT
- Module APIs additionally filter `species_code`
- Workers can be granted any subset of species modules (goat-only, poultry+cattle, etc.)
- Meilisearch indexes must include `farm_id` and filter on every query; never a global animal search

---

### 16. DevOps & Infrastructure

#### Docker Compose (MVP)

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

#### CI/CD Pipeline

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

### 17. Open-Source Tools & Repos Reference

#### Core Framework & Infrastructure

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

#### Offline & Sync

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Room + outbox** | Local-first writes | AndroidX | Apache-2 |
| **SQLCipher (optional)** | Encrypted SQLite | [sqlcipher/android-database-sqlcipher](https://github.com/sqlcipher/android-database-sqlcipher) | BSD |
| **Synchro Kotlin client** | WAL-based Postgres sync | [trainstar/synchro](https://github.com/trainstar/synchro) | — |

#### Event Sourcing

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Custom Postgres event table** | MVP event ledger | In-repo | — |
| **Axon Framework** (optional later) | JVM aggregates/sagas | [AxonFramework/AxonFramework](https://github.com/AxonFramework/AxonFramework) | Apache-2 |

#### Genetics & Pedigree

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **PyAGH** | Inbreeding, relationship matrices | [zhaow-01/PyAGH](https://github.com/zhaow-01/PyAGH) | MIT |
| **ribd** (pedsuite) | Pedigree relatedness coefficients (R) | [magnusdv/ribd](https://github.com/magnusdv/ribd) | GPL-3 |
| **visPedigree** | Pedigree visualization + high-perf inbreeding (R) | [luansheng/visPedigree](https://github.com/luansheng/visPedigree) | MIT |

#### IoT & Hardware

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **CameraX** | Camera + QR | AndroidX Camera | Apache-2 |
| **ML Kit Barcode** | Barcode/QR decode | Google ML Kit | Apache-2 |
| **Vendor RFID SDKs** | Chainway / Zebra / Urovo | Vendor developer portals | Proprietary AARs |
| **Mosquitto** | MQTT broker for sensors | [eclipse/mosquitto](https://github.com/eclipse/mosquitto) | EPL-2 |

#### Search, Storage & Auth

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Meilisearch** | Animal/farm search | [meilisearch/meilisearch](https://github.com/meilisearch/meilisearch) | MIT |
| **MinIO** | S3-compatible file storage | [minio/minio](https://github.com/minio/minio) | AGPL-3 (self-host or licensed alternative) |
| **Keycloak** | Identity & access management | [keycloak/keycloak](https://github.com/keycloak/keycloak) | Apache-2 |

#### Reference Farm/Livestock Platforms

| Tool | Purpose | Repo / Link | Notes |
|------|---------|-------------|-------|
| **farmOS** | Web-based farm record keeping | [farmOS/farmOS](https://github.com/farmOS/farmOS) | Drupal-based; reference for data models |
| **Penzi Farm ERP** | Laravel livestock management | [maurice2428/PenziFarm](https://github.com/maurice2428/PenziFarm) | Reference for breeding/health workflows |
| **Cattleitics** | Cattle herd management | [ToastSeagers/Cattleitics](https://github.com/ToastSeagers/Cattleitics) | Reference for pedigree + pasture UX |
| **GENMON** | Livestock population monitoring | [Listed in awesome-agriculture](https://github.com/brycejohnston/awesome-agriculture) | Goat/sheep/cattle population genetics |
| **ICAR** | Recording guidelines (sheep, goats, cattle, poultry where published) | [icar.org](https://www.icar.org/) | Inform KPI definitions; do not copy web IA |
| **awesome-agriculture** | Curated ag-tech list | [brycejohnston/awesome-agriculture](https://github.com/brycejohnston/awesome-agriculture) | Discovery resource |

#### AI/ML & embedded analytics

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

#### Monitoring & DevOps

| Tool | Purpose | Repo / Link | License |
|------|---------|-------------|---------|
| **Grafana** | Dashboards & alerting | [grafana/grafana](https://github.com/grafana/grafana) | AGPL-3 |
| **Prometheus** | Metrics collection | [prometheus/prometheus](https://github.com/prometheus/prometheus) | Apache-2 |
| **Loki** | Log aggregation | [grafana/loki](https://github.com/grafana/loki) | AGPL-3 |

---

### 18. Phased Delivery Plan

#### Phase 1: Foundation (Weeks 1–8)
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

#### Phase 2: Reproduction (Weeks 9–14)
**Goal:** Lifecycle automation **inside each module**

- [ ] Goat module: heat/mating → pregnancy → kidding → kid records → lactation tasks
- [ ] Rabbit module: mating → nest box → kindling → **litter** → kit IDs when needed
- [ ] Rabbit breeding programme **full MVP** per `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`: 3×11 boxes, waves, notifications, nest sanitize/QR, cage pairing 2:11, COI+colour pairing, palpation/false-pregnancy, foster, photo journal, kit promote/retention, doe/buck scorecards, market planner, waitlist/contracts, multi-breed packs, cage cards PDF, ICS export, inventory bedding hooks
- [ ] No shared “reproductive calendar” that mixes kidding and kindling as one grid
- [ ] Each module’s expected-birth board on **that** module home

#### Phase 3: Growth & Production (Weeks 15–18)
**Goal:** Performance **per species** + chart/anomaly UX foundations

- [ ] Goat: weights, ADG, optional milk/lactation curves
- [ ] Rabbit: litter/grow-out weights, FCR, days to market
- [ ] Bluetooth scale in the **active module** session
- [ ] Cohorts = same species (never mixed-species growth overlay as default)
- [ ] Gradle: `:domain-ai`, `:feature-ai` (chart shells + Lane A rules)
- [ ] Growth (and milk/eggs where present): **Table | Chart | Both** (Vico)
- [ ] Lane A anomalies: negative / positive / watch + corrective-action drafts (Accept → task)
- [ ] No Model API required yet (offline rules only)

#### Phase 4: Health (Weeks 19–23)
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

#### Phase 5: Farm Operations (Weeks 24–28)
**Goal:** Field execution without a generic livestock IA

- [ ] Goat module: paddock/grazing plans
- [ ] Rabbit module: cage map + QR
- [ ] Shared Tasks tool = merged queue with `moduleId`
- [ ] Each module Today = species queue only
- [ ] RFID → module routing
- [ ] FEFO inventory (platform)

#### Phase 6: Finance (Weeks 29–33)
**Goal:** Cost objects that match biology

- [ ] Allocate to animal, litter, paddock, or farm in goat/rabbit MVP
- [ ] Flock/mob/lot cost objects when those modules (Phases 9–11) write `animal_groups`
- [ ] Sales/cull in the **module that owns the animal**
- [ ] P&L can roll up farm-wide; drill-down stays per module

#### Phase 7: Genetics & Breeding Intelligence (Weeks 34–38)
**Goal:** Intelligence inside each module

- [ ] PyAGH platform service
- [ ] Goat module: full tree 6+ gen, inbreeding warnings on **goat** mating plans
- [ ] Rabbit module: compact pedigree + breeder full-tree; rabbit breeding index
- [ ] DNA/parentage verification in the module that recorded the animal

#### Phase 8: Intelligence & Copilot (Weeks 39–46)
**Goal:** In-app Copilot + hybrid analytics **without** farm AI hosts

- [ ] Anomaly rules **per species** (and poultry **kind**); sync `analytics_anomalies` to Supabase
- [ ] Settings → **AI & analytics**: BYO Model API URL, model id, Keystore key **or** Supabase Edge proxy
- [ ] Modes: Hybrid / On-device only / API preferred
- [ ] In-app `CopilotEngine` tool-loop; allow-listed Supabase tools; module Markdown skills
- [ ] `ai_audit` rows; Accept/Reject recommendation drafts in owning module
- [ ] Optional LiteRT/ONNX packs from Supabase Storage
- [ ] **Do not** ship Hermes, Ollama, or a farm ML sidecar as part of this phase

#### Phase 9: Sheep product (after goat/rabbit MVP is stable)
**Goal:** `:feature-sheep` as its own OS

- [ ] Research pack: ICAR sheep, national EID, scanning codes (dry/single/twin/triplet)
- [ ] `animal_groups` type `mob` used by sheep home (not a goat list filter)
- [ ] Joining → scan board → lambing → marking → weaning
- [ ] Wool pack optional: shearing, crutching, micron
- [ ] Sheep health forms: footrot, flystrike, FAMACHA-as-sheep (not goat copy)
- [ ] `mv_sheep_kpis`; Copilot skill `sheep-lambing-briefing`
- [ ] Do not ship until lambing + scanning screens exist

#### Phase 10: Cattle product
**Goal:** `:feature-cattle` with dairy and/or beef packs

- [ ] NLIS/NAIT/official ID identifier types + movement events
- [ ] Heat/AI/ET → PD → calving; `genetic_dam` / `recipient_dam` for ET
- [ ] Dairy pack: parlour, milk meters, SCC, dry-off, milk withdrawal
- [ ] Beef pack: lots, weaning weights, days on feed
- [ ] `mv_cattle_kpis`; Copilot skill `cattle-calving-briefing`
- [ ] Do not clone sheep scanning UI for PD

#### Phase 11: Poultry product
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

### 19. Risk Register

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

### 20. Technical invariants

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

### 21. Research status by branch

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

### Appendix: Repository Structure

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



---

<a id="part-b"></a>

# Part B — Veterinary Expert Research Pack

*Source: `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md`*

---

## Farm OS — Veterinary expert research packs

**Companion to:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md`  
**Date:** 19 August 2026  
**Purpose:** Species-level clinical *recording*, scoring, reproduction, housing, KPIs, and red flags so each Android module behaves like a competent farm vet’s notebook — **not** a prescribing robot.

#### Hard rules (product = vet-expert, not vet-replacement)

1. **The attending veterinarian owns protocol packs** (vaccines, anthelmintics, withdrawals, notifiable-disease reporting). The app stores *what was done* and *when the hold ends*. It never invents a dose, product, or route.
2. **Copilot / Hermes** may explain recorded scores and cite ICAR-style definitions. It must not recommend a named drug or dose.
3. **FAMACHA®** is a licensed/certified system. The app records 1–5 scores and interval reminders. Shipping a colour chart in-app requires licence/training copy; do not treat the score as a diagnosis of *Haemonchus* alone (other anaemias exist).
4. **Red flags** open “Call vet / isolate” — they do not start a treatment wizard.
5. **Jurisdiction packs** (NLIS, NAIT, EU ear tags, USDA APHIS, notifiable lists) are plug-ins. This document names the *hook*, not every country’s law.
6. Numbers below are **defaults for engines and forms**. Farm config + attending vet override them.

---

### 0. Cross-cutting clinical model (all modules)

#### 0.1 Every health event (implement once, forms per species)

| Field | Why |
|-------|-----|
| Subject | `animal_id` and/or `group_id` |
| When / where / observer | Audit |
| Complaint / observation catalog | Species list (below), not free-text only |
| Severity | low / medium / high / **critical (red flag)** |
| Scores | BCS, FAMACHA, locomotion, FAMACHA, SCC, etc. as applicable |
| Differential *category* | e.g. “anaemia — parasite vs other”; not a locked diagnosis |
| Plan | Observe / sample / isolate / **vet consult** / treatment *if vet-authored protocol* |
| Withdrawal | meat / milk / **egg** until dates from **product label** entered by authorised role |

#### 0.2 Body condition scoring (do not mix scales)

| Species | Scale | Typical target | Palpation |
|---------|-------|----------------|-----------|
| Goat, sheep, dairy cattle | **1–5** (½ scores OK) | Breeding ~2.5–3.5; pre-parturition ~3–3.5 (small ruminants) | Loin spinous/transverse processes, ribs, sternum fat pad (goats/sheep). Hair/wool hides condition — must palpate. |
| Beef cattle | **1–9** | Breeding ~5 | Ribs 12–13, hooks/pins, tailhead, brisket |
| Rabbit | Flesh condition + coat; weight trend more useful than bovine BCS | Avoid obesity in pets; production does: adequate loin cover | Spine, ribs, pelvic bones |
| Poultry | Keel score / fleshing (flock sample) | Purpose-specific | Keel prominence; never mix broiler and layer targets |

**Engine rule:** store `bcs_scale` with the value (`five` vs `nine`). Never plot beef 5 next to dairy 5 as the same fatness.

#### 0.3 Shared vital / welfare red flags

| Finding | Action |
|---------|--------|
| Recumbent, unresponsive, open-mouth breathing (ruminant), severe haemorrhage | Critical → vet now |
| Neurological signs, abortion storm, sudden high mortality (poultry house) | Isolate + vet; possible **notifiable** (jurisdiction pack) |
| Rabbit: temperature &lt;38.0°C or &gt;40.0°C (Merck: normal ~39.6–40°C; &lt;100.4°F or &gt;104°F concerning) | Critical |
| Rabbit struggling + unsupported spine | Stop; lumbar fracture risk (Merck) |
| Milk SCC interpretation | Ignore samples **&lt;5 days in milk** for herd genetic/udder-health analyses (ICAR udder health) |

#### 0.4 Zoonoses / occupancy (record + PPE prompt, not diagnosis)

Q fever (*Coxiella*) and chlamydiosis (sheep/goats/cattle abort), *Salmonella* / campylobacter (poultry, calves), cryptosporidium (calves), orf (sheep/goats), ringworm. Worker-facing copy: hygiene, pregnant-worker policy, abortus handling — **farm SOP**, not Copilot medical advice.

---

### 1. Goat module — expert pack

**Sources:** ICAR Guidelines Section 21 (sheep *and* goats, June 2021); Alabama Extension BCS goats/sheep; FAMACHA / WormBoss / ACSRPC; gestation literature (~145–155 d, breed/parity effects).

#### 1.1 Reproduction (clinical timeline)

| Item | Default | App behaviour |
|------|---------|----------------|
| Oestrus cycle | ~18–24 days | Heat board optional |
| Gestation | **150 d** (range **145–155**; minis often shorter; multiples often earlier) | Farm override; “watch from day 140” task |
| Pregnancy check | Blood / ultrasound / no-return; typical booking **~day 40–45** | Task from mating; method recorded |
| Late gestation nutrition | Last ~6 weeks (~day 108+ on 150 d) | Task, not a ration calculator |
| Pre-kidding | Clean dry pen, colostrum plan | Day −7 |
| Kidding assistance | Record: unassisted / assist / vet | ICAR-style lamb/kid survival later |
| Colostrum | Time-to-suckle, failure-of-passive-transfer risk if delayed | Observation, not IgG lab unless entered |
| Rebreed | Farm policy (often not twice/year without BCS) | Do not default to 2 kiddings/year |

**Language:** doe, buck, kid, kidding, lactation (dairy pack).

#### 1.2 ICAR-aligned recording (goats + kids)

From ICAR Section 21 (growth / reproduction / maternal, sheep **and** goats):

- Weights: birth; **~30 d**; **42–120 d**; post-wean; 12-month; adult pre-mating.
- Pregnancy scan (if used): **~day 70 (60–90)** foetal count 0/1/2/3/4+. Same trait family as sheep; goat UI still says *kids*, not lambs.
- Survival: lambs/kids born alive vs reared; alternatively foetuses scanned minus reared.
- Standardise weights to age (ICAR Annex A) for genetic comparison — genetics service, not the field form.

**KPI (goat home):** kidding interval; kids born/weaned per doe; kid survival; ADG (first–last weight); dairy: yield, fat/protein, SCC if milk-recorded (ICAR milk analysis covers **cow, goat, ewe** milk).

#### 1.3 Health — goat forms (not cattle screens)

| Domain | Record | Expert notes for UX |
|--------|--------|---------------------|
| Parasites | **FAMACHA 1–5** (lower eyelid conjunctiva vs card). Guide: 1–2 usually no drench; **4–5 treat per vet protocol**; 3 = risk-based (young, lactating, poor BCS, poor feed). Recheck ~weekly in challenge. Herd check **every 2–3 weeks** in season (UT FAMACHA sheet). | Selective treatment = refugia. Score 3 policy is **vet/farm**, not hardcoded treat-all. Other anaemias exist. |
| BCS | 1–5, palpated loin | Target breeding 2.5–3.5; pre-kidding 3–3.5 (ACES) |
| Clostridial risk | Protocol-pack vaccine class (often “enterotoxemia/tetanus” *as a pack name*) timed **~1 month pre-kidding** so colostrum antibodies rise — **product chosen by vet** | Task only |
| Mastitis / CAE / CL / Johnes / orf | Observation catalog + lab attachments | Dairy: milk withdrawal |
| Foot / CAE arthritis | Lameness score | |
| Metabolic | Pregnancy toxaemia / hypocalcaemia **suspect** in late gestation / early lactation thin or overfat does | Red flag recumbency |

**Dairy goat pack:** milk recording fields aligned with ICAR milk analysis (fat, protein, SCC). Do not reuse cattle 305-day assumptions without farm lactation-length config.

#### 1.4 Housing / movement

Paddock, barn, pen, quarantine. Grazing rest for parasite control is a **paddock task**, not a goat cloned from cattle feedlot.

#### 1.5 Red flags (goat)

Dystocia &gt;30–60 min with no progress (farm SOP), kid not nursing, FAMACHA 5, recumbent late-pregnant doe, neurological, abortion outbreak.

---

### 2. Rabbit module — expert pack

**Sources:** Merck Vet Manual — Management of Rabbits (review Jul 2021, update Apr 2025, Mayer); Merck breeding/reproduction; Peace Corps / MSU commercial rabbit production; USU producer guide.

#### 2.1 Biology & handling (safety is clinical)

- Never lift by ears. Support rump; hind-limb kick → **L7–S1 fracture/luxation**.
- Normal temperature **~39.6–40.0°C (100.5–104°F)**; outside → concern (Merck).
- Sexing: depress genitalia; testes descend **~10–12 weeks**.
- Oral/dental exam is part of rabbit medicine; production UI still needs **malocclusion / inappetence** as red flags (GI stasis risk).

#### 2.2 Reproduction (litter is the working object)

| Item | Default | App |
|------|---------|-----|
| Gestation | **31–33 d** (Merck); small litters often longer | If no kindling by **day 32**, prompt **vet** (Merck: induction / dead litter risk after ~34) |
| Nest box | **Day 28–29** after mating. Too early → soiling | Task; do not move box after placement (MSU) |
| Kindling | Count live / stillborn; foster in **first 3 days** (Merck) | Litter record |
| Nursing | Does nurse **1–2×/day**, &lt;3 min | “Kits cold/empty” check, not hourly nursing expectation |
| Weaning | ~**4–6 weeks** (Merck ~4–5 prod / ~6 domestic); commercial meat often **28 d** wean on intensive rebreed | Farm purpose pack |
| Rebreed | Commercial tables: 14–21 d post-kindle intensive; **35 d** kinder | BCS/condition gate |
| False pregnancy | Common | Status, don’t assume pregnancy |

**KPIs:** kits born/weaned per doe/year; pre-wean mortality; litter size; days to market; FCR if feed allocated to cage/doe.

#### 2.3 Health catalog (rabbit-specific)

| Catalog | Notes |
|---------|--------|
| Pasteurellosis (“snuffles”) | Nasal/ocular discharge, sneezing; recovered animals often **carriers** (MSU). Colony outbreak view. |
| GI stasis / inappetence | Emergency in rabbits |
| Coccidiosis, enteritis | Young kits |
| Sore hocks | Wire floors |
| Myxomatosis / RHD (RHDV) | Vaccination = **jurisdiction + vet pack**; biosecurity |
| Mastitis | Nursing does |
| Dental | Malocclusion |

No FAMACHA. No milk withdrawal unless dairy rabbits (rare) — default meat hold from treatment label.

#### 2.4 Housing & breeding programme

Cage/hutch map + QR. Nest box as a **named, tracked asset** (not only a task). Colony vs single-doe cages are place types, not a goat paddock UI.

**Operational programme (KudBat-style):** 11 does + 1 buck per cage; nest schedule and 1:11 / paired 2:11 ratios — `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md`. Full app module (waves, notifications, cage pairing, COI mating gates) — `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`.

---

### 3. Sheep module — expert pack

**Sources:** ICAR Section 21; MLA/AWI pregnancy scanning (litter size ~day 70; dry/single/twin); sheep gestation **~147 d (142–152)**; FAMACHA (sheep+goats); AWI Visual Sheep Scores (breech wrinkle, fleece rot 1–5); Footrot (AWI — often **notifiable** by state).

#### 3.1 Reproduction

| Item | Default | App |
|------|---------|-----|
| Joining | Ram in / ram out dates on **mob** | Joining event on group |
| Gestation | **147 d** | Override by breed |
| Scan | **~day 70 (60–90)**; record **0 / 1 / 2 / 3+** not only pregnant/dry | ICAR + MLA: litter size drives nutrition and lambing paddock. Scanning for multiples is the expert workflow; pregnant/empty only is weaker. |
| Accuracy (context) | MLA: pregnancy status often ~91–98% agreement; fetal *count* vs lambs born lower (~85–88%) partly due to **unobserved lamb loss** | UI: scan ≠ lambing count; survival = scan minus reared (ICAR) |
| Pre-lamb | Vaccination/nutrition protocol pack; lambing paddock; mob size for multiples | Tasks from joining + scan result |
| Lambing | Born, live, dead, birth type (S/T/Tr) | Marking/tailing as later events |
| Weaning | Farm | Weights per ICAR ages |

**Scan board (first-class sheep UX):** columns Dry | Single | Twin | Triplet+ → feed budget and paddock. Empty ewes: cull/re-mate policy is farm, not hardcoded.

#### 3.2 Health

| Domain | Record |
|--------|--------|
| FAMACHA | Same 1–5 system as goats; sheep often show deeper red at score 1 (WormBoss) |
| BCS | 1–5 (½). Hill vs lowland targets differ (Scotland FAS: hill ewes not returned to hill &lt;2). Rams **~3.5–4** pre-tupping. ~1 BCS unit ≈ **12% liveweight** (FAS). |
| Footrot | Lesion/score; **jurisdiction notifiable** flag |
| Flystrike | Breech/body; Visual Sheep Scores wrinkle & fleece rot **1–5** (AWI/MLA) as *risk traits*, not a diagnosis |
| Pregnancy toxaemia | Late gestation multiples, poor BCS |
| Clostridial / CLA | Vet pack |

**Wool pack:** micron (mean fibre diameter), staple, fleece weight, classing. Micron is the primary value driver (industry standard).

#### 3.3 ICAR KPIs

Lambs weaned per ewe joined; scanning %; lambing %; survival scanned→marked; weaning weight age-adjusted; wool kg and micron if enabled.

**Do not** copy goat “kidding interval” labels. Sheep commercial life is **mob + drop**.

---

### 4. Cattle module — expert pack

**Sources:** ICAR calving traits (gestation **~280 d** dairy); ICAR dairy milk recording & udder health (SCC; IDF **&gt;200,000 cells/ml** often used as subclinical mastitis threshold); Wisconsin/UT/SDSU PD methods; MLA weaner throughput (breed by **~d 82** postpartum for 365-day calving interval).

#### 4.1 Reproduction

| Item | Default | App |
|------|---------|-----|
| Gestation | **~280–283 d** (ICAR ~280 dairy; beef similar, breed variation) | Farm/breed override |
| Heat / AI / ET | Record method, semen/sire, **genetic dam vs recipient** (ET) | Pedigree `genetic_dam` / `recipient_dam` |
| PD | Ultrasound often **~d 28–32**; rectal palpation often **~d 35+**; PAG blood/milk **~d 28–32** | Record method + gestational age estimate. **Reconfirm** if PD &lt;~50 d (embryonic loss common). |
| Calving | ICAR: stillbirth, **calving ease**, calf size, gestation length, sex, liveability. Live calves ID **within 48 h** where I&R requires; stillbirths need **dummy ID** if no official tag | Calving form |
| Calving interval | Days between calvings | Dairy KPI |
| Beef 365-d | Conception by **~82 d** post-calving | Task/window |
| Dry-off (dairy) | **From next expected calving − dry period** (often ~45–60 d), not from this calving + 223 | Already corrected in master plan |

**PD ≠ sheep scan.** Cattle PD is pregnant/open ± twins ± age of fetus. Sheep scan is litter-size nutrition.

#### 4.2 Dairy pack

| Record | Notes |
|--------|--------|
| Milk kg, fat %, protein %, lactose, urea optional | ICAR milk recording |
| SCC | Periodical with unique animal + sample date. Convert to **SCS** (log) for analysis. Skip **DIM &lt; 5** for many analyses (ICAR). First-parity healthy often ~50–100k; inflammation &gt;1M. IDF-style **200k** threshold as *configurable* flag, not a diagnosis. |
| Clinical mastitis | Direct health: quarter, secretion change, systemic signs, culture if any. Distinct from high SCC. |
| Parlour session | Meter → cattle module save; milk **withdrawal** from treatment label |
| Dry cow therapy | Vet protocol pack only |

#### 4.3 Beef pack

BCS **1–9**, target ~5 at breeding. Weaning weight; days on feed / lot close-out; ADG. Lots = `animal_groups` accounting census or roster.

#### 4.4 Health catalog (cattle)

Metabolic (milk fever, ketosis), mastitis, lameness/locomotion (dairy 1–5 M-score common), BVD/IBR/Lepto as **vaccination pack names**, calf scours (crypto/rota/corona/E. coli *categories*), BRD. Notifiable: jurisdiction (e.g. FMD, TB).

#### 4.5 Identification

Hook: **official EID** (NLIS AU, NAIT NZ, EU, etc.). App stores identifier type + movement events. Do not hardcode only NLIS.

---

### 5. Poultry module — expert pack (all kinds)

**Sources:** Mississippi State Extension hatchery tables; Oklahoma / USU incubation; Aviagen-style broiler management principles (all-in/all-out, biosecurity); hen-housed vs hen-day egg metrics (industry standard).

#### 5.1 Working object

Commercial: **flock + house**. Individuals: breeders / bands only. Census `head_count` + daily mortality/culls/eggs/feed.

#### 5.2 Incubation & hatchery (forced-air defaults)

MSU Extension (game bird & small flock). Still-air: add ~2–3°F. Lockdown = stop turning.

| Kind | Incubation (d) | Stop turning (day) | Notes |
|------|----------------|--------------------|--------|
| Chicken | 21 | 18 | Dry-bulb ~100°F small machines / ~99°F large |
| Turkey | 28 | 25 | ~99°F |
| Duck (common) | 28 | 25 | Waterfowl humidity higher in lock-down |
| Muscovy | **35–37** | 31 | Not “duck 28” |
| Goose | **28–34** | 25 | Seasonal lay common |
| Guinea fowl | 28 | 25 | Keets: heat critical |
| Peafowl | 28–30 | 25 | Custom kind OK |
| Coturnix quail | **17** | 15 | Bobwhite ~23–24 |
| Pigeon | **17** | 15 | Loft/pair object |

Hatchery KPI: **hatchability** = chicks / eggs set; candling fertility vs mid-dead. Do not default 21 d for every set.

**Brooding:** first-week mortality is a standard broiler KPI; sample weights; water/feed.

#### 5.3 Layer vs meat vs breeder (purpose packs)

| KPI | Definition (implement exactly) |
|-----|--------------------------------|
| Hen-day egg production | eggs / hens **alive that day** |
| Hen-housed egg production | eggs / hens **placed** (includes mortality penalty) |
| Peak % | max hen-day in a rolling window |
| FCR | feed / gain or feed / egg mass — **state which** |
| Livability | 100 − mortality − culls (define culls) |
| Broiler days to target wt | farm target, often ~32–42 d modern genetics (strain handbook) |
| Uniformity | CV of sample weights |

Never average chicken and duck FCR.

#### 5.4 Health & biosecurity (flock forms)

| Record | Expert UX |
|--------|-----------|
| Daily mortality / culls | Spike vs 7-day baseline → Copilot *investigate house* |
| All-in / all-out | Placement + empty days (downtime often cited ≥10–14 d between cycles — farm pack) |
| Vaccination | **By kind + purpose** calendar (ND, IB, IBD, etc. as pack *slots*, products by vet) |
| Water / NH3 / temp | House sensors; NH3 welfare often discussed around **~25 ppm** as a damage threshold in literature — configurable alarm, not a diagnosis |
| Salmonella / AI / ND | Notifiable hooks |
| Wet litter (waterfowl) | Duck/muscovy/goose |

**Egg withdrawal** on treatments. No FAMACHA, no pregnancy table.

#### 5.5 Kind-specific clinical notes

- **Chicken:** ND, IB, IBD, coccidiosis, Marek (layers/breeders) as catalog.
- **Ducks/Muscovy:** water access; duck viral hepatitis / duck plague as catalog where relevant; muscovy longer hatch.
- **Guinea:** keet brooding; ranging; different vax pack from chickens.
- **Turkey:** slower meat cycle; separate vax; histomoniasis risk if with chickens (record mixed-species housing as **biosecurity warning**).
- **Goose:** seasonal lay; pond/pasture.
- **Quail:** fast sexual maturity (~6–8 weeks coturnix); small-egg counts.
- **Pigeon:** loft, pair bond, squab harvest ~4 weeks; incubation ~17 d.

---

### 6. Intelligence (vet-expert Copilot)

For **every enabled module**, Copilot may:

- Explain a **recorded** score (FAMACHA 4 = pale conjunctiva / anaemia *category*).
- Compare animal or flock to **same species (and poultry kind)** cohort.
- Cite KPI formulas (ICAR lamb/kid survival, hen-housed eggs, calving interval, SCC flag).
- List overdue protocol-pack tasks.

Copilot must not:

- Choose an anthelmintic, antibiotic, or dose.
- Equate sheep scan with cattle PD.
- Treat commercial poultry as 5,000 patients with individual stories.
- Ignore embryonic-loss caveat on early cattle PD.

Skills to ship with each module: `goat-kidding-briefing`, `rabbit-kindling-briefing`, `sheep-lambing-briefing`, `cattle-calving-briefing`, `poultry-house-briefing` (kind-aware).

---

### 7. Protocol pack schema (data, not prose)

```json
{
  "id": "goat-dairy-temperate-v1",
  "species": "goat",
  "jurisdiction": "optional",
  "attendingVet": "required before enable",
  "vaccines": [{ "slot": "clostridial_prepartum", "offsetDays": -30, "from": "expected_birth" }],
  "parasite": { "famacha": true, "score3Policy": "vet" },
  "withdrawals": "from_product_label",
  "notifiable": ["list_from_jurisdiction"]
}
```

No default pack is “complete” until a vet (or licensed advisor) accepts it for that farm.

---

### 8. Source list (this research pass)

| Topic | Source |
|-------|--------|
| Sheep/goat growth, scan ~d 70, survival, standardised weights | [ICAR Guidelines Section 21](https://www.icar.org/Guidelines/21-Meat-reproduction-and-maternal-traits-in-sheep-and-goats.pdf) (June 2021) |
| Dairy milk, SCC, DIM&lt;5, mastitis vs SCC | [ICAR milk recording](https://www.icar.org/Guidelines/02-Overview-Cattle-Milk-Recording.pdf); [ICAR udder health](https://www.icar.org/Guidelines/07.3-Functional-traits-Udder-health-in-dairy-cattle.pdf); [ICAR milk analysis](https://www.icar.org/Guidelines/12-Milk-Analysis.pdf) (cow, goat, ewe) |
| Calving traits, ~280 d gestation, stillbirth ID | [ICAR calving traits](https://www.icar.org/Guidelines/07.6-Functional-traits-Calving-Traits-in-Dairy-Cattle.pdf) |
| Sheep scan for litter size | MLA L.LSM.0021 / AWI pregnancy scanning reports |
| FAMACHA 1–5, *H. contortus*, selective drench | WormBoss; University of Tennessee FAMACHA factsheet; ACSRPC / URI training |
| Goat/sheep BCS 1–5 | Alabama Extension; Scotland FAS sheep BCS |
| Beef BCS 1–9 | MU Extension / Purdue AS-550 |
| Rabbit handling, temp, reproduction, nest box | Merck Vet Manual (Mayer); Merck breeding; MSU commercial rabbit PDF |
| Incubation by species | [MSU hatchery guide](https://extension.msstate.edu/agriculture/livestock/poultry/hatchery-management-guide-for-game-bird-and-small-poultry-flock-owners) |
| Broiler all-in/all-out | Commercial broiler management handbooks (e.g. Aviagen Ross) — strain-specific targets stay in farm config |
| Cattle PD timing | UW Livestock; SDSU; UT Beef |
| Beef calving interval / d 82 | MLA More Beef from Pasture — weaner throughput |
| Flystrike visual scores | AWI/MLA Visual Sheep Scores |
| Footrot | AWI footrot (notifiable variation) |
| Wool micron | Industry fibre-diameter standard |

**Still not a substitute for:** country medicine labels, full WOAH listed-disease procedures, strain-specific broiler handbooks, or a signed VCPR (veterinarian–client–patient relationship).

---

*End of veterinary expert research packs.*



---

<a id="part-c"></a>

# Part C — Health Module Spec

*Source: `FARM_OS_HEALTH_MODULE_SPEC.md`*

---

## Farm OS — Shared Health module (all species)

**Companion to:** technical master plan + `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md`  
**Date:** 19 August 2026  
**Android:** `:feature-health` plus dashboard **Tip of the day** and **Weather** (this file).

This module is **farm-wide** (one Health tile) but **species-aware**: opening a goat uses goat forms; a duck flock uses poultry-kind forms. It is not a mixed “sick animals list” as the home of goats or rabbits.

#### Product law (non-negotiable)

| The app **does** | The app **does not** |
|------------------|----------------------|
| Ship **vaccination schedule templates** (disease slots, timing, route class) | Invent a brand, batch, or **dose** |
| Catalog **common diseases** with signs, first response, prevention | Diagnose from a photo and start a drug |
| Hold a **farm formulary** of products the vet approved (name, route, withdrawal days from **label**) | Let Copilot or a worker pick “the usual antibiotic” |
| Record treatments, withdrawals (meat / milk / **egg**), tasks | Treat without a veterinarian–client–patient relationship (VCPR) |
| Supportive **first aid** the stockperson can do while waiting for the vet | Publish mg/kg tables for farmers to copy |

Every schedule below is an **example template** from extension / Merck-style programs. The farm’s attending veterinarian **accepts or edits** a pack before the tile generates due dates.

---

### 1. Health information architecture

```
Dashboard
  ├─ Weather widget  →  Weather week (Open-Meteo)
  ├─ Health tip of the day  →  tip detail (optional) or Health home
  └─ ❤ Health tile  →  Health home (shared tool)
         ├─ Today: due vaccinations, withdrawals, FAMACHA window, flock mortality
         ├─ Record observation / treatment (species form from context)
         ├─ Schedules (by enabled species / poultry kind)
         ├─ Disease library (read-only education + “start record”)
         ├─ Formulary (vet-approved products + stock)
         └─ Outbreak / biosecurity (location + species)
```

Deep links: `farm://module/health/...`, `farm://weather`, `farm://tips/{id}`.

Species modules keep **in-context** health (goat FAMACHA on the goat profile). The Health tile is the **calendar, library, formulary, and cross-species withdrawals**.

---

### 2. Data model (additions)

```sql
ALTER TABLE farms ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE farms ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE farms ADD COLUMN timezone TEXT DEFAULT 'UTC';

CREATE TABLE health_protocol_packs (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms(id),
    species_code TEXT NOT NULL,
    poultry_kind_code TEXT,
    name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('draft', 'vet_accepted', 'retired')),
    accepted_by_vet TEXT,
    accepted_at TIMESTAMPTZ,
    body JSONB NOT NULL, -- slots, offsets, optional products
    UNIQUE (farm_id, species_code, poultry_kind_code, name)
);

CREATE TABLE health_schedule_slots (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES health_protocol_packs(id),
    slot_code TEXT NOT NULL,       -- cdt_prepartum, nd_day1, ...
    disease_codes TEXT[] NOT NULL,
    offset_rule JSONB NOT NULL,    -- {from:'expected_birth', days:-28} or {from:'age_days', days:42}
    route_class TEXT,              -- sq, im, in, oral, spray, water, wing_web, in_ovo
    is_core BOOLEAN DEFAULT true,
    notes TEXT
);

CREATE TABLE disease_catalog (
    code TEXT PRIMARY KEY,
    species_codes TEXT[] NOT NULL,
    poultry_kinds TEXT[],          -- null = all kinds / n/a
    display_name TEXT NOT NULL,
    signs TEXT NOT NULL,
    first_aid TEXT NOT NULL,
    prevention TEXT NOT NULL,
    vet_class TEXT NOT NULL,       -- anthelmintic, antibiotic, vaccine, fluids, surgery, none
    red_flag BOOLEAN DEFAULT false,
    zoonotic BOOLEAN DEFAULT false,
    notifiable_hint BOOLEAN DEFAULT false
);

CREATE TABLE formulary_items (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms(id),
    product_name TEXT NOT NULL,
    active_ingredient TEXT,
    species_codes TEXT[] NOT NULL,
    vet_class TEXT NOT NULL,
    route_class TEXT,
    meat_withdrawal_days INT,
    milk_withdrawal_days INT,
    egg_withdrawal_days INT,
    label_notes TEXT,
    inventory_item_id UUID REFERENCES inventory_items(id),
    vet_approved BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE health_tips (
    id UUID PRIMARY KEY,
    module_ids TEXT[] NOT NULL,    -- goat, rabbit, poultry, sheep, cattle, weather, general
    season TEXT,                   -- any, wet, dry, heat, freeze
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    source TEXT,
    sort_key INT
);

CREATE TABLE health_tip_impressions (
    farm_id UUID NOT NULL REFERENCES farms(id),
    tip_id UUID NOT NULL REFERENCES health_tips(id),
    shown_on DATE NOT NULL,
    PRIMARY KEY (farm_id, shown_on)
);
```

Treatments already exist; they **must** reference `formulary_items.id` when a medicine is given (not a free-typed drug name from a worker).

---

### 3. Vaccination schedule templates

**Core vs optional:** Core slots generate tasks when the pack is `vet_accepted`. Optional slots are off until the vet enables them (local risk).

#### 3.1 Goats and sheep — CDT is the universal core

Sources: MSU Extension CDT; Mississippi State M2484; NMSU B-127; UF/IFAS EDIS VM272 (2025).

**Core — Clostridium perfringens C & D + tetanus (CDT / CD&T)**

| Who | Timing (template) | Notes |
|-----|-------------------|--------|
| Pregnant doe / ewe | **2–6 weeks** (MSU often **~4 weeks**; NMSU **2–4 weeks**) pre-kidding/lambing | Passive immunity via colostrum |
| Kids / lambs from **vaccinated** dam | First dose **6–8 weeks** of age (MSU) **or ~30 days** (MS State sheet) then booster **3–4 weeks** later | Pick one template in the pack; do not fire both |
| Kids / lambs from **unvaccinated** dam or no colostrum | Start **1–3 weeks** of age; **two** boosters 3–4 weeks apart (EDIS) **or** first week + booster 4 weeks (MS State) | Pack chooses |
| Adults | **Annual** booster | Align with prepartum when possible |
| Docking / castration / disbudding | Tetanus **antitoxin** only if dam not vaccinated (NMSU example) | Optional slot; vet |

**Route class:** subcutaneous (tented skin, axillary / behind elbow — training copy, not a video substitute).

**Optional slots (vet-on):** soremouth (orf) where endemic; CLA/caseous; rabies if local law; footrot (sheep, some regions); pneumonia pasteurella; abortion pathogens (campylobacter, chlamydia, toxoplasma) **by region**; selenium/vitamin E where white-muscle disease occurs (NMSU optional — **not** a default medicine).

**Deworming is not a calendar vaccine.** Use FAMACHA / FEC / vet protocol — not “worm every 30 days” (resistance).

#### 3.2 Cattle — dairy and beef templates

Sources: OSU dairy herd health fact sheet; MSD/Merck beef vaccination programs; OSU sample spring-calving protocol. **MLV vs killed:** killed (or label-permitted) products if the animal is **pregnant**; do not use naïve-cow MLV IBR/BVD on pregnant cattle (protocol note). Give MLV **≥1 month before breeding** when using live IBR/BVD (MSD).

**Calves / heifers (example skeleton)**

| Window | Core slots | Optional |
|--------|------------|----------|
| Day 1–7 | Colostrum **3 L within 1 h, 6 L in 24 h** (OSU dairy example — farm litres config) | Intranasal IBR-PI3 |
| Weaning / pre-group | MLV **IBR, BVD, PI3, BRSV** + **7-way clostridial** | Mannheimia/Pasteurella, lepto |
| 6–10 months | Repeat respiratory MLV; clostridial; **brucellosis** only where **law** requires accredited vet | 5-way lepto |
| Pre-breeding (~3–4 weeks) | Respiratory + lepto; vibrio/campylobacter if **natural service** | |

**Adult cows (example)**

| Window | Core slots |
|--------|------------|
| Annual / pre-breeding | IBR-BVD-PI3-BRSV + 5-way lepto (killed if pregnant) |
| 40–60 d pre-calving | Killed respiratory + lepto; **scour** pack (rota, corona, *E. coli*, Cl. perfringens C&D) if used |
| ~3 weeks pre-calving | Scour booster if pack uses two-shot |
| Dry-off (dairy) | Killed respiratory/lepto as pack; **coliform mastitis** bacterin optional; dry-cow therapy = **vet only** |

Bulls: same herd respiratory/clostridial/lepto; **no brucellosis vaccine**.

#### 3.3 Rabbits — jurisdiction-dependent viruses

Sources: Rabbit Welfare Association (UK); VCA; Merck-related practice notes.

| Slot | Typical template | Availability |
|------|------------------|--------------|
| Myxomatosis + RHDV1 + RHDV2 | From **~5–7 weeks**; **annual** (sometimes 6–12 months by product/risk) | Common UK/EU combination vaccines; **US myxomatosis vaccine often unavailable** — pack `jurisdiction` |
| Extra RHDV2 | 2+ weeks apart from combination if vet requires | High-virulence strain products — vet |
| Pasteurella bacterin | Uncommon / inconsistent; not core | Optional |

**No colostrum CDT analogue.** Biosecurity + insect control (fleas/mosquitos) are first-class prevention tasks.

#### 3.4 Poultry — by kind and purpose (Merck, Apr 2023 / Sept 2024)

Programs **vary by challenge**. These are **starter templates**; the vet localises strains (IB variants, AI, etc.).

**Broiler chickens (Merck example)**

| Age | Slot | Route class |
|-----|------|-------------|
| In ovo 17–19 d **or** day 1 | Marek | In ovo / SC |
| Day 1 | Newcastle; infectious bronchitis | Coarse spray |
| 14–21 d | ND, IB, IBD (Gumboro) | Water or spray (optional 2nd ND/IB) |

**Commercial layers (Merck example, abbreviated)**

| Age | Slot | Route class |
|-----|------|-------------|
| Day 1 | Marek | SC |
| 14–21 d | ND/IB; IBD | Water |
| 5 wk, 8–10 wk, 12–14 wk, 16–18 wk | ND/IB boosters | Water/spray/aerosol |
| 10–12 wk | AE, fowlpox, ILT | Wing web / eye (local) |
| 10–14 or 18 wk | *M. gallisepticum* | **Regulated/prohibited some states** |
| 18 wk or every 60–90 d | ND/IB inactivated | Parenteral |

**Turkeys (Merck where diseases common)**

ND at ~2–3, 9–10, 15 wk (market); hemorrhagic enteritis ~4 wk; fowl cholera ~6 and 12 wk. Breeders continue cholera, ND, erysipelas, pox, AE. **Do not spray ND on birds already in respiratory disease** — use milder water B1 (Merck).

**Ducks (Merck)**

| | Ducklings (commercial) | Breeders extra |
|--|------------------------|----------------|
| Day 1 | *Riemerella anatipestifer* aerosol live | |
| 10–14 d | RA drinking water | |
| 3 wk | RA bacterin SC | |
| 4 wk+ | | Duck viral hepatitis; duck viral enteritis |
| 10 & 20 wk | | RA + DVH before lay (~24 wk Pekin) |
| Slaughter | **No vaccine within 21 d of slaughter** (Merck duckling note) | |

**Guinea, goose, quail, pigeon, muscovy:** no Merck table in this pass — use **chicken or duck pack as nearest neighbour** only after vet sign-off (guinea ≠ chicken ND program by default). Store as `poultry_kind_code` packs.

Drinking-water vaccination: withhold water **1–2 h**, consume vaccine within ~2 h (industry practice) — show as a **task checklist**, not a silent assumption.

---

### 4. Disease library + first aid vs vet class

`vet_class` drives which **formulary** items can attach. Worker UI: first aid + “Call vet”. Vet/manager: attach approved product.

#### 4.1 Goats & sheep (shared catalog, copy differs)

| Code | Signs (short) | Farmer first aid | Vet class | Red flag |
|------|----------------|------------------|-----------|----------|
| enterotoxemia_cd | Sudden death, convulsions, bloated, top kids/lambs on rich feed | Isolate remaining; **stop** sudden grain; call vet | vaccine (prevent); antitoxin/support **vet** | Yes |
| tetanus | Stiffness, lockjaw, after wounds/castration | Quiet dark; **vet now** | vaccine / antitoxin | Yes |
| haemonchus | FAMACHA 4–5, bottle jaw, weakness | Shade, water; do **not** blanket-drench the mob | anthelmintic **per FEC/FAMACHA protocol** | FAMACHA 5 |
| pregnancy_toxaemia | Late gestation, down, multiples, poor BCS | Prop up, offer energy **per vet SOP**; don’t force grain | fluids / dextrose **vet** | Recumbent |
| hypocalcaemia | Down around parturition | **Vet** — calcium is a drug | calcium **vet** | Yes |
| mastitis | Hot udder, clots, sick dam | Milk out if dairy SOP; isolate kids/lambs from bad milk | antibiotic **vet** + NSAID **vet** | Systemic |
| orf | Scabby mouth/teats | Gloves (zoonotic); isolate | supportive; vaccine optional endemic | |
| footrot | Lame, interdigital | Isolate; dry ground | antibiotic/footbath **vet pack**; **notifiable** some regions | |
| flystrike | Maggots, wool stain | Clip/clean **if trained**; shade | insecticide **label**; vet if shock | Yes |
| cl_caseous | Abscesses | Do not lance in the kitchen; biosecurity | vet / cull policy | |
| pneumonia | Cough, fever, dyspnoea | Dust-free, dry, isolate | antibiotic **vet** | Open-mouth |
| abortion_storm | Multiple abortions | PPE, **pregnant workers off**; bag fetuses | lab + vaccine pack | Yes / zoonotic |

#### 4.2 Rabbits

| Code | Signs | First aid | Vet class | Red flag |
|------|-------|-----------|-----------|----------|
| gi_stasis | No faeces, hunched, anorexic | **Keep warm**, offer hay/water; **vet same day** — do not wait | fluids, analgesia, prokinetic **vet** (never farmer-dosed) | Yes |
| pasteurella_snuffles | Sneeze, nasal/ocular pus | Isolate, ventilation, reduce dust | antibiotic **culture-guided, weeks** | Chronic carriers |
| rhd | Sudden death, blood | Biosecurity; report | none farmer; vaccine prevent | Yes |
| myxomatosis | Swollen eyes/genitals, lethargy | Isolate insects; vet | vaccine prevent; supportive | Yes |
| e_cuniculi | Head tilt, hind weakness | Safe cage, vet | antiparasitic **vet** | Neuro |
| malocclusion | Drooling, not eating | Soft food **briefly**; vet dentistry | — | If anorexic |
| sore_hocks | Raw hocks | Resting board, dry | wound care **vet** | |
| flystrike_rabbit | Maggots (soiled hind) | Clip if trained; **vet** | — | Yes |

**Never** list farmer NSAID mg/kg (rabbit doses differ from dogs/cats).

#### 4.3 Cattle

| Code | Signs | First aid | Vet class | Red flag |
|------|-------|-----------|-----------|----------|
| brd | Fever, cough, hang head | Isolate, water, shade | antibiotic/anti-inflam **vet**; metaphylaxis = vet protocol | Dyspnoea |
| bvd_ibr | Abortion, mucosal, respiratory | Biosecurity; PI hunt is **vet/lab** | vaccine prevent | |
| mastitis | Abnormal milk, hard quarter | Strip quarter; hygiene | intramammary/systemic **vet**; record SCC | Toxic mastitis |
| milk_fever | Down fresh cow | **Do not** oral calcium if recumbent without vet SOP | calcium **vet** | Yes |
| ketosis | Off feed, sweet breath | Propylene glycol **only if in approved formulary** | vet | |
| calf_scour | Watery calf | Electrolytes, warmth, **continue milk** unless vet says; isolate | fluids; anti-infective **vet** | Recumbent calf |
| blackleg_clostridial | Sudden death youngstock | Vaccinate rest **per pack** | — | Yes |
| lameness | Locomotion score | Dry yard, trim later | NSAID **vet**; hoof | |

#### 4.4 Poultry (flock)

| Code | Signs | First aid | Vet class | Red flag |
|------|-------|-----------|-----------|----------|
| nd_newcastle | Neuro, respiratory, egg drop | **Notifiable** many countries; stop movements | vaccine prevent | Yes |
| ib | Respiratory, egg shell | Ventilation, biosecurity | vaccine | |
| ibd_gumboro | Chick mortality, bursal | Support, biosecurity | vaccine | |
| marek | Paralysis, tumours | Cull policy; vaccine day-old/in ovo | vaccine | |
| ai | High death, swelling | **Notifiable** | — | Yes |
| coccidiosis | Bloody droppings, chicks | Dry litter | coccidiostat **vet/feed pack** | |
| fowl_cholera | Acute death, swollen wattles | Isolate | vaccine/bacterin; antibiotic **vet** | |
| riemerella_ducks | Neuro, respiratory ducklings | Isolate | vaccine template §3.4 | |
| histomonas | Caecal, liver; turkeys with chickens | **Do not co-house** turkeys with chickens | vet | |
| heat_stress | Panting, mortality spike | Water, shade, airflow | — | Mass death |

---

### 5. Formulary (medicines) — how richness works without a pirate pharmacy

Workers see: **product name the farm already stocked**, species, withdrawal clock, last given.

Vet/manager adds items from a **class list**:

| `vet_class` | Examples of *class* (not a shopping list) | Farmer may apply? |
|-------------|-------------------------------------------|-------------------|
| vaccine | CDT, MLV respiratory, Marek, ND, Myxo-RHD | If trained + pack due |
| anthelmintic | Benzimidazole, ML, salicylanilide, amino-acetonitrile — **rotate per FEC** | Only if protocol says this animal |
| antibiotic | Labelled livestock products | **Never** “because last time” |
| nsaid | Flunixin, meloxicam *as labelled for species* | Vet pack |
| electrolyte | Calf/kid oral electrolytes, poultry vitamins | Often yes |
| topical | Wound spray, flystrike dressing **on label** | If trained |
| antitoxin | Tetanus / enterotoxemia antitoxin | Vet / emergency pack |
| hormone | PGF, GnRH | Breeding mgr + vet |
| intramammary | Dry cow / lactating tubes | Dairy trained |

**Withdrawal:** copy days from **this farm’s label** into `formulary_items`. UI blocks sale/slaughter/milk/egg until `*_until`.

Inventory FEFO: vaccines are cold-chain items (`inventory_items` + fridge location).

---

### 6. Best-practice playbooks (Health home cards)

1. **Colostrum** — ruminant calves/kids/lambs: time to first feed, volume config, FPT risk.  
2. **Needle hygiene** — one needle policy; SQ tent; never inject in valuable cuts.  
3. **Biosecurity** — visitors, dead stock, all-in/all-out poultry, 30-day rabbit isolate.  
4. **Heat / cold** — driven by weather module (see §8).  
5. **Parasite refugia** — do not drench the whole goat/sheep herd on a calendar.  
6. **Poultry downtime** — empty house days between flocks.  
7. **Withdrawal board** — visible on Health Today and dashboard alerts.  
8. **Notifiable** — tap opens jurisdiction SOP (phone number), does not auto-notify government unless farm configures it.

---

### 7. Health tip of the day (dashboard)

**Placement:** top of module dashboard, one card, max ~2 lines + “Health” chevron.

**Selection algorithm (on-device if offline):**

1. Build candidate tips where `module_ids` ∩ farm enabled modules, or `general` / `weather`.  
2. If Open-Meteo says max ≥ heat threshold or min ≤ freeze, or rain≥X mm, prefer matching `season`.  
3. Exclude tips shown in the last 14 days (`health_tip_impressions`).  
4. Stable pick: `hash(farmId + localDate)` among remaining so all devices agree.  
5. Footer: “Not a diagnosis. Follow your vet pack.”

**Seed tips (examples — ship 60+ in `health_tips`):**

- Goat: Score FAMACHA in shade; heat changes eyelid colour.  
- Goat: CDT due ~4 weeks before kidding if your pack says so.  
- Sheep: Scan for **twins**, not only in-lamb — feed the multiples.  
- Sheep: After rain, walk for flystrike.  
- Cattle: MLV respiratory vaccines and pregnancy — check pack.  
- Cattle: Calf electrolytes do not replace milk unless the vet said so.  
- Rabbit: No droppings today is an emergency, not “wait until Monday.”  
- Rabbit: Never lift by the ears.  
- Poultry: First-week mortality is a flock KPI — log daily.  
- Poultry: Turkeys and chickens together raise blackhead risk.  
- Duck: RA vaccination slots are not a chicken Marek calendar.  
- Weather/heat: Extra water and shade; watch panting poultry and rabbits.  
- Weather/freeze: Check waterers; newborn kids/lambs/calves.  
- General: Record the batch number when you vaccinate.

Tap tip → optional detail screen or Health library article. Do not auto-open Copilot.

---

### 8. Weather widget and week page

**API:** Open-Meteo (already in the stack). No key for forecast. Cache 30–60 min in Room.

**Farm location:** `farms.latitude`, `longitude`, `timezone`. If missing, prompt once (GPS or map pin) — weather hidden until set.

**Dashboard widget (compact):**

- Icon + current temperature  
- Today high/low  
- Precip probability or mm  
- One-line alert if heat/freeze/wind/rain thresholds (farm config)

Tap → **`WeatherScreen`** (`:feature-weather` or inside `:app`):

| Block | Content |
|-------|---------|
| Now | Temp, apparent temp, humidity, wind, UV, weather code |
| Hourly (today) | 24 h strip |
| **7-day forecast** | Daily max/min, precip, wind, weather code (Open-Meteo `daily`) |
| Livestock notes | Generated **non-diagnostic** lines: e.g. “Heat: extra water, delay midday moving”; “Rain: flystrike walk (sheep)”; “Wind chill: nest-box kits” — only for **enabled** modules |
| Attribution | Open-Meteo |

**Open-Meteo (example):**  
`https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m&hourly=temperature_2m,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max&forecast_days=7&timezone={tz}`

Offline: show last cache + “Updated …”.

Heat/freeze thresholds default (farm override): ruminants heat caution ~**27°C** with humidity; poultry more sensitive — separate poultry threshold; freeze **0°C**.

---

### 9. Roles

| Action | Worker | Manager | Vet role |
|--------|--------|---------|----------|
| Record observation | Assigned animals | Yes | Yes |
| Give vaccine if task due + trained | Yes | Yes | Yes |
| Add formulary product / accept pack | No | No | Yes (or owner with vet attestation) |
| Override withdrawal | No | No | Owner + audit |
| See finance of vet spend | No | Summary | No |

---

### 10. Phase 4 (Health) — ship checklist

- [ ] Species-aware observation + treatment forms  
- [ ] Protocol packs + schedule engine → tasks  
- [ ] Disease library (this catalog)  
- [ ] Formulary + withdrawals (meat/milk/egg)  
- [ ] Dashboard tip of the day  
- [ ] Weather widget + 7-day page (Open-Meteo)  
- [ ] FAMACHA / BCS / locomotion entry from Health or species module  
- [ ] Disclaimer + VCPR gate on first Health open  

---

### 11. Sources (this health pass)

- MSU Extension — CDT sheep/goats; Mississippi State M2484 CD&T  
- NMSU B-127 sheep/goat vaccine & health schedule  
- UF/IFAS EDIS VM272 (2025) small ruminant CD&T  
- OSU — dairy vaccination schedules; colostrum volumes as example  
- MSD/Merck — beef cattle vaccination programs  
- Merck Vet Manual — poultry vaccination programs (broiler, layer, turkey, duck) Apr 2023 / Sept 2024  
- Rabbit Welfare Association / VCA — myxomatosis & RHD vaccines (jurisdiction)  
- Open-Meteo — forecast API  

---

*Health module = rich schedules and disease literacy + vet-controlled medicines. Weather and daily tips keep prevention in the farmer’s eyeline.*



---

<a id="part-d"></a>

# Part D — Embedded AI Module Spec

*Source: `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`*

---

## Farm OS — Embedded AI module (Supabase + app only)

**Companion to:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md`  
**Date:** 20 August 2026 (hosting pass)  
**Android packages:** `:domain-ai` · `:feature-ai` · optional `:ai-runtime-litert` / `:ai-runtime-onnx` · `:ai-client`  
**Backend footprint:** **Supabase only** (Postgres + Auth + Storage + Edge Functions). **No** farm-hosted Ktor analytics service, **no** Ollama/Hermes sidecar, **no** Python ML VM.

#### Hosting law (non-negotiable)

| Allowed | Not allowed |
|---------|-------------|
| Logic and UI inside the **Android APK** | Farm-operated Docker / VMs / “free” sidecars for AI |
| Data + RLS in **Supabase Postgres** | Separate analytics microservice |
| Thin **Supabase Edge Functions** (Deno) as optional secret-holding proxies | Self-hosted Hermes + Ollama as part of product deploy |
| **BYO Model API** URL + key in Settings (vendor free tiers OK: Groq, OpenRouter, etc.) | Shipping a requirement to rent GPU / always-on agent hosts |

**Hermes / free-sidecar verdict:** Hermes needs a **persistent** process plus an LLM backend. Official local guidance is roughly **8 GB RAM minimum** (tiny chat models) and **~32 GB+** for reliable tool-calling agents, with **≥64k context**. Free sidecars (Render/Railway/Fly free allowances, Supabase Edge) do **not** provide that RAM, persistence, or GPU. Therefore **Hermes is out of the hosting footprint**. Copilot is implemented **in-app** against Supabase + optional BYO OpenAI-compatible API. Hermes remains a documented *optional personal experiment* only if someone runs it on their own PC — not a product dependency.

---

### 0. Locked decisions (binding)

These decisions are product law. Do not re-litigate in implementation without updating this file and master plan §12 / §12a.

#### 0.1 LLM backends — Settings only

| Decision | Lock |
|----------|------|
| Where backends are configured | **Settings → AI & analytics** only (Owner; Farm Manager may set endpoint/model; only Owner rotates keys) |
| How | BYO OpenAI-compatible: `model_api_base_url` + `model_id` + `model_api_key` (Keystore) **or** `use_supabase_proxy` |
| What ships as presets | Groq, OpenRouter, Google AI Studio, OpenAI, Custom URL |
| What must **not** appear in Settings | Hermes URL, Ollama LAN URL, farm analytics microservice URL |
| Empty Model API | Valid product mode — charts + Lane A anomalies still work; Copilot NL chat disabled |

#### 0.2 Hermes task → concrete replacement (locked)

| Former Hermes task | Locked replacement | Concrete tool / component |
|--------------------|--------------------|---------------------------|
| LLM “brain” / chat completions | BYO Model API from Settings | `:ai-client` OpenAI-compat HTTP (SSE); optional Edge `ai-proxy` |
| Agent runtime / tool loop | In-app engine | `CopilotEngine` in `:feature-ai` / `:domain-ai` |
| Farm MCP protocol (Hermes↔data) | **Same tool jobs, different transport** — see §0.3 | `FarmToolRegistry` + Supabase Kotlin client / RPC (RLS) |
| Module skills | Bundled Markdown | `skills/{module}-*.md` in APK (optional sync from Storage) |
| Session / memory | App + DB | Room conversation cache + `ai_audit`; scope `farm_id`+user+module |
| Cron / daily briefing | Supabase schedule | `pg_cron` or scheduled Edge Function → `tasks` / push |
| Event → investigate | DB / Edge | Trigger or Edge on `analytics_anomalies` insert → optional Lane C enrich |
| Draft recommendations | Unchanged product rule | `propose_recommendation` → Accept/Reject UI |
| Terminal / shell tools | **Removed** | Never reintroduce |

#### 0.3 Why “Farm MCP” is not kept (and what is kept)

**Farm MCP was not the farm data API.** It was a **protocol adapter** so a *remote* Hermes process could call read tools over MCP HTTP/stdio.

Without Hermes:

- There is **no remote agent** that needs MCP.
- The **same tool contracts** (`search_in_module`, `get_animal`, `get_kpis`, …) stay as the Copilot allow-list.
- The app executes them **directly** via Supabase (JWT + RLS) inside `FarmToolRegistry`.

| Kept | Dropped |
|------|---------|
| Tool **names, args, species scoping, read-mostly rules** | MCP server process / MCP wire protocol |
| `propose_recommendation` as the only write-shaped tool | Hermes `mcp_servers` config pointing at Ktor |
| Audit of which tool ran | Exposing tools to any external agent runtime |

**Rule:** Do not say “we removed farm data tools.” Say “we removed the MCP *transport* because Copilot runs in-process; `FarmToolRegistry` is the successor to Farm MCP’s tool surface.”

If a future optional external agent is ever added (not planned), it would call the **same** `FarmToolRegistry` contracts via a controlled Edge gateway — not by resurrecting Hermes+MCP as the default.

---

#### Product law

| The app **does** | The app **does not** |
|------------------|----------------------|
| Show **graphs** on chart-compatible features (growth, milk, eggs, FCR, FAMACHA, SCC, …) | Replace tables with charts only |
| Flag **positive / negative / watch** anomalies with evidence | Auto-treat, auto-cull, or invent drug doses |
| Recommend **corrective actions** as Accept/Reject drafts or tasks | Mutate biology/finance without a human |
| Call a **Model API from Settings** (BYO) or stay fully on-device | Require a farm-hosted model server |
| Run **Lane A rules** (+ optional LiteRT/ONNX) offline | Depend on the network for basic ADG / mortality flags |

---

### 1. Architecture (app + Supabase)

```
┌─────────────────────────────────────────────────────────────────┐
│  Feature screens (growth, milk, eggs, health scores, KPIs)      │
│  Table | Chart | Both  +  Anomaly chips  +  Actions CTA         │
└────────────────────────────┬────────────────────────────────────┘
                             │ AnalyticsFeature
┌────────────────────────────▼────────────────────────────────────┐
│  :feature-ai / :domain-ai  (EMBEDDED IN APK)                     │
│  ┌──────────────┐  ┌────────────────┐  ┌─────────────────────┐ │
│  │ Lane A Rules │  │ Lane B On-dev  │  │ Lane C BYO Model API│ │
│  │ ADG / MAD    │  │ LiteRT / ONNX  │  │ OpenAI-compat HTTP  │ │
│  │ cohort bands │  │ optional packs │  │ (Settings URL+key)  │ │
│  └──────┬───────┘  └───────┬────────┘  └──────────┬──────────┘ │
│         └──────────────────┴──────────────────────┘             │
│              AnomalyEngine → RecommendationEngine                │
│              CopilotEngine (tool-calling in Kotlin, not Hermes)  │
└──────────────┬─────────────────────────────┬────────────────────┘
               │                             │
               ▼                             ▼
     Room (offline cache)          Supabase (hosted)
     anomaly drafts                Postgres + RLS
                                   Auth JWT (farm_id)
                                   Storage (optional model packs)
                                   Edge Functions (optional proxy)
```

#### Routing policy (Settings → AI)

1. **Offline / `on_device_only`:** Lane A always; Lane B if assets installed. Copilot = local template answers + cached tips only (or queue until online).  
2. **`hybrid` (default):** Lane A/B first; Lane C enrich when online and Model API configured.  
3. **`api_preferred`:** Lane C for forecast/explain when online; still fall back to A/B on failure.  
4. **Copilot:** App → (optional Edge Function proxy) → BYO OpenAI-compat chat completions. Tools execute **in the app** against Supabase client (RLS), never as a remote agent with a shell.  
5. **No Hermes URL** in Settings. No “farm analytics microservice” URL — only BYO Model API or empty (on-device only).

---

### 2. What runs where

| Capability | Where it runs | Notes |
|------------|---------------|-------|
| Charts (Vico) | App | Additional representation only |
| ADG / cohort / FAMACHA / egg-drop rules | App (`:domain-ai`) | Primary brain for anomalies |
| Corrective action catalog | App | Templates → Tasks / Health drafts |
| Optional tiny `.tflite` / ONNX | App + optional download from Supabase Storage | Size-gated; not required for MVP |
| Persist anomalies / recommendations | Supabase tables + Room mirror | Sync like other farm rows |
| KPI SQL / views | Supabase Postgres | Same DB as SoR projections |
| Pedigree / inbreeding (light) | App Kotlin first; heavy matrices later via Edge only if needed | No PyAGH sidecar for MVP |
| NL Copilot | App tool-loop + BYO Model API | Skills = bundled Markdown + system prompt |
| Secret Model API keys | Android Keystore **or** Supabase Vault + Edge proxy | Prefer Edge proxy so keys never leave device storage awkwardly shared |
| Scheduled briefings | Supabase `pg_cron` / scheduled Edge Function → push / `tasks` | Not Hermes cron |

---

### 3. Chart-compatible features

Every time-series or cohort screen implements `ChartableFeature`.

| Feature | Module(s) | Series | Chart (Vico) |
|---------|-----------|--------|--------------|
| Growth / ADG | Mammals; poultry meat/breeders | Weight vs age; cohort band | Line + band |
| Litter / kit weights | Rabbit | Litter mean + individuals | Line / grouped |
| Milk / lactation | Goat dairy, cattle dairy | kg, fat, protein | Multi-series |
| SCC / SCS | Dairy | SCC log or SCS | Line + threshold |
| Egg production | Poultry layers (by kind) | Hen-day %, hen-housed | Line |
| FCR / feed | Rabbit, poultry, beef | Feed/gain or egg mass | Line |
| Hatchability | Poultry hatchery | Hatch % by set | Line / bar |
| FAMACHA / BCS | Goat, sheep, cattle | Score over time | Step / line |
| Mortality | Poultry flock, rabbit litter | Daily / cumulative | Line / bar |
| KPI scorecard | Module home | Target vs actual | Bullet / bar |

**UI:** `Table | Chart | Both`. Phone default **Both**. Library: [Vico](https://github.com/patrykandpatrick/vico).

---

### 4. Anomalies & corrective actions

#### 4.1 Polarity

| Polarity | Meaning | Examples |
|----------|---------|----------|
| **Negative** | Risk / underperformance | ADG &lt; cohort −2σ; FAMACHA ≥ 4; mortality spike; SCC flag |
| **Positive** | Favourable | ADG &gt; +2σ; FCR better than pack; conception up |
| **Watch** | Early signal | ADG −1.5σ; one-day egg dip |

Fields: `feature_code`, subject (`animal_id` | `group_id`), `species_code`, optional `poultry_kind_code`, `polarity`, `severity`, evidence JSON, `lane` (`rules` | `on_device` | `model_api`), `status`.

#### 4.2 Actions (drafts only)

| Trigger | Action templates |
|---------|------------------|
| Growth negative | Recheck scale; BCS/FAMACHA; feed review; reweigh 7 d; **vet if FAMACHA≥4 / off-feed** |
| Growth positive | Confirm measure; genetics note; optional breeding-candidate flag |
| Milk/SCC negative | Hygiene / CMT SOP task; withdrawal check; vet if toxic signs |
| Egg drop | Water/feed/light; mortality sheet; biosecurity walk |
| Mortality spike | Quarantine house; stop movements; Health outbreak path |
| FAMACHA negative | Selective drench **only via vet-accepted pack**; recheck 7 d |

**Never** invent named drugs/doses. Prefer “Open Health → pack” / “Call vet”.

#### 4.3 Kotlin contracts (`:domain-ai`)

```kotlin
enum class InferenceLane { RULES, ON_DEVICE, MODEL_API }

interface AnalyticsFeature {
    val code: String
    fun supportsChart(): Boolean
    fun series(ctx: AnalyticsContext): TimeSeries
    fun detect(ctx: AnalyticsContext): List<Anomaly>
    fun actionsFor(anomaly: Anomaly): List<CorrectiveAction>
}
```

Lane A growth: cohort = same `species_code` (+ poultry kind + purpose); expected ADG = median; flag with MAD/z-score; ≥3 points else Watch.

---

### 5. Settings → LLM / Model API (locked)

**Screen:** Settings → **AI & analytics** (Owner; Farm Manager may set endpoint/model; only Owner rotates keys).

**This is the only place** LLM backends are configured. Copilot and Lane C both read these fields. No hard-coded vendor; no Hermes/Ollama fields.

| Setting | Purpose |
|---------|---------|
| `ai_enabled` | Master switch |
| `analytics_mode` | `on_device_only` \| `hybrid` \| `api_preferred` |
| `model_api_base_url` | OpenAI-compatible base URL |
| `model_api_key` | Keystore-backed (or unused when proxy on) |
| `model_id` | Vendor model id (must support **tool/function calling** for Copilot) |
| `use_supabase_proxy` | If true, app calls Edge `ai-proxy`; key in Supabase secrets |
| `on_device_models` | Enable downloaded LiteRT/ONNX packs |
| `share_animal_ids` | Default **off** — opaque series only |
| `copilot_enabled` | In-app Copilot using the same Model API |

**Locked presets:** Groq · OpenRouter · Google AI Studio · OpenAI · Custom URL.  
**Empty URL:** charts + Lane A only; Copilot NL off.

**Do not ship presets for:** Hermes LAN, Ollama on farm server, “Farm Analytics Ktor”.

#### Optional Supabase Edge proxy

```
POST /functions/v1/ai-proxy
Authorization: Bearer <user JWT>
Body: { "purpose": "chat"|"anomaly_enrich"|"forecast", "payload": {...} }
```

Edge Function:

1. Validates JWT → `farm_id` claim.  
2. Loads Model API key from Supabase secrets (or farm row encrypted at rest).  
3. Forwards minimised JSON to BYO provider.  
4. Returns structured anomalies/actions that must match the **app action catalog** (no free-form doses).  
5. Logs `ai_audit` row (prompt hash, model, farm_id, species_code) — not full pedigree dumps.

CPU-bound Chronos / sklearn Isolation Forest **are not hosted**. If Lane C is used for “forecast”, it is **LLM or vendor API** interpretation of series the app already computed — or skip forecast and show rule-based bands only.

---

### 6. In-app Copilot + FarmToolRegistry (successor to Farm MCP tools)

```
User message
    → CopilotEngine builds system prompt (module skill Markdown)
    → Model API chat.completions (backend from Settings; tools from FarmToolRegistry)
    → App executes FarmToolRegistry via Supabase client (RLS)
    → Loop until final answer
    → Citations = row IDs from tool results
    → Optional propose_recommendation → Accept/Reject UI
```

#### 6.1 FarmToolRegistry (locked tool surface)

Same jobs formerly exposed over Farm MCP to Hermes. **Transport = in-process Kotlin + Supabase**, not MCP.

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

**Forbidden:** raw SQL, shell, arbitrary HTTP, writing treatments/finance without Accept.

Skills: `skills/{module}-*.md` in APK (optional Storage sync).  
Morning briefing: Supabase scheduled function → `tasks` / push — not Hermes cron.

---

### 7. Gradle layout

```
:domain-ai/           # AnalyticsFeature, AnomalyEngine, RecommendationEngine, Copilot tool schemas
:feature-ai/          # Charts, anomaly UI, Copilot screen, Settings AI
:ai-client/           # OpenAI-compat OkHttp + optional Supabase function client
:ai-runtime-litert/   # Optional
:ai-runtime-onnx/     # Optional
```

Species modules register `AnalyticsFeature` via Hilt multibinding. They do not own ML code.

**Removed from product deploy:** `:server/modules/analytics` as a separate host, `hermes/` container, Ollama compose service.

---

### 8. Supabase data model

```sql
-- Same shape as master plan §4.11; apply RLS with farm_id = auth JWT claim

CREATE TABLE analytics_anomalies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    feature_code TEXT NOT NULL,
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

CREATE TABLE ai_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    user_id UUID,
    purpose TEXT NOT NULL,
    model_id TEXT,
    species_code TEXT,
    prompt_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Non-secret defaults on farms.config JSONB: model_api_base_url template, model_id, mode
-- Secrets: device Keystore OR Supabase Vault / Edge secrets — never plaintext in Postgres
```

---

### 9. Tooling (fits this footprint)

| Need | Choice | Why |
|------|--------|-----|
| Charts | Vico | In-app |
| Rules / anomalies | Kotlin `:domain-ai` | No server ML |
| On-device ML (optional) | LiteRT / ONNX Runtime Mobile | Runs in APK |
| Tiny forecast experiments | Chronos-Bolt **only if** size allows on-device or via BYO API — not self-hosted | No farm GPU |
| LLM Copilot | BYO OpenAI-compat (Groq/OpenRouter/…) | User’s key / free tier |
| Proxy / secrets | Supabase Edge Functions | Inside Supabase env |
| Agent framework | **Not Hermes** for product | Free sidecar infeasible |
| Client pattern | Thin OkHttp client (Kompletions-style refs) | Avoid shipping whole chat apps |

**Explicitly deferred / out of footprint:** Hermes Agent, Ollama/vLLM farm hosts, scikit-learn/XGBoost farm microservices, Chronos on farm GPU, PyAGH as a required sidecar.

---

### 10. Safety & privacy

1. Advisory only — Accept/Reject for mutations.  
2. Minimised payloads to Model API; `share_animal_ids` default off.  
3. Keys in Keystore or Supabase secrets.  
4. Species / poultry-kind isolation on every series.  
5. Health formulary still gates treatments (`FARM_OS_HEALTH_MODULE_SPEC.md`).  
6. Copilot tools are a fixed allow-list; no terminal.

---

### 11. Delivery checklist

| Phase | Deliver |
|-------|---------|
| **3 Growth** | Vico charts; Lane A anomalies + actions; Room cache |
| **4+** | Sync anomalies to Supabase; RLS |
| **8 Intelligence** | Settings BYO Model API; optional Edge `ai-proxy`; in-app Copilot tool-loop; `ai_audit` |
| **Optional** | LiteRT/ONNX packs in Supabase Storage; on-device RAG over protocol packs (PocketSage-style) |

**Not in checklist:** Hermes compose file, Ollama, free sidecar experiments as product gates.

---

### 12. Growth page (reference)

Route: `goat/{id}/growth` (same pattern per species).

1. Table of weights / ADG.  
2. Chart + cohort band (Lane A).  
3. Anomaly chips.  
4. Actions → tasks.  
5. **Explain** button → Copilot with citations if Model API configured; else show rule evidence text only.

---

*Embedded AI for this product = APK engines + Supabase persistence/proxy + optional BYO Model API. No farm-hosted AI sidecars. Hermes is not part of the deploy.*



---

<a id="part-e"></a>

# Part E — Rabbit Nest-Box Ratio & Schedule

*Source: `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md`*

---

## Rabbit nest-box ratio & schedule cycle
### Derived from KudBat semi-intensive sheet + cycle graphic

**Date:** 20 August 2026  
**Unit of analysis:** One breeding cage = **11 does + 1 buck**  
**Buck rule:** mates **2 does / day**, then **rests 1 day**, next pair (last day = 1 doe).  
**Companion:** rabbit module in `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md` (gestation 31–33 d clinical); this file locks the **producer schedule offsets from the attached Excel**.

---

### 1. Locked day offsets (from the sheet)

Relative to a doe’s **Breeding** date = Day 0:

| Event | Offset | Example (breed 2026-09-01) |
|-------|--------|----------------------------|
| Nest box **in** | **+28 d** | 2026-09-29 |
| Kindling (expected) | **+32 d** | 2026-10-03 |
| Rebreeding | **+43 d** | 2026-10-14 |
| Nest box **out** | Kindling **+21 d** = Breeding **+53 d** | 2026-10-24 |
| Weaning | Kindling **+35 d** = Breeding **+67 d** | 2026-11-07 |
| Nest box **in** (next cycle) | Rebreeding **+28 d** = Breeding **+71 d** | 2026-11-10 |
| Next kindling | Rebreeding **+32 d** = Breeding **+75 d** | 2026-11-14 |

Notes:

- The KudBat graphic labels rebreed as **Day 42** and next kindling **Day 73** (≈31 d gestation after rebreed). The **Excel uses +43 / +32** as above — use the Excel offsets for this farm’s task engine unless the owner switches the purpose pack to KudBat-31.
- Nest box is present **25 calendar days** per placement if counted as inclusive start, exclusive remove day: `[+28, +53)` → **25 days in use**, then **18 days idle** until next in at +71.

```
Breed ──28d── Nest IN ──4d── Kindling ──21d── Nest OUT ──18d── Nest IN (next) …
                 |______________ 25 d nest use ______________|
         0                                              53        71
                              Rebreed @ 43 ────────────────┘
```

---

### 2. One-cage mating stagger (11♀ + 1♂)

| Cohort | Does | Breed day (relative) |
|--------|------|----------------------|
| 1 | 2 | Day 0 |
| 2 | 2 | Day 2 |
| 3 | 2 | Day 4 |
| 4 | 2 | Day 6 |
| 5 | 2 | Day 8 |
| 6 | 1 | Day 10 |

Buck: work / rest / work / rest … over 11 calendar days to finish the wave.

---

### 3. Nest-box demand (concurrent count)

Each doe needs **her own** nest box while her window is open. Two does bred the same day ⇒ **2 boxes** that day.

#### Nest window per cohort (relative to cage Day 0 = first breed)

| Cohort | Does | Nest IN | Nest OUT |
|--------|------|---------|----------|
| 1 | 2 | +28 | +53 |
| 2 | 2 | +30 | +55 |
| 3 | 2 | +32 | +57 |
| 4 | 2 | +34 | +59 |
| 5 | 2 | +36 | +61 |
| 6 | 1 | +38 | +63 |

**Peak:** from the day the last nest goes in (**+38**) until the first nests come out (**+53**):

→ **all 11 does have a nest box at once**  
→ **minimum nest boxes for one cage on this schedule = 11**

#### Cage : nesting-box ratio (locked)

| Scope | Ratio | Meaning |
|-------|-------|---------|
| **One cage (11♀), this stagger** | **1 cage : 11 nest boxes** | Peak concurrency forces 1 box per doe |
| **Doe : nest box (same cage)** | **1 : 1** at peak | Cannot run fewer than 11 without changing stagger or nest dwell |
| **Two cages A then B** (sheet: B starts ~34 d after A’s first breed) | **2 cages : 11 nest boxes** (shared pool) | Nest waves barely overlap — one physical set of 11 can rotate from A → B |

Sheet check (2026):

- Cage A peak nest use: **2026-10-09 → 2026-10-23** (11 boxes)  
- Cage A last remove: **2026-11-03** (A12)  
- Cage B first nest in: **2026-11-02** (B2 & B3)  
- Overlap of A+B: **1–2 days**, only **A12 (1) + B first pair (2) ≈ 3** if sharing — still **≤ 11** if A’s earlier cohorts already returned boxes  

**Practical recommendation:** Buy/build **11 nest boxes per breeding cage** you want to run **independently**. If you run **paired waves** (Cage A then Cage B) like the sheet, you can **share one pool of 11** between those two cages. A third concurrent wave would need another 11 (or a redesigned stagger).

---

### 4. Nesting-box schedule cycle (reusable)

#### 4.1 Per-doe cycle (repeats every rebreed)

| Step | When | Action |
|------|------|--------|
| 1 | Breed (Day 0) | Mate; no nest yet |
| 2 | Day 28 | **Place** nest box + bedding |
| 3 | Day 32 | Expected kindling |
| 4 | Day 53 (kindling + 21) | **Remove** nest box; clean/disinfect; dry store |
| 5 | Day 43 | Rebreed same doe (may be while kits still nursing — KudBat semi-intensive) |
| 6 | Day 71 (rebreed + 28) | **Place** nest box again |
| 7 | Day 75 | Next kindling |
| 8 | Day 96 | Remove again (next kindling + 21) |
| … | | Repeat from rebreed rhythm |

#### 4.2 Cage “Today” task template (relative to cage Day 0)

Generate tasks for cohorts at breed offsets 0,2,4,6,8,10:

| Relative day | Nest tasks |
|--------------|------------|
| 28 | Place ×2 (cohort 1) |
| 30 | Place ×2 |
| 32 | Place ×2 |
| 34 | Place ×2 |
| 36 | Place ×2 |
| 38 | Place ×1 |
| 53 | Remove ×2 (cohort 1) |
| 55 | Remove ×2 |
| 57 | Remove ×2 |
| 59 | Remove ×2 |
| 61 | Remove ×2 |
| 63 | Remove ×1 |
| 71 | Place ×2 (cohort 1, **cycle 2**) |
| 73 | Place ×2 |
| … | Same +43 pattern from each cohort’s rebreed |

#### 4.3 Worked example — Cage A (from sheet)

| Does | Breed | Nest IN | Kindling | Nest OUT | Rebreed | Nest IN (2) | Kindling (2) |
|------|-------|---------|----------|----------|---------|-------------|--------------|
| A2 & A3 | 09-01 | 09-29 | 10-03 | 10-24 | 10-14 | 11-10 | 11-14 |
| A4 & A5 | 09-03 | 10-01 | 10-05 | 10-26 | 10-16 | 11-12 | 11-16 |
| A6 & A7 | 09-05 | 10-03 | 10-07 | 10-28 | 10-18 | 11-14 | 11-18 |
| A8 & A9 | 09-07 | 10-05 | 10-09 | 10-30 | 10-20 | 11-16 | 11-20 |
| A10 & A11 | 09-09 | 10-07 | 10-11 | 11-01 | 10-22 | 11-18 | 11-22 |
| A12 | 09-11 | 10-09 | 10-13 | 11-03 | 10-24 | 11-20 | 11-24 |

**Boxes in service (Cage A only):**

| Date range | Boxes needed |
|------------|--------------|
| 09-29 → 09-30 | 2 |
| 10-01 → 10-02 | 4 |
| 10-03 → 10-04 | 6 |
| 10-05 → 10-06 | 8 |
| 10-07 → 10-08 | 10 |
| **10-09 → 10-23** | **11 (peak)** |
| 10-24 → 10-25 | 9 |
| 10-26 → 10-27 | 7 |
| 10-28 → 10-29 | 5 |
| 10-30 → 10-31 | 3 |
| 11-01 → 11-02 | 1 |
| 11-03 | 0 (then cycle-2 places start 11-10) |

---

### 5. App / task-engine rules (for `:feature-rabbit`)

```text
purpose_pack: kudbat_semi_intensive_excel  # or kudbat_graphic_d42
nest_in_days_after_mating: 28
kindling_days_after_mating: 32
rebreed_days_after_mating: 43
nest_out_days_after_kindling: 21
wean_days_after_kindling: 35
buck_mates_per_day: 2
buck_rest_days: 1
does_per_cage: 11
nest_boxes_required_per_cage: 11
nest_box_share_across_paired_cages: optional  # only if B-wave starts after A peak clears
```

Task types: `NEST_BOX_PLACE`, `NEST_BOX_REMOVE`, `REBREED`, `WEAN`, `EXPECTED_KINDLING`.

Do **not** assume nest boxes can be fewer than 11 for a single active wave of this design.

---

### 6. Summary

| Question | Answer |
|----------|--------|
| Cage : nesting box ratio (one cage, this schedule) | **1 : 11** |
| Why? | Stagger still overlaps so all 11 nest windows are open together for ~15 days |
| Nest dwell per placement | **25 days** in, **18 days** out, then in again at rebreed+28 |
| Cycle driver | Rebreed every **+43 d** from mating (sheet); nest rhythm follows +28 / kindling+21 |
| Sharing | Two cages phased like A/B on the sheet can share **one pool of 11** |

*Clinical gestation remains ~31–33 d (Merck); the +32 kindling column is the farm’s planning date for tasks, not a substitute for watching the doe from day 30.*

**Programme module:** For cages, named boxes, pairing (2:11), notifications, and inbreeding gates, see `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`.



---

<a id="part-f"></a>

# Part F — Rabbit Breeding Programme Spec (Full MVP)

*Source: `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`*

---

## Farm OS — Rabbit breeding programme module
### Full MVP implementation (complete feature set)

**Companion to:** master plan §7.2 · `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` · vet pack §2  
**Date:** 20 August 2026 (full-MVP pass)  
**Android:** `:feature-rabbit` · `:domain` engines · Room mirrors · Supabase sync  
**Purpose pack id:** `kudbat_semi_intensive_excel` (default); multi-breed packs supported in MVP

#### MVP scope lock

**Everything below ships in rabbit MVP.** There is no “Phase 2+ / later” deferral for programme features listed here. Only explicitly out-of-scope items (§18) stay out.

| Former bucket | Now |
|---------------|-----|
| Must-have | MVP |
| Should-have (palpation, foster, scorecards, sanitation, inventory, QR, ICS, …) | **MVP** |
| Nice-to-have (colour genetics, market planner, waitlist/sales, multi-breed templates, photo journal) | **MVP** |

---

### 0. Locked farm startup profile

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

### 1. Navigation & screens (`:feature-rabbit`)

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

### 2. Purpose packs & multi-breed templates (MVP)

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

### 3. Complete data model (MVP)

#### 3.1 Cages, pairs, occupancy

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

#### 3.2 Nest pools, boxes, sanitation

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

#### 3.3 Waves & matings

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

#### 3.4 Litters, kits, foster, photos

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

#### 3.5 False pregnancy / failed kindling

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

#### 3.6 Line tags, colour genetics

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

#### 3.7 Retention, sales waitlist, contracts

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

#### 3.8 Market / butcher planner

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

#### 3.9 Inventory hooks (bedding / feed)

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

#### 3.10 Cage cards & calendar export

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

### 4. Domain engines (full API)

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

#### 4.1 COI policy (MVP defaults)

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

#### 4.2 Task generation (complete list)

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

### 5. Notifications & WorkManager

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

### 6. Screen-level implementation notes

#### 6.1 Setup wizard (`StartupThreeCageCmd`)

1. Names A/B/C → insert cages + QR codes.  
2. For each cage: pool `POOL-{code}`, boxes `NB-{code}-01`…`11`, QR per box.  
3. Optional CSV/manual assign does to slots A2…A12 pattern.  
4. Assign buck; warn if COI unknown.  
5. Bind inventory bedding item.  
6. Enable palpation default on.  
7. Offer “Plan Wave 1” CTA.

#### 6.2 Pairing planner

Compose sections: animal pickers → relationship badge → COI gauge → colour probability table → sire history on doe → Acknowledge warn → Confirm.  
Blocks navigation to save if `block`.

#### 6.3 Litter + photo journal

Kindling form: live/stillborn, abnormalities, assisted, nest box confirm.  
Creates N `rabbit_kits` rows.  
Photo: CameraX → compress → Supabase Storage path `farm/{id}/litters/{litterId}/…` → `rabbit_litter_photos`. Gallery on litter tab; tag filters.

#### 6.4 Foster match

Lists litters kindled ≤3 days apart with capacity hint (doe milking load = live kits). Drag/select kits → confirm → events + tasks.

#### 6.5 Scorecards

**Doe:** matings, kindling rate, avg live born, weaned, prewean mortality, mean interval, last COI of litters, nest on-time %.  
**Buck:** matings count, unique does, litters sired, mean litter size, mean offspring COI, borrow count.  
SQL views `mv_rabbit_doe_scorecard`, `mv_rabbit_buck_scorecard` filtered by `farm_id`.

#### 6.6 Cage cards

Generate PDF (Android Print / OpenPDF): cage name, QR, occupant list, next 3 tasks, active nest box codes. Share sheet. Box card: code, QR, pool, status.

#### 6.7 ICS export

RFC5545 VEVENT per task in range; `UID = taskId@farm-os-rabbit`; download/share `.ics`.

#### 6.8 Sales

Waitlist board → Match kit (retention `sale_pet`) → Contract draft → mark paid/delivered → finance posting optional via finance module event.

---

### 7. Nest concurrency & cage pairing (MVP)

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

### 8. Events (append to domain_events)

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

### 9. Offline & sync

- Room entities mirror all rabbit_* tables used barn-side.  
- Mutations: outbox with `mutation_id`; sync to Supabase.  
- Photos: queue file upload.  
- COI: works offline if pedigree synced.  
- Conflict: last-write on sanitation logs; mating status use domain rules (kindled wins over false_pregnant if both pending — surface conflict UI).

---

### 10. Permissions

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

### 11. KPIs & anomalies (MVP)

KPIs as § prior +: foster rate, on-time sanitize %, mean days dirty, waitlist fulfillment days, % litters with photos, market projection accuracy.

Lane A anomalies: missed nest place, kindling overdue, litter mortality spike, dirty box SLA breach, buck overload, pool concurrency near 11.

---

### 12. Package structure

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

### 13. Test plan (MVP acceptance)

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

### 14. Delivery checklist (single MVP)

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

### 15. Out of scope (still)

- Goat/cattle UI inside rabbit module  
- Drug prescribing / dose invention  
- Hermes sidecar  
- Automatic financial ledger beyond optional posting hook  
- DNA lab integrations (manual parentage verify flag only)

---

### 16. Summary locks

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

<a id="part-g"></a>

# Part G — Technical Implementation Handbook (DDE-aligned)

*Source: `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`*

---

## Farm OS — Technical Implementation Handbook

**Version:** 1.0 · **Date:** 22 August 2026
**Upstream methodology:** [DDE — Development & Engineering Engine](https://github.com/Vanguduza/dde) (`docs/blueprint/REV_2_0.md`, `AGENTS.md`). This handbook adapts DDE's authority model, schema law, gates and governance to the Farm OS platform.
**Sibling documents:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md` · `FARM_OS_HEALTH_MODULE_SPEC.md` · `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` · `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` · `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`

---

### Contents

| Ch | Title |
|----|-------|
| 0 | How to use this handbook |
| 1 | Authority model |
| 2 | Product Constitution (Farm OS) |
| 3 | The five environments |
| 4 | Architectural principles |
| 5 | Canonical manufacturing spine |
| 6 | Schema authority |
| 7 | Data laws |
| 8 | Module boundary map |
| 9 | Definition of done |
| 10 | Phase gates — CI green ≠ phase done |
| 11 | Engineering Decision Records (EDRs) |
| 12 | Verification & evidence |
| 13 | Offline-first & durability |
| 14 | Security & credential law |
| 15 | Staged delivery map |
| 16 | Forbidden patterns |
| 17 | Governance & when blocked |
| 18 | Traceability appendix |

---

### 0. How to use this handbook

**Audience.** The owner, and every AI agent or human contributor building Farm OS. Agents MUST read Chapters 1, 9, 10 and 16 before writing code.

**Division of authority.**

- The **master plan** and **feature specs** govern **WHAT** Farm OS is (scope, data model, screens, schedules).
- This **handbook** governs **HOW** Farm OS is built (authority, process, gates, verification, durability).
- Neither overrides the other; they compose. A spec change that alters architecture follows Chapter 17 change control.

**Precedence on conflict** (highest first):

1. Product Constitution (Chapter 2)
2. Feature specs / master plan normative sections
3. This handbook's process rules
4. Accepted EDRs where they amend any of the above (an EDR supersedes; it never rewrites history)
5. Agent suggestions, conversation memory, model opinion — never authoritative

**Reading discipline (DDE rule).** Read the relevant spec section before changing anything it governs. Do not implement from chat memory.

---

### 1. Authority model

Farm OS copies DDE's precedence ranks. A lower rank may *inform* a higher rank but may never modify it. Modification requires a governed change path (Chapter 17) terminating in a rank-0 owner decision. This rule is the primary defence against stale chat memory, donor contamination and conversational drift.

| Rank | Artifact class | Farm OS concrete artifact | Changed by |
|------|----------------|---------------------------|------------|
| 0 | Human-approved governance decisions | Owner decisions recorded in mission/issue log | Owner only |
| 1 | Product Constitution | Chapter 2 below + master plan "Alignment rules (non-negotiable)" | Rank-0 decision via change control |
| 2 | Approved product brief | Master plan purpose/scope header | Rank ≤1 |
| 3 | Approved requirements | Spec requirement statements (e.g. breeding programme §MVP scope lock) | Rank ≤2 |
| 4 | Accepted EDRs | `docs/truth/edr/EDR-*.md` | Supersede only, never rewrite |
| 5 | Business rules | KudBat offsets, COI thresholds, foster windows (locked tables in specs) | Rank ≤4 |
| 6 | Architecture | Master plan data model §4, module map §5, AI spec §architecture | Rank ≤5 |
| 7 | Task specification | Phase task breakdowns, milestone tickets | Planner within phase scope |
| 8 | Verified implementation & evidence | Code + tests + gate reports — produced, never asserted | Produced by missions |
| 9 | External evidence | Vet research pack, Merck Manual, supplier docs, web research | Ingested with citation, never auto-promoted |
| 10 | Agent hypotheses | Chat suggestions, drafts | Freely produced, never authoritative |

**Two standing rules.**

1. Never edit `docs/truth/**`, the Constitution, or locked spec sections as a side effect of implementing a task. Propose; do not mutate.
2. When code must diverge from a spec, stop: the divergence is an EDR candidate, not a commit.

---

### 2. Product Constitution (Farm OS)

*Versioned Project Truth. Changes only through change control (Chapter 17).*

#### 2.1 Purpose

A multi-species livestock operating system for smallholder and commercial farms: Android-first recording, species-correct lifecycle workflows, advisory intelligence, offline resilience. Goats and rabbits are the Phase 1–8 system of record; poultry, sheep and cattle are first-class products, not filters.

#### 2.2 Target users

Owner-operators (single farm), farm workers (task executors), breeding managers (rabbit programme), veterinarians/advisors (read-mostly review).

#### 2.3 Non-negotiable constraints

1. **Hosting law.** The backend is Supabase (Postgres, Auth, Storage, Edge Functions) plus the APK. No farm-hosted servers, VMs, sidecars, containers or self-managed LLM runtimes are part of the product.
2. **Species alignment rules.** No generic Animals home; the only home is a module dashboard. Goat UX designed for goats; rabbit UX for rabbits; poultry kinds live inside `:feature-poultry`; sheep/cattle likewise. Shared tables (`animals`, `domain_events`, measurements) are infrastructure and must never become a shared animal-list product. RFID/search identifies a record then opens that species' module.
3. **AI is advisory.** Lane A/B/C outputs recommend; they never auto-treat, auto-cull, auto-sell or mutate clinical records without a human confirm step.
4. **Offline-first.** Every daily workflow works without connectivity; sync reconciles idempotently later.
5. **Tenancy.** `farm_id` from the authenticated JWT + Postgres RLS on every table. Application-code filtering alone is non-compliant.

#### 2.4 Core workflows

Daily round (dashboard → today board → tasks), breeding cycle (mate → palpate → nest box → kindling → wean → rebreed), health event capture → treatment course → withdrawal tracking, growth recording → charts/anomalies, inventory drawdown linked to events, sales/waitlist fulfilment.

#### 2.5 UX principles

Species-native vocabulary; one-screen-per-task flows usable with dirty hands; notifications actionable (complete/snooze/escalate); charts answer a husbandry question, never decorate; dark-mode and large-touch defaults.

#### 2.6 Security principles

Fail-closed RLS; secrets in Android Keystore; least-privilege DB roles; audit trail for AI actions (`ai_audit`) and sensitive mutations; no long-lived credentials to anything executing model-generated content.

#### 2.7 Architecture principles

Modular Gradle monolith; CQRS with `domain_events` as Source of Record; Room mirror for offline; WorkManager for scheduled truth; BYO Model API behind settings; design for replacement (see Chapter 4).

#### 2.8 Explicit exclusions

iOS/web clients; multi-farm org hierarchies (Phase 8+); drug dose invention; automatic financial ledger beyond optional posting hook; DNA lab integrations; marketplace payments; herd-level ML training in-app.

#### 2.9 Governance rules

Constitution changes require rank-0 approval + dated entry in the master plan revision header. Specs may elaborate but not contradict. Conflicts resolve downward through Chapter 1 ranks.

---

### 3. The five environments

Confusing these is the most common way this project fails. They stay separate.

| # | Environment | Farm OS instance | Authority |
|---|-------------|------------------|-----------|
| 1 | **Authoring environment** | Cursor Desktop + local repo on the owner's Windows machine | Never authoritative for anything |
| 2 | **Platform state** | Supabase project: Postgres (`domain_events`, projections, RLS), Auth, Storage | The only authoritative state |
| 3 | **Execution runtime** | On-device app sandbox: Room mirror, WorkManager workers, outbox queue | Derived/mirrored state only |
| 4 | **Product verification** | Instrumented test runs, debug builds against a staging Supabase project, golden fixtures | Produces evidence, holds no truth |
| 5 | **Model providers** | BYO OpenAI-compatible Model API endpoints configured in Settings; optional `ai-proxy` Edge Function | Replaceable, never trusted, never authoritative |

Rules: environment 1 never writes directly to environment 2 except through reviewed migrations; environment 3 reconciles to environment 2 and never becomes a second source of truth; environment 5 sees only minimised, purpose-scoped payloads (AI spec §safety) and its output is evidence-class (rank 9), not truth.

### 5. Canonical manufacturing spine

This is the normative chain for every Farm OS change. Subordinate runtime bindings (Room mirror, WorkManager job, notification channel) never become alternative sources of state.

```
Owner intent / husbandry need
    |
Requirement .......................... spec section w/ acceptance conditions   (rank 3)
    |
EDR (if architectural) ............... docs/truth/edr/                        (rank 4)
    |
Schema migration ..................... supabase/migrations/*.sql               (Ch.6 authority)
    |
domain_event type registered ......... domain_events ledger contract            (Source of Record)
    |
Projection built ..................... analytics_* / breeding read tables       (derived, rebuildable)
    |
Room entity + DAO mirror ............. app offline cache                        (Ch.13 durability)
    |
UI screen (:feature-*) ............... species-native Compose screens
    |
Task/notification generated .......... WorkManager templates from locked offsets
    |
Verification ......................... unit/DAO/RLS/UI/golden fixtures        (Ch.12)
    |
Evidence + gate sign-off ............. phase gate report                        (Ch.10)
```

Each step maps to a governing document: steps 1–2 to the relevant spec; step 3 to the master plan data model; steps 4–5 to the CQRS section of the master plan; step 8 to the species alignment rules; step 10 to this handbook's gates.

---

### 6. Schema authority

1. **Single source of truth:** Supabase SQL migrations (`supabase/migrations/`). Room entities, API DTOs and doc tables are downstream artifacts.
2. **Rule of three.** A schema change ships as: SQL migration **+** matching Room entity update **+** test coverage (DAO test and, where tenancy applies, an RLS policy test). Any two without the third is rejected in review.
3. **Migrations apply cleanly to an empty database and are reversible** (down migration or verified compensating migration). CI applies `up`, asserts, applies `down`, re-applies `up`.
4. **Additive first.** Breaking changes require a new versioned path and a compatibility note in the EDR/spec section they amend.
5. **Prose vs schema:** when documentation and migrations disagree, migrations win; the prose is corrected in the same change.
6. **Generated artifacts are committed, never hand-edited** (e.g. generated Room DAOs from processors, exported schema snapshots). Drift between snapshot and migrations fails CI.

**Identity law.**

| Rule | Farm OS application |
|---|---|
| Time-ordered UUIDs (UUIDv7), generated client-side, native `uuid` PKs | Every durable table (`animals`, `rabbit_matings`, `domain_events`, …); client-side generation keeps offline creates conflict-free |
| Human-facing codes are separate immutable `slug` columns — never PKs, never FK targets | `ear_tag`, cage `code` ('A','B','C'), nest box `code`, wave `code`, `EDR-nnnn`. Unique per farm via `UNIQUE (farm_id, code)` |

---

### 7. Data laws

| Law | Rule | Enforcement |
|---|---|---|
| Tenancy | Every table carries `farm_id`; RLS predicate `farm_id = auth.jwt() ->> 'farm_id'` (or session GUC equivalent) on all farm-scoped rows | Postgres RLS, fail-closed: no claim ⇒ zero rows. RLS tests per table (Ch.12) |
| Append-only truth | `domain_events` is insert-only; corrections are compensating events, never updates/deletes | DB role for app has INSERT-only on ledger; revoke UPDATE/DELETE |
| Projections are rebuildable | Read models (`analytics_anomalies`, breeding projections, dashboards) can be dropped and rebuilt from the ledger alone | Rebuild script exercised in CI against fixture ledger |
| Idempotent mutations | Client mutations carry `mutation_id`; server deduplicates; retries never double-apply (no double mating record, no double inventory draw) | Unique index on `(farm_id, mutation_id)` in command intake |
| Optimistic concurrency | Mutable aggregates (`tasks`, `litters`, contracts) carry `lock_version`; mismatch returns `VERSION_CONFLICT` to UI | Domain layer check + integration test |
| Safe queue draining | Outbox/dispatch loops use `SELECT … FOR UPDATE SKIP LOCKED` so multiple workers never double-send notifications | Integration test with concurrent dispatchers |
| Single-database scale invariant | Modules are Gradle packages, not services; one Supabase Postgres is shared and a transaction may span module boundaries. Extracting any service must first preserve this invariant — that is an EDR, not a refactor | Architecture test + review rule |
| Retention & growth | Ledger partitioned by month once volume warrants; projections carry the query load; raw ledger retention is permanent (audit value) | Deferred until measured — named deferral, Ch.15 |

---

### 8. Module boundary map

Boundaries are enforced by tests (Konsist/arch-unit style module-graph test in CI), not goodwill — mirroring DDE "boundaries enforced by tests".

| Module | May depend on | Must never |
|---|---|---|
| `:core:*` (db, sync, design, notifications) | nothing internal beyond other `:core-*` | import any `:feature-*` or vendor AI SDK |
| `:domain-*` (per-species engines, `:domain-ai`) | `:core-*` | import Compose/UI, Android framework classes, network clients; only `:domain-*` may emit `domain_event`s |
| `:feature-goat` / `:feature-rabbit` / `:feature-poultry` / `:feature-sheep` / `:feature-cattle` | own `:domain-<species>`, `:core-*` | touch another feature's tables/DAOs/screens; embed another species' UX vocabulary |
| `:ai-client` | `:core-*` | be imported directly by features (features use `:domain-ai` ports) |
| `:ai-runtime-rules` / `:ai-runtime-onnx` / `:ai-runtime-litert` | `:domain-ai` port interfaces | appear anywhere else — vendor SDKs exist only here, behind adapters |
| Copilot tool layer (`FarmToolRegistry`) | allow-listed tool interface | expose write tools without declared side-effect class + confirm step |
| `interfaces/*` (settings, exports) | public ports | reach core tables directly |

Additional rules: RFID/search resolves identity then routes into the owning species feature (never a cross-species list screen). Shared infrastructure tables have exactly one owning writer module per event type. Any boundary exception requires an EDR naming why the graph test should gain an exemption.

---

### 9. Definition of done

All of these, every time — a task missing one is not done:

- [ ] A failing-first test existed that failed before the implementation existed (unit/domain engine, DAO, RLS, or UI).
- [ ] Full local check green: `lint`, `testDebugUnitTest`, instrumented subset for touched modules, `assembleDebug`.
- [ ] New migrations apply cleanly to an empty database and reverse cleanly.
- [ ] New tables carry `farm_id` with RLS enabled and tested; new queries cannot bypass tenancy.
- [ ] New async operation (worker, sync job, notification, export) has a durable identity, an idempotency key (`mutation_id`/dedupe key) and observable state (status visible in UI or logs).
- [ ] Side-effecting capability declares a side-effect class: `local_record` (ledger write), `notification`, `external_call` (Model API), `commercial` (sales contract/inventory deduction). Commercial + external require human-confirm or reconciliation read.
- [ ] Public behaviour change reflected in the governing spec section it belongs to (not chat memory).
- [ ] Golden fixtures still pass: 11-doe KudBat wave schedule fixture, COI pedigree fixtures (parent-child block, half-sib warn, foundation override), offline outbox replay fixture.
- [ ] No forbidden pattern introduced (Chapter 16).

### 10. Phase gates — CI green ≠ phase done

**CI green is a CI gate, not a phase gate.** Lint/tests passing does not close a phase. A phase closes only after the spec-gate review below.

#### 10.1 Gate procedure

Before declaring a phase (or mission within it) complete, re-read the chartered spec section(s). For every MUST/shall/recovery-grade rule in scope, either:

- **(a)** name the production mutation call site that enforces it — the actual code path where the rule fires on real data, opened and verified, not a docstring claim; or
- **(b)** list it as **deferred** with a proposed EDR.

Record the mapping in the phase gate report. Never "tests pass ⇒ phase closed."

#### 10.2 Worked example — rabbit nest-box rule

Rule: *"nest box placed mating+28; removed kindling+21; reintroduced rebreed+28"*

| Gate step | Result |
|---|---|
| Call sites named | `WaveScheduler`/task template generation writes `nest_in_on`/`nest_out_on`; `NestAssigner.reserveNestBoxes()` enforces availability at placement; `NestBoxReminderWorker` fires place/remove notifications from those columns |
| Verified at mutation site | Placement task creation mutates `rabbit_nest_box_assignments` only through `reserveNestBoxes`, which rejects a box not in `available`/`sanitized` state |
| Adversarial check | Could a new sync session replay a placement with a fresh `mutation_id` and double-assign? No: unique active-assignment constraint per box + idempotency index |
| Deferred items | QR-scan assisted placement = deferred, EDR proposed |

Only then does Phase 2's breeding-programme slice sign off, and only then may the next slice chain.

#### 10.3 Adversarial self-check (before any gate sign-off)

- Could a new device session or new `mutation_id` bypass this control?
- Is each claimed call site a real mutation of authoritative state, not a read/helper?
- Does the rule hold with connectivity off (offline path) as well as online?

#### 10.4 No blind chaining

Do not start the next phase until the current gate report is written. If a correction mission is open, freeze further phase progression. Standing auto-resume applies only when an independent gate returns PASS or PASS-WITH-EDR.

---

### 11. Engineering Decision Records (EDRs)

Accepted EDRs are immutable; they are superseded by newer EDRs, never rewritten. Location: `docs/truth/edr/EDR-nnnn-<slug>.md`. Any architectural divergence discovered mid-task becomes an EDR candidate — stop and propose.

#### 11.1 Template

```markdown
# EDR-nnnn — <slug title>

**Status:** Proposed | Accepted | Superseded by EDR-mmmm
**Date:** YYYY-MM-DD
**Affected requirements/spec sections:** …

## Context
Why this decision exists now; constraints in force.

## Alternatives considered
| Option | Assessment |
|---|---|

## Decision
The decision, stated normatively.

## Rationale
Why this option; what evidence ranks support it.

## Consequences
Positive, negative, neutral. What becomes easier/harder.

## Open questions (require explicit human decision)
…

## Smallest safe next step
…
```

#### 11.2 EDR-0001 — In-app CopilotEngine replaces hosted Hermes

**Status:** Accepted · **Date:** 2026-08-20 · **Amends:** AI spec §architecture; master plan stack matrix

**Context.** Original design assumed a farm-hosted Hermes/Ollama sidecar for LLM reasoning. The owner set the hosting law: nothing hosted outside Supabase + APK; no sidecars unless free-tier viable. Hermes requires sustained RAM/CPU beyond any free tier.

**Alternatives considered.** Farm-hosted Ollama (violates hosting law, costs); free sidecar hosting (insufficient RAM, sleeps kill tool-loops); cloud LLM API direct-from-app without governance (credential leakage, unscoped tools).

**Decision.** The Copilot is an in-app Kotlin tool-loop (`CopilotEngine`). Reasoning calls go to a BYO Model API (Settings). Tool execution goes through allow-listed `FarmToolRegistry` against Supabase. Hermes task map: planning → engine loop; retrieval → compiled context builder; tool execution → registry; memory → Room+Supabase state; voice → deferred.

**Consequences.** No server ops for the owner; model quality depends on owner-configured endpoint; audit via `ai_audit`; long loops bounded by device battery policy.

**Open questions.** Default recommended Model API preset list (rank 0 to confirm).

**Smallest safe next step.** Settings screen + `AiClient` contract test behind a fake transport.

#### 11.3 EDR-0002 — BYO Model API in Settings; optional Supabase ai-proxy

**Status:** Accepted · **Date:** 2026-08-20 · **Amends:** AI spec §settings/routing

**Context.** Lane C needs an external LLM endpoint. Keys must never ship inside the APK nor be logged; some owners prefer not to expose even their own key to the client.

**Alternatives considered.** Hard-coded vendor SDK per provider (violates design-for-replacement); embedded default key (forbidden); proxy-only with platform key (creates vendor lock at platform layer).

**Decision.** Settings fields: base URL, model ID, API key, mode (`on_device_only` / `hybrid`), optional `ai-proxy` Edge Function toggle. Routing: offline ⇒ Lane A/B only; hybrid ⇒ Lane C when reachable; proxy mode sends minimised payloads via Edge Function holding the key server-side (Supabase secret). Key stored in Android Keystore; never logged, never rendered in full in UI.

**Consequences.** Owner controls cost/vendor; proxy adds a Supabase-only escape hatch; no vendor code outside `:ai-client`.

**Open questions.** Rate-limit/backoff defaults; payload redaction checklist finalisation.

---

### 12. Verification & evidence

#### 12.1 Test pyramid mapped to Farm OS

| Layer | Tooling | Covers |
|---|---|---|
| Domain engines (unit) | JUnit | WaveScheduler offsets, NestAssigner selection, COI math, foster window rules, colour prediction |
| Persistence | Room in-memory DB | DAO queries, migration integrity, projection rebuild |
| Tenancy | SQL-level RLS tests run against staging Postgres | Every table's policy: right farm sees rows, wrong farm sees zero, missing claim fails closed |
| UI | Compose tests | Species screens, today board, pairing planner flows |
| Background | WorkManager `TestDriver` | Notification schedules fire at locked offsets; retry/backoff policies |
| End-to-end golden fixtures | Instrumented scenario runs | 11-doe KudBat wave; COI pedigree cases; outbox replay |

#### 12.2 Independent verification principle

The code that generates must not be its only judge. Schedule dates produced by `WaveScheduler` are verified against independently computed fixture dates (hand-derived from the locked offset table), never re-computed by calling the same engine. Similarly, COI results are checked against precomputed pedigree values, and anomaly detection against labelled example series.

#### 12.3 Evidence

Every gate produces durable evidence: gate report file, test output, fixture diffs, linked requirement IDs. Evidence is append-only and referenceable from later missions ("Phase 2 gate, item 4"). Verifier quality is itself measured: if a verifier never fails across stages, it is reviewed for vacuity.

---

### 13. Offline-first & durability

| Concern | Rule |
|---|---|
| Outbox mutations | Client-side changes queue in a Room outbox with `mutation_id`; dispatch is idempotent server-side; order per aggregate preserved |
| Replay safety | Replaying the outbox after restore produces identical server state (idempotency index absorbs duplicates) |
| Pull-cursor sync | Device tracks last-received `domain_events` cursor; catch-up pulls events in ledger order; projections rebuilt locally deterministically |
| Conflicts | Optimistic `lock_version` on mutable aggregates; event-ledger merges are additive; UI surfaces genuine conflicts (e.g. same kit sold twice) for human resolution |
| Checkpoint/restore | Long workers (export builders, bulk photo upload) checkpoint progress; crash resume continues, never restarts side effects already applied |
| Effect journal | Side-effecting actions beyond the ledger (sales contract PDF, inventory deduction, notification send) record intent→result in a local journal; reconciliation reads confirm outcome before marking done; UNKNOWN outcomes are never blind-retried — only verified absence permits a new attempt |

---

### 14. Security & credential law

1. Identity: Supabase Auth JWT carries `farm_id`; RLS derives tenancy from the token, never from client-supplied target IDs.
2. Fail closed: missing/expired claim ⇒ zero rows everywhere; no anonymous read paths.
3. Secrets: Model API keys and service keys live in Android Keystore / Supabase secrets respectively; never in code, logs, screenshots, or prompt payloads.
4. Model payloads: minimised, purpose-scoped, species-filtered; no cross-farm data ever leaves row scope.
5. Least privilege: app DB role holds INSERT on ledger, SELECT/DML on its scopes only; Edge Functions use isolated service roles with narrow grants.
6. Audit: `ai_audit` records every Copilot tool invocation (tool, args hash, principal, result class); sensitive commercial actions append `audit_events`.
7. Forbidden: passing any long-lived credential to code that executes model-generated content (prompt-injected code must never inherit keys).

### 15. Staged delivery map

Phases follow the master plan. Every stage leaves the whole system runnable (principle: every stage ships a working system).

| Phase | Goal | Gate criteria (beyond DoD) | Explicitly deferred |
|---|---|---|---|
| 1 | Foundations: auth, farms, animals, ledger, offline sync, species shells | RLS proven fail-closed on all base tables; outbox replay fixture green | Multi-farm orgs; role UI beyond Owner/Worker |
| 2 | Reproduction: goat kidding; rabbit breeding programme (waves, nest boxes, COI, litters, tasks) | Nest-box rule gate (Ch.10.2); COI fixtures; 11-doe golden wave | Buck borrowing marketplace; QR hardware printing |
| 3 | Growth & production: measurements, charts (Vico), Lane A anomalies, `analytics_*` projections | Anomaly call sites named; rebuild-from-ledger proven | Lane B models; Lane C activation |
| 4 | Health module: events, treatments, withdrawal, task integration | Withdrawal blocking rules at mutation sites | Vet telemedicine; lab integrations |
| 5 | Poultry / sheep / cattle modules as first-class products | Species alignment rule audit per module | Kinds beyond chicken/duck in poultry v1 |
| 6 | Inventory, sales, waitlist, contracts, market planner | Commercial side-effect class enforced; reconciliation reads proven | Payments processing; invoicing taxes |
| 7 | Exports: cage cards PDF, ICS calendar, photo journal, reports | Export determinism fixtures | Custom report builder |
| 8 | Intelligence: Lane B on-device models, Lane C BYO Model API, Copilot + FarmToolRegistry, `ai_audit` | Tool allow-list audit; payload minimisation checklist; advisory-only confirm gates | Voice; autonomous treatment suggestions; herd-level training |

Deferrals are named, not silent: each has an owner decision pending and may become an EDR when activated.

---

### 16. Forbidden patterns

1. A second source of truth for any mutable state (shadow tables, un-synced local edits, spreadsheet imports that bypass the ledger).
2. A generic Animals home screen or cross-species animal-list product.
3. AI auto-treatment, auto-culling, auto-selling, or clinical mutation without human confirmation.
4. Hard-coding a model vendor, cloud region or storage provider behind anything but Settings/contracts.
5. Retrying a side-effecting operation without an idempotency key or reconciliation read.
6. Widening RLS, a tool lease, or an autonomy scope to make a test pass.
7. Hand-editing generated artifacts (migrations snapshots, generated DAOs, exported schema docs).
8. Introducing a framework, message bus or microservice split for core state without a measured need + EDR.
9. Editing `docs/truth/**` or locked spec sections as a side effect of implementation.
10. Shipping a "temporary" alternative when blocked instead of stating the blocking decision.

---

### 17. Governance & when blocked

#### 17.1 Change control flow

| Change type | Path |
|---|---|
| Constitution / alignment rule change | Rank-0 owner decision → dated revision entry in master plan header → specs updated |
| Spec elaboration (no contradiction) | Direct edit of the owning spec, noted in its revision line |
| Architectural divergence found mid-task | Stop → draft EDR → owner decision → accepted EDR supersedes; specs amended by reference |
| Business-rule change (offsets, thresholds) | Spec change (rank 5 artifacts) + fixture update in same change |
| EDR supersession | New EDR references old; old file gains no edits beyond a superseded-by header note |

#### 17.2 When blocked

Say so. State the smallest decision that would unblock. Stop. Do not invent a contract, do not implement a temporary alternative, do not widen any scope to progress. A blocked state is reported in the mission/gate record with the exact question for the owner.

---

### 18. Traceability appendix

| Handbook chapter | Farm OS source | DDE counterpart |
|---|---|---|
| 1 Authority model | Master plan alignment rules | REV_2_0 §2.2 authority ranks |
| 2 Constitution | Master plan + AI spec hosting law | Product Constitution template |
| 3 Environments | AI spec hosting footprint | REV_2_0 §1.3 five environments |
| 4 Principles | Master plan principles; AI spec product law | REV_2_0 §2.3 |
| 5 Spine | Master plan CQRS §4 | REV_2_0 §2.5 canonical spine |
| 6 Schema authority | Master plan data model §4 | REV_2_0 §3.1–3.4 |
| 7 Data laws | Master plan CQRS + sync; breeding spec schema | REV_2_0 §3.2, §3.5, §12 |
| 8 Boundaries | Master plan module map §5 | REV_2_0 §2.6; AGENTS.md boundaries |
| 9 Definition of done | Breeding spec test plan §13 | AGENTS.md definition of done |
| 10 Phase gates | Nest box schedule locked offsets | `.cursor/rules/mission-chapter-gate.mdc` |
| 11 EDRs | AI spec locked decisions | EDR-0001…0007 in dde repo |
| 12 Verification | Breeding spec tests; AI spec evals | REV_2_0 Ch.11 |
| 13 Durability | Master plan offline sync | REV_2_0 Ch.12 |
| 14 Security | AI spec safety/privacy | REV_2_0 Ch.14 |
| 15 Staged delivery | Master plan phased delivery | REV_2_0 Ch.18 |
| 16 Forbidden | Master plan invariants; AI spec forbidden | AGENTS.md forbidden list |
| 17 Governance | Master plan revision process | REV_2_0 Ch.20; AGENTS.md when blocked |
| 18 Traceability | This table | REV_2_0 Ch.20 traceability |

*End of handbook. This document governs HOW; the specs govern WHAT; the owner outranks everything.*

<a id="part-h"></a>

# Part H — Vet Intelligence Feature Implementation Spec

*Source: `FARM_OS_VET_INTELLIGENCE_FEATURE_IMPLEMENTATION_SPEC.md`*

---

## Farm OS — Vet Intelligence & Expert Research → Usable Features

**Version:** 1.0 · **Date:** 22 August 2026
**Purpose:** Turn the veterinary expert research pack (`FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md`), the shared Health module (`FARM_OS_HEALTH_MODULE_SPEC.md`) and the embedded AI module (`FARM_OS_EMBEDDED_AI_MODULE_SPEC.md`) into **implemented, usable features**: concrete backend tables, domain engines, and screen-by-screen UX flows.
**Governs:** WHAT ships for vet-level intelligence. HOW it ships (gates, EDRs, tenancy) is governed by `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.
**Hosting law unchanged:** app + Supabase only. AI advisory-only. Species-native UX.

---

### Contents

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

### 1. The knowledge pipeline: research pack → shipped feature

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

### 2. Backend: knowledge base schema

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

### 3. Backend: clinical recording & intelligence projections

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

### 4. Domain engines (Kotlin, in `:domain-*`)

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

### 5. UX flows: daily intelligence surfaces

#### 5.1 Module dashboard — "Health tip of the day" card

```
[Dashboard top card]
  "Score FAMACHA in shade; heat changes eyelid colour."        (TipSelector output)
  Footer: Not a diagnosis. Follow your vet pack.   [Health >]
```

- Tap → Health home. Card suppressed if no tips remain (never repeats within 14 days).
- Weather-aware: Open-Meteo fetch (existing weather widget) feeds `season` preference.

#### 5.2 Health Today board

Sections, in order:

1. **Withdrawal board strip** (if any active): animal/group, product, clock chip `milk: 3d left`, red if sale attempted while active.
2. **Overdue pack tasks**: vaccination/deworming-check tasks from ProtocolPackEngine, oldest first, each opens the capture form pre-filled.
3. **Red-flag anomalies** (`severity=critical`): FAMACHA 4–5, recumbency flags, mortality spike — each row: title, one-line evidence, two buttons `[Record check]` `[Call vet]`.
4. **Watch list**: `watch` polarity anomalies, swipe to dismiss (writes `status=dismissed`, audited).
5. **Playbook cards** (health spec §6): colostrum, needle hygiene, biosecurity… tap opens static playbook page with source citation.

Empty state: "No health actions today. Next pack task: CDT booster — 12 May."

#### 5.3 Animal timeline (per-animal intelligence)

```
[Goat "Nala"]  FAMACHA 3 ▂▃▅  BCS 3  ADG 120 g/d (cohort band ±σ)
  Events: 02 Feb CDT dose ✓ · 28 Jan FAMACHA 3 ✓ · 20 Jan drench (FEC 750) ✓
  [Chart|Table|Both]  [Ask Copilot about Nala]
```

- Every chart implements `ChartableFeature` (AI spec §3); Vico; cohort band from baselines.
- Anomaly chips on the series (▲ negative, ● watch, ★ positive) tap → anomaly detail sheet: evidence JSON rendered as sentences, action drafts from `action_catalog` with `[Accept]` (creates task) / `[Dismiss]`.
- "Ask Copilot" opens the skill-scoped chat (`goat-kidding-briefing` etc.), which may only cite rows the session's tools returned.

#### 5.4 Outbreak path (poultry mortality spike / notifiable hint)

```
Mortality spike detected (House 2, 3.1× 7-day baseline)
  [Quarantine house]  [Record mortality]  [Biosecurity walk]  [Notifiable info]
```

- `Quarantine house` sets group status, stops movements in-app, creates daily recheck task ×7.
- `Notifiable info` opens jurisdiction SOP card (phone number, farm-configured) — never auto-reports.

### 6. UX flows: clinical capture forms

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

### 7. UX flows: protocol packs & vet acceptance

```
Health > Protocol packs
  [Goat — dairy-temperate-v1]        status: draft
     Slots: CDT prepartum (−30 d) · CDT kid priming (+30/±4wk alt) · [optional: orf]
  [Accept pack]  (Owner + vet name + date required)
```

- Accepting writes `health_protocol_packs.status='vet_accepted'`, stamps `accepted_by_vet`/`accepted_at`; only then does ProtocolPackEngine generate tasks.
- Editing a slot after acceptance creates a **new pack version** (draft); the accepted one is retained for audit.
- Pack detail screen shows each slot's source citation (health spec §3 sources).

### 8. UX flows: Copilot & anomaly triage

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

### 9. Roles, safety gates & withdrawal enforcement

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

### 10. Offline behaviour

- Seeds bundled in APK assets → Room mirror at first run; Supabase refresh when online (version check).
- Tip selection, Lane A detection, KPI computation: fully on-device from Room mirror.
- Copilot: offline ⇒ NL disabled; charts/anomaly drafts still work (AI spec §5 empty-URL mode).
- Forms/outbox per master plan sync; withdrawal board mirrored to Room so sale-blocking works in a dead-zone barn.

### 11. Seeding plan (day-1 content)

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

### 12. Acceptance tests & golden fixtures

1. **CDT pack expansion fixture**: doe kidded 01 Mar ⇒ tasks: prepartum CDT (if accepted late, retro-dated), kid primer at 30 d ±template choice, booster +4 wk — exactly one template fires (health spec §3.1 rule).
2. **Withdrawal block fixture**: milk withdrawal 4 d ⇒ sale of milk blocked day −1, allowed day 0; FEFO decremented on treatment save.
3. **Lane A growth fixture**: synthetic cohort (n≥8) with one animal at −2.2σ ⇒ exactly one negative anomaly + reweigh/vet-check drafts, no drug actions.
4. **Tip stability fixture**: same farm+date across devices picks identical tip; impression exclusion within 14 d.
5. **RLS fixtures**: wrong-farm token sees zero rows in every new table; missing claim fails closed.
6. **Outbreak fixture**: mortality spike ⇒ quarantine group status + 7 daily tasks + notifiable card opens SOP, sends nothing.

### 13. Traceability

| Feature area | Source |
|---|---|
| Knowledge pipeline sinks | vet pack §0–§8; health spec §2–§7; AI spec §3–§6 |
| Withdrawal/formulary law | health spec §5 |
| Tip algorithm | health spec §7 |
| Anomaly/action law | AI spec §4; action catalog §2.3 here |
| Copilot constraints | vet pack §6 must/must-not list; AI spec §6 |
| Pack acceptance gate | health spec §3; vet pack §7 |
| Handbook gates | FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md Ch.9–10 |

*End of Part H.*

<a id="part-i"></a>

# Part I — Operations & Economics Layer

*Source: `FARM_OS_OPERATIONS_ECONOMICS_LAYER_SPEC.md`*

---

## Farm OS — Operations & Economics Layer

**Version:** 1.0 · **Date:** 22 August 2026
**Status:** Scope EXPANSION approved by owner (22 Aug 2026) — supersedes "no financial ledger / inventory hooks only" locks per EDR-0003.
**Owner decisions locked this day:** full cost accounting · standard stock management + feed planning & simple ration checks · paddock records + rotation planner · farm-owner business cockpit.
**Governs:** WHAT ships for operations and money. HOW: `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.
**Unchanged:** app + Supabase hosting law; offline-first; RLS tenancy; species-native UX; advisory-only AI.

### Contents

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

### 1. Design stance: money as events

Money never lives in a shadow spreadsheet-in-the-database. All financial facts are `domain_events` of class `commercial`:

- `money.recorded` (expense or income, with `category_code`, optional link to animals/group/litter/paddock/enterprise)
- `money.allocated` (derived projection rows only; allocation is recomputable)

Consequences:

- Append-only, replayable, RLS-scoped like every other event (handbook Ch.7).
- No double-entry GL. This is **farm unit economics**, not statutory accounting. Exports exist for accountants; we do not become one.
- Every stock movement that has value can optionally carry a cost line (`inventory_issue` → expense), which is what makes cost-per-kg computable without manual entry.

### 2. Backend schema — cost accounting

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

### 3. Backend schema — inventory & supply

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

### 4. Backend schema — pasture & rotation

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

### 5. Domain engines

| Engine | Module | Responsibility |
|---|---|---|
| `MoneyRecorder` | `:domain-finance` | Validate + emit `money.recorded`; idempotent by `mutation_id`; auto-post purchase/expense links from inventory & treatments |
| `AllocationEngine` | `:domain-finance` | Spread records to enterprises by basis (direct / head_days / weight_gain / feed_kg / manual); rebuild `money_allocations` from ledger |
| `UnitEconomicsEngine` | `:domain-finance` | §11 formulas → projection rows for cockpit & module KPIs |
| `InventoryProjector` | `:domain-inventory` | movements → `inventory_levels` (qty, avg cost, FEFO earliest expiry); low-stock/reorder detection vs `reorder_point` |
| `RationPlanner` | `:domain-inventory` | §8: demand forecast from groups + purpose packs; simple ration check (energy/protein/DM vs species tables); feed_issue movements |
| `RotationEngine` | `:domain-pasture` | sessions → `paddock_state`; rest-day counters; rotation suggestion (ready paddocks ranked by days_rest + shade/water fit); quarantine interlock |
| `CockpitAggregator` | `:domain-finance` | §10 read model: KPIs, money, stock, compliance, exports |

All pure Kotlin; projections rebuildable; every engine emits through its owning module's event emitter (handbook Ch.8).

### 6. UX flows — money capture

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

### 7. UX flows — inventory operations

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

### 8. UX flows — feed planning & ration checks

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

### 9. UX flows — paddock rotation planner

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

### 10. UX flows — owner business cockpit

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

### 11. Unit economics: formulas

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

### 12. Roles & safety

| Capability | Worker | Farm mgr | Owner |
|---|---|---|---|
| Record money entry | ✗ | ✓ | ✓ |
| Receive/issue stock, stocktake count | ✓ | ✓ | ✓ |
| Post stocktake / edit reorder points | ✗ | ✓ | ✓ |
| Move grazing group | ✓ | ✓ | ✓ |
| Lock paddock / set quarantine | ✗ | ✓ | ✓ |
| Edit categories, enterprises, allocation rules | ✗ | propose | ✓ |

Money records are immutable once synced; corrections are reversing entries (`money.recorded` negative-mirror with link) — consistent with append-only law.

### 13. Offline behaviour

- All captures (money, movements, sessions, stocktake counts) queue in outbox with `mutation_id`; projections recompute on device from Room mirror.
- Cockpit reads are projection-first: month view renders offline with last-synced banner.
- Rotation engine and ration checks run fully on-device; purchase drafts send when online.

### 14. Phasing & gates

| Phase slice | Ships | Gate (handbook Ch.10 style) |
|---|---|---|
| O1 Inventory core | items+movements+levels, receive/issue/stocktake UI, low-stock worker | FEFO fixture: earliest-expiry batch issued first; variance adjust on stocktake post; RLS fixtures |
| O2 Money core | categories, records, auto-post links, price history | idempotent replay fixture (double-purchase never double-posts); reversal flow test |
| O3 Pasture | paddocks, sessions, rotation planner, quarantine interlock | overlap-rejection call-site named; quarantine interlock adversarial check |
| O4 Feed & rations | demand forecast, coverage chips, ration check drafts | demand math golden fixture (24-head dairy herd example above); suggestion-is-draft gate |
| O5 Allocations & unit economics | AllocationEngine, §11 metrics | rebuild-from-ledger proof: drop allocations, rebuild, byte-equal |
| O6 Cockpit & exports | cockpit screen, PDF/CSV export | every tile provenance drill-down works; export determinism fixture |

### 15. Traceability

| Area | Source / relation |
|---|---|
| Event/idempotency/RLS law | Handbook Ch.6–7; master plan CQRS |
| Treatment→inventory auto-issue | Vet intelligence spec §4 WithdrawalClock; health spec §5 |
| Sales contracts → income | Rabbit breeding spec (contracts/waitlist) |
| Market planner targets | Rabbit breeding spec (market plans) ↔ cost/kg gain here |
| Weather inputs | Health spec §8 widget feeds rotation/feed heat hints |
| KPI formulas | Vet pack §8 ICAR sources; kpi_definitions table |
| Scope change authority | EDR-0003 (owner decision 22 Aug 2026) |

*End of Part I.*

<a id="part-j"></a>

# Part J — Design System Spec (Field-first visual language)

*Source: `FARM_OS_DESIGN_SYSTEM_SPEC.md`*

---

## Farm OS — Design System Spec ("Field-first visual language")

**Version:** 1.0 · **Date:** 22 August 2026
**Purpose:** Make Farm OS look and feel like a crafted professional product, not generated output. Defines tokens, typography, colour, components, patterns, motion, content voice, and the automation that enforces all of it.
**Applies to:** all `:feature-*`, `:core-design`; binding on humans and agents.
**Enforcement:** §14–§15 (lint, snapshot tests, gallery, gate items). HOW-law per `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.

---

### Contents

| § | Title |
|---|-------|
| 0 | Why apps look AI-generated — the anti-goals |
| 1 | Design principles |
| 2 | Colour tokens (semantic, never raw) |
| 3 | Typography |
| 4 | Spacing, shape, elevation, iconography |
| 5 | Core component specs |
| 6 | Named screen patterns |
| 7 | Motion |
| 8 | Content voice (microcopy law) |
| 9 | Accessibility |
| 10 | Theming & branding seams |
| 11 | Screen inventory & flow polish standards |
| 12 | App icon & system surfaces |
| 13 | Asset pipeline |
| 14 | Enforcement automation |
| 15 | Definition of done — UI slice |
| 16 | Anti-generic guardrails ("designed, not generated" protocol) |
| 17 | Traceability |

### 0. Why apps look AI-generated — the anti-goals

| AI-tell | Our standing rule |
|---|---|
| Default Material purple/blue, gradient buttons | One brand colour, used sparingly; zero gradients in controls |
| Emoji in UI copy and empty states | Never. Icons from one set; words from the voice guide |
| Everything rounded 28dp+, floating cards everywhere | Radii scale ≤16dp; flat surfaces, hairline borders, tone-shift elevation |
| Generic copy: "Welcome to your amazing farm!" | Species-correct, terse, sentence case; empty states state the next action |
| Random spacing, mixed text sizes per screen | 4dp grid + fixed type scale; nothing off-scale |
| Decorative illustrations, glassmorphism, neon dark mode | None. Dark mode is a true tonal counterpart, not inverted neon |
| Every screen invented ad hoc | Screens assemble from the shared component library + named patterns |

### 1. Design principles

1. **Field-first.** Sunlight-legible contrast, 48–64dp targets, one-thumb capture flows. A vet wrap or dust decides usability more than beauty does.
2. **Calm data density.** Farmers scan; dashboards are dense but quiet — hairlines, alignment, tabular numbers, no decoration between the user and data.
3. **One accent.** A single deep agricultural green carries identity; species colours appear only as small identity chips; status colours are reserved and rare.
4. **Native, tuned.** Material 3 components with our tokens — never custom-rebuilt basics, never web-clone patterns.
5. **Motion restraint.** Transitions explain hierarchy (shared-axis); nothing bounces, floats, or celebrates.
6. **Content is UI.** Microcopy, empty states, and errors are designed artifacts with the same rigor as pixels.

### 2. Colour tokens (semantic, never raw)

Defined once in `:core-design/theme/FosColors.kt` (+ XML equivalent); features may reference **tokens only**.

#### 2.1 Light

| Token | Hex | Use |
|---|---|---|
| `brand/primary` | #2C5539 | Primary actions, active nav, links |
| `brand/onPrimary` | #FFFFFF | |
| `brand/primaryContainer` | #DDE8DD | Selected backgrounds |
| `surface/canvas` | #FAFAF6 | App background (warm paper, not white) |
| `surface/card` | #FFFFFF | Cards, sheets |
| `surface/sunken` | #F1F1EA | Wells, chart plots |
| `text/primary` | #1B1D1A | |
| `text/secondary` | #5A5D57 | Labels, metadata |
| `border/hairline` | #E3E4DC | Card outlines, dividers |
| `status/critical` | #B3261E | Red flags, withdrawal breach |
| `status/warning` | #8A5A00 | Watch anomalies, low stock |
| `status/positive` | #2E6B34 | Positive anomaly, ready state |
| `status/info` | #3A5A78 | Neutral notices |
| `status/withdrawal` | #8E3B62 | Withdrawal clocks (reserved hue) |

#### 2.2 Species accents — chips and badges ONLY (never themes, never headers)

| Module | Token | Hex |
|---|---|---|
| Goat | `species/goat` | #A9762B (ochre) |
| Sheep | `species/sheep` | #6B8F71 (sage) |
| Cattle | `species/cattle` | #8A6240 (leather) |
| Rabbit | `species/rabbit` | #5B7C99 (slate) |
| Poultry | `species/poultry` | #B65C33 (terracotta) |

#### 2.3 Dark & outdoor variants

- Dark: tonal counterparts (canvas #121411, card #1C1F1B, primary #9BC49F, same hues lifted for ≥4.5:1). Not pure black, not neon.
- **Outdoor mode** (toggleable, default ON for field roles): canvas #FFFFFF, text #000000, minimum contrast 7:1, target size floor 64dp, haptic confirmations emphasized.

Contrast floors: text 4.5:1 (7:1 outdoor), icons/status 3:1. Verified in §14 CI.

### 3. Typography

Single family: **Inter** (variable, bundled). Tabular figures for all numeric data. No second family anywhere.

| Style | Size/Line | Weight | Use |
|---|---|---|---|
| display | 28/34 | 600 | Module dashboard greeting only |
| titleLg | 22/28 | 600 | Screen titles |
| titleSm | 16/22 | 600 | Card titles, section heads |
| body | 15/22 | 400 | Default |
| bodyStrong | 15/22 | 600 | Emphasis, values in lists |
| label | 13/18 | 500 | Metadata, chips |
| numeric | 15/22 | 500, tabular | Weights, dates, money |
| numericLg | 24/30 | 600, tabular | KPI tiles |

Rules: sentence case everywhere; dates as `02 Feb`; weights `45.2 kg`; money with farm currency, no cents unless entered.

### 4. Spacing, shape, elevation, iconography

- **Grid:** 4dp base. Screen margin 16dp; card padding 16dp; list item 56dp min; intra-card gap 12dp; section gap 24dp.
- **Radii scale (only these):** 8dp (inputs, chips), 12dp (cards, sheets), 16dp (bottom sheets max). Buttons 8dp. **No pill buttons, no 28dp cards.**
- **Borders over shadows:** cards are `surface/card` + 1dp `border/hairline`. Elevation reserved for: FAB (level 1), drag states, modal sheets (level 2). No drop shadows on lists or tiles.
- **Icons:** Material Symbols (rounded variant), one weight, 20/24dp, always with text label in buttons. Species glyphs only in identity chips. No emoji, ever.
- **Touch targets:** ≥48dp (64dp outdoor). Primary action per screen = one filled button; everything else tonal/text.

### 5. Core component specs

#### 5.1 Cards

```
┌─────────────────────────────────┐
│ ● Goat · Nala            3d ▾  │  label row: species dot 8dp + name + meta
│ FAMACHA 3 · BCS 3.0 · 45.2 kg   │  body: numeric style, dot-separated
│ ─────────────────────────────── │  hairline
│ [Record check]  [Call vet]      │  actions: text buttons, right-aligned
└─────────────────────────────────┘
```

- Radius 12dp, hairline border, no shadow. Title row 40dp min. Max one primary action per card.

#### 5.2 Data table (dense lists)

- Row 56dp; column headers `label` style, hairline under; numeric columns right-aligned tabular; zebra **off**; row press = 8% primary overlay; sticky header on scroll.
- Status appears as a 6dp dot + word, never as a coloured pill unless it's a chip pattern.

#### 5.3 Chips

Input chips for filters (8dp radius, hairline, selected = `primaryContainer`). Species chip = 20dp circle + label. Status chip reserved for withdrawal/countdown only (uses `status/withdrawal`).

#### 5.4 Empty states

Structure: icon (24dp, `text/secondary`) → one-line statement → one action button. No illustration, no apology, no emoji.

> "No health actions today. Next task: CDT booster — 12 May." `[View schedule]`

#### 5.5 Forms

- Single column; labels above fields (`label` style); helper text ≤1 line; validation inline below field in `status/critical`, never dialogs.
- Section headers as hairline + `label` text, not cards-in-cards.
- Score pads (FAMACHA/BCS): segmented control with photo reference sheet behind a `ⓘ` — the photo guide is a documented asset, not a web image.
- Numeric entry: numeric keyboard, decimal comma/point per locale, unit suffix inside the field (`kg`), tabular results.

#### 5.6 Navigation

- Bottom bar: 4 destinations max per module (Today, Record, [Species core], More). 24dp icons + 12dp labels, active = `brand/primary` icon + dot indicator (no filled-pill nav).
- Module switcher: top bar left — species chip + module name, opens drawer with species list (identity chips only, no colour themes).
- Top bar: flat `surface/canvas`, title `titleSm`, actions as icon buttons 48dp.

#### 5.7 Charts (Vico)

- Line 2dp, no fill gradients; cohort band = 12% primary; anomaly markers: ▲ 6dp triangle critical, ● 6dp dot watch; axis labels `label` style, max 4 y-ticks; tabular axis numerals.
- Chart background `surface/sunken`, hairline frame. Tooltip: single card, `titleSm` value + `label` date.

### 6. Named screen patterns (the library agents build from)

Every screen is an instance of one of these — no invented layouts:

| Pattern | Used by | Structure |
|---|---|---|
| `ModuleDashboard` | 5 species homes | greeting (display) → tip card → KPI strip (3 numericLg tiles) → Today list → charts section |
| `TodayBoard` | Health/rabbit/pasture today | section headers hairline → action rows (dot status + title + meta + action) |
| `TimelineScreen` | animal/litter/paddock detail | sticky header card → segmented [Chart/Table/Both] → event list grouped by month |
| `CaptureForm` | all recording | single column sections → sticky bottom bar [Save] + unsaved-changes chip |
| `InventoryList` | stock, formulary | search + filter chips → dense table → FAB |
| `PlanBoard` | feed plan, market planner, rotation | week/month columns → coverage chips → draft suggestion cards with [Apply] |
| `Cockpit` | owner business view | month switcher → net hero (numericLg) → enterprise table → KPI grid → export row |
| `SettingsList` | settings, pack management | grouped hairline sections → rows with value + chevron |
| `Wizard` | farm setup, pack acceptance | one question per step → progress dots → [Back][Next] |

Pattern specs live as Compose templates in `:core-design/patterns/` — features compose, never restyle.

### 7. Motion

- Durations: 150ms (chips, presses), 220ms (screens, shared-axis X forward/back), 300ms (sheets).
- Easing: standard acceleration; **no springs, no bounce, no overshoot**.
- List diffs: animate placement only; no item fade-stagger cascades.
- Respect system "remove animations"; reduce-motion ⇒ crossfade 100ms.

### 8. Content voice (microcopy law)

- Terse, specific, sentence case. Verbs first on buttons ("Record check", not "Would you like to record a check?").
- Numbers in copy: figures, not words ("3 tasks due", not "three tasks due").
- Species vocabulary enforced: "kidding" not "birthing" in goat; "kindling" in rabbit.
- Errors say what happened + the fix: "Sync failed — will retry on connection. Your entries are safe on this device."
- No marketing tone inside the product. No exclamation marks. No "simply/easily/just".

### 9. Accessibility

- Contrast floors per §2.3; all text scales to 200% (no fixed-height text containers; wrap, don't truncate).
- Touch ≥48dp/64dp outdoor; every icon button has contentDescription; charts have table toggle (data parity rule).
- TalkBack order = visual order; focus goes to first field on form open; live region for sync/withdrawal countdowns.

### 10. Theming & branding seams

- `:core-design/theme/` owns: tokens, Type.kt, Shapes.kt, Theme.kt (light/dark/outdoor), PlatformIcons.
- Farm-level config: currency symbol, date locale, unit system (metric/imperial) — data-driven, not re-themed.
- **No user themes, no colour skins.** Species identity = chips only. This constraint is the brand.

### 11. Screen inventory & flow polish standards

- Every screen from the master plan/binder specs maps to a pattern in §6; the mapping table lives in `:core-design/patterns/PATTERNS.md` and is reviewed at each phase gate.
- Flow rules: any capture ≤3 taps from its dashboard; destructive actions confirm via bottom sheet (not dialog); every list has search + empty state + offline banner slot.
- Loading: skeleton of the real layout (same pattern), never spinners-in-cards; sync state is one status chip in the top bar, not per-card badges.

### 12. App icon & system surfaces

- Icon: single glyph — simplified goat head silhouette on `brand/primary` field, no gradients, no text. Adaptive icon layers: background solid, foreground 1dp-safe.
- Splash: brand field + glyph, 200ms max, then content. No loading slogans.
- Notifications: templates use app icon + species dot + terse title ("Nest box due — Cage B"); actions mirror in-app buttons.

### 13. Asset pipeline

- Icons: Material Symbols Rounded via material-icons-extended (tree-shaken by lint).
- Photography: score reference sheets (FAMACHA eyelid, BCS hands-on) bundled at 2x, compressed WebP, credited to source in-app ⓘ.
- Exports (cage cards, PDFs): rendered from same tokens via Compose → PDF path so paper matches screen.

### 14. Enforcement automation (this is what actually prevents "AI-generated")

1. **Token lint (Konsist/arch test):** features may not declare `Color(0x…)`, raw `dp` outside the approved scales, or `FontFamily` other than Inter. CI fails the build. Only `:core-design/theme` defines colours.
2. **Pattern lint:** screens must extend/compose a §6 pattern base; new one-off layouts require a design-system EDR.
3. **Contrast check:** unit test computes contrast for all token pairs used in text/status roles; fails below floors (incl. dark + outdoor variants).
4. **Screenshot tests (Paparazzi or Roborazzi):** every core component × light/dark/outdoor × font-scale 1.0/1.3/2.0 renders golden images; diffs block PRs. This catches drift that code review misses.
5. **Component gallery debug screen** (`GalleryActivity`): all tokens/components/patterns live in-app for eyeballing; gallery screenshots are part of the gate evidence.
6. **Copy lint:** forbidden strings list (`!`, "welcome to", "amazing", emoji ranges, ALL-CAPS headers >4 chars) fails a string-resource test.

### 15. Definition of done — UI slice

A UI task is done only when:

- [ ] Built from §6 patterns with §2–§5 tokens/styles; zero raw values
- [ ] Light + dark + outdoor screenshot goldens updated and reviewed
- [ ] Font scale 2.0 usable (no clipped text)
- [ ] TalkBack passes the flow; targets ≥48dp
- [ ] Empty/loading/error states designed (all three)
- [ ] Microcopy follows §8 voice
- [ ] Gallery updated if any component changed

### 16. Anti-generic guardrails ("designed, not generated" protocol)

Repo consulting produced generic apps because agents (a) write UI without ever seeing rendered pixels and (b) converge on the *average* aesthetic of whatever they consult. These ten guardrails eliminate both failure modes. Binding on humans and agents.

1. **Authority split — repos are engineering references, never visual ones.** Now in Android et al. are consulted for module graphs, test harnesses, sync plumbing — cited by file path in PRs. Consulting any external repo for layout, spacing, colour or component choice is forbidden; the only visual authority is this spec (`FARM_OS_DESIGN_SYSTEM_SPEC.md`). An agent needing a layout decision cites a §6 pattern code, not a repo screen.
2. **Design-before-code.** Every new screen lands in the Gallery debug app **first**, built from patterns/tokens with realistic fixtures, screenshotted, and approved by the owner **before** the integration PR exists. A screen that never appeared in the Gallery cannot ship. This converts "taste debates in code review" into cheap pixel reviews.
3. **Realistic-fixture law.** All previews, screenshot tests, demo mode and gallery entries render the named fixture dataset — Nala (goat, FAMACHA 3, ADG 118 g/d), Cage B KudBat wave dates, August money records. Forbidden in any committed UI code or test asset: `Lorem`, `Item 1`, `Test`, `Sample`, `John Doe`, `foo`, placeholder avatars. Generic fixtures produce generic-feeling screens and hide density problems.
4. **Screenshot-evidence gate.** UI PRs must attach Roborazzi/Paparazzi captures (light + dark + outdoor × font-scale 1.3) generated in CI; CI posts gallery diffs as PR comments. Extends handbook Ch.10: **tests green ≠ UI done** — the phase gate includes owner visual sign-off of the gallery diff, recorded in the gate report.
5. **Expanded static lints (CI-blocking).**
   - Material 2 APIs and default `MaterialTheme.typography`/`MaterialTheme.colorScheme` direct usage outside `:core-design` — fail.
   - `Brush.linearGradient/radialGradient` anywhere in features — fail (gradients banned).
   - Emoji and high-triage codepoints in string resources or composables — fail.
   - Raw `dp`/`sp` values outside the §4 scales and type scale — fail (constants only).
   - `TextStyle(...)` construction outside `:core-design/theme` — fail; `FosText.*` styles only.
6. **Density matrix in CI.** Goldens render at font-scale 1.0/1.3/2.0 and smallest-width 360/411/780. A layout that breaks at 200% text is broken, full stop.
7. **One-screen-one-pattern audit.** Each phase gate walks `PATTERNS.md`: every shipped screen maps to exactly one §6 pattern instance; deviations surface as EDR candidates. Orphan screens (pattern-less) are release blockers.
8. **Microcopy pass at gate.** §8 voice checklist run over every new string resource: verb-first buttons, sentence case, species vocabulary, numbers as figures, zero exclamation marks. Copy defects block like failing tests.
9. **Weekly sunlight walk.** Owner installs the weekly debug build, walks the five core flows outdoors on a real device (outdoor mode ON), files findings as P1 visual bugs. Release trains require a clean walk. This is the single highest-signal guardrail: sunlight reveals contrast, target-size and glanceability lies that emitters hide.
10. **Aesthetic-debt budget.** Token-lint violations are counted per module on every CI run and trended. Any upward trend freezes new feature UI in that module until debt returns to zero. Generic drift becomes economically impossible to accumulate silently.

#### 16.1 Why this works where "good prompts" fail

| Failure mode | Guardrail that kills it |
|---|---|
| Agent never sees output | #2 Gallery-first + #4 screenshot evidence |
| Average-pull from reference repos | #1 authority split |
| Placeholder-feeling screens | #3 realistic fixtures |
| Slow silent drift | #5 lints + #10 debt budget |
| Looks fine in IDE, unusable in field | #6 density matrix + #9 sunlight walk |
| Taste argued in review | #2 pixel review before code exists |

### 17. Traceability

| Area | Source |
|---|---|
| Species-native UX law | Master plan alignment rules |
| Chartable/table parity | AI spec §3 |
| Withdrawal/status colour reservation | Vet intelligence spec §5.2, §9 |
| Outdoor mode rationale | Field-first principle; vet pack handling sections |
| Gate integration | Handbook Ch.9 DoD, Ch.10 phase gates |
| Agent enforcement | `.cursor/rules/farmos-design-guardrails.mdc`; root `AGENTS.md` |

*End of Part J.*

















