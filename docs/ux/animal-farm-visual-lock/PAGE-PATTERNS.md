# All-page design grammar

Every quantum entry receives a recipe in `registry/page-alignment.json`. Recipes are a starting composition, not a replacement for the page-specific feature contract. The index explicitly marks them for contract review. A page may combine a primary recipe with a nested sheet; do not force every screen into the same card layout.

## Pattern library

### P01 — Entrance and guided setup

Use for login, account/farm setup and onboarding. Order: concise identity/title → appropriate existing brand artwork → one current step's fields/choices → primary action → necessary secondary recovery/back action. Login follows the exact brand/lineup order in DESIGN-SYSTEM. Show step progress only for a real multi-step process. Permissions appear in context with a concise reason and denial recovery; native permission dialogs remain native. Do not require irrelevant farm setup fields at login.

### P02 — Management command center

Use A's hierarchy: context → priority attention → paired work/resources → species/modules → permitted deeper summaries. Owner and manager share this family but not identical data permissions. Exceptions lead to source evidence and a permitted action. Never present unavailable money or count projections as zero. See DESIGN-SYSTEM for locked geometry and imagery.

### P03 — Worker workday

Use D+C exactly: day/work context → stage filter → Review & decide image-led card → accessible carousel controls → quick recording/resources/guides/updates. Keep the active task stable on return. An assigned workflow opens the correct subject and capture form, not a generic module. The worker home is not the Today board reused as the whole app home.

### P04 — Module or specialist dashboard

Use concise scope header with a family portrait where meaningful → a small set of actionable KPIs → priority/Today section → operational tools → deeper trend/detail. Same surfaces and typography as A, not a new visual theme. Choose KPIs from the module's real biology/business. Optional brief advice must be relevant, approved and non-redundant; no permanent helper paragraph. Tablet may show related detail alongside overview. Specialist home extensions are proposed derivatives, not owner-approved new compositions.

### P05 — Scoped list, queue or catalogue

Header/scope → search/filter/sort → meaningful record rows → contextual add/action. Animal rows include identity image, tag, name and one or two discriminating facts. Stock rows prioritize available quantity/unit/location/expiry. Work rows show due/state/subject. Use chips and progressive disclosure, not paragraph cards. Selection/bulk actions are explicit and permission checked. Maintain filter, scroll and selection on return. Differentiate no records, no match, denied and stale data.

### P06 — Individual, group or record detail

Identity header with real photo/fallback where applicable → current status and critical restrictions → quick permitted actions → summary/history/linked records. Individual photos are editable only by permission. Group/cage/flock/lot details use the correct record identity and membership/census, not a fabricated personal profile. Long details use a small meaningful section set, not dozens of tabs. Current values drill to source records. Tablet uses master/detail without duplicating navigation.

### P07 — Capture and edit

Subject identity → happened-at/actor/context → grouped typed fields with units → evidence/notes when relevant → persistent primary save action → truthful receipt. Show the next necessary input, hide advanced optional fields behind disclosure, and keep essential warnings visible. Numeric keyboard, chips, steppers, approved selectors and scan/scale entry reduce typing. Do not replace structured domain fields with a free-text “details” box. Preserve non-secret drafts, validation and cancel behavior. Safety-sensitive capture adds P12 without becoming a separate style.

### P08 — Planning board and schedule

Scope/date window → calendar/stage/occupancy board → selected job detail → permitted schedule/edit action. Make dates, capacity, dependencies and conflicts visible; support a list alternative. Drag/drop is optional, never the only control. Plans do not pretend a physical operation happened. Tablet can show timeline and inspector side by side. Rabbit waves, nest schedules, grazing and hatchery batches retain their own state models.

### P09 — Timeline and history

Identity/scope → date/type filters → chronological events with author/time/outcome/sync markers → expandable evidence and correction route. Records distinguish original/corrected/rejected/pending. Never silently overwrite historical facts. Maintain position, show linked effects and distinguish local from authoritative event order where needed.

### P10 — Analytics, comparison and reports

Question/scope/time window → selected metrics → readable chart with units/legend/source/freshness → equivalent table → source drilldown/export. Comparisons show the same dimensions, units and missing values for each candidate. No decorative sparkline pretending to be evidence; no cross-species aggregate of incomparable biology. Simulations are explicitly hypothetical, retain assumptions/version, and never apply a scenario as a business command. Tablet supports side-by-side comparison and usable tables; phone uses ranked summaries plus detail.

### P11 — Guides, reference and documents

Searchable relevant titles → selected content with species/kind, source, version/approval and offline availability → steps/media/tables → task return or contact action. Keep long instruction text here, not on the homepage. Essential warnings still appear at the action boundary. Clinical reference imagery is approved/licensed, not generated from the brand animals. Documents have real download/offline/error states, zoom and accessible text where available.

### P12 — Safety review and irreversible decision

Subject and restriction → source facts and evidence → exact proposed effect → validation/conflict information → explicit authorized action/cancel. Use semantic warning/error surfaces, concise factual language and strong hierarchy. Suppress decorative scenery, celebratory graphics and distracting animal heroes; keep identity if it prevents mistakes. Explain downstream stock/money/status effects. Show local commit versus server outcome correctly. Never give a worker treatment selection, approval or financial posting because it looks like a convenient button.

### P13 — Search, selection and hardware

Search/scan/query or connection state → scoped results/devices → clear current selection → confirm/return. Use correct family identity, unambiguous tags and duplicate handling. Bluetooth/RFID/QR screens distinguish unavailable, denied, discovering, connected, disconnected and retry. Search outage has a labeled local fallback; it is not total app failure. Native pickers/scanners are permitted exceptions to custom chrome; use a consistent contextual wrapper and preserve platform accessibility.

### P14 — Settings, administration and access

Concise grouped settings → current values → detail/editor → apply/confirmation. Most rows are simple, not hero cards. The home gear is a separate Theme-only entry; Settings Home and deeper administrative pages remain distinct authorized routes. Members, exports, security and API/provider connections enforce sensitive-data access. Never render keys as ordinary settings text or persist them in a theme file. Unknown role fails closed, not to a permissive general dashboard.

### P15 — Sync, recovery and transient feedback

Status and affected scope → clear counts/operations/freshness → operation detail/recovery → truthful receipt. Use compact banners for transient status; dedicated centers for actionable queues. Retrying respects operation identity. Explain rejected/conflicting/dead-letter records without losing local work. Receipt says “Saved on this device” until authoritative acknowledgment, not “Uploaded”. Show auth/access revocation distinctly. Skeletons preserve layout; no spinner on a purely local preference. Atom sheets inherit the host page identity and return context.

### P16 — Evidence-bound AI workspace

Question/context → response or insight → evidence/source links and limitations → optional permitted action proposal → explicit review route. Use readable conversation/detail layout, not a novelty chatbot theme. Advisory content cannot auto-prescribe, dose, sell, cull, post money or bypass grants. Provider unavailable/permission/cost/error states remain clear. AI-suggested actions enter the same command contract as manual actions and never become authoritative just because they are in a lime card.

## Module-specific design requirements

### GLOBAL and HOME

Cover all authentication, membership, invitations, first-run setup, permissions, account and session states. Farm Home and Role-tailored Home are dispatch/entry contracts as well as visible destinations: document aliases rather than building duplicate inconsistent homes. Species Navigator is a family launcher, not a mixed-species list. Today Summary, alerts, activity, quick capture, search, sync entry, farm switcher and notifications remain reachable and scoped. Theme lives inside the bottom-right gear sheet; full administration is separate.

Role variants: `012-A` owner and `012-B` manager use P02/A. `012-D` worker uses P03/D+C. Proposed derivatives: `012-C` supervisor prioritizes team coverage, assigned work and review; `012-E` breeding manager prioritizes species-specific reproduction schedules and candidate evidence; `012-F` vet/health prioritizes cases, withdrawals and clinical evidence; `012-G` finance prioritizes money, reconciliation and exceptions; `012-H` buyer/read-only exposes only granted records and no mutations. Verify actual role claims; “buyer” is not automatically a farm-wide read-only grant. The suffix `012-C` is a registry role ID, **not visual option C**.

### GOAT — 55 base entries

Herd and individual identity, kid identity, photo/gallery, tags/scanning, weight/BLE, growth/ADG, BCS, milk/lactation/SCC, FAMACHA/reference, health/restrictions, reproduction, pregnancy/kidding/kids/weaning, pedigree/parentage/candidate comparison, groups/movement and exits all retain their distinct jobs. A herd dashboard is not a single menu of text links. Capture shows subject and unit; history/chart and report routes remain accessible. Sale, cull and mortality are separate governed decisions. Never make a family image look like proof of individual identification.

### RABBIT — 36 entries

Distinguish doe, buck, cage, occupancy, wave, nest schedule, litter and individual kit. Nest placement/removal and kindling capture need appropriate context, not a goat reproduction form renamed. Kit census, fostering and mortality reconcile counts across the correct litters; individualization creates genuine individual identity. Retention/replacement comparisons, market plan, waitlist, reservation, contract and allocation have distinct business stages. GI-stasis red flag is a safety/reference route, not a treatment recommendation generated by the UI.

### SHEEP — 32 entries

Distinguish mob/flock and individual records; EID, BCS, joining, scanning, lambing/marking/weaning, wool/shearing/fleece/fibre and species health have native fields. FAMACHA, dag score, footrot and flystrike use approved references and restriction handling. Pasture assignment and official movement are not interchangeable. Include histories and reports, not only capture buttons.

### CATTLE — 36 entries

Keep herd/mob/lot and individual context explicit; dairy and beef/feedlot surfaces differ. Heat/service/artificial insemination/embryo transfer/pregnancy/calving require their specific fields and evidence. In this module, “AI Record” means artificial insemination, not the app copilot. Milk/SCC/locomotion/dry-off and days-on-feed/close-out need their own display units and actions. Official identifiers/movement and health restrictions remain visible at commercial transitions.

### POULTRY — 27 entries

Kind → purpose → house/flock/batch is the context chain. No mammal pregnancy screens; no chicken assumptions for every kind. Placement, daily mortality, egg production/collection, feed/FCR, vaccination, biosecurity, eggs set/incubation/candling/hatch/placement, moves and close-out reconcile counts and sources. A hen can identify the Poultry family, not a duck flock. Individually tracked birds use the common photo contract even if the current atlas has no separate profile/photo entry: register that gap instead of pretending flock profile covers it.

### HEALTH — 30 entries

Separate observations/cases, authorized treatment, withdrawal, vaccination, formulary, protocol ownership/acceptance/application, vet visits, labs, references and emergencies. Worker findings do not authorize prescribing. Source/version, selected product/lot and relevant restrictions stay visible at the correct action boundary. Safety states are not replaced by decorative art or generic green success cards.

### TASK — 15 entries

Today/all/assigned/scheduled/overdue/completed/calendar are views of consistent task state. Detail, recurrence, attachments and completion evidence own real workflows. Generated-by-protocol/lifecycle pages show provenance and linked source. Stage filtering on worker home must agree with the task board. Opening, starting or attaching evidence alone does not necessarily complete a task.

### INV, FEED, WATER, PASTURE and GROUP

Inventory: item/lot/location/unit, FEFO, expiry, receiving/issues, adjustments/counts/reorder and suppliers. A count is not an unreviewed adjustment. Feed: schedules/plans versus physical consumption; ration assumptions/comparison, availability and cost. Water: points, readings, inspection, quality, issues/maintenance/history. Pasture: paddock/rotation/rest/capacity/condition/map with a non-map alternative. Groups: membership/census/move/health/timeline; no fake individual profile. Make linked material effects visible and preserve reconciliation.

### LABOUR and ASSET

Labour views show worker/assignment/work log/availability/workload; cost only with grants. Do not expose private employment details by reusing a generic detail screen. Asset register/details, meters, schedules/jobs/breakdowns/service/documents/photos use equipment identity, not animal art. Keep finding→job→work evidence linkage.

### FIN, SALES and PROC

Exact currency and units, scoped transactions, source-linked totals and reconciliation first. Finance covers income/expense, enterprise/species/activity cost, cashflow/budget/variance and exports. Sales covers customer/order/sale allocation/animal/produce/reservation/delivery/history. Procurement covers supplier/order/purchase/receipt/inventory linkage/history. A receipt may have downstream stock/money effects; show pending/partial/conflict without inviting double posting. No carousel for ledger browsing.

### CAP, GEN, AN and SIM

Capacity displays actual versus limits plus assumptions across housing/cages/poultry/paddock/feed/water. Genetics shows pedigree, parentage evidence, relationships/COI, candidate ranking/compare and warnings; avoid inventing genetic inference from appearance. Analytics provides units/windows/denominators/source drilldown. Simulation separates hypothetical scenarios from actual records and preserves assumptions. All charts have an equivalent table; missing inputs remain missing.

### SEARCH and AI

Search spans permitted entities with distinct family/kind identity, filters, recent queries, scan results and labeled offline fallback. Do not leak cross-farm matches or treat Meilisearch as authoritative state. AI follows P16 with evidence, reviewed actions, provider/privacy controls and unavailable states. Approval UI does not expand agent powers.

### REPORT, ADMIN, SYNC and ATOM

Reports include generation, document viewing, export/share/print and certificate states, not only a list of report names. Administration includes farm/members/grants/species/kinds/locations/units/currency/notifications/sync/search/integrations/hardware/provider/security/export/backup/audit/version. Sync has distinct operation states and recovery. All 35 atomic surfaces count: date/time and entity selectors, photo/attachments/documents/scanners/devices, filters/sort/bulk actions, destructive/conflict/permission sheets and empty/loading/error/receipt states. Their existence in a component library is not proof that every owning route correctly invokes them.

## Gaps must be registered, not concealed

The source atlas is not proof of complete feature coverage. Explicitly check all feature contracts for guides/resources/own updates, theme and outdoor selection, photo replacement/removal for every individual species, individually tracked poultry, attachment queues and any missing variants from the older detailed catalogue. Reuse existing IDs when they truly own the job; otherwise propose a new stable ID and update both registries. Mark headless contracts with reasons. No orphan feature or route may be certified.
