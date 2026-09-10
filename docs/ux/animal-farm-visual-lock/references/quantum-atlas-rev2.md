# Farm OS — Quantum-Complete Screen / Feature / Visual-Realisation Mapping — REV 2

**Authority purpose:** realign the current implementation to the locked illustrated Farm OS product family and prevent future drift.
**Visual provenance:** session `01a04f06-bfbe-7b32-bf12-f9834e900244`.
**Portable visual authority:** `docs/ux/FARM_OS_VISUAL_AUTHORITY.md`.
**Machine registry:** `docs/ux/FARM_OS_SCREEN_REGISTRY.yaml`.
**Current-code map:** `docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml`.

## Cold-start execution prompt

Reconstruct branch/PR/HEAD before writes. Do not treat existing UI code as visual authority. Preserve working Room/outbox/RPC/RLS/reconciliation/search behavior. Map every UI surface to a registered `FOS-*` Screen ID. Rebuild presentation around stable state/command contracts. Do not add an unregistered route. Do not claim `VISUAL_GREEN` or `FEATURE_GREEN` from code existence. The illustrated visual family in `FARM_OS_VISUAL_AUTHORITY.md` is mandatory and supersedes contradictory anti-illustration rules.

Before broad UI fan-out, establish and approve Splash, Login, Farm Selector, Farm Setup, Farm Home, Species Navigator, Goat Dashboard, Goat Profile, one I3 capture surface, one I4 safety surface and one tablet adaptive surface.

## Whole-product screen atlas

The canonical registry covers these complete families: Global/Auth 001–020; Home 001–012; Goat 001–055; Rabbit 001–036; Sheep 001–032; Cattle 001–036; Poultry 001–027; Health 001–030; Tasks 001–015; Inventory 001–019; Feed 001–012; Water 001–010; Pasture 001–011; Groups 001–009; Labour 001–009; Assets 001–012; Finance 001–013; Sales 001–012; Procurement 001–010; Capacity 001–010; Genetics 001–010; Analytics 001–014; Simulation 001–009; Search 001–009; AI/Copilot 001–012; Reports 001–015; Admin 001–025; Sync 001–018; atomic sheets/pickers/scanners 001–035.

### Global/Auth
FOS-GLOBAL-001 Splash; 002 Login; 003 Create account; 004 Password recovery; 005 Farm membership selector; 006 Create farm wizard; 007 Join invited farm; 008 First-run species setup; 009 Role explanation; 010 Notification permission; 011 Camera permission; 012 Bluetooth permission; 013 Location permission; 014 Offline-first introduction; 015 Terms/privacy; 016 Session expired; 017 Access revoked; 018 Backend unavailable; 019 Account profile; 020 Sign-out confirmation.

### Home
FOS-HOME-001 Farm Home; 002 Species Navigator; 003 Today Summary; 004 Alerts Centre; 005 Activity Stream; 006 Global Search; 007 Search Results; 008 Quick Capture; 009 Sync entry; 010 Farm Switcher; 011 Notifications; 012 Role-tailored Home. Role-tailored Home is atomic: 012-A Owner; 012-B Farm Manager; 012-C Supervisor; 012-D Worker My Day; 012-E Breeding Manager; 012-F Vet/Health; 012-G Finance; 012-H Buyer/read-only. These are structurally role-specific surfaces, not one dashboard with cards merely hidden.

### Goat
001 Dashboard; 002 Herd; 003 Profile; 004 Register; 005 Edit identity; 006 Search; 007 RFID/tag; 008 Gallery; 009 Timeline; 010 Documents; 011 Weight; 012 BLE scale; 013 Growth history; 014 Growth chart; 015 ADG; 016 BCS; 017 Milk; 018 Lactation dashboard; 019 Lactation history; 020 SCC; 021 SCC history; 022 FAMACHA; 023 reference; 024 history; 025 health summary; 026 treatment history; 027 withdrawal; 028 vet visit; 029 lab result; 030 doe reproduction; 031 heat; 032 mating; 033 sire selector; 034 pregnancy check; 035 pregnancy dashboard; 036 kidding due; 037 record kidding; 038 event detail; 039 register kid; 040 kid cohort; 041 kid profile; 042 weaning; 043 lactation plan; 044 pedigree; 045 parentage; 046 candidate compare; 047 groups; 048 membership; 049 movement; 050 official identifier; 051 status; 052 sale exit; 053 mortality; 054 cull; 055 report.

### Rabbit
001 Rabbitry Dashboard; 002 breeding animals; 003 Doe; 004 Buck; 005 registration; 006 cages; 007 cage detail; 008 occupancy; 009 breeding wave; 010 mating; 011 palpation; 012 pregnancy; 013 nest schedule; 014 nest placement; 015 nest removal; 016 kindling due; 017 kindling; 018 litter; 019 kit census; 020 foster; 021 kit mortality; 022 weaning; 023 individualise; 024 retention; 025 replacement compare; 026 market plan; 027 waitlist; 028 reservation; 029 contract; 030 sale allocation; 031 pedigree; 032 weight; 033 health; 034 GI-stasis red flag; 035 timeline; 036 report.

### Sheep
001 Dashboard; 002 mob/flock; 003 profile; 004 registration; 005 EID; 006 weight; 007 growth; 008 BCS; 009 FAMACHA; 010 joining dashboard; 011 joining; 012 pregnancy scan; 013 lambing due; 014 lambing; 015 lamb profile; 016 marking; 017 weaning; 018 wool dashboard; 019 shearing; 020 fleece; 021 micron/fibre; 022 dag score; 023 footrot; 024 flystrike; 025 health; 026 pedigree; 027 movement; 028 paddock; 029 identifier; 030 status; 031 timeline; 032 report.

### Cattle
001 Dashboard; 002 herd/mob/lot; 003 profile; 004 registration; 005 EID/identifier; 006 weight; 007 growth; 008 BCS; 009 heat; 010 service; 011 AI; 012 ET; 013 pregnancy diagnosis; 014 calving due; 015 calving; 016 calf; 017 weaning; 018 dairy dashboard; 019 milk; 020 lactation; 021 SCC; 022 SCC history; 023 locomotion; 024 dry-off; 025 beef dashboard; 026 feedlot placement; 027 lot; 028 days on feed; 029 close-out; 030 movement; 031 official movement record; 032 pedigree; 033 health; 034 status; 035 timeline; 036 report.

### Poultry
001 Dashboard; 002 enabled kinds; 003 enable kind; 004 flocks; 005 flock profile; 006 houses; 007 house; 008 placement; 009 mortality; 010 egg production; 011 egg collection; 012 feed/FCR; 013 vaccination schedule; 014 vaccination; 015 biosecurity dashboard; 016 biosecurity check; 017 hatchery; 018 eggs set; 019 incubation; 020 candling; 021 hatch result; 022 chick placement; 023 flock move; 024 close-out; 025 health; 026 timeline; 027 report.

### Shared operations and business
Health 001–030 covers dashboard, observations/cases, treatments, withdrawals, vaccination, formulary, protocol packs, vet visits, labs, reference library, emergencies and reports. Tasks 001–015 covers Today, all/detail/create/edit/assigned/scheduled/overdue/completed/calendar/recurrence/evidence and generated tasks. Inventory 001–019 covers items, receive/issue, lots, FEFO, expiry, low stock, reorder, movement, adjustment/count, supplier and reports. Feed 001–012 covers inventory, plans, schedules, consumption, cost, ration building/analysis/compare and alerts. Water 001–010 covers points, consumption, inspections, quality, issues, maintenance and reports. Pasture 001–011 covers paddocks, rotation, movement, rest, capacity, condition, history and map. Groups 001–009, Labour 001–009 and Assets 001–012 cover their complete operational cycles. Finance 001–013, Sales 001–012 and Procurement 001–010 cover business records, exact-money views, orders/receipts/history and reports.

### Intelligence/admin
Capacity 001–010; Genetics 001–010; Analytics 001–014; Simulation 001–009; Search 001–009; Copilot 001–012; Reports 001–015; Admin 001–025; Sync/offline 001–018. AI remains advisory and cannot autonomously mutate authoritative farm state. Genetics must not masquerade as complete from pedigree links alone. Ration analysis must not masquerade as complete until formulation logic exists.

## Required screen contract
Every Screen ID must map purpose, roles, entry/exit routes, primary object, feature IDs, commands, read models, Room entities, outbox mutations, RPCs, search dependencies, notifications, analytics, reports, permissions, all applicable states, visual class/assets, phone/tablet adaptation, current implementation, feature state, visual state and evidence.

## Global states
Initial, loading, populated, empty, offline, stale-but-usable, local-unsynced, syncing, retry, conflict, rejection, auth expired, membership revoked, permission denied, validation error, partial data, search unavailable, destructive confirmation, local-save receipt, server acknowledgement, 200% text, tablet adaptive and outdoor/high-contrast.

## Navigation
Home = farm intelligence/today. Animals = illustrated species entrances, never a generic animal list. Work = Tasks/Health/Feed/Water/Pasture/Labour/Assets. Business = Inventory/Sales/Procurement/Finance. More = Analytics/Genetics/Capacity/Simulation/Search/Copilot/Reports/Admin. Role/device adaptation is allowed without breaking Screen IDs.

## Atomic-screen law
Distinct workflows may not remain collapsed into giant scrolling implementations. The current Goat vertical-slice surface is architecture proof and must be decomposed into the registered Goat pages while retaining its working domain/state/command contracts.

## Cross-module traceability
Health: animal → observation → treatment → formulary → inventory issue → withdrawal → follow-up task → timeline → finance → report. Reproduction: candidate → mating/service → pregnancy/palpation → due → birth → offspring → cohort/litter → weaning → retention/sale → pedigree → analytics. Inventory: low stock → reorder → supplier → purchase → receive → lot → FEFO → cost → audit. Work: task → assignment → execution/evidence → animal/asset/inventory effects → completion → analytics. Search/RFID/QR always opens the native module surface.

## Visual certification
Visual contract defined ≠ implemented ≠ VISUAL_GREEN. `VISUAL_GREEN` requires reference lineage, implementation, phone golden, tablet golden where required, state evidence, accessibility, drift comparison and approval. `FEATURE_GREEN` remains separate.

## Drift prevention
CI/review must reject unregistered routes, unregistered assets, unapproved visual families, raw off-token styling, missing state evidence, missing goldens, orphan screens and visually drifted screens. Every UI PR names affected Screen IDs and parent visual references.

## Atomic shared-module index

### Health — FOS-HEALTH
001 Health Dashboard; 002 Today Health Actions; 003 Observation List; 004 Record Observation; 005 Health Case Detail; 006 Treatment List; 007 Record Treatment; 008 Treatment Detail; 009 Withdrawal Dashboard; 010 Withdrawal Detail; 011 Vaccination Schedule; 012 Vaccination Capture; 013 Formulary; 014 Formulary Item; 015 Protocol Packs; 016 Protocol Detail; 017 Accept Protocol; 018 Apply Protocol; 019 Protocol Slot Editor; 020 Vet Visit List; 021 Record Vet Visit; 022 Vet Visit Detail; 023 Lab Results; 024 Record Lab Result; 025 Lab Result Detail; 026 Disease/Reference Library; 027 Reference Detail; 028 Emergency/Red Flag; 029 Health Timeline; 030 Health Report.

### Tasks — FOS-TASK
001 Today Board; 002 All Tasks; 003 Task Detail; 004 Create Task; 005 Edit Task; 006 Assigned to Me; 007 Scheduled; 008 Overdue; 009 Completed; 010 Calendar; 011 Recurrence; 012 Attachment; 013 Completion Evidence; 014 Protocol-generated Task; 015 Lifecycle-generated Task.

### Inventory — FOS-INV
001 Dashboard; 002 List; 003 Item; 004 Create Item; 005 Receive Stock; 006 Receive Lot; 007 Issue Stock; 008 FEFO Issue; 009 Lot Detail; 010 Expiry Queue; 011 Low Stock; 012 Reorder Rules; 013 Reorder Alert; 014 Movement History; 015 Adjustment; 016 Stock Count; 017 Search; 018 Supplier Binding; 019 Report.

### Feed — FOS-FEED
001 Dashboard; 002 Inventory; 003 Item; 004 Feed Plan; 005 Feeding Schedule; 006 Issue/Consumption; 007 Feed Cost; 008 Ration Builder; 009 Ration Analysis; 010 Ration Compare; 011 Feed Alert; 012 Report.

### Water — FOS-WATER
001 Dashboard; 002 Water Points; 003 Water Point Detail; 004 Consumption Capture; 005 Inspection; 006 Quality Result; 007 Water Issue; 008 Maintenance; 009 History; 010 Report.

### Pasture — FOS-PASTURE
001 Dashboard; 002 Paddock List; 003 Paddock Detail; 004 Grazing Rotation; 005 Group Movement; 006 Rest Period; 007 Carrying Capacity; 008 Condition; 009 Grazing History; 010 Pasture Map; 011 Report.

### Groups — FOS-GROUP
001 Groups; 002 Detail; 003 Create; 004 Edit; 005 Membership; 006 Census; 007 Move; 008 Group Health; 009 Timeline.

### Labour — FOS-LABOUR
001 Dashboard; 002 Workers; 003 Worker Detail; 004 Assignment; 005 Work Log; 006 Attendance/Availability; 007 Labour Cost; 008 Coverage/Workload; 009 Report.

### Assets — FOS-ASSET
001 Dashboard; 002 Register; 003 Detail; 004 Create Asset; 005 Meter/Usage; 006 Maintenance Schedule; 007 Maintenance Job; 008 Breakdown; 009 Service History; 010 Documents; 011 Gallery; 012 Report.

### Finance — FOS-FIN
001 Dashboard; 002 Transactions; 003 Transaction Detail; 004 Record Income; 005 Record Expense; 006 Enterprise Profitability; 007 Species Cost; 008 Activity Cost; 009 Cash Flow; 010 Budget; 011 Variance; 012 Report; 013 Export.

### Sales — FOS-SALES
001 Dashboard; 002 Customers; 003 Customer Detail; 004 Sales Orders; 005 Sale Detail; 006 Create Sale; 007 Animal Sale; 008 Produce Sale; 009 Rabbit Reservation Sale; 010 Delivery/Collection; 011 History; 012 Report.

### Procurement — FOS-PROC
001 Dashboard; 002 Suppliers; 003 Supplier Detail; 004 Purchase Orders; 005 Purchase Detail; 006 Create Purchase; 007 Receive Purchase; 008 Purchase-to-Inventory; 009 History; 010 Report.

### Capacity — FOS-CAP
001 Dashboard; 002 Housing Capacity; 003 Stocking Pressure; 004 Cage Capacity; 005 Poultry House Capacity; 006 Paddock Capacity; 007 Feed Capacity; 008 Water Capacity; 009 Forecast; 010 Alert.

### Genetics — FOS-GEN
001 Dashboard; 002 Pedigree Explorer; 003 Parentage Record; 004 Parentage Verification; 005 Relationship Explorer; 006 COI Analysis; 007 Mate Comparison; 008 Candidate Ranking; 009 Genetic Warning; 010 Report.

### Analytics / Decision Intelligence — FOS-AN
001 Farm Analytics; 002 Species Performance; 003 Reproduction; 004 Growth; 005 Mortality; 006 Health Trends; 007 Production; 008 Feed Efficiency; 009 Financial Performance; 010 Benchmark/Compare; 011 Anomaly Centre; 012 Forecasts; 013 Insight Detail; 014 Evidence/Source Drilldown.

### Simulation — FOS-SIM
001 Hub; 002 Herd Growth Scenario; 003 Breeding Scenario; 004 Feed Scenario; 005 Capacity Scenario; 006 Financial Scenario; 007 Scenario Compare; 008 Scenario Save; 009 Scenario Report.

### Search — FOS-SEARCH
001 Search Home; 002 Results; 003 Filter Sheet; 004 No Results; 005 Offline Local Search; 006 Server Search Unavailable; 007 RFID Result; 008 QR/Barcode Result; 009 Recent Searches.

### AI/Copilot — FOS-AI
001 Copilot Home; 002 Ask Farm OS; 003 Conversation; 004 Insight Detail; 005 Evidence/Sources; 006 Suggested Actions; 007 Action Review; 008 Anomaly Review; 009 Model Provider Settings; 010 API/Provider Connection; 011 AI Privacy/Controls; 012 AI Unavailable.

### Reports — FOS-REPORT
001 Hub; 002 Animal Report; 003 Herd/Flock Report; 004 Health Report; 005 Production Report; 006 Finance Report; 007 Inventory Report; 008 Breeding Report; 009 Genetics Report; 010 Operations Report; 011 Export; 012 Generated Documents; 013 Viewer; 014 Share/Print; 015 Certificate Viewer.

### Administration — FOS-ADMIN
001 Settings Home; 002 Farm Profile; 003 Members; 004 Invite Member; 005 Role Detail; 006 Permissions; 007 Species Configuration; 008 Poultry Kinds; 009 Locations; 010 Units; 011 Currency; 012 Notifications; 013 Offline/Sync Settings; 014 Search Settings; 015 Integrations; 016 Hardware; 017 BLE Devices; 018 RFID Devices; 019 Model API; 020 Security; 021 Session/Devices; 022 Data Export; 023 Backup/Restore Status; 024 Audit Log; 025 About/Version.

### Sync / offline — FOS-SYNC
001 Offline Banner; 002 Sync Status; 003 Pending Changes; 004 In-Flight; 005 Retry Waiting; 006 Conflict Centre; 007 Conflict Detail; 008 Rejected Mutation; 009 Dead Letter; 010 Auth Required; 011 Farm Access Revoked; 012 Backend Outage; 013 Search Outage; 014 Local Data Safe Confirmation; 015 Manual Sync; 016 Last Successful Sync; 017 Reconciliation Detail; 018 Mutation Trace (admin/debug).

### Shared atomic surfaces — FOS-ATOM
001 Date Picker; 002 Time Picker; 003 Species Selector; 004 Animal Selector; 005 Group Selector; 006 Farm Selector; 007 Location Selector; 008 Sire/Dam Selector; 009 Inventory Item Selector; 010 Lot Selector; 011 Supplier Selector; 012 Customer Selector; 013 Worker Selector; 014 Asset Selector; 015 Photo Capture; 016 Attachment Picker; 017 Document Picker; 018 QR/Barcode Scanner; 019 RFID Scanner; 020 BLE Device Picker; 021 Filter Sheet; 022 Sort Sheet; 023 Bulk Selection; 024 Bulk Action Sheet; 025 Delete Confirmation; 026 Irreversible Status Confirmation; 027 Conflict Resolution; 028 Permission Explanation; 029 Export Format; 030 Share Sheet; 031 Search Empty; 032 Loading Skeleton; 033 Error Recovery; 034 Offline Save Receipt; 035 Sync Pending Receipt.

## Role and device law
Canonical roles: FARM_OWNER, FARM_MANAGER, SUPERVISOR, FARM_WORKER, VET/VET_ADJACENT_REVIEWER, FINANCE/ADMIN, READ_ONLY, SYSTEM_ADMIN where applicable. Every screen declares visibility, edit authority, approval authority, destructive-action authority, sensitive-data access and export authority.

Phone prioritises capture, identification, quick history, tasks, health and offline operation. Tablet prioritises analytics, breeding, finance, reports, large tables, planning, comparison, capacity and administration. Required tablet layouts must use master/detail, split planning or data-table patterns rather than merely stretching phone composition.

## Visual intensity inheritance
I1 uses the full illustrated scene system. I2 uses environment/species heroes plus operational dashboards. I3 uses content-first surfaces with Farm OS illustration identity in headers, portraits, section art and empty states. I4 suppresses decorative scene weight so treatment, mortality, withdrawal, biosecurity and destructive decisions remain unambiguous.

## Completion law
Every documented feature maps to at least one Screen ID or is explicitly `headless/system-only`. Every Screen ID maps back to a feature contract. Orphan features and orphan screens block completion. A screen that matches the theme but lacks working feature contracts is not feature green; a working feature rendered in a drifted UI is not visual green.
