# Farm OS deterministic completion control plane

**Authority:** Project Truth + owner instruction of 24 September 2026  
**Generated state:** `PROJECT_COMPLETION_STATE.json`  
**Feature authority:** `docs/realisation/FEATURE_REGISTRY.yaml`

This control plane converts the complete registered Farm OS scope into deterministic units of completion without changing product scope.

- 545 registered Screen IDs are preserved.
- 156 mandatory Feature IDs are established.
- Every Screen ID has exactly one primary Feature ID.
- Primary binding assigns completion ownership only; it does not erase cross-module dependencies.
- Feature IDs are stable and may not be removed or repurposed to thin scope.
- No catalogue entry is green at establishment.

`scripts/development/feature_catalog.py` is the machine-readable identity source. `scripts/development/verify_quantum_control_plane.py` validates coverage and generates the Feature Registry, QDU Registry, implementation state and completion state. Canonical CI runs the verifier in `--check` mode.

Development state remains evidence-derived: `REGISTERED -> CONTRACT_READY -> IMPLEMENTED_UNVERIFIED -> FEATURE_GREEN`. Visual certification is orthogonal and remains governed by the Animal Farm visual lock. Module and MVP green states are derived and cannot be set optimistically.
