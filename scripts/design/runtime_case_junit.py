"""Read actual additional-case JUnit bytes and retained CI checkout/task evidence.

This parser checks consistency; the caller must separately verify first-party CI origin
and the complete run result. It does not authenticate approvals or certify a product.
"""
from __future__ import annotations

import datetime as dt
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from runtime_navigation_cases import COMMIT, digest, inside, require

TASK = ":app:testDebugUnitTest"


def utc(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(parsed.tzinfo is not None, "execution timestamp needs an explicit timezone")
    return parsed.astimezone(dt.timezone.utc)


def verify_junit_cases(catalog: dict, root: Path, evidence_root: Path, task_log: Path, run_id: int) -> dict:
    require(type(run_id) is int and run_id > 0, "additional case execution needs its verified Foundation run ID")
    current_status = subprocess.check_output(["git", "status", "--porcelain=v1"], cwd=root, text=True)
    require(not current_status.strip(), "passing runtime evidence requires a clean current candidate")
    identity = evidence_root / "app/build/test-evidence"
    checkout = (identity / "commit.txt").read_text().strip()
    tree = (identity / "tree.txt").read_text().strip()
    require(COMMIT.fullmatch(checkout) is not None and COMMIT.fullmatch(tree) is not None, "invalid retained checkout identity")
    require(not (identity / "worktree-status.txt").read_text().strip(), "CI checkout contained uncommitted source")
    candidate = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    candidate_tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=root, text=True).strip()
    require(tree == candidate_tree, "test artifact belongs to a different candidate tree")
    for source in catalog["sources"]:
        committed = subprocess.check_output(["git", "show", f"{candidate}:{source['path']}"], cwd=root)
        require(digest(committed) == source["sha256"], "mapped tests differ from committed candidate")
    committed_map = subprocess.check_output(["git", "show", f"{candidate}:{catalog['mapping_path']}"], cwd=root)
    require(digest(committed_map) == catalog["mapping_sha256"], "case map differs from committed candidate")

    raw_log = task_log.read_bytes()
    log = re.sub(r"\x1b\[[0-9;]*m", "", raw_log.decode("utf-8"))
    require(checkout in log, "test log does not identify retained CI checkout")
    outcomes = re.findall(r"> Task " + re.escape(TASK) + r"(?: ([A-Z][A-Z-]*))?\s*$", log, re.MULTILINE)
    require(outcomes == [""], "additional cases need one executed task; cached, up-to-date, skipped or failed task output is not new execution")
    stamps = re.findall(r"(?m)^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\s", log)
    require(len(stamps) >= 2, "retained CI log needs its timestamped execution window")
    start, finish = min(map(utc, stamps)), max(map(utc, stamps))
    require(start < finish, "invalid CI execution window")

    expected = {}
    for case in catalog["cases"]:
        expected.setdefault(case["class_name"], set()).add(case["method"])
    results, reports = [], []
    for source in catalog["sources"]:
        class_name = source["class_name"]
        relative = f"app/build/test-results/testDebugUnitTest/TEST-{class_name}.xml"
        path = inside(evidence_root, relative)
        require(path.is_file(), f"missing JUnit class report: {class_name}")
        raw = path.read_bytes()
        require(len(raw) <= 20_000_000 and b"<!DOCTYPE" not in raw and b"<!ENTITY" not in raw, "unsupported JUnit XML")
        report = ET.fromstring(raw)
        require(report.tag == "testsuite" and report.get("name") == class_name, "JUnit suite identity mismatch")
        require(all(report.get(key) == "0" for key in ("failures", "errors", "skipped")), "JUnit suite has failed, skipped or unclassified cases")
        timestamp = report.get("timestamp", "")
        require(start <= utc(timestamp) <= finish, "JUnit results were not produced inside this CI log window")
        cases = report.findall("testcase")
        require(report.get("tests") == str(len(cases)) and bool(cases), "JUnit case count is inconsistent")
        identities = [(case.get("classname"), case.get("name")) for case in cases]
        require(len(identities) == len(set(identities)), "duplicate JUnit case")
        require(set(identities) == {(class_name, method) for method in expected[class_name]}, "missing or unreviewed JUnit method identity")
        for case in cases:
            require(not any(case.find(tag) is not None for tag in ("failure", "error", "skipped")), "JUnit case failed or was skipped")
            results.append({"class_name": class_name, "method": case.get("name"), "status": "PASS", "junit_path": relative, "junit_sha256": digest(raw), "suite_timestamp": timestamp})
        reports.append({"path": relative, "sha256": digest(raw), "tests": len(cases), "timestamp": timestamp})
    require(len(results) == catalog["expected_case_count"], "additional execution does not cover mapped cases")
    return {
        "schema_version": 1,
        "foundation_run_id": run_id,
        "candidate_commit": candidate,
        "candidate_tree": candidate_tree,
        "actual_checkout_commit": checkout,
        "actual_checkout_tree": tree,
        "test_task": TASK,
        "task_outcome": "EXECUTED",
        "task_log_sha256": digest(raw_log),
        "execution_window_utc": {"start": start.isoformat(), "finish": finish.isoformat()},
        "reports": reports,
        "cases": sorted(results, key=lambda case: (case["class_name"], case["method"])),
        "origin_verification": "FIRST_PARTY_CI_ORIGIN_AND_RUN_ACCEPTANCE_REQUIRED_SEPARATELY",
        "limits": "JVM/Room/Compose case evidence. No physical-device, live-provider, visual approval or full-product qualification.",
    }
