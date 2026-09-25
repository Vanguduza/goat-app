#!/usr/bin/env python3
"""Build deterministic Phase 5 runtime-navigation evidence from executable tests."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml"
UI_TESTS = (
    "app/src/test/java/com/farmos/app/FarmRuntimeNavigationTest.kt",
    "app/src/test/java/com/farmos/app/RabbitRuntimeNavigationTest.kt",
    "app/src/test/java/com/farmos/app/OperationalRuntimeNavigationTest.kt",
)
ROUTE_CONTRACT_TESTS = (
    "app/src/test/java/com/farmos/app/FarmRuntimeRouteTest.kt",
)
SCREEN_ID = re.compile(r"FOS-[A-Z]+(?:-[0-9]+)+(?:-[A-Z])?")


def ids_in(paths: tuple[str, ...]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for rel in paths:
        text = (ROOT / rel).read_text(encoding="utf-8")
        result[rel] = sorted(set(SCREEN_ID.findall(text)))
    return result


def registry_ids() -> set[str]:
    # The registry is canonical YAML, but Screen IDs are lexical identifiers.
    # Extracting them directly keeps this verifier dependency-free in CI.
    return set(SCREEN_ID.findall(REGISTRY.read_text(encoding="utf-8")))


def git_head() -> str:
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()


def build(status: str, run_id: int | None) -> dict:
    ui_sources = ids_in(UI_TESTS)
    contract_sources = ids_in(ROUTE_CONTRACT_TESTS)
    ui_ids = sorted({x for values in ui_sources.values() for x in values})
    contract_ids = sorted({x for values in contract_sources.values() for x in values})
    all_ids = sorted(set(ui_ids) | set(contract_ids))
    known = registry_ids()
    missing = sorted(set(all_ids) - known)

    return {
        "schema_version": 1,
        "phase": "phase_5_runtime_navigation_reachability",
        "evidence_class": "EXECUTABLE_TEST_DERIVED_RUNTIME_NAVIGATION",
        "tested_commit": git_head(),
        "ci_status": status,
        "foundation_run_id": run_id,
        "test_sources": {
            "ui_traversal": ui_sources,
            "route_contract": contract_sources,
        },
        "coverage": {
            "registered_screen_count": len(known),
            "ui_traversal_screen_count": len(ui_ids),
            "route_contract_screen_count": len(contract_ids),
            "combined_screen_count": len(all_ids),
            "ui_traversal_screen_ids": ui_ids,
            "route_contract_screen_ids": contract_ids,
            "route_contract_only_screen_ids": sorted(set(contract_ids) - set(ui_ids)),
            "missing_registry_ids": missing,
        },
        "certification": {
            "runtime_reachability_executed": len(ui_ids) if status == "PASS_EXACT_HEAD_CI" else 0,
            "route_contracts_executed": len(contract_ids) if status == "PASS_EXACT_HEAD_CI" else 0,
            "parameter_scope_executed": status == "PASS_EXACT_HEAD_CI" and "FOS-TASK-003" in contract_ids,
            "return_restoration_executed": len(ui_ids) if status == "PASS_EXACT_HEAD_CI" else 0,
            "deep_links": "NOT_CLAIMED_BY_THIS_EVIDENCE",
        },
        "green_claims": {
            "visual_green_screens": 0,
            "feature_green": 0,
            "module_green": 0,
            "mvp_green": False,
        },
        "law": (
            "A Screen ID is runtime-reachable here only when an executable Compose traversal "
            "test names it and the recorded exact-head CI run passes. Route-contract tests are "
            "reported separately and never substitute for UI traversal. This evidence makes no "
            "VISUAL_GREEN, FEATURE_GREEN, MODULE_GREEN, or MVP_GREEN claim."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="docs/ux/evidence/animal-farm-visual-lock/phase5-runtime-navigation-ledger.json")
    parser.add_argument("--status", choices=("CI_PENDING", "PASS_EXACT_HEAD_CI"), default="CI_PENDING")
    parser.add_argument("--run-id", type=int)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.status == "PASS_EXACT_HEAD_CI" and args.run_id is None:
        parser.error("--run-id is required for PASS_EXACT_HEAD_CI")

    payload = build(args.status, args.run_id)
    if payload["coverage"]["missing_registry_ids"]:
        raise SystemExit(f"unregistered screen ids in runtime evidence: {payload['coverage']['missing_registry_ids']}")
    if payload["coverage"]["ui_traversal_screen_count"] < 80:
        raise SystemExit("runtime UI traversal evidence unexpectedly below 80 Screen IDs")
    if payload["coverage"]["route_contract_screen_count"] < 30:
        raise SystemExit("runtime route-contract evidence unexpectedly below 30 Screen IDs")

    if args.self_test:
        print(
            "PASS runtime navigation evidence self-test: "
            f"{payload['coverage']['ui_traversal_screen_count']} UI-traversed / "
            f"{payload['coverage']['route_contract_screen_count']} route-contract Screen IDs"
        )
        return 0

    out = ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
