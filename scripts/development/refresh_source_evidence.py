#!/usr/bin/env python3
"""Refresh source inventories with zero new runtime or product certification.

See docs/realisation/COMPLETION_CONTROL_PLANE.md. This helper cannot promote CI.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import runpy
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / "docs/ux/evidence/animal-farm-visual-lock/phase5-runtime-navigation-ledger.json"
HISTORY = RUNTIME.parent / "historical" / "phase5-runtime-navigation"
PROTECTED = (
    ROOT / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml",
    ROOT / "docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml",
    ROOT / "docs/ux/evidence/animal-farm-visual-lock/phase4-native-reference-ledger.json",
    ROOT / "docs/realisation/VERTICAL_SLICE_GATE.json",
)
RUNTIME_SCRIPT = "scripts/design/build-runtime-navigation-evidence.py"
EXPORT_SCRIPT = "scripts/design/export-runtime-route-ownership.py"
QUANTUM_SCRIPT = "scripts/development/verify_quantum_control_plane.py"
AUDIT_SCRIPT = "scripts/design/audit-navigation.cjs"
GAP_SCRIPT = "scripts/design/build-route-screen-feature-gap.py"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def verify_pending(ledger: dict) -> None:
    require(ledger.get("ci_status") == "CI_PENDING", "candidate refresh requires CI_PENDING")
    require(ledger.get("foundation_run_id") is None, "pending evidence must not carry a CI run")
    certification = ledger["certification"]
    for key in (
        "runtime_reachability_executed",
        "entry_action_emission_executed",
        "rendered_surface_owners_executed",
        "route_contracts_executed",
        "return_restoration_executed",
    ):
        require(certification.get(key) == 0, f"pending evidence claims {key}")
    require(certification.get("parameter_scope_executed") is False, "pending parameter certification")
    require(certification.get("deep_links") == "NOT_CLAIMED_BY_THIS_EVIDENCE", "pending deep-link certification")
    for key in ("visual_green_screens", "feature_green", "module_green", "mvp_green"):
        require(ledger["green_claims"].get(key) == 0, f"refresh claims {key}")


def preserve_passing_ledger(source: Path, history: Path) -> Path | None:
    """Keep proven bytes and their original source/CI pins; never relabel them."""
    raw = source.read_bytes()
    ledger = json.loads(raw)
    status = ledger.get("ci_status")
    require(status in {"CI_PENDING", "PASS_EXACT_HEAD_CI"}, "unrecognized prior runtime evidence")
    if status == "CI_PENDING":
        return None
    commit = ledger.get("tested_commit", "")
    fingerprint = ledger.get("source_fingerprint", "")
    run_id = ledger.get("foundation_run_id")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", commit)), "passing history needs its original commit")
    require(bool(re.fullmatch(r"sha256:[0-9a-f]{64}", fingerprint)), "passing history needs its original source fingerprint")
    require(type(run_id) is int and run_id > 0, "passing history needs its original CI run")
    digest = hashlib.sha256(raw).hexdigest()
    destination = history / f"{commit}-{digest}.json"
    history.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        require(destination.read_bytes() == raw, f"historical evidence collision at {destination}")
    else:
        with destination.open("xb") as output:
            output.write(raw)
    return destination


def run(*command: str) -> None:
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def source_identity() -> tuple[str, str]:
    # Reuse the canonical generator's fingerprint, including production and test Kotlin.
    namespace = runpy.run_path(str(ROOT / RUNTIME_SCRIPT))
    return namespace["git_head"](), namespace["source_fingerprint"]()


def verify_zero_current_claims() -> None:
    verify_pending(read_json(RUNTIME))
    route = read_json(RUNTIME.parent / "runtime-route-export.json")
    feature = read_json(ROOT / "docs/realisation/FEATURE_ROUTE_COVERAGE.json")
    require(route["certification_status"] == "CI_PENDING", "route evidence was promoted")
    require(route["registry_screen_count"] == 545, "screen identities were removed")
    require(route["route_screen_count"] == 0 and route["screen_ids"] == [], "current route claims are nonzero")
    require(route["unresolved_screen_count"] == 545 and route["coverage_complete"] is False, "current routes were certified")
    require(feature["certification_status"] == "CI_PENDING", "feature route evidence was promoted")
    require(feature["feature_count"] == 156 and feature["mandatory_feature_count"] == 156, "mandatory feature scope changed")
    require(feature["features_with_route_evidence"] == 0 and feature["features_without_route_evidence"] == 156, "current feature route claims are nonzero")
    require(feature["explicit_headless_contracts"] == 0 and feature["route_gate_ready"] is False, "route exceptions or readiness were invented")
    require(all(not row["feature_green"] for row in feature["features"]), "feature promotion is forbidden")
    completion = read_json(ROOT / "PROJECT_COMPLETION_STATE.json")
    require(completion["release_blocked"] is True, "candidate refresh cannot unblock release")
    for key in ("features_green", "modules_green", "visual_green_screens"):
        require(completion["coverage"][key] == 0, f"candidate refresh claims {key}")
    require(completion["runtime_navigation"]["status"] == "PENDING", "runtime phase was promoted")
    gap = read_json(RUNTIME.parent / "route-screen-feature-gap-summary.json")
    require(gap["registry_rows"] == 545 and gap["runtime_evidence"] is None, "gap inventory certified runtime evidence")
    for key in ("runtime_reachability_executed", "rendered_surface_owners_executed", "entry_action_emission_executed", "route_contracts_executed"):
        require(gap[key] == 0, f"gap inventory claims {key}")


def check() -> None:
    run(sys.executable, "scripts/development/test_source_evidence.py")
    run(sys.executable, RUNTIME_SCRIPT, "--self-test")
    run(sys.executable, EXPORT_SCRIPT, "--self-test", "--check")
    run(sys.executable, QUANTUM_SCRIPT, "--check")
    run(sys.executable, "scripts/development/feature_dependencies.py", "--check")
    run("node", AUDIT_SCRIPT, "--self-test", "--quiet")
    run(sys.executable, GAP_SCRIPT, "--self-test")
    verify_zero_current_claims()
    print("PASS current source inventories are consistent; zero new runtime or product certification")


def refresh() -> None:
    before = source_identity()
    protected = {path: path.read_bytes() for path in PROTECTED}
    archived = preserve_passing_ledger(RUNTIME, HISTORY)
    if archived:
        print(f"Preserved prior passing ledger unchanged: {archived.relative_to(ROOT)}")
    run(sys.executable, RUNTIME_SCRIPT, "--status", "CI_PENDING")
    verify_pending(read_json(RUNTIME))
    # Bootstrap the source fingerprint before the control-plane verifier reads route data.
    # A second pass consumes the newly generated canonical feature catalogue.
    for _ in range(2):
        run(sys.executable, EXPORT_SCRIPT, "--write")
        run(sys.executable, QUANTUM_SCRIPT)
    run("node", AUDIT_SCRIPT, "--out", str(RUNTIME.parent / "navigation-source-audit.json"), "--quiet")
    run(sys.executable, GAP_SCRIPT)
    require(source_identity() == before, "source changed during refresh; freeze edits and rerun")
    require(all(path.read_bytes() == raw for path, raw in protected.items()), "an authority or historical evidence input changed")
    check()


def self_test() -> None:
    """Exercise the evidence-safety boundaries using temporary files only."""
    pending = {
        "ci_status": "CI_PENDING", "foundation_run_id": None,
        "certification": {
            "runtime_reachability_executed": 0, "entry_action_emission_executed": 0,
            "rendered_surface_owners_executed": 0, "route_contracts_executed": 0,
            "return_restoration_executed": 0, "parameter_scope_executed": False,
            "deep_links": "NOT_CLAIMED_BY_THIS_EVIDENCE",
        },
        "green_claims": {"visual_green_screens": 0, "feature_green": 0, "module_green": 0, "mvp_green": False},
    }
    verify_pending(pending)
    for mutate in (
        lambda data: data.update(ci_status="PASS_EXACT_HEAD_CI"),
        lambda data: data.update(foundation_run_id=1),
        lambda data: data["certification"].update(runtime_reachability_executed=1),
        lambda data: data["certification"].update(parameter_scope_executed=True),
        lambda data: data["green_claims"].update(feature_green=1),
    ):
        invalid = json.loads(json.dumps(pending))
        mutate(invalid)
        try:
            verify_pending(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("unsafe pending certification was accepted")
    with tempfile.TemporaryDirectory(prefix="farm-evidence-history-test-") as temp:
        base = Path(temp)
        ledger = base / "ledger.json"
        ledger.write_text(json.dumps(pending), encoding="utf-8")
        require(preserve_passing_ledger(ledger, base / "history") is None, "pending history was promoted")
        proven = dict(pending, ci_status="PASS_EXACT_HEAD_CI", foundation_run_id=17,
                      tested_commit="a" * 40, source_fingerprint="sha256:" + "b" * 64)
        raw = (json.dumps(proven, indent=2) + "\n").encode()
        ledger.write_bytes(raw)
        archived = preserve_passing_ledger(ledger, base / "history")
        require(archived is not None and archived.read_bytes() == raw, "history bytes or pins changed")
        require(preserve_passing_ledger(ledger, base / "history") == archived, "history preservation is not idempotent")
        archived.write_text("conflicting history", encoding="utf-8")
        try:
            preserve_passing_ledger(ledger, base / "history")
        except ValueError:
            pass
        else:
            raise AssertionError("existing historical evidence was overwritten")
        proven["tested_commit"] = "../../unverified"
        ledger.write_text(json.dumps(proven), encoding="utf-8")
        try:
            preserve_passing_ledger(ledger, base / "history")
        except ValueError:
            pass
        else:
            raise AssertionError("unbound historical evidence was accepted")
    print("PASS refresh safety: pending claims rejected; history preserved with original pins; collisions refused")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="refresh only CI_PENDING derived inventories after a source freeze")
    mode.add_argument("--check", action="store_true", help="run existing static gates and require zero current certification")
    mode.add_argument("--self-test", action="store_true", help="test safety guards using temporary files, without refreshing repository outputs")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    elif args.write:
        refresh()
    else:
        check()


if __name__ == "__main__":
    main()
