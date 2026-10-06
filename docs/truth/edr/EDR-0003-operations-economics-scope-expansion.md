# EDR-0003 — Expand platform scope to full operations & economics layer

**Status:** Accepted
**Date:** 2026-08-22
**Decided by:** Owner (rank-0 decision, this conversation, 22 Aug 2026)
**Affected documents:** `FARM_OS_OPERATIONS_ECONOMICS_LAYER_SPEC.md` (new); master plan out-of-scope lists; consolidated binder; `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` Ch.2.8 exclusions.

## Context

The platform previously locked "no automatic financial ledger beyond optional posting hook" and "inventory hooks only". While integrating the veterinary intelligence features it became clear the platform could not answer owner questions (cost per kg gain, margin per litter, enterprise profitability) without money and stock as first-class data. The owner was asked for four scope decisions and made them explicitly.

## Alternatives considered

| Option | Assessment |
|---|---|
| Keep optional posting hook only | No unit economics possible; cockpit would be KPIs-only |
| Light money records | Cheaper but still no allocations → no enterprise net, no cost/kg |
| Full cost accounting (chosen) | Enables unit economics + cockpit; more capture surface to design carefully |

## Decision (owner selections)

1. **Economics:** full cost accounting — enterprises, allocations, unit economics, P&L-style monthly view (not statutory double-entry GL).
2. **Inventory:** standard stock management plus feed planning and simple ration checks.
3. **Pasture:** paddock records plus rotation planner (goat/sheep/cattle).
4. **Cockpit:** full farm-owner business dashboard — KPIs, money, stock, compliance, exports in one place.

## Consequences

- New spec `FARM_OS_OPERATIONS_ECONOMICS_LAYER_SPEC.md` governs schema, engines, UX, phasing O1–O6.
- Master plan explicit-exclusion lines for financial ledger / inventory depth are amended by reference: replaced by "statutory accounting/payments remain excluded; internal cost accounting is in scope".
- Handbook Ch.2.8 exclusion list is amended by the same reference.
- New side-effect classes apply (`commercial`); reversal-entry rule keeps ledger append-only.
- Phase map gains optional slices O1–O6 after core phases; each carries a named gate.

## Open questions (require explicit human decision later)

- Currency defaults per farm locale; benchmark price source (manual only for v1).
- Enterprise code taxonomy finalisation (defaults proposed in spec §11).

## Smallest safe next step

Implement slice O1 (inventory core) behind its FEFO + RLS fixtures; no money tables before O1 gate passes.

---

*Supersession note: supersedes the "optional posting hook" wording in earlier docs by reference; does not rewrite them.*
