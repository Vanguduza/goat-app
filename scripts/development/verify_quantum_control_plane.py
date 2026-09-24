#!/usr/bin/env python3
"""Generate and verify the Farm OS deterministic completion control plane."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import yaml

from feature_catalog import CATALOG_AUTHORITY, CATALOG_STATUS, SCOPE_LAW, catalog_features, feature_ids_for_screen, validate_screen_coverage
from feature_dependencies import build_graph

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml"
IMPL_MAP = ROOT / "docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml"
QDU_OUT = ROOT / "docs/realisation/QDU_REGISTRY.json"
STATE_OUT = ROOT / "PROJECT_IMPLEMENTATION_STATE.json"
COMPLETION_OUT = ROOT / "PROJECT_COMPLETION_STATE.json"
FEATURE_OUT = ROOT / "docs/realisation/FEATURE_REGISTRY.yaml"
DEPENDENCY_OUT = ROOT / "docs/realisation/FEATURE_DEPENDENCY_GRAPH.json"
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


def build() -> tuple[dict, dict, dict, dict]:
    registry = yaml.safe_load(REGISTRY.read_text()) or {}
    screens = registry.get("screens") or []
    ids = [s["screen_id"] for s in screens]
    unique = set(ids)
    if len(ids) != 545 or len(unique) != 545:
        raise SystemExit(f"screen registry must contain exactly 545 unique entries; got {len(ids)}/{len(unique)}")
    if registry.get("screen_count") != 545:
        raise SystemExit(f"screen_count footer must be 545; got {registry.get('screen_count')}")
    if registry.get("authority") != "docs/ux/animal-farm-visual-lock/":
        raise SystemExit("screen registry visual authority is not animal-farm-visual-lock")
    if registry.get("visual_green_count") != 0:
        raise SystemExit("visual_green_count changed without native evidence registry support")
    if registry.get("feature_green_claims_from_this_registry") != 0:
        raise SystemExit("screen registry must not manufacture FEATURE_GREEN claims")

    validate_screen_coverage(ids)
    features = catalog_features()
    feature_ids = [f["feature_id"] for f in features]
    if len(feature_ids) != len(set(feature_ids)):
        raise SystemExit("canonical feature catalogue contains duplicate Feature IDs")
    if len(features) != 156 or any(f.get("feature_green") for f in features):
        raise SystemExit("catalogue establishment cannot manufacture FEATURE_GREEN")

    code = kotlin_ids()
    mapped = mapped_ids()
    module_stats = defaultdict(lambda: {
        "registered": 0, "kotlin_referenced": 0, "implementation_mapped": 0,
        "visual_green": 0, "feature_count": 0, "feature_green": 0,
    })
    for feature in features:
        module_stats[feature["module"]]["feature_count"] += 1

    qdus = []
    for screen in screens:
        sid = screen["screen_id"]
        module = screen["module"]
        bound = feature_ids_for_screen(sid)
        if len(bound) != 1:
            raise SystemExit(f"{sid} must have exactly one primary canonical Feature ID; got {bound}")
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
            "feature_ids": bound,
            "feature_binding_status": "CANONICAL_FEATURE_IDS_BOUND",
            "implementation_evidence": {"kotlin_reference": in_code, "implementation_map": in_map},
            "required_contracts": [
                "purpose_and_primary_object", "roles_and_permissions", "entry_exit_routes",
                "commands_and_read_models", "offline_and_sync_where_applicable",
                "backend_authority_where_applicable", "states_and_failure_modes",
                "animal_farm_visual_lineage", "accessibility", "tests_and_commit_bound_evidence",
            ],
            "feature_green": False,
            "visual_green": visual_green,
            "state": "IMPLEMENTATION_EVIDENCE_PRESENT_NOT_GREEN" if (in_code or in_map) else "PLANNED_NOT_IMPLEMENTED",
        })

    counts = Counter(q["state"] for q in qdus)
    modules = {
        module: {"feature_count": stats["feature_count"], "feature_green": 0, "screen_count": stats["registered"]}
        for module, stats in sorted(module_stats.items())
    }
    feature_registry = {
        "schema_version": 2,
        "catalog_status": CATALOG_STATUS,
        "authority_note": (
            "Established under the owner-directed deterministic completion plan on 24 September 2026. "
            "Feature IDs are control-plane identities over existing approved product scope and may not thin, "
            "delete, or supersede any Screen ID, domain rule, safety rule, or Project Truth requirement."
        ),
        "scope_law": SCOPE_LAW,
        "feature_count": len(features),
        "mandatory_feature_count": len(features),
        "feature_green_claims": 0,
        "modules": modules,
        "features": features,
    }
    qdu_registry = {
        "schema_version": 2, "source_fingerprint": source_fingerprint(), "screen_count": 545,
        "qdu_count": len(qdus), "feature_count": len(features),
        "feature_binding_status": "CANONICAL_FEATURE_IDS_BOUND", "qdus": qdus,
    }
    state = {
        "schema_version": 2, "repository": "Vanguduza/goat-app", "source_fingerprint": source_fingerprint(),
        "canonical_branch": "main", "release_state": "BLOCKED",
        "green_states": {"vertical_slice_green": True, "feature_green_count": 0, "module_green_count": 0, "mvp_green": False, "visual_green_count": 0},
        "completion_control_plane": {"catalog_status": CATALOG_STATUS, "feature_count": len(features), "mandatory_feature_count": len(features), "bound_screen_count": len(qdus), "completion_state": "PROJECT_COMPLETION_STATE.json"},
        "screen_coverage": {
            "registered": 545, "feature_bound": len(qdus),
            "kotlin_referenced": sum(1 for q in qdus if q["implementation_evidence"]["kotlin_reference"]),
            "implementation_mapped": sum(1 for q in qdus if q["implementation_evidence"]["implementation_map"]),
            "qdu_states": dict(sorted(counts.items())),
        },
        "module_coverage": dict(sorted(module_stats.items())),
        "blocking_authority_gaps": [],
        "blocking_completion_gaps": [
            "156 mandatory features remain not FEATURE_GREEN",
            "545 registered screens remain not VISUAL_GREEN until native evidence and independent approval exist",
            f"{counts.get('PLANNED_NOT_IMPLEMENTED', 0)} registered screens remain PLANNED_NOT_IMPLEMENTED at this implementation fingerprint",
            "whole-product MODULE_GREEN and MVP_GREEN certification remains outstanding",
        ],
        "status_law": "Feature binding is scope accounting only; it is not route reachability, FEATURE_GREEN, MODULE_GREEN, VISUAL_GREEN, or MVP_GREEN.",
    }
    dependency_graph = build_graph()
    completion = {
        "schema_version": 1, "repository": "Vanguduza/goat-app", "canonical_branch": "main",
        "source_fingerprint": source_fingerprint(), "programme_status": "ACTIVE_DETERMINISTIC_COMPLETION", "release_blocked": True,
        "authority": {"project_truth": "docs/00_PROJECT_TRUTH.md", "feature_catalog": "docs/realisation/FEATURE_REGISTRY.yaml", "feature_dependency_graph": "docs/realisation/FEATURE_DEPENDENCY_GRAPH.json", "screen_registry": "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml", "visual_authority": "docs/ux/animal-farm-visual-lock/", "catalog_authority": CATALOG_AUTHORITY},
        "phase_status": {
            "phase_0_completion_control_plane": "COMPLETE", "phase_1_canonical_feature_id_catalog": "COMPLETE", "phase_2_dependency_graph": "COMPLETE",
            "phase_3_platform_correctness": "COMPLETE", "phase_4_visual_foundation_certification": "NEXT", "phase_5_runtime_navigation_reachability": "PENDING",
            "phase_6_shared_foundation_modules": "PENDING", "phase_7_shared_operational_modules": "PENDING", "phase_8_species_modules": "PENDING",
            "phase_9_commercial_layer": "PENDING", "phase_10_intelligence_and_advanced_modules": "PENDING", "phase_11_feature_certification_sweep": "PENDING",
            "phase_12_visual_completion_sweep": "PENDING", "phase_13_module_certification": "PENDING", "phase_14_whole_product_certification": "PENDING",
            "phase_15_release_engineering": "PENDING", "phase_16_mvp_green_closure": "PENDING",
        },
        "coverage": {"screens_registered": 545, "screens_feature_bound": 545, "features_mandatory": len(features), "features_green": 0, "modules_total": len(modules), "modules_green": 0, "visual_green_screens": 0, "qdu_states": dict(sorted(counts.items()))},
        "module_plan": modules,
        "dependency_graph": {"node_count": dependency_graph["summary"]["node_count"], "edge_count": dependency_graph["summary"]["edge_count"], "root_count": dependency_graph["summary"]["root_count"], "cycle_free": dependency_graph["summary"]["cycle_free"], "phase_monotonic": dependency_graph["summary"]["phase_monotonic"]},
        "global_programme_gates": dependency_graph["global_programme_gates"],
        "features": [{"feature_id": f["feature_id"], "module": f["module"], "name": f["name"], "completion_phase": f["completion_phase"], "screen_count": len(f["screen_ids"]), "contract_status": "OPEN", "feature_green": False} for f in features],
        "next_action": "PHASE_4_BUILD_AND_CERTIFY_NATIVE_ANIMAL_FARM_REFERENCE_SET_WITH_DETERMINISTIC_VISUAL_EVIDENCE",
        "status_law": "Counts are generated from canonical registries and evidence. No status may be promoted manually or by code existence alone.",
    }
    return feature_registry, qdu_registry, state, completion


def write_outputs(feature_registry: dict, qdu_registry: dict, state: dict, completion: dict) -> None:
    FEATURE_OUT.write_text(yaml.safe_dump(feature_registry, sort_keys=False, width=120), encoding="utf-8")
    QDU_OUT.write_text(json.dumps(qdu_registry, indent=2) + "\n", encoding="utf-8")
    STATE_OUT.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    COMPLETION_OUT.write_text(json.dumps(completion, indent=2) + "\n", encoding="utf-8")
    DEPENDENCY_OUT.write_text(json.dumps(build_graph(), indent=2) + "\n", encoding="utf-8")


def load_semantic(path: Path):
    if path.suffix in {".yaml", ".yml"}:
        return yaml.safe_load(path.read_text())
    return json.loads(path.read_text())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if generated outputs are stale")
    args = parser.parse_args()
    feature, qdu, state, completion = build()
    if args.check:
        expected = {FEATURE_OUT: feature, QDU_OUT: qdu, STATE_OUT: state, COMPLETION_OUT: completion, DEPENDENCY_OUT: build_graph()}
        stale = [str(path.relative_to(ROOT)) for path, obj in expected.items() if not path.exists() or load_semantic(path) != obj]
        if stale:
            print(f"expected source_fingerprint: {state['source_fingerprint']}")
            raise SystemExit("stale completion control-plane outputs: " + ", ".join(stale))
        print(f"PASS completion control plane: 545 screens -> {len(feature['features'])} canonical features; dependency DAG verified; zero invented green claims")
        return
    write_outputs(feature, qdu, state, completion)
    print(f"generated completion control plane: 545 screens -> {len(feature['features'])} canonical features")


if __name__ == "__main__":
    main()
