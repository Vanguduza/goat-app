#!/usr/bin/env python3
"""Generate and verify Farm OS quantum development state without inventing feature IDs."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml"
IMPL_MAP = ROOT / "docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml"
QDU_OUT = ROOT / "docs/realisation/QDU_REGISTRY.json"
STATE_OUT = ROOT / "PROJECT_IMPLEMENTATION_STATE.json"
FEATURE_OUT = ROOT / "docs/realisation/FEATURE_REGISTRY.yaml"
SCREEN_RE = re.compile(r"FOS-[A-Z]+-[0-9]{3}(?:-[A-Z])?")


def source_fingerprint() -> str:
    digest = hashlib.sha256()
    inputs = [REGISTRY, IMPL_MAP]
    for base in ("app", "core", "data", "domain", "feature"):
        inputs.extend(sorted((ROOT / base).rglob("*.kt")))
    for path in inputs:
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return "sha256:" + digest.hexdigest()


def kotlin_ids() -> set[str]:
    found: set[str] = set()
    for base in ("app", "core", "data", "domain", "feature"):
        for path in (ROOT / base).rglob("*.kt"):
            found.update(SCREEN_RE.findall(path.read_text(errors="ignore")))
    return found


def mapped_ids() -> set[str]:
    data = yaml.safe_load(IMPL_MAP.read_text()) or {}
    found: set[str] = set()
    for surface in data.get("surfaces", []):
        found.update(surface.get("target_screens") or [])
    return found


def build() -> tuple[dict, dict, dict]:
    registry = yaml.safe_load(REGISTRY.read_text()) or {}
    screens = registry.get("screens") or []
    ids = [s["screen_id"] for s in screens]
    unique = set(ids)
    if len(ids) != 545 or len(unique) != 545:
        raise SystemExit(f"screen registry must contain exactly 545 unique entries; got {len(ids)}/{len(unique)}")
    if registry.get("screen_count") != 545:
        raise SystemExit(f"screen_count footer must be 545; got {registry.get('screen_count')}")
    if registry.get("authority") != "docs/ux/animal-farm-visual-lock/":
        raise SystemExit("screen registry visual authority is not the Layer 3 animal-farm-visual-lock")
    if registry.get("visual_green_count") != 0:
        raise SystemExit("visual_green_count changed without native evidence registry support")
    if registry.get("feature_green_claims_from_this_registry") != 0:
        raise SystemExit("screen registry must not manufacture FEATURE_GREEN claims")

    code = kotlin_ids()
    mapped = mapped_ids()
    module_stats = defaultdict(lambda: {"registered": 0, "kotlin_referenced": 0, "implementation_mapped": 0, "visual_green": 0})
    qdus = []
    for screen in screens:
        sid = screen["screen_id"]
        module = screen["module"]
        in_code = sid in code
        in_map = sid in mapped
        visual_green = bool(screen.get("visual_green"))
        module_stats[module]["registered"] += 1
        module_stats[module]["kotlin_referenced"] += int(in_code)
        module_stats[module]["implementation_mapped"] += int(in_map)
        module_stats[module]["visual_green"] += int(visual_green)
        qdus.append({
            "qdu_id": f"QDU-SCREEN-{sid}",
            "screen_id": sid,
            "name": screen["name"],
            "module": module,
            "feature_binding_status": "UNRESOLVED_CANONICAL_FEATURE_ID_CATALOG",
            "implementation_evidence": {
                "kotlin_reference": in_code,
                "implementation_map": in_map,
            },
            "required_contracts": [
                "purpose_and_primary_object", "roles_and_permissions", "entry_exit_routes",
                "commands_and_read_models", "offline_and_sync_where_applicable",
                "backend_authority_where_applicable", "states_and_failure_modes",
                "layer3_visual_lineage", "accessibility", "tests_and_commit_bound_evidence",
            ],
            "feature_green": False,
            "visual_green": visual_green,
            "state": "IMPLEMENTATION_EVIDENCE_PRESENT_NOT_GREEN" if (in_code or in_map) else "PLANNED_NOT_IMPLEMENTED",
        })

    counts = Counter(q["state"] for q in qdus)
    feature_registry = {
        "schema_version": 1,
        "catalog_status": "BLOCKED_CANONICAL_FEATURE_ID_CATALOG_NOT_FOUND",
        "authority_note": "Do not invent Feature IDs. Bind screens/QDUs only after an owner-approved or higher-authority canonical Feature-ID catalog is established.",
        "feature_green_claims": 0,
        "features": [],
    }
    qdu_registry = {
        "schema_version": 1,
        "source_fingerprint": source_fingerprint(),
        "screen_count": 545,
        "qdu_count": len(qdus),
        "feature_binding_status": feature_registry["catalog_status"],
        "qdus": qdus,
    }
    state = {
        "schema_version": 1,
        "repository": "Vanguduza/goat-app",
        "source_fingerprint": source_fingerprint(),
        "canonical_branch": "main",
        "release_state": "BLOCKED",
        "green_states": {
            "vertical_slice_green": True,
            "feature_green_count": 0,
            "module_green_count": 0,
            "mvp_green": False,
            "visual_green_count": 0,
        },
        "screen_coverage": {
            "registered": 545,
            "kotlin_referenced": sum(1 for q in qdus if q["implementation_evidence"]["kotlin_reference"]),
            "implementation_mapped": sum(1 for q in qdus if q["implementation_evidence"]["implementation_map"]),
            "qdu_states": dict(sorted(counts.items())),
        },
        "module_coverage": dict(sorted(module_stats.items())),
        "blocking_authority_gaps": ["CANONICAL_FEATURE_ID_CATALOG_NOT_FOUND"],
        "status_law": "Code or static mapping is implementation evidence only; it is not route reachability, FEATURE_GREEN, MODULE_GREEN, VISUAL_GREEN, or MVP_GREEN.",
    }
    return feature_registry, qdu_registry, state


def write_outputs(feature_registry: dict, qdu_registry: dict, state: dict) -> None:
    FEATURE_OUT.write_text(yaml.safe_dump(feature_registry, sort_keys=False), encoding="utf-8")
    QDU_OUT.write_text(json.dumps(qdu_registry, indent=2) + "\n", encoding="utf-8")
    STATE_OUT.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if generated outputs are stale")
    args = parser.parse_args()
    feature, qdu, state = build()
    if args.check:
        expected = {
            FEATURE_OUT: yaml.safe_dump(feature, sort_keys=False),
            QDU_OUT: json.dumps(qdu, indent=2) + "\n",
            STATE_OUT: json.dumps(state, indent=2) + "\n",
        }
        stale = [str(path.relative_to(ROOT)) for path, text in expected.items() if not path.exists() or path.read_text() != text]
        if stale:
            raise SystemExit("stale quantum control-plane outputs: " + ", ".join(stale))
        print("PASS quantum development control plane: 545 screens, 545 QDUs, zero invented green claims")
        return
    write_outputs(feature, qdu, state)
    print("generated quantum development control plane for 545 screens")


if __name__ == "__main__":
    main()
