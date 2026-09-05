# Farm OS — Canonical Role Dashboard Contract

**Authority:** owner-directed recovery of session `01a04f06-bfbe-7b32-bf12-f9834e900244`
**Status:** binding visual/product contract; implementation evidence still requires screenshot/golden review
**Root references:** `Farm OS Countryside Login Screen.png`, `Farm OS Visual Style Guide.png`, `Farm OS Design Audit Guide.png`

## 1. Non-negotiable lineage

Role dashboards do not create a new design family. They inherit the exact Farm OS language shown in the locked references:

- warm painterly/editorial agricultural scene;
- pastoral foreground/midground/background depth;
- cream/off-white rounded operational surfaces;
- farm/deep green primary action and identity;
- clean operational sans typography with restrained handwritten emotional accents;
- illustrated animal/environment identity without stock-vector, glossy 3D or generic Material substitution;
- bottom navigation vocabulary `Home / Animals / Tasks / More`;
- Farm Home hierarchy of greeting → farm identity → Today → farm/role content;
- Tasks hierarchy of Today/Upcoming/Completed and quick operational actions.

The visual test is not “farm themed.” The screen must look authored by the same designer and illustrator as the locked session reference.

## 2. Role-routing law

The authenticated farm membership role selects the dashboard. UI visibility is not an authorization boundary; Supabase membership/RLS/RPC authorization remains authoritative.

Canonical atomic screens:

- `FOS-HOME-012-A` Owner Dashboard
- `FOS-HOME-012-B` Farm Manager Dashboard
- `FOS-HOME-012-C` Supervisor Dashboard
- `FOS-HOME-012-D` Worker My Day Dashboard
- `FOS-HOME-012-E` Breeding Manager Dashboard
- `FOS-HOME-012-F` Vet / Health Dashboard
- `FOS-HOME-012-G` Finance Dashboard
- `FOS-HOME-012-H` Buyer / Read-only Dashboard

Unknown roles fall back to `FOS-HOME-001` rather than receiving guessed privileges.

## 3. Owner / Farm Manager cockpit

Management answers: **what is happening across the farm, what needs intervention, and what decision is next?**

Composition:
1. `Good Morning` + farm name in the pastoral hero;
2. handwritten role accent (`Owner overview` or `Management overview`);
3. Today metrics: tasks due, health alerts/withdrawals, local changes awaiting sync;
4. Farm Pulse cards: Work, Health, Inventory, People, Assets, Land;
5. illustrated species strip: Goats, Cattle, Sheep, Poultry, Rabbits;
6. Business & stewardship: Finance, Sales, Procurement;
7. persistent Home / Animals / Tasks / More navigation.

Tablet adaptation is management-first: widen cards, preserve the same hierarchy, and use dashboard/detail compositions without changing the visual family.

## 4. Worker — My Day

Worker answers: **what do I do now, where do I go, what do I record, and is my work safely saved?**

Composition:
1. `Good Morning` + farm name;
2. handwritten `My day` accent;
3. explicit offline-safe message;
4. Today metrics: tasks due, health flags, changes to sync;
5. My Tasks as the dominant operational card;
6. Quick Actions matching the locked style-guide vocabulary: Add Task, Scan Animal, Record Weight, Add Treatment;
7. Field Areas: Animals, Feed, Water, Pasture, Assets;
8. Home / Animals / Tasks / More.

Worker is phone-first and must remain one-thumb, sunlight-legible and capture-oriented.
## 5. Supervisor

Supervisor answers: **what must my team complete and what needs escalation?**

Hierarchy: Team tasks → People/workload → Health exceptions → Equipment → Feed → Water.

## 6. Breeding Manager

Breeding answers: **which reproductive events are due and which animals/programmes need action?**

Hierarchy: Goats → Rabbits → Sheep → Cattle → Poultry → due breeding work. Species modules remain biologically native.

## 7. Vet / Health

Vet/health answers: **what health evidence needs review, what treatment/withdrawal state exists, and what follow-up is due?**

Hierarchy: Health centre → Follow-up tasks → Goat → Rabbit → Sheep → Cattle. Advisory/reviewer presentation never bypasses treatment authorization.

## 8. Finance

Finance answers: **what is the farm's commercial position and which records need attention?**

Hierarchy: Finance → Sales → Procurement → Inventory → Labour-cost context → Asset-cost context.

## 9. Buyer / Read-only

Buyer answers: **what supply pressure exists and what purchasing records are relevant?**

Hierarchy: Procurement → Inventory → Feed → Asset/maintenance supply context. The dashboard must not imply mutation rights that the membership does not possess.

## 10. Visual certification

Implementation is `VISUAL_IMPLEMENTED`, not `VISUAL_GREEN`, until each role surface has:
- deterministic phone golden;
- tablet golden where applicable;
- loading, empty, offline, pending-sync, error and revoked-access evidence;
- 200% font-scale review;
- TalkBack/order review;
- visual comparison against the locked reference family;
- owner approval.
