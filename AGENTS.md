# Farm OS — Agent Operating Rules

Farm OS is a multi-species livestock management platform (Android/Kotlin/Compose + Supabase).
The authoritative documents are in `docs/` — the consolidated binder indexes all of them.
Read the relevant document section before changing anything it governs. Do not implement from chat memory.

## Authority — non-negotiable

1. Owner decisions outrank everything. The precedence chain is: Product Constitution → feature specs →
   `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` process law → accepted EDRs (in `docs/truth/edr/`) →
   this file → agent suggestions.
2. Never edit `docs/truth/**`, the Constitution (handbook Ch.2), or locked spec sections as a side effect
   of a task. Propose an EDR instead; do not mutate.
3. `supabase/migrations/` is the single schema source of truth. A schema change = migration + Room entity +
   test, all together. Generated artifacts are never hand-edited.
4. The design system spec (`FARM_OS_DESIGN_SYSTEM_SPEC.md`) is the ONLY visual authority. Reference repos
   (nowinandroid, compose-samples) are engineering references only — never visual ones.

## Boundaries — enforced by tests, do not work around

- Only `:domain-*` modules emit `domain_events`. Features never touch another feature's tables or screens.
- Vendor AI SDKs exist only inside `:ai-client` / `:ai-runtime-*`, behind port interfaces.
- Species UX is species-native: no cross-species list screens, no generic Animals home, vocabulary exact.
- Every durable table carries `farm_id` with fail-closed RLS. Application-code filtering alone is non-compliant.
- AI is advisory: outputs are drafts requiring human Accept. No auto-treat, auto-cull, auto-sell.

## Definition of done — all of these, every time

- [ ] Failing-first test existed.
- [ ] Build + lint + unit tests green; migrations apply to empty DB and reverse.
- [ ] New tables: `farm_id` + RLS + policy test. Async ops: durable identity + idempotency key + observable state.
- [ ] Side-effecting actions declare a side-effect class (`local_record`, `notification`, `external_call`, `commercial`).
- [ ] Golden fixtures pass (11-doe KudBat wave, COI pedigree cases, outbox replay, FEFO, withdrawal block).
- [ ] UI slices: gallery entry first, screenshot goldens updated, all five UI states, voice-checked copy (see design rule).
- [ ] Public behaviour change reflected in the governing spec section.

## Forbidden

- A second source of truth for mutable state; shadow tables; spreadsheet imports bypassing the ledger.
- Retrying side effects without an idempotency key or reconciliation read.
- Widening RLS, tool scope, or autonomy to make a test pass.
- Hard-coding model vendors, storage providers, or currency/locale assumptions.
- Free-typed drug names in treatments; formulary items only.
- Placeholder UI content (Lorem/Test/Item 1) or emoji in product copy.

## When blocked

Say so. State the smallest decision that would unblock. Stop. Do not invent contracts,
do not ship "temporary" alternatives, do not widen any scope to progress.
