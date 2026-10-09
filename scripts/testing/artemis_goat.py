#!/usr/bin/env python3
"""Prepare governed GOAT Artemis requests and inspect sealed Hermes test evidence.

This helper never invokes Artemis, installs an APK, reads credentials, changes host admission,
or issues a product/visual approval. Execute prepared requests through dial_android_testing.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
CONTROL_HOME = Path("/var/lib/dial-control")
REPOSITORY = "Vanguduza/goat-app"
PACKAGE = "com.farmos.app"
EVIDENCE_AUTHORITY = "TEST_EVIDENCE_NON_AUTHORITATIVE_UNTIL_RECONCILED"
SERIAL = re.compile(r"[A-Za-z0-9._:-]{1,160}")
TRACE = re.compile(r"[a-z0-9][a-z0-9._:-]{0,199}")
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def text_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink(), "Expected a regular JSON evidence file")
    require(path.stat().st_size <= 4 * 1024 * 1024, "JSON evidence exceeds the bounded format")
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), "Expected a JSON object")
    return value


def contained_file(root: Path, value: str) -> Path:
    base = root.resolve()
    candidate = Path(value)
    raw = candidate if candidate.is_absolute() else base / candidate
    require(raw.is_file() and not raw.is_symlink(), "Evidence or APK is not a regular file")
    resolved = raw.resolve()
    require(resolved.is_relative_to(base) and resolved == raw.absolute(),
            "Evidence or APK path escapes its approved directory or follows a symlink")
    return resolved


def command(*args: str, cwd: Path | None = None) -> str:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=True,
                          timeout=60).stdout.strip()


def source_identity(repo: Path, expected: str) -> dict:
    require(bool(HEX40.fullmatch(expected)), "Supply the full expected commit SHA")
    repo = repo.resolve()
    require(command("git", "rev-parse", "--show-toplevel", cwd=repo) == str(repo),
            "Unexpected repository root")
    remote = command("git", "remote", "get-url", "origin", cwd=repo)
    require(remote in (f"https://github.com/{REPOSITORY}.git",
                       f"https://github.com/{REPOSITORY}", f"git@github.com:{REPOSITORY}.git"),
            "Origin must identify GOAT without embedded credentials")
    require(command("git", "rev-parse", "HEAD", cwd=repo) == expected, "Unexpected candidate commit")
    require(not command("git", "status", "--porcelain=v1", "--untracked-files=all", cwd=repo),
            "Commit or isolate all source changes before preparing a request")
    return {"repository": REPOSITORY, "commit": expected,
            "tree": command("git", "rev-parse", "HEAD^{tree}", cwd=repo),
            "ref": command("git", "symbolic-ref", "--quiet", "--short", "HEAD", cwd=repo)}


def inspect_apk(apk: Path, sdk: Path, build_tools: str, certificate_sha1: str) -> dict:
    require(bool(re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}", build_tools)), "Invalid SDK build-tools version")
    tools = sdk.resolve() / "build-tools" / build_tools
    for name in ("apksigner", "aapt2"):
        require((tools / name).is_file(), f"Required SDK inspection tool is missing: {name}")
    signature = command(str(tools / "apksigner"), "verify", "--verbose", "--print-certs",
                        "--print-certs-pem", str(apk))
    badging = command(str(tools / "aapt2"), "dump", "badging", str(apk))
    # Reuse the canonical test-build APK parser and public-certificate checks.
    spec = importlib.util.spec_from_file_location("goat_test_build", ROOT / "scripts/testing/prepare-google-test-build.py")
    require(spec is not None and spec.loader is not None, "Canonical APK parser is unavailable")
    parser = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parser)
    identity, _ = parser.apk_identity(signature, badging, parser.fingerprint(certificate_sha1, 40))
    return identity


def prepare_request(repo: Path, expected_commit: str, apk_relative: str, provenance_path: Path,
                    sdk: Path, build_tools: str, certificate_sha1: str,
                    scenario_id: str, device_serial: str) -> dict:
    require(bool(SERIAL.fullmatch(device_serial)), "Invalid device serial")
    require(not Path(apk_relative).is_absolute(), "Supply an APK path relative to the GOAT checkout")
    before = source_identity(repo, expected_commit)
    apk = contained_file(repo, apk_relative)
    require(apk.suffix == ".apk" and apk.stat().st_size > 0, "Expected a nonempty APK")
    digest = sha256(apk)
    provenance = read_json(provenance_path)
    provenance_digest = sha256(provenance_path)
    require(provenance.get("schema_version") == 1 and
            provenance.get("purpose") == "OWNER_AUTHORIZED_TESTING_ONLY", "Unsupported build provenance")
    for key in ("repository", "commit", "tree"):
        require(provenance.get("source", {}).get(key) == before[key], "APK build/source identity differs")
    artifact = provenance.get("artifact", {})
    require(artifact.get("sha256") == digest and artifact.get("size_bytes") == apk.stat().st_size,
            "APK bytes differ from the retained build provenance")
    application = inspect_apk(apk, sdk, build_tools, certificate_sha1)
    require(provenance.get("application") == application, "Actual APK identity differs from build provenance")
    manifest_path = repo / "scripts/testing/artemis_scenarios.json"
    manifest = read_json(manifest_path)
    require(manifest.get("schema_version") == 1 and manifest.get("package_name") == PACKAGE,
            "Unsupported GOAT scenario manifest")
    scenarios = [x for x in manifest.get("scenarios", []) if x.get("id") == scenario_id]
    require(len(scenarios) == 1, "Select exactly one repository-defined scenario")
    scenario = scenarios[0]
    assertions = scenario["assertions"]
    ids = [x["id"] for x in assertions]
    require(ids and len(ids) == len(set(ids)), "Scenario assertion IDs must be nonempty and unique")
    objective = "\n".join([
        f"Execute GOAT scenario {scenario_id} only inside {PACKAGE} on {device_serial}.",
        "Use the actual visible Android app; do not infer success from source, a screenshot label, or process exit.",
        "Preserve existing local data. Do not clear app data, uninstall, change credentials, or configure Google.",
        "If a precondition is unmet, record BLOCKED and stop; do not retire or replace an assertion.",
        "Keep every assertion ID below as a strict Pro assert check item in the task plan.",
        "Preconditions:", *[f"- {x}" for x in scenario["preconditions"]],
        "Steps:", *[f"{i + 1}. {x}" for i, x in enumerate(scenario["steps"])],
        "Required assertions:", *[f"{x['id']}: {x['description']}" for x in assertions],
        "Do not claim feature, module, MVP, physical-device, Google/Drive or visual approval.",
    ])
    args = {"project": "goat-app", "device_serial": device_serial, "profile": "pro",
            "package_name": PACKAGE, "apk_path": apk_relative, "objective": objective,
            "verification_level": "strict", "explorer_mode": "flash",
            "expected_output": "Report each required assertion ID, observed result, and trace step evidence. "
                               "List failed, blocked, inconclusive or unexecuted requirements explicitly. "
                               "Do not include credentials, recovery codes or private account data."}
    require(sha256(provenance_path) == provenance_digest, "Build provenance changed during preparation")
    require(sha256(apk) == digest and source_identity(repo, expected_commit) == before,
            "Source or APK changed during request preparation")
    return {"schema_version": 1, "purpose": "ARTEMIS_REQUEST_PREPARATION_ONLY",
            "prepared_at_utc": datetime.now(timezone.utc).isoformat(), "source": before,
            "artifact": {"path": apk_relative, "sha256": digest, "size_bytes": apk.stat().st_size},
            "application": application, "build_provenance_sha256": provenance_digest,
            "scenario": {"id": scenario_id, "assertions": assertions,
                         "screen_ids": scenario["screen_ids"], "manifest_sha256": sha256(manifest_path)},
            "objective_sha256": text_hash(objective), "execution": "NOT_STARTED",
            "request": {"jsonrpc": "2.0", "id": 3, "method": "tools/call",
                        "params": {"name": "android_task_start", "arguments": args}},
            "qualification": "NOT_GRANTED"}


def verify_receipt(plan: dict, trace_id: str, control_home: Path = CONTROL_HOME) -> dict:
    require(bool(TRACE.fullmatch(trace_id)), "Invalid canonical trace ID")
    require(plan.get("schema_version") == 1 and plan.get("purpose") == "ARTEMIS_REQUEST_PREPARATION_ONLY",
            "Unsupported prepared request")
    request = plan.get("request", {})
    require(request.get("method") == "tools/call" and request.get("params", {}).get("name") == "android_task_start",
            "Receipt must correspond to the governed asynchronous task request")
    args = request["params"]["arguments"]
    require(args.get("project") == "goat-app" and args.get("package_name") == PACKAGE and
            args.get("profile") == "pro" and args.get("verification_level") == "strict",
            "Unexpected project, package or verification mode")
    require(text_hash(args["objective"]) == plan.get("objective_sha256"), "Prepared objective hash changed")
    require(bool(HEX40.fullmatch(plan["source"]["commit"])) and
            bool(HEX64.fullmatch(plan["artifact"]["sha256"])), "Invalid prepared source/APK identity")
    task_path = contained_file(control_home / "android-testing/tasks", f"{trace_id}.json")
    task = read_json(task_path)
    expected = {"schema_version": 1, "trace_id": trace_id, "authority": "HERMES_OWNED_ARTEMIS_SUBORDINATE_TASK",
                "project": "goat-app", "repository_sha": plan["source"]["commit"],
                "repository_branch": plan["source"]["ref"], "device_serial": args["device_serial"],
                "profile": "pro", "verification_level": "strict", "package_name": PACKAGE,
                "apk_sha256": plan["artifact"]["sha256"], "objective_sha256": plan["objective_sha256"]}
    for key, value in expected.items():
        require(task.get(key) == value, f"Hermes task binding differs: {key}")
    seal = task.get("evidence", {})
    require(bool(seal.get("sealed_at")), "Task evidence has not been sealed")
    directory = (control_home / "android-testing/evidence/goat-app" / f"trace-{trace_id}").resolve()
    require(Path(seal.get("dir", "")).resolve() == directory, "Unexpected sealed evidence directory")
    summary_path = contained_file(directory, seal.get("summary_path", ""))
    require(summary_path.name == "summary.json", "Unexpected canonical summary name")
    summary = read_json(summary_path)
    for key in ("trace_id", "project", "repository_sha", "device_serial", "profile", "objective_sha256"):
        require(summary.get(key) == task.get(key), f"Sealed summary differs from task: {key}")
    require(summary.get("schema_version") == 1 and summary.get("authority") == EVIDENCE_AUTHORITY,
            "Summary has an unsupported authority")
    require(summary.get("sealed_at") == seal["sealed_at"] and summary.get("started_at") == task.get("started_at"),
            "Summary/task lifecycle binding differs")
    require(summary.get("upstream_status") in ("completed", "failed", "cancelled"), "Task is not terminal")
    candidate = seal.get("memory_candidate", {})
    require(bool(candidate.get("memory_id")) and bool(candidate.get("object_rel")),
            "Canonical task record has no evidence candidate")

    verified = {}
    files = {}
    for category in ("artifacts", "upstream_artifacts"):
        rows = summary.get(category, {})
        require(isinstance(rows, dict), "Invalid artifact collection")
        for name, row in rows.items():
            require(isinstance(row, dict), f"Artifact is unavailable: {category}.{name}")
            path = contained_file(directory, row.get("file", ""))
            digest = sha256(path)
            require(row.get("sha256") == digest and row.get("bytes") == path.stat().st_size,
                    f"Sealed artifact bytes differ: {category}.{name}")
            key = f"{category}.{name}"
            verified[key] = {"filename": path.name, "sha256": digest, "bytes": path.stat().st_size}
            files[key] = path
    for required in ("artifacts.screenshot", "artifacts.logcat", "upstream_artifacts.run_outcome",
                     "upstream_artifacts.task_plan", "upstream_artifacts.output"):
        require(required in files, f"Required terminal evidence is absent: {required}")
    require(files["artifacts.screenshot"].read_bytes().startswith(b"\x89PNG\r\n\x1a\n"),
            "Final screenshot is not a PNG")
    outcome = read_json(files["upstream_artifacts.run_outcome"])
    counts = outcome.get("tests", {})
    for name in ("passed", "failed", "inconclusive", "unchecked", "retired"):
        require(type(counts.get(name)) is int and counts[name] >= 0, "Missing or invalid assertion counts")
    for field, count in (("failed_items", "failed"), ("retired_items", "retired")):
        require(isinstance(counts.get(field), list) and len(counts[field]) == counts[count],
                "Assertion details disagree with their counts")
    require(summary.get("test_summary") == {"task_status": outcome.get("task_status"), **counts},
            "Hermes test_summary differs from retained run_outcome")
    plan_text = files["upstream_artifacts.task_plan"].read_text(encoding="utf-8")
    report = files["upstream_artifacts.output"].read_text(encoding="utf-8")
    ids = [x["id"] for x in plan["scenario"]["assertions"]]
    require(ids and all(item in plan_text and item in report for item in ids),
            "Scenario requirements are absent from the retained plan/report")
    passed = (summary["upstream_status"] == "completed" and outcome.get("task_status") == "completed"
              and counts["passed"] >= len(ids)
              and all(counts[x] == 0 for x in ("failed", "inconclusive", "unchecked", "retired")))
    return {"schema_version": 1, "trace_id": trace_id, "source": plan["source"],
            "device_serial": args["device_serial"], "artifact": plan["artifact"],
            "scenario_id": plan["scenario"]["id"], "receipt_sha256": sha256(summary_path),
            "task_record_sha256": sha256(task_path), "artifacts": verified,
            "task_status": outcome.get("task_status"),
            "assertions": {k: counts[k] for k in ("passed", "failed", "inconclusive", "unchecked", "retired")},
            "memory_candidate": candidate,
            "result": "TRACE_CHECKS_PASSED_REVIEW_REQUIRED" if passed else "TRACE_REQUIREMENTS_NOT_PASSED",
            "scenario_review": "REQUIRED_FOR_EACH_ASSERTION_AGAINST_ACTUAL_TRACE",
            "authority": EVIDENCE_AUTHORITY, "qualification": "NOT_GRANTED"}


def save_new(path: Path, value: dict) -> None:
    destination = path.absolute()
    require(not destination.resolve().is_relative_to(ROOT), "Keep request/evidence outputs outside the checkout")
    require(destination.parent.is_dir(), "Output directory must already exist")
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    prepare = sub.add_parser("prepare")
    prepare.add_argument("--repo", type=Path, default=ROOT)
    prepare.add_argument("--expected-commit", required=True)
    prepare.add_argument("--apk", required=True)
    prepare.add_argument("--build-provenance", type=Path, required=True)
    prepare.add_argument("--sdk", type=Path, required=True)
    prepare.add_argument("--build-tools", default="37.0.0")
    prepare.add_argument("--expected-cert-sha1", required=True)
    prepare.add_argument("--scenario", required=True)
    prepare.add_argument("--device-serial", required=True)
    prepare.add_argument("--output", type=Path, required=True)
    verify = sub.add_parser("verify")
    verify.add_argument("--prepared-request", type=Path, required=True)
    verify.add_argument("--trace-id", required=True)
    verify.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.action == "prepare":
            require(not args.output.resolve().is_relative_to(args.repo.resolve()),
                    "Keep requests outside the bound checkout")
            result = prepare_request(args.repo.resolve(), args.expected_commit, args.apk,
                                     args.build_provenance, args.sdk, args.build_tools,
                                     args.expected_cert_sha1, args.scenario, args.device_serial)
        else:
            result = verify_receipt(read_json(args.prepared_request), args.trace_id)
        save_new(args.output, result)
        print(json.dumps({"output": str(args.output), "result": result.get("result", "REQUEST_PREPARED_NOT_EXECUTED"),
                          "qualification": "NOT_GRANTED"}))
        return 1 if result.get("result") == "TRACE_REQUIREMENTS_NOT_PASSED" else 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Artemis evidence check failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
