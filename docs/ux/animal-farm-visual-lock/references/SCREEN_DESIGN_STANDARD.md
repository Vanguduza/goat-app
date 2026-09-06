# Screen design standard — thoughtful workflows throughout

31 August 2026 · Applies to the catalogue and every subsequent screen brief. Design-system spec and frontend playbook remain the visual authority except for the explicit [owner-locked home and animal imagery amendment](../../development/HOME_DESIGN_LOCK.md).

## 1. Screen brief contract

Every catalogue entry supplies: stable ID, source-backed feature, stage, intended pattern, persona/access scope, user job, information order, actions, data/effects, special risk/validation and acceptance test. Entries describe a route or coherent screen family; listed variants are mandatory, not optional details.

Before design-ready, expand each entry into actual fields (required/optional, type, unit, bounds, default, source, editability), named data contract, navigation/deep-link parameters, action permissions, state fixtures and acceptance IDs. Do not infer medical thresholds, irreversible transitions, backend shape or financial semantics from UI labels.

Every screen has one obvious primary task. If it serves two incompatible jobs, separate the destinations or use an explicitly reviewed secondary sheet. Catalogue grouping is not permission to cram an entire workflow onto one page.

## 2. Common behavior inherited by all entries

- Keep farm, role, species/kind, subject and time window unambiguous; never silently switch subjects during scanning or save.
- Show only enabled capabilities and granted actions. Deep links, cached data, exports, scanner results and command handlers enforce the same scope.
- Use the named pattern and shared tokens. Unfitting patterns remain design-decision candidates, not ad-hoc production screens.
- Declare idle/loading/empty/error/disabled presentations. Also specify invalid input, offline/stale data, unsaved draft, local save pending, sync rejection/conflict, revoked permission and no applicable capability where relevant.
- Static local guides/preferences still declare state handling, but do not invent network spinners or fake failures. Unsupported/N/A states need rationale and a test of the real boundary.
- Never turn unknown/missing counts into zero. Empty states distinguish no records, no permission, no enabled module and no search matches; give one valid next action.
- Field capture reads local data, saves durably, and surfaces local/pending/confirmed status. Destructive/commercial transitions confirm at the right boundary. Retry reuses operation identity.
- Keep drafts across rotation/process interruption as appropriate for non-secret farm records; never persist plain credentials. Specify cancel/back behavior and abandoned drafts.
- Provide field labels and unit suffixes, locale-aware dates/numbers, timezone rules and error recovery. Long text wraps; no fixed text heights.
- List pages include scoped search/filter and stable return position. Every chart supports Table/Chart/Both with equivalent values and context; screen-reader users do not lose findings.
- Preserve 48dp targets (64dp outdoor), TalkBack order/names, hardware keyboard operation, safe insets, 200% text and reduced-motion support. Tablets gain space, not a separate product.
- Notification taps and external links reopen the owning feature and reauthorize; they do not execute privileged commands from an intent alone.
- Product copy is concise. Do not repeat preview/developer explanations inside production forms or label every screen with a paragraph describing the app.

## 3. Form/action contract

For every write define: subject + happened-at time + observer/actor + typed outcome + relevant quantity/score/unit + permitted notes/attachments; farm/assignment comes from trusted context, not freely typed fields. Use species-specific records rather than a universal animal form.

Specify command owner, idempotency, aggregate version, linked effects and the success receipt. Example: nest placement can assign a box, issue bedding and complete a task; all effects must reconcile as one logical action. A save that partially succeeds must not invite blind repeat posting.

Corrections are new audit-preserving commands, not hidden record overwrites. Mark who may correct, cancel, reopen or approve and which downstream tasks, clocks or totals recalculate. Show the submitted result and a route to recent updates/history.

## 4. Worked brief — management command center

**Job:** identify what requires management attention and understand farm performance without visiting every module. **Access:** owner/full; farm manager only permitted summaries; specialist roles receive scoped alternatives. **Pattern:** owner-selected A / Control room Cockpit adaptation. Visual direction is locked; D05/D18's remaining integration and permissions questions remain open.

Information order:

1. Farm/date window and one concise sync/freshness status.
2. Critical restrictions/escalations and overdue decisions, with real evidence and accountable next action.
3. Work execution: overdue/due/completed by module/team, unassigned work and pending review only where the task contract supports them.
4. Species-native summaries (population/productivity/health) that open the correct module; no blended ADG or hatchability.
5. Stock expiry/low coverage and feed/paddock exceptions; action opens receiving, approved reorder, plan or location detail.
6. Farm/enterprise economics with source-linked net/income/expense, unit cost and allocation coverage; owner-only detail stays protected.
7. Compliance, reports and enabled tools; deeper administration is separate from the Theme-only home gear.

Primary action is the highest-priority permitted exception action, not a permanent generic Add everything button. Hide irrelevant capability blocks; distinguish disconnected/missing projections from healthy zero. Test same underlying events against module views, command-center totals and exported results. Review large datasets, old data and empty farm—not only an ideal full dashboard.

## 5. Worked brief — worker workspace

**Job:** know what to do, carry it out correctly, record the result and obtain help/resources. **Access:** assigned subjects and granted operational actions. **Pattern:** owner-selected D / Work board shell with C's Review & decide task carousel inside each selected work stage. My work, Record, Resources and Guides remain the primary worker areas. Stage filtering must not leak or relabel tasks from another stage.

My work shows urgent/overdue/due/upcoming tasks, location and subject. Search/filter reflect the assignment scope. Scan and calendar are quick actions; assigned module/subject detail and own recent updates are reachable. A task opens a concise context/result screen with the relevant approved guide, not a long generic description.

Record presents permitted domain captures: observations, weights, counts and the species' real daily workflows. Resources exposes accessible stock, batch/expiry/location, receiving/issuing/counting, feed instructions, nest-box readiness and permitted grazing moves. Guides shows only relevant, versioned, sourced farm-approved content and emergency contacts. Instruction details are on demand except essential safety warnings.

Complete opens or validates the required record. Start/snooze/escalate/reassign obey the final task-transition matrix (D15). Do not create an unsupported Blocked enum merely because it looks useful. New shortage/equipment requests remain proposed until receipt and review workflow is approved.

Acceptance: worker performs a due task with no network, sees a local save, resumes after restart, and management sees the authoritative outcome after reconciliation. No access to money detail, breeding approvals, pack editing or treatment product selection; do not hide the gear below an unreachable long page.

## 6. Worked brief — species module home

**Job:** understand and act within one species. **Pattern:** ModuleDashboard (greeting → relevant short tip → three selected KPIs → Today → relevant charts), with navigable record/planning tools.

Choose KPIs for this species and enabled capability, not the same three for every tile. Goat home highlights herd/kidding/growth or dairy; rabbit home colony/litters/nests; sheep mobs/scans/lambing or wool; cattle herd/PD/calving plus dairy/beef; poultry kind/purpose/house/flock with hatchery or lay/grow-out. All relevant tools are reachable even if they do not merit a dashboard card.

Today's worker actions depend on grants. Manager planning appears without exposing it to workers. Empty module setup is actionable; a species tile is not enabled as a completed product before its workflows exist. Tap a KPI to its contributing records and equivalent table.

## 7. Worked brief — observation and score capture

**Job:** accurately record a finding, not diagnose. **Pattern:** CaptureForm. **Access:** assigned worker observations; authorized roles for clinical/product commands.

Order: subject identity → date/place/observer → species observation category → severity → applicable score/measurement and unit/scale → notes/evidence → save. Auto-fill trusted task context, but make observation time explicit. Reference imagery is licensed, species-correct and behind a contextual guide control; critical instructions remain visible.

A product chosen by an authorized role/accepted task cannot be freely replaced by a worker. Clinical red flags offer record/contact/SOP paths and require permitted confirmation for quarantine; no automatic treatment. Pending photo upload is distinct from a recorded observation. Required fields, score scale and missing accepted pack cannot be bypassed through notification completion.

## 8. Worked brief — receiving, issuing and stocktake

**Job:** record a real material movement without duplicate stock or financial effects. **Patterns:** InventoryList → CaptureForm → TimelineScreen detail.

Stock list shows item, location, usable quantity, unit and next expiry, with search/filter. Receiving captures supplier/batch/expiry/location/quantity; cost fields follow D07's approved visibility. Issue captures reason, destination subject/location and quantity; FEFO explains actual batches selected. Stocktake is a saved count sheet per location, not an immediate ledger adjustment. Manager review shows variance and posts once; worker cannot post.

Feed/bedding/treatment auto-issues cannot be independently repeated by a second save. Show invalid quantity, unknown/expired batch, insufficient stock and stale count conflicts. A plan change is not a physical feed issue. Stock balance, valuation and unit-economics changes must drill back to the source operation.

## 9. Worked brief — poultry daily sheet and hatchery

**Job:** record flock-level production correctly for its kind and purpose. **Patterns:** CaptureForm and PlanBoard; not mammal reproduction screens.

Daily sheet starts with kind, purpose, flock/house and date; then opening census context, eggs where relevant, feed quantity, water, mortality/culls and relevant environmental observations. Required fields/units derive from the pack. A duplicate date from another worker surfaces the approved merge/review rule rather than creating two competing daily totals.

Hatchery requires kind and purpose, set count/time, source flock, machine/setting context, candling result, transfer/lockdown, hatch outcomes and chick/duckling/etc. placement. Dates derive from that kind's approved biology; no silent chicken default. Counts reconcile from set to outcome/placement; flock records are not fake individual birds.

## 10. Guides and concise copy

Keep action labels brief and specific: Record weight, Place nest box, Issue feed, Count stock, View guide. Remove repeated orientation paragraphs, marketing slogans, redundant captions, and instructions already obvious from labels.

Keep what changes safe action: units/scales, required constraint explanations, withdrawn-product restrictions, due/overdue context, data freshness, save/sync outcome, destructive confirmation and permission scope. Show deeper procedures in a task-linked guide. Source/version/approval status belongs in guide detail, not every homepage card.

Guides need a content owner, source/license, supported species/kind/capability, approved version, offline availability and update history. Expired/unapproved clinical material cannot quietly look current. FAMACHA imagery and product-label values are not generated art assets.

## 11. Screen acceptance gate

Each screen is checked against its own job, not only a generic screenshot rubric:

- User can find it through its home/task/scan/deep-link entry and return without losing work.
- Required actions exist and forbidden ones are denied at both UI and mutation boundary.
- Correct fields and relevant guide are within the <=3-tap capture-entry budget from the appropriate dashboard; typing/form steps are measured separately.
- Idle/loading/empty/error/disabled and applicable offline/draft/conflict/revocation cases are tested.
- Light/dark/outdoor × font 1.0/1.3/2.0 × widths 360/411/780 are rendered. Every state is covered; exhaustive state×density intersections apply unless an explicit reviewed equivalence/reduced-matrix rationale is recorded, never a silent omission.
- Keyboard/insets, long labels, pseudolocale/RTL, TalkBack, outdoor targets and no-text-clipping are demonstrated.
- Performance and battery budgets measured on representative hardware; the playbook's startup/jank targets are targets, not claims made from JVM screenshots.
- Source-to-record-to-task/stock/money/calendar effects reconcile where applicable.
- Independent critique meets the playbook threshold and owner pixel acceptance is recorded before feature integration.

Readable does not mean feature-poor. Concise does not mean omitting a warning. A complete page is one that lets the correct person finish the real job safely and recover when it goes wrong.
