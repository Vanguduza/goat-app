# Farm OS — Design System Spec ("Field-first visual language")

**Version:** 1.0 · **Date:** 22 August 2026
**Purpose:** Make Farm OS look and feel like a crafted professional product, not generated output. Defines tokens, typography, colour, components, patterns, motion, content voice, and the automation that enforces all of it.
**Applies to:** all `:feature-*`, `:core-design`; binding on humans and agents.
**Enforcement:** §14–§15 (lint, snapshot tests, gallery, gate items). HOW-law per `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`.

---

## 0. Why apps look AI-generated — the anti-goals

| AI-tell | Our standing rule |
|---|---|
| Default Material purple/blue, gradient buttons | One brand colour, used sparingly; zero gradients in controls |
| Emoji in UI copy and empty states | Never. Icons from one set; words from the voice guide |
| Everything rounded 28dp+, floating cards everywhere | Radii scale ≤16dp; flat surfaces, hairline borders, tone-shift elevation |
| Generic copy: "Welcome to your amazing farm!" | Species-correct, terse, sentence case; empty states state the next action |
| Random spacing, mixed text sizes per screen | 4dp grid + fixed type scale; nothing off-scale |
| Decorative illustrations, glassmorphism, neon dark mode | None. Dark mode is a true tonal counterpart, not inverted neon |
| Every screen invented ad hoc | Screens assemble from the shared component library + named patterns |

## 1. Design principles

1. **Field-first.** Sunlight-legible contrast, 48–64dp targets, one-thumb capture flows. A vet wrap or dust decides usability more than beauty does.
2. **Calm data density.** Farmers scan; dashboards are dense but quiet — hairlines, alignment, tabular numbers, no decoration between the user and data.
3. **One accent.** A single deep agricultural green carries identity; species colours appear only as small identity chips; status colours are reserved and rare.
4. **Native, tuned.** Material 3 components with our tokens — never custom-rebuilt basics, never web-clone patterns.
5. **Motion restraint.** Transitions explain hierarchy (shared-axis); nothing bounces, floats, or celebrates.
6. **Content is UI.** Microcopy, empty states, and errors are designed artifacts with the same rigor as pixels.

## 2. Colour tokens (semantic, never raw)

Defined once in `:core-design/theme/FosColors.kt` (+ XML equivalent); features may reference **tokens only**.

### 2.1 Light

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

### 2.2 Species accents — chips and badges ONLY (never themes, never headers)

| Module | Token | Hex |
|---|---|---|
| Goat | `species/goat` | #A9762B (ochre) |
| Sheep | `species/sheep` | #6B8F71 (sage) |
| Cattle | `species/cattle` | #8A6240 (leather) |
| Rabbit | `species/rabbit` | #5B7C99 (slate) |
| Poultry | `species/poultry` | #B65C33 (terracotta) |

### 2.3 Dark & outdoor variants

- Dark: tonal counterparts (canvas #121411, card #1C1F1B, primary #9BC49F, same hues lifted for ≥4.5:1). Not pure black, not neon.
- **Outdoor mode** (toggleable, default ON for field roles): canvas #FFFFFF, text #000000, minimum contrast 7:1, target size floor 64dp, haptic confirmations emphasized.

Contrast floors: text 4.5:1 (7:1 outdoor), icons/status 3:1. Verified in §14 CI.

## 3. Typography

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

Rules: sentence case everywhere (no ALL CAPS headers, no title case buttons); dates as `02 Feb`; weights `45.2 kg`; money with farm currency, no cents unless entered.

## 4. Spacing, shape, elevation, iconography

- **Grid:** 4dp base. Screen margin 16dp; card padding 16dp; list item 56dp min; intra-card gap 12dp; section gap 24dp.
- **Radii scale (only these):** 8dp (inputs, chips), 12dp (cards, sheets), 16dp (bottom sheets max). Buttons 8dp. **No pill buttons, no 28dp cards.**
- **Borders over shadows:** cards are `surface/card` + 1dp `border/hairline`. Elevation reserved for: FAB (level 1), drag states, modal sheets (level 2). No drop shadows on lists or tiles.
- **Icons:** Material Symbols (rounded variant), one weight, 20/24dp, always with text label in buttons. Species glyphs only in identity chips. No emoji, ever.
- **Touch targets:** ≥48dp (64dp outdoor). Primary action per screen = one filled button; everything else tonal/text.

## 5. Core component specs

### 5.1 Cards

```
┌─────────────────────────────────┐
│ ● Goat · Nala            3d ▾  │  label row: species dot 8dp + name + meta
│ FAMACHA 3 · BCS 3.0 · 45.2 kg   │  body: numeric style, dot-separated
│ ─────────────────────────────── │  hairline
│ [Record check]  [Call vet]      │  actions: text buttons, right-aligned
└─────────────────────────────────┘
```

- Radius 12dp, hairline border, no shadow. Title row 40dp min. Max one primary action per card.

### 5.2 Data table (dense lists)

- Row 56dp; column headers `label` style, hairline under; numeric columns right-aligned tabular; zebra **off**; row press = 8% primary overlay; sticky header on scroll.
- Status appears as a 6dp dot + word, never as a coloured pill unless it's a chip pattern.

### 5.3 Chips

Input chips for filters (8dp radius, hairline, selected = `primaryContainer`). Species chip = 20dp circle + label. Status chip reserved for withdrawal/countdown only (uses `status/withdrawal`).

### 5.4 Empty states

Structure: icon (24dp, `text/secondary`) → one-line statement → one action button. No illustration, no apology, no emoji.

> "No health actions today. Next task: CDT booster — 12 May." `[View schedule]`

### 5.5 Forms

- Single column; labels above fields (`label` style); helper text ≤1 line; validation inline below field in `status/critical`, never dialogs.
- Section headers as hairline + `label` text, not cards-in-cards.
- Score pads (FAMACHA/BCS): segmented control with photo reference sheet behind a `ⓘ` — the photo guide is a documented asset, not a web image.
- Numeric entry: numeric keyboard, decimal comma/point per locale, unit suffix inside the field (`kg`), tabular results.

### 5.6 Navigation

- Bottom bar: 4 destinations max per module (Today, Record, [Species core], More). 24dp icons + 12dp labels, active = `brand/primary` icon + dot indicator (no filled-pill nav).
- Module switcher: top bar left — species chip + module name, opens drawer with species list (identity chips only, no colour themes).
- Top bar: flat `surface/canvas`, title `titleSm`, actions as icon buttons 48dp.

### 5.7 Charts (Vico)

- Line 2dp, no fill gradients; cohort band = 12% primary; anomaly markers: ▲ 6dp triangle critical, ● 6dp dot watch; axis labels `label` style, max 4 y-ticks; tabular axis numerals.
- Chart background `surface/sunken`, hairline frame. Tooltip: single card, `titleSm` value + `label` date.

## 6. Named screen patterns (the library agents build from)

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

## 7. Motion

- Durations: 150ms (chips, presses), 220ms (screens, shared-axis X forward/back), 300ms (sheets).
- Easing: standard acceleration; **no springs, no bounce, no overshoot**.
- List diffs: animate placement only; no item fade-stagger cascades.
- Respect system "remove animations"; reduce-motions ⇒ crossfade 100ms.

## 8. Content voice (microcopy law)

- Terse, specific, sentence case. Verbs first on buttons ("Record check", not "Would you like to record a check?").
- Numbers in copy: figures, not words ("3 tasks due", not "three tasks due").
- Species vocabulary enforced: "kidding" not "birthing" in goat; "kindling" in rabbit; "farrowing" never (swine).
- Errors say what happened + the fix: "Sync failed — will retry on connection. Your entries are safe on this device."
- No marketing tone inside the product. No exclamation marks. No "simply/easily/just".

## 9. Accessibility

- Contrast floors per §2.3; all text scales to 200% (no fixed-height text containers; wrap, don't truncate).
- Touch ≥48dp/64dp outdoor; every icon button has contentDescription; charts have table toggle (data parity rule).
- TalkBack order = visual order; focus goes to first field on form open; live region for sync/withdrawal countdowns.

## 10. Theming & branding seams

- `:core-design/theme/` owns: tokens, Type.kt, Shapes.kt, Theme.kt (light/dark/outdoor), PlatformIcons.
- Farm-level config: currency symbol, date locale, unit system (metric/imperial) — data-driven, not re-themed.
- **No user themes, no colour skins.** Species identity = chips only. This constraint is the brand.

## 11. Screen inventory & flow polish standards

- Every screen from the master plan/binder specs maps to a pattern in §6; the mapping table lives in `:core-design/patterns/PATTERNS.md` and is reviewed at each phase gate.
- Flow rules: any capture ≤3 taps from its dashboard; destructive actions confirm via bottom sheet (not dialog); every list has search + empty state + offline banner slot.
- Loading: skeleton of the real layout (same pattern), never spinners-in-cards; sync state is one status chip in the top bar, not per-card badges.

## 12. App icon & system surfaces

- Icon: single glyph — simplified goat head silhouette on `brand/primary` field, no gradients, no text. Adaptive icon layers: background solid, foreground 1dp-safe.
- Splash: brand field + glyph, 200ms max, then content. No loading slogans.
- Notifications: FCM/WorkManager templates use app icon + species dot + terse title ("Nest box due — Cage B"); actions mirror in-app buttons.

## 13. Asset pipeline

- Icons: Material Symbols Rounded via material-icons-extended (tree-shaken by lint).
- Photography: score reference sheets (FAMACHA eyelid, BCS hands-on) bundled at 2x, compressed WebP, credited to source in-app ⓘ.
- Charts/graphic exports (cage cards, PDFs): rendered from same tokens via Compose → PDF path so paper matches screen.

## 14. Enforcement automation (this is what actually prevents "AI-generated")

1. **Token lint (Konsist/arch test):** features may not declare `Color(0x…)`, raw `dp` outside the approved scales, or `FontFamily` other than Inter. CI fails the build. Only `:core-design/theme` defines colours.
2. **Pattern lint:** screens must extend/compose a §6 pattern base; new one-off layouts require a design-system EDR.
3. **Contrast check:** unit test computes contrast for all token pairs used in text/status roles; fails below floors (incl. dark + outdoor variants).
4. **Screenshot tests (Paparazzi or Roborazzi):** every core component × light/dark/outdoor × font-scale 1.0/1.3/2.0 renders golden images; diffs block PRs. This catches drift that code review misses.
5. **Component gallery debug screen** (`GalleryActivity`): all tokens/components/patterns live in-app for eyeballing; gallery screenshots are part of the gate evidence.
6. **Copy lint:** forbidden strings list (`!`, "welcome to", "amazing", emoji ranges, ALL-CAPS headers >4 chars) fails a string-resource test.

## 15. Definition of done — UI slice

A UI task is done only when:

- [ ] Built from §6 patterns with §2–§5 tokens/styles; zero raw values
- [ ] Light + dark + outdoor screenshot goldens updated and reviewed
- [ ] Font scale 2.0 usable (no clipped text)
- [ ] TalkBack passes the flow; targets ≥48dp
- [ ] Empty/loading/error states designed (all three)
- [ ] Microcopy follows §8 voice
- [ ] Gallery updated if any component changed

## 16. Anti-generic guardrails ("designed, not generated" protocol)

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

### 16.1 Why this works where "good prompts" fail

| Failure mode | Guardrail that kills it |
|---|---|
| Agent never sees output | #2 Gallery-first + #4 screenshot evidence |
| Average-pull from reference repos | #1 authority split |
| Placeholder-feeling screens | #3 realistic fixtures |
| Slow silent drift | #5 lints + #10 debt budget |
| Looks fine in IDE, unusable in field | #6 density matrix + #9 sunlight walk |
| Taste argued in review | #2 pixel review before code exists |

## 17. Traceability

| Area | Source |
|---|---|
| Species-native UX law | Master plan alignment rules |
| Chartable/table parity | AI spec §3 |
| Withdrawal/status colour reservation | Vet intelligence spec §5.2, §9 |
| Outdoor mode rationale | Field-first principle; vet pack handling sections |
| Gate integration | Handbook Ch.9 DoD, Ch.10 phase gates |
| Agent enforcement | `.cursor/rules/farmos-design-guardrails.mdc`; root `AGENTS.md` |

*End of design system spec.*


