# Rabbit nest-box ratio & schedule cycle
## Derived from KudBat semi-intensive sheet + cycle graphic

**Date:** 20 August 2026  
**Unit of analysis:** One breeding cage = **11 does + 1 buck**  
**Buck rule:** mates **2 does / day**, then **rests 1 day**, next pair (last day = 1 doe).  
**Companion:** rabbit module in `FARM_OS_VETERINARY_EXPERT_RESEARCH_PACK.md` (gestation 31–33 d clinical); this file locks the **producer schedule offsets from the attached Excel**.

---

## 1. Locked day offsets (from the sheet)

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

## 2. One-cage mating stagger (11♀ + 1♂)

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

## 3. Nest-box demand (concurrent count)

Each doe needs **her own** nest box while her window is open. Two does bred the same day ⇒ **2 boxes** that day.

### Nest window per cohort (relative to cage Day 0 = first breed)

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

### Cage : nesting-box ratio (locked)

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

## 4. Nesting-box schedule cycle (reusable)

### 4.1 Per-doe cycle (repeats every rebreed)

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

### 4.2 Cage “Today” task template (relative to cage Day 0)

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

### 4.3 Worked example — Cage A (from sheet)

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

## 5. App / task-engine rules (for `:feature-rabbit`)

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

## 6. Summary

| Question | Answer |
|----------|--------|
| Cage : nesting box ratio (one cage, this schedule) | **1 : 11** |
| Why? | Stagger still overlaps so all 11 nest windows are open together for ~15 days |
| Nest dwell per placement | **25 days** in, **18 days** out, then in again at rebreed+28 |
| Cycle driver | Rebreed every **+43 d** from mating (sheet); nest rhythm follows +28 / kindling+21 |
| Sharing | Two cages phased like A/B on the sheet can share **one pool of 11** |

*Clinical gestation remains ~31–33 d (Merck); the +32 kindling column is the farm’s planning date for tasks, not a substitute for watching the doe from day 30.*

**Programme module:** For cages, named boxes, pairing (2:11), notifications, and inbreeding gates, see `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`.
