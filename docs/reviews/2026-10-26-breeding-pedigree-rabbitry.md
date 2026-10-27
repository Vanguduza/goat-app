# Breeding, pedigree and rabbitry completion — 26 October 2026

This change extends the published owner-feedback checkpoint 80bad18b56db7304b8d92beb816ee8efc9fd3915.

## Breeding and pedigree

The app now compares every active same-farm sire candidate of the selected species using an owner-selected objective. The recorded-inbreeding objective ranks only candidates meeting the selected pedigree-completeness and optional coefficient ceiling. The offspring-growth objective additionally requires an explicit age window, measurement tolerance, sample-size minimum and recorded-inbreeding ceiling. It compares measured offspring phenotypes; it is not an EBV/genomic evaluation and does not claim genetic superiority.

Unknown founders remain unknown. Conflicting, cross-farm, cross-species and cyclic parentage is not guessed. Genetic dam supersedes gestational/foster relationships for genetic lineage. Equal evidence shares a rank. Pairing analysis performs no mating, pregnancy, parentage or treatment write.

The connected native pedigree displays recorded parents and ancestors, missing/conflicting branches, an offspring view and tap-to-recentre navigation. Individual goat, rabbit, sheep/lamb and cattle profiles expose the common lineage/pairing workspace; aggregate poultry flocks do not receive invented individual pedigrees.

## Rabbitry units

Room schema 38 adds farm-scoped current rabbit housing and nest assignments plus dated buck-service and observed-pair-mating records. Migration 37 to 38 is additive: existing farms keep their cages/nests/waves while occupancy/service/assignment remains unrecorded until a user records it.

The rabbitry board shows each recorded cage unit, actual residents, configured doe capacity, current buck-service interval, individual nest-box condition/assignment and related breeding-wave programme. A planned wave is not occupancy, an observed mating is not pregnancy, and wave-level litter counts are not attributed to a particular nest/individual.

All writes use the existing local command authority, permission checks, idempotent journal and replica appliers. Housing/nest writes use expected versions; stale edits fail rather than silently overwrite.

## Launcher icon

The Android launcher icon now uses the existing protected Farm OS animal lineup (goat, sheep, cattle, rabbit and poultry) on the app forest/cream palette. The original animal artwork is byte-for-byte unchanged; only adaptive/legacy launcher derivatives were added.

## Source-bound local verification

breeding-rabbitry-validation-r5-20261026: 37 tests, 0 failures/errors/skips, no compile errors, no source drift.

breeding-rabbitry-dark-r1-20261026: 6 of 6 native breeding/rabbitry/icon checks pass in dark mode.

breeding-rabbitry-outdoor-large-r2-20261026: 6 of 6 pass in Outdoor mode at 200 percent font scale.

These checks are source/fixture evidence, not independent approval of all 545 registered screens or a physical-device certification.
