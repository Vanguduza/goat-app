# Farm OS — Veterinary expert research packs

**Companion to:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md`  
**Date:** 19 August 2026  
**Purpose:** Species-level clinical *recording*, scoring, reproduction, housing, KPIs, and red flags so each Android module behaves like a competent farm vet’s notebook — **not** a prescribing robot.

### Hard rules (product = vet-expert, not vet-replacement)

1. **The attending veterinarian owns protocol packs** (vaccines, anthelmintics, withdrawals, notifiable-disease reporting). The app stores *what was done* and *when the hold ends*. It never invents a dose, product, or route.
2. **Copilot / Hermes** may explain recorded scores and cite ICAR-style definitions. It must not recommend a named drug or dose.
3. **FAMACHA®** is a licensed/certified system. The app records 1–5 scores and interval reminders. Shipping a colour chart in-app requires licence/training copy; do not treat the score as a diagnosis of *Haemonchus* alone (other anaemias exist).
4. **Red flags** open “Call vet / isolate” — they do not start a treatment wizard.
5. **Jurisdiction packs** (NLIS, NAIT, EU ear tags, USDA APHIS, notifiable lists) are plug-ins. This document names the *hook*, not every country’s law.
6. Numbers below are **defaults for engines and forms**. Farm config + attending vet override them.

---

## 0. Cross-cutting clinical model (all modules)

### 0.1 Every health event (implement once, forms per species)

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

### 0.2 Body condition scoring (do not mix scales)

| Species | Scale | Typical target | Palpation |
|---------|-------|----------------|-----------|
| Goat, sheep, dairy cattle | **1–5** (½ scores OK) | Breeding ~2.5–3.5; pre-parturition ~3–3.5 (small ruminants) | Loin spinous/transverse processes, ribs, sternum fat pad (goats/sheep). Hair/wool hides condition — must palpate. |
| Beef cattle | **1–9** | Breeding ~5 | Ribs 12–13, hooks/pins, tailhead, brisket |
| Rabbit | Flesh condition + coat; weight trend more useful than bovine BCS | Avoid obesity in pets; production does: adequate loin cover | Spine, ribs, pelvic bones |
| Poultry | Keel score / fleshing (flock sample) | Purpose-specific | Keel prominence; never mix broiler and layer targets |

**Engine rule:** store `bcs_scale` with the value (`five` vs `nine`). Never plot beef 5 next to dairy 5 as the same fatness.

### 0.3 Shared vital / welfare red flags

| Finding | Action |
|---------|--------|
| Recumbent, unresponsive, open-mouth breathing (ruminant), severe haemorrhage | Critical → vet now |
| Neurological signs, abortion storm, sudden high mortality (poultry house) | Isolate + vet; possible **notifiable** (jurisdiction pack) |
| Rabbit: temperature &lt;38.0°C or &gt;40.0°C (Merck: normal ~39.6–40°C; &lt;100.4°F or &gt;104°F concerning) | Critical |
| Rabbit struggling + unsupported spine | Stop; lumbar fracture risk (Merck) |
| Milk SCC interpretation | Ignore samples **&lt;5 days in milk** for herd genetic/udder-health analyses (ICAR udder health) |

### 0.4 Zoonoses / occupancy (record + PPE prompt, not diagnosis)

Q fever (*Coxiella*) and chlamydiosis (sheep/goats/cattle abort), *Salmonella* / campylobacter (poultry, calves), cryptosporidium (calves), orf (sheep/goats), ringworm. Worker-facing copy: hygiene, pregnant-worker policy, abortus handling — **farm SOP**, not Copilot medical advice.

---

## 1. Goat module — expert pack

**Sources:** ICAR Guidelines Section 21 (sheep *and* goats, June 2021); Alabama Extension BCS goats/sheep; FAMACHA / WormBoss / ACSRPC; gestation literature (~145–155 d, breed/parity effects).

### 1.1 Reproduction (clinical timeline)

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

### 1.2 ICAR-aligned recording (goats + kids)

From ICAR Section 21 (growth / reproduction / maternal, sheep **and** goats):

- Weights: birth; **~30 d**; **42–120 d**; post-wean; 12-month; adult pre-mating.
- Pregnancy scan (if used): **~day 70 (60–90)** foetal count 0/1/2/3/4+. Same trait family as sheep; goat UI still says *kids*, not lambs.
- Survival: lambs/kids born alive vs reared; alternatively foetuses scanned minus reared.
- Standardise weights to age (ICAR Annex A) for genetic comparison — genetics service, not the field form.

**KPI (goat home):** kidding interval; kids born/weaned per doe; kid survival; ADG (first–last weight); dairy: yield, fat/protein, SCC if milk-recorded (ICAR milk analysis covers **cow, goat, ewe** milk).

### 1.3 Health — goat forms (not cattle screens)

| Domain | Record | Expert notes for UX |
|--------|--------|---------------------|
| Parasites | **FAMACHA 1–5** (lower eyelid conjunctiva vs card). Guide: 1–2 usually no drench; **4–5 treat per vet protocol**; 3 = risk-based (young, lactating, poor BCS, poor feed). Recheck ~weekly in challenge. Herd check **every 2–3 weeks** in season (UT FAMACHA sheet). | Selective treatment = refugia. Score 3 policy is **vet/farm**, not hardcoded treat-all. Other anaemias exist. |
| BCS | 1–5, palpated loin | Target breeding 2.5–3.5; pre-kidding 3–3.5 (ACES) |
| Clostridial risk | Protocol-pack vaccine class (often “enterotoxemia/tetanus” *as a pack name*) timed **~1 month pre-kidding** so colostrum antibodies rise — **product chosen by vet** | Task only |
| Mastitis / CAE / CL / Johnes / orf | Observation catalog + lab attachments | Dairy: milk withdrawal |
| Foot / CAE arthritis | Lameness score | |
| Metabolic | Pregnancy toxaemia / hypocalcaemia **suspect** in late gestation / early lactation thin or overfat does | Red flag recumbency |

**Dairy goat pack:** milk recording fields aligned with ICAR milk analysis (fat, protein, SCC). Do not reuse cattle 305-day assumptions without farm lactation-length config.

### 1.4 Housing / movement

Paddock, barn, pen, quarantine. Grazing rest for parasite control is a **paddock task**, not a goat cloned from cattle feedlot.

### 1.5 Red flags (goat)

Dystocia &gt;30–60 min with no progress (farm SOP), kid not nursing, FAMACHA 5, recumbent late-pregnant doe, neurological, abortion outbreak.

---

## 2. Rabbit module — expert pack

**Sources:** Merck Vet Manual — Management of Rabbits (review Jul 2021, update Apr 2025, Mayer); Merck breeding/reproduction; Peace Corps / MSU commercial rabbit production; USU producer guide.

### 2.1 Biology & handling (safety is clinical)

- Never lift by ears. Support rump; hind-limb kick → **L7–S1 fracture/luxation**.
- Normal temperature **~39.6–40.0°C (100.5–104°F)**; outside → concern (Merck).
- Sexing: depress genitalia; testes descend **~10–12 weeks**.
- Oral/dental exam is part of rabbit medicine; production UI still needs **malocclusion / inappetence** as red flags (GI stasis risk).

### 2.2 Reproduction (litter is the working object)

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

### 2.3 Health catalog (rabbit-specific)

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

### 2.4 Housing & breeding programme

Cage/hutch map + QR. Nest box as a **named, tracked asset** (not only a task). Colony vs single-doe cages are place types, not a goat paddock UI.

**Operational programme (KudBat-style):** 11 does + 1 buck per cage; nest schedule and 1:11 / paired 2:11 ratios — `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md`. Full app module (waves, notifications, cage pairing, COI mating gates) — `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`.

---

## 3. Sheep module — expert pack

**Sources:** ICAR Section 21; MLA/AWI pregnancy scanning (litter size ~day 70; dry/single/twin); sheep gestation **~147 d (142–152)**; FAMACHA (sheep+goats); AWI Visual Sheep Scores (breech wrinkle, fleece rot 1–5); Footrot (AWI — often **notifiable** by state).

### 3.1 Reproduction

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

### 3.2 Health

| Domain | Record |
|--------|--------|
| FAMACHA | Same 1–5 system as goats; sheep often show deeper red at score 1 (WormBoss) |
| BCS | 1–5 (½). Hill vs lowland targets differ (Scotland FAS: hill ewes not returned to hill &lt;2). Rams **~3.5–4** pre-tupping. ~1 BCS unit ≈ **12% liveweight** (FAS). |
| Footrot | Lesion/score; **jurisdiction notifiable** flag |
| Flystrike | Breech/body; Visual Sheep Scores wrinkle & fleece rot **1–5** (AWI/MLA) as *risk traits*, not a diagnosis |
| Pregnancy toxaemia | Late gestation multiples, poor BCS |
| Clostridial / CLA | Vet pack |

**Wool pack:** micron (mean fibre diameter), staple, fleece weight, classing. Micron is the primary value driver (industry standard).

### 3.3 ICAR KPIs

Lambs weaned per ewe joined; scanning %; lambing %; survival scanned→marked; weaning weight age-adjusted; wool kg and micron if enabled.

**Do not** copy goat “kidding interval” labels. Sheep commercial life is **mob + drop**.

---

## 4. Cattle module — expert pack

**Sources:** ICAR calving traits (gestation **~280 d** dairy); ICAR dairy milk recording & udder health (SCC; IDF **&gt;200,000 cells/ml** often used as subclinical mastitis threshold); Wisconsin/UT/SDSU PD methods; MLA weaner throughput (breed by **~d 82** postpartum for 365-day calving interval).

### 4.1 Reproduction

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

### 4.2 Dairy pack

| Record | Notes |
|--------|--------|
| Milk kg, fat %, protein %, lactose, urea optional | ICAR milk recording |
| SCC | Periodical with unique animal + sample date. Convert to **SCS** (log) for analysis. Skip **DIM &lt; 5** for many analyses (ICAR). First-parity healthy often ~50–100k; inflammation &gt;1M. IDF-style **200k** threshold as *configurable* flag, not a diagnosis. |
| Clinical mastitis | Direct health: quarter, secretion change, systemic signs, culture if any. Distinct from high SCC. |
| Parlour session | Meter → cattle module save; milk **withdrawal** from treatment label |
| Dry cow therapy | Vet protocol pack only |

### 4.3 Beef pack

BCS **1–9**, target ~5 at breeding. Weaning weight; days on feed / lot close-out; ADG. Lots = `animal_groups` accounting census or roster.

### 4.4 Health catalog (cattle)

Metabolic (milk fever, ketosis), mastitis, lameness/locomotion (dairy 1–5 M-score common), BVD/IBR/Lepto as **vaccination pack names**, calf scours (crypto/rota/corona/E. coli *categories*), BRD. Notifiable: jurisdiction (e.g. FMD, TB).

### 4.5 Identification

Hook: **official EID** (NLIS AU, NAIT NZ, EU, etc.). App stores identifier type + movement events. Do not hardcode only NLIS.

---

## 5. Poultry module — expert pack (all kinds)

**Sources:** Mississippi State Extension hatchery tables; Oklahoma / USU incubation; Aviagen-style broiler management principles (all-in/all-out, biosecurity); hen-housed vs hen-day egg metrics (industry standard).

### 5.1 Working object

Commercial: **flock + house**. Individuals: breeders / bands only. Census `head_count` + daily mortality/culls/eggs/feed.

### 5.2 Incubation & hatchery (forced-air defaults)

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

### 5.3 Layer vs meat vs breeder (purpose packs)

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

### 5.4 Health & biosecurity (flock forms)

| Record | Expert UX |
|--------|-----------|
| Daily mortality / culls | Spike vs 7-day baseline → Copilot *investigate house* |
| All-in / all-out | Placement + empty days (downtime often cited ≥10–14 d between cycles — farm pack) |
| Vaccination | **By kind + purpose** calendar (ND, IB, IBD, etc. as pack *slots*, products by vet) |
| Water / NH3 / temp | House sensors; NH3 welfare often discussed around **~25 ppm** as a damage threshold in literature — configurable alarm, not a diagnosis |
| Salmonella / AI / ND | Notifiable hooks |
| Wet litter (waterfowl) | Duck/muscovy/goose |

**Egg withdrawal** on treatments. No FAMACHA, no pregnancy table.

### 5.5 Kind-specific clinical notes

- **Chicken:** ND, IB, IBD, coccidiosis, Marek (layers/breeders) as catalog.
- **Ducks/Muscovy:** water access; duck viral hepatitis / duck plague as catalog where relevant; muscovy longer hatch.
- **Guinea:** keet brooding; ranging; different vax pack from chickens.
- **Turkey:** slower meat cycle; separate vax; histomoniasis risk if with chickens (record mixed-species housing as **biosecurity warning**).
- **Goose:** seasonal lay; pond/pasture.
- **Quail:** fast sexual maturity (~6–8 weeks coturnix); small-egg counts.
- **Pigeon:** loft, pair bond, squab harvest ~4 weeks; incubation ~17 d.

---

## 6. Intelligence (vet-expert Copilot)

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

## 7. Protocol pack schema (data, not prose)

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

## 8. Source list (this research pass)

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
