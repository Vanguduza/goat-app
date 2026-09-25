#!/usr/bin/env python3
"""Generate the Phase 5 actual route-ownership export and feature coverage.

This exporter is deliberately conservative. A Screen ID enters screen_ids only
when it is backed by exact-head CI evidence from the Phase 5 runtime ledger.
Registry membership or a static implementation-map hint is never sufficient.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml"
IMPL_MAP = ROOT / "docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml"
FEATURES = ROOT / "docs/realisation/FEATURE_REGISTRY.yaml"
RUNTIME = ROOT / "docs/ux/evidence/animal-farm-visual-lock/phase5-runtime-navigation-ledger.json"
VERIFY_PACK = ROOT / "docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs"
ROUTE_OUT = ROOT / "docs/ux/evidence/animal-farm-visual-lock/runtime-route-export.json"
FEATURE_OUT = ROOT / "docs/realisation/FEATURE_ROUTE_COVERAGE.json"
SCREEN_ID = re.compile(r"FOS-[A-Z]+(?:-[0-9]+)+(?:-[A-Z])?")


def registry_ids() -> set[str]:
    return set(SCREEN_ID.findall(REGISTRY.read_text(encoding="utf-8")))


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def source_fingerprint() -> str:
    digest = hashlib.sha256()
    inputs = [REGISTRY, IMPL_MAP]
    for base in ("app", "core", "data", "domain", "feature"):
        inputs.extend(sorted((ROOT / base).rglob("*.kt"), key=lambda path: path.relative_to(ROOT).as_posix()))
    for path in inputs:
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(b"\0")
        canonical = path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n")
        digest.update(canonical)
        digest.update(b"\0")
    return "sha256:" + digest.hexdigest()


def payloads() -> tuple[dict, dict]:
    registry = registry_ids()
    features = load_json(FEATURES)["features"]
    runtime = load_json(RUNTIME)
    current_fingerprint = source_fingerprint()
    ci_status = runtime.get("ci_status")
    if ci_status not in {"CI_PENDING", "PASS_EXACT_HEAD_CI"}:
        raise SystemExit(f"unsupported runtime evidence state: {ci_status}")
    if runtime.get("source_fingerprint") != current_fingerprint:
        raise SystemExit("runtime route evidence source fingerprint is stale")
    certified = ci_status == "PASS_EXACT_HEAD_CI"

    proof_sources: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    kind_to_class = {
        "rendered_destination_traversal": "RENDERED_DESTINATION_TRAVERSAL",
        "rendered_surface_owner": "RENDERED_SURFACE_OWNER",
        "entry_action_emission": "ENTRY_ACTION_DESTINATION",
        "route_contract": "TYPED_ROUTE_OWNER",
    }
    if certified:
        for kind, source_map in runtime["test_sources"].items():
            proof_class = kind_to_class[kind]
            for source, ids in source_map.items():
                for sid in ids:
                    proof_sources[sid][proof_class].add(source)

    route_ids = sorted(proof_sources)
    unknown = sorted(set(route_ids) - registry)
    if unknown:
        raise SystemExit(f"route export contains unregistered Screen IDs: {unknown}")

    records = []
    for sid in route_ids:
        classes = proof_sources[sid]
        records.append(
            {
                "screen_id": sid,
                "proof_classes": sorted(classes),
                "evidence_sources": {
                    proof_class: sorted(paths)
                    for proof_class, paths in sorted(classes.items())
                },
            }
        )

    unresolved = sorted(registry - set(route_ids))
    route_export = {
        "schema_version": 2,
        "evidence_class": "CI_PROVEN_ROUTE_OWNERSHIP_SUBSET" if certified else "CI_PENDING_ZERO_CLAIM_ROUTE_EXPORT",
        "certification_status": ci_status,
        "source_fingerprint": runtime["source_fingerprint"],
        "runtime_evidence_tested_commit": runtime["tested_commit"],
        "runtime_evidence_run_id": runtime["foundation_run_id"],
        "registry_screen_count": len(registry),
        "route_screen_count": len(route_ids),
        "unresolved_screen_count": len(unresolved),
        "coverage_complete": len(unresolved) == 0,
        "screen_ids": route_ids,
        "routes": records,
        "unresolved_registry_screen_ids": unresolved,
        "aliases": [],
        "alias_status": "NONE_CI_PROVEN",
        "law": (
            "screen_ids contains only exact-head CI-proven rendered traversals, rendered surface "
            "owners, entry-action destinations, or typed route owners. CI_PENDING exports contain zero "
            "certified route IDs. Registry membership alone is forbidden. "
            "This export must fail verify-pack --routes until all 545 Screen IDs have actual "
            "navigation/composable ownership evidence."
        ),
    }

    feature_rows = []
    with_route = 0
    fully_route_bound = 0
    without_route = 0
    for feature in features:
        bound = sorted(feature["screen_ids"])
        routed = sorted(set(bound) & set(route_ids))
        missing = sorted(set(bound) - set(route_ids))
        if routed and not missing:
            state = "ALL_BOUND_SCREENS_ROUTE_PROVEN"
            with_route += 1
            fully_route_bound += 1
        elif routed:
            state = "PARTIAL_ROUTE_COVERAGE"
            with_route += 1
        else:
            state = "NO_ROUTE_COVERAGE"
            without_route += 1
        feature_rows.append(
            {
                "feature_id": feature["feature_id"],
                "module": feature["module"],
                "name": feature["name"],
                "mandatory": bool(feature["mandatory"]),
                "screen_ids": bound,
                "route_screen_ids": routed,
                "unresolved_screen_ids": missing,
                "headless_contract": None,
                "coverage_state": state,
                "feature_green": False,
            }
        )

    feature_export = {
        "schema_version": 2,
        "catalog_status": "ROUTE_COVERAGE_IN_PROGRESS" if certified else "ROUTE_EVIDENCE_PENDING_CI",
        "certification_status": ci_status,
        "source_fingerprint": runtime["source_fingerprint"],
        "feature_count": len(feature_rows),
        "mandatory_feature_count": sum(1 for row in feature_rows if row["mandatory"]),
        "features_with_route_evidence": with_route,
        "features_fully_route_bound": fully_route_bound,
        "features_without_route_evidence": without_route,
        "explicit_headless_contracts": 0,
        "route_gate_ready": len(unresolved) == 0 and without_route == 0,
        "features": feature_rows,
        "law": (
            "No headless contract is inferred from missing UI. A feature is headless only when "
            "an explicit reviewed contract says so. Route evidence does not imply FEATURE_GREEN."
        ),
    }
    return route_export, feature_export


def canonical(data: dict) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def verify_invariants(route_export: dict, feature_export: dict) -> None:
    assert route_export["registry_screen_count"] == 545
    assert route_export["route_screen_count"] == len(route_export["screen_ids"])
    assert route_export["route_screen_count"] + route_export["unresolved_screen_count"] == 545
    assert len(route_export["screen_ids"]) == len(set(route_export["screen_ids"]))
    assert feature_export["feature_count"] == 156
    assert feature_export["mandatory_feature_count"] == 156
    assert feature_export["explicit_headless_contracts"] == 0
    assert all(not row["feature_green"] for row in feature_export["features"])
    assert all(row["headless_contract"] is None for row in feature_export["features"])


def verify_pack_fail_closed(route_export: dict) -> None:
    with tempfile.TemporaryDirectory(prefix="farm-route-export-") as temp:
        path = Path(temp) / "routes.json"
        path.write_text(canonical(route_export), encoding="utf-8")
        proc = subprocess.run(
            [
                "node",
                str(VERIFY_PACK),
                "--registry",
                str(REGISTRY),
                "--routes",
                str(path),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if route_export["coverage_complete"]:
            if proc.returncode != 0:
                raise SystemExit(f"complete route export rejected by verify-pack: {proc.stderr}{proc.stdout}")
        else:
            combined = proc.stdout + proc.stderr
            if proc.returncode == 0 or "Route/registry coverage mismatch" not in combined:
                raise SystemExit("incomplete route export did not fail closed at verify-pack --routes")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    route_export, feature_export = payloads()
    verify_invariants(route_export, feature_export)

    if args.self_test:
        verify_pack_fail_closed(route_export)
        print(
            "PASS route ownership self-test: "
            f"{route_export['route_screen_count']}/545 CI-proven routes; "
            f"{feature_export['features_with_route_evidence']}/156 features have route evidence; "
            f"{feature_export['features_without_route_evidence']} features remain without route evidence"
        )

    if args.write:
        ROUTE_OUT.parent.mkdir(parents=True, exist_ok=True)
        FEATURE_OUT.parent.mkdir(parents=True, exist_ok=True)
        ROUTE_OUT.write_text(canonical(route_export), encoding="utf-8")
        FEATURE_OUT.write_text(canonical(feature_export), encoding="utf-8")
        print(f"wrote {ROUTE_OUT.relative_to(ROOT)}")
        print(f"wrote {FEATURE_OUT.relative_to(ROOT)}")

    if args.check:
        expected = {
            ROUTE_OUT: canonical(route_export),
            FEATURE_OUT: canonical(feature_export),
        }
        stale = [str(path.relative_to(ROOT)) for path, text in expected.items() if not path.exists() or path.read_text(encoding="utf-8") != text]
        if stale:
            raise SystemExit("stale route ownership outputs: " + ", ".join(stale))
        print("PASS route ownership outputs are current")

    if not (args.write or args.check or args.self_test):
        parser.error("choose --write, --check, or --self-test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
