# Farm OS — Role Dashboard Product Contract

**Purpose:** role routing, information ownership and authorization only.  
**Visual authority:** exclusively `docs/ux/animal-farm-visual-lock/`. This document defines no palette, typography, illustration style, geometry or layout family.

## Canonical role surfaces

- `FOS-HOME-012-A` Owner Dashboard
- `FOS-HOME-012-B` Farm Manager Dashboard
- `FOS-HOME-012-C` Supervisor Dashboard
- `FOS-HOME-012-D` Worker My Day Dashboard
- `FOS-HOME-012-E` Breeding Manager Dashboard
- `FOS-HOME-012-F` Vet / Health Dashboard
- `FOS-HOME-012-G` Finance Dashboard
- `FOS-HOME-012-H` Buyer / Read-only Dashboard

Unknown roles fall back to `FOS-HOME-001`; a guessed role must never grant capability.

## Authorization law

The authenticated farm membership selects the dashboard. UI visibility is not an authorization boundary. Supabase membership, RLS and versioned RPC authorization remain authoritative for every mutation and protected read.

## Role information priorities

- **Owner / Farm Manager:** farm-wide work, health exceptions, inventory/resource pressure, people/assets/land, finance, sales and procurement.
- **Worker:** assigned work, safe capture, task-linked records, permitted health observations, resources and own progress.
- **Supervisor:** team work, workload, exceptions, equipment and resource escalation.
- **Breeding Manager:** species-correct reproductive programmes and due breeding work.
- **Vet / Health:** health evidence, follow-up, treatment/withdrawal state and authorized clinical workflows.
- **Finance:** finance, sales, procurement, inventory and cost context.
- **Buyer / Read-only:** purchasing and supply context without implied mutation rights.

## Presentation binding

Management surfaces use the lock's A / Control room family. Worker surfaces use the D shell + C Review & decide family. All remaining composition, tokens, assets, typography, state treatment, responsive behavior and accessibility requirements come from the visual-lock package and its page patterns. This contract may not introduce a second visual language.

## Certification

A role surface is not `VISUAL_GREEN` until required native phone/tablet, state, font-scale, accessibility and owner-approval evidence is attached to the tested commit. Product authorization and visual certification are separate gates.
