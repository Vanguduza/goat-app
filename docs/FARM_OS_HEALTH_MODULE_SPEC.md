# Farm OS — Shared Health module (all species)

**Companion to:** technical master plan + `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md`  
**Date:** 19 August 2026  
**Android:** `:feature-health` plus dashboard **Tip of the day** and **Weather** (this file).

This module is **farm-wide** (one Health tile) but **species-aware**: opening a goat uses goat forms; a duck flock uses poultry-kind forms. It is not a mixed “sick animals list” as the home of goats or rabbits.

### Product law (non-negotiable)

| The app **does** | The app **does not** |
|------------------|----------------------|
| Ship **vaccination schedule templates** (disease slots, timing, route class) | Invent a brand, batch, or **dose** |
| Catalog **common diseases** with signs, first response, prevention | Diagnose from a photo and start a drug |
| Hold a **farm formulary** of products the vet approved (name, route, withdrawal days from **label**) | Let Copilot or a worker pick “the usual antibiotic” |
| Record treatments, withdrawals (meat / milk / **egg**), tasks | Treat without a veterinarian–client–patient relationship (VCPR) |
| Supportive **first aid** the stockperson can do while waiting for the vet | Publish mg/kg tables for farmers to copy |

Every schedule below is an **example template** from extension / Merck-style programs. The farm’s attending veterinarian **accepts or edits** a pack before the tile generates due dates.

---

## 1. Health information architecture

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

## 2. Data model (additions)

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

## 3. Vaccination schedule templates

**Core vs optional:** Core slots generate tasks when the pack is `vet_accepted`. Optional slots are off until the vet enables them (local risk).

### 3.1 Goats and sheep — CDT is the universal core

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

### 3.2 Cattle — dairy and beef templates

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

### 3.3 Rabbits — jurisdiction-dependent viruses

Sources: Rabbit Welfare Association (UK); VCA; Merck-related practice notes.

| Slot | Typical template | Availability |
|------|------------------|--------------|
| Myxomatosis + RHDV1 + RHDV2 | From **~5–7 weeks**; **annual** (sometimes 6–12 months by product/risk) | Common UK/EU combination vaccines; **US myxomatosis vaccine often unavailable** — pack `jurisdiction` |
| Extra RHDV2 | 2+ weeks apart from combination if vet requires | High-virulence strain products — vet |
| Pasteurella bacterin | Uncommon / inconsistent; not core | Optional |

**No colostrum CDT analogue.** Biosecurity + insect control (fleas/mosquitos) are first-class prevention tasks.

### 3.4 Poultry — by kind and purpose (Merck, Apr 2023 / Sept 2024)

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

## 4. Disease library + first aid vs vet class

`vet_class` drives which **formulary** items can attach. Worker UI: first aid + “Call vet”. Vet/manager: attach approved product.

### 4.1 Goats & sheep (shared catalog, copy differs)

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

### 4.2 Rabbits

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

### 4.3 Cattle

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

### 4.4 Poultry (flock)

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

## 5. Formulary (medicines) — how richness works without a pirate pharmacy

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

## 6. Best-practice playbooks (Health home cards)

1. **Colostrum** — ruminant calves/kids/lambs: time to first feed, volume config, FPT risk.  
2. **Needle hygiene** — one needle policy; SQ tent; never inject in valuable cuts.  
3. **Biosecurity** — visitors, dead stock, all-in/all-out poultry, 30-day rabbit isolate.  
4. **Heat / cold** — driven by weather module (see §8).  
5. **Parasite refugia** — do not drench the whole goat/sheep herd on a calendar.  
6. **Poultry downtime** — empty house days between flocks.  
7. **Withdrawal board** — visible on Health Today and dashboard alerts.  
8. **Notifiable** — tap opens jurisdiction SOP (phone number), does not auto-notify government unless farm configures it.

---

## 7. Health tip of the day (dashboard)

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

## 8. Weather widget and week page

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

## 9. Roles

| Action | Worker | Manager | Vet role |
|--------|--------|---------|----------|
| Record observation | Assigned animals | Yes | Yes |
| Give vaccine if task due + trained | Yes | Yes | Yes |
| Add formulary product / accept pack | No | No | Yes (or owner with vet attestation) |
| Override withdrawal | No | No | Owner + audit |
| See finance of vet spend | No | Summary | No |

---

## 10. Phase 4 (Health) — ship checklist

- [ ] Species-aware observation + treatment forms  
- [ ] Protocol packs + schedule engine → tasks  
- [ ] Disease library (this catalog)  
- [ ] Formulary + withdrawals (meat/milk/egg)  
- [ ] Dashboard tip of the day  
- [ ] Weather widget + 7-day page (Open-Meteo)  
- [ ] FAMACHA / BCS / locomotion entry from Health or species module  
- [ ] Disclaimer + VCPR gate on first Health open  

---

## 11. Sources (this health pass)

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
