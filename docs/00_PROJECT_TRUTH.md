# Farm OS — Project Truth

**Authority level:** Owner-approved implementation truth  
**Effective:** 29 August 2026  
**Applies to:** all Farm OS product, engineering, donor-reuse and release work.

## 1. Current product scope

Farm OS MVP is the complete documented Farm OS product. MVP includes all documented modules and all documented species products:

- Goats
- Rabbits
- Poultry (chicken, duck, muscovy, guinea fowl, turkey, goose, quail, pigeon/squab and farm-defined avian kinds)
- Sheep
- Cattle
- Health
- Tasks
- Farm Operations
- Feed
- Water
- Inventory
- Labour
- Capacity
- Assets/Maintenance
- Finance
- Sales
- Procurement
- Genetics
- Analytics
- Decision Intelligence
- Simulation
- Search
- AI/Copilot
- Reports/Documents
- Administration/Audit

Internal delivery waves may sequence engineering, but they do not remove any item from MVP.

## 2. Locked backend and infrastructure

- Native Android/Kotlin/Compose client.
- Room is the local operational source for Android UI/offline workflows, never the server system of record.
- WorkManager drains the durable transactional outbox.
- Supabase is the canonical backend: Auth, PostgreSQL, RLS, Storage, Database Functions/RPC and Edge Functions.
- PostgreSQL `domain_events` is the authoritative append-only business ledger for event-ledger domains.
- Master/config records remain governed relational records with audit/versioning.
- Read models/analytics/search are rebuildable projections.
- Meilisearch is mandatory MVP server search and is never authoritative.
- Local search must remain usable offline.
- No mandatory Ktor, Keycloak, MinIO, Kubernetes, Hermes, Ollama or Python/R genetics runtime is part of the MVP topology.

## 3. Write-authority rule

Material business mutations use one canonical command boundary:

```text
Android command
→ Room transaction + outbox
→ versioned Supabase RPC / Database Function
→ authenticate
→ resolve farm membership
→ authorize
→ validate tenant/species/domain rules
→ deduplicate mutation_id
→ enforce expected stream version where applicable
→ append authoritative fact/change master state
→ update required synchronous projections
→ enqueue async search/provider work
→ commit
→ acknowledgement
```

Direct table CRUD must not become an alternate business writer.

## 4. Tenant law

Every farm-owned row carries `farm_id`. Cross-farm relations are structurally prevented with tenant-aware keys/foreign keys where applicable. RLS uses authenticated identity + authoritative farm membership. Application filtering alone is never sufficient.

No service-role key is shipped in the APK.

## 5. Offline law

Field actions become successful only after local durability succeeds. Outbox states are:

```text
PENDING → IN_FLIGHT → ACKNOWLEDGED
                    ↘ CONFLICT
                    ↘ REJECTED
                    ↘ RETRY_WAIT → IN_FLIGHT
                    ↘ DEAD_LETTER
```

Server pull ordering uses a server-issued monotonic cursor, not device time.

## 6. Search law

```text
Supabase authority
→ durable search_index_job
→ Meilisearch
```

The app receives only short-lived, server-minted tenant tokens whose search rules enforce farm/module/species scope. Meilisearch admin/master/search parent keys remain server-side.

Search failure never rolls back an already accepted authoritative mutation.

## 7. AI law

AI is advisory and evidence-bound. It cannot autonomously prescribe, dose, treat, cull, sell, post accounting entries, alter authoritative records or bypass authorization. Core operation does not depend on an LLM provider.

## 8. Donor-repository law

Open-source donor repositories are engineering knowledge sources, not architecture authorities.

Allowed modes are explicit per feature: reference-only, behavioral reconstruction, algorithm/conformance oracle, UI/engineering pattern reference, selective code import after licence/security review, retained commodity dependency, or native build.

GPL/AGPL donors default to behavioral reconstruction/reference rather than code import. Every donor-dependent feature must identify what is inherited, what is rejected, native Farm OS gaps and parity/superiority tests.

## 9. Green-state law

Four different states exist:

### `VERTICAL_SLICE_GREEN`
Shared architecture is proven end-to-end sufficiently to authorize broad feature implementation. It does **not** mean the slice feature is complete.

### `FEATURE_GREEN`
The entire Feature Implementation Contract is implemented and every applicable gate passes. This is the first state that may be called feature-complete.

### `MODULE_GREEN`
Every mandatory Feature ID in a module is `FEATURE_GREEN`, and module-level integration/field acceptance passes.

### `MVP_GREEN`
Every mandatory module is `MODULE_GREEN` and whole-product security, data-integrity, offline, restore/DR, performance and representative mixed-farm certification passes.

A green vertical slice must never be promoted into a feature/module/MVP completeness claim.

## 10. Designated architecture slice

The first architecture slice is:

```text
Register Goat
→ Record Weight Offline
→ survive process/device restart
→ synchronize through versioned Supabase RPC
→ event/projection reconciliation
→ Meilisearch projection
→ farm-scoped search
→ second-device visibility
```

The slice must additionally prove duplicate mutation handling, RLS/tenant attacks, clock drift, auth expiry/re-auth, revoked membership, backend outage, search outage/rebuild and observability.

## 11. Authority hierarchy

1. explicit owner decisions;
2. this Project Truth and the accepted Farm OS v3 Implementation Closure;
3. v3 Full Realisation Pack registries;
4. Revision 2 product/domain material not contradicted above;
5. specialist veterinary/health/AI/rabbit specifications;
6. accepted EDRs implementing or clarifying this truth;
7. implementation/tests as evidence;
8. older documents as provenance only.

If an older document says goats/rabbits are the only MVP system-of-record species, excludes Meilisearch from MVP, mandates Ktor/Keycloak/MinIO/Kubernetes, or treats a vertical slice as feature completion, that statement is superseded.
