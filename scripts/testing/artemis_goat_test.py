#!/usr/bin/env python3
"""Pure contract/fixture tests; no Android, Artemis, credentials or canonical evidence writes."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("artemis_goat", Path(__file__).with_name("artemis_goat.py"))
subject = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(subject)
COMMIT = "a" * 40
SOURCE = {"repository": "Vanguduza/goat-app", "commit": COMMIT, "tree": "b" * 40, "ref": "main"}
APK_IDENTITY = {"package_name": "com.farmos.app", "test_identity": "fixture-only-not-an-apk"}
TRACE = "fixture-trace-001"
ASSERTIONS = [{"id": "ENTRY-01", "description": "A visible assertion"},
              {"id": "ENTRY-02", "description": "A second visible assertion"}]


class ArtemisContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="goat-artemis-contract-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repo = self.base / "goat"
        self.repo.mkdir()
        self.apk = self.repo / "app/build/outputs/apk/debug/app-debug.apk"
        self.apk.parent.mkdir(parents=True)
        self.apk.write_bytes(b"unit-fixture APK bytes; never installed")
        self.manifest = self.repo / "scripts/testing/artemis_scenarios.json"
        self.manifest.parent.mkdir(parents=True)
        self.manifest.write_text(json.dumps({"schema_version": 1, "package_name": subject.PACKAGE,
            "scenarios": [{"id": "fixture", "screen_ids": ["FOS-GLOBAL-014"],
                           "preconditions": ["Dedicated fixture"], "steps": ["Inspect"],
                           "assertions": ASSERTIONS}]}))
        self.provenance = self.base / "build-provenance.json"
        self.write_provenance()

    def write_provenance(self, **changes):
        value = {"schema_version": 1, "purpose": "OWNER_AUTHORIZED_TESTING_ONLY", "source": SOURCE,
                 "artifact": {"sha256": subject.sha256(self.apk), "size_bytes": self.apk.stat().st_size},
                 "application": APK_IDENTITY}
        value.update(changes)
        self.provenance.write_text(json.dumps(value))

    def prepare(self):
        with patch.object(subject, "source_identity", return_value=SOURCE), \
             patch.object(subject, "inspect_apk", return_value=APK_IDENTITY):
            return subject.prepare_request(self.repo, COMMIT, str(self.apk.relative_to(self.repo)),
                self.provenance, self.base / "sdk", "37.0.0", "AB" * 20, "fixture", "emulator-5560")

    def receipt(self, counts=None, status="completed"):
        prepared = self.prepare()
        counts = counts or {"passed": 2, "failed": 0, "inconclusive": 0, "unchecked": 0,
                            "retired": 0, "failed_items": [], "retired_items": []}
        root = self.base / "control"
        directory = root / "android-testing/evidence/goat-app" / f"trace-{TRACE}"
        directory.mkdir(parents=True)
        def artifact(name, data):
            target = directory / name
            target.write_bytes(data)
            return {"file": str(target), "sha256": subject.sha256(target), "bytes": len(data)}
        outcome = {"task_status": status, "tests": counts}
        summary = {"schema_version": 1, "trace_id": TRACE, "project": "goat-app",
                   "repository_sha": COMMIT, "device_serial": "emulator-5560", "profile": "pro",
                   "objective_sha256": prepared["objective_sha256"], "started_at": "2026-10-09T14:00:00Z",
                   "sealed_at": "2026-10-09T14:01:00Z", "upstream_status": "completed",
                   "authority": subject.EVIDENCE_AUTHORITY,
                   "test_summary": {"task_status": status, **counts},
                   "artifacts": {"screenshot": artifact("final-screen.png", b"\x89PNG\r\n\x1a\nfixture"),
                                 "logcat": artifact("logcat.txt", b"fixture log\n")},
                   "upstream_artifacts": {
                       "run_outcome": artifact("run_outcome.json", json.dumps(outcome).encode()),
                       "task_plan": artifact("task_plan.md", b"assert ENTRY-01\nassert ENTRY-02\n"),
                       "output": artifact("output.md", b"ENTRY-01 observed step 1\nENTRY-02 observed step 2\n")}}
        summary_path = directory / "summary.json"
        summary_path.write_text(json.dumps(summary))
        task = {"schema_version": 1, "trace_id": TRACE, "project": "goat-app",
                "authority": "HERMES_OWNED_ARTEMIS_SUBORDINATE_TASK",
                "repository_sha": COMMIT, "repository_branch": "main", "device_serial": "emulator-5560",
                "profile": "pro", "verification_level": "strict", "package_name": subject.PACKAGE,
                "apk_sha256": subject.sha256(self.apk), "objective_sha256": prepared["objective_sha256"],
                "started_at": summary["started_at"], "evidence": {
                    "sealed_at": summary["sealed_at"], "dir": str(directory),
                    "summary_path": str(summary_path),
                    "memory_candidate": {"memory_id": "fixture-memory", "object_rel": "memory/fixture.json"}}}
        task_path = root / "android-testing/tasks" / f"{TRACE}.json"
        task_path.parent.mkdir(parents=True)
        task_path.write_text(json.dumps(task))
        return prepared, root, task_path, summary_path

    def test_request_uses_only_governed_task_contract_and_never_claims_execution(self):
        request = self.prepare()
        self.assertEqual("NOT_STARTED", request["execution"])
        self.assertEqual("NOT_GRANTED", request["qualification"])
        self.assertEqual("android_task_start", request["request"]["params"]["name"])
        args = request["request"]["params"]["arguments"]
        self.assertEqual("goat-app", args["project"])
        self.assertEqual("strict", args["verification_level"])
        self.assertEqual(subject.PACKAGE, args["package_name"])
        self.assertEqual(subject.text_hash(args["objective"]), request["objective_sha256"])
        self.assertEqual(subject.sha256(self.apk), request["artifact"]["sha256"])

    def test_preparation_rejects_wrong_build_commit_and_changed_apk_bytes(self):
        self.write_provenance(source={**SOURCE, "commit": "c" * 40})
        with self.assertRaisesRegex(ValueError, "build/source identity"):
            self.prepare()
        self.write_provenance()
        self.apk.write_bytes(b"changed APK")
        with self.assertRaisesRegex(ValueError, "APK bytes differ"):
            self.prepare()

    def test_actual_sdk_identity_must_match_build_provenance(self):
        self.write_provenance(application={"package_name": "another.package"})
        with self.assertRaisesRegex(ValueError, "Actual APK identity"):
            self.prepare()

    def test_preparation_refuses_an_apk_symlink_even_when_target_is_inside_repo(self):
        alias = self.apk.with_name("alias.apk")
        alias.symlink_to(self.apk)
        with self.assertRaisesRegex(ValueError, "not a regular file"):
            subject.contained_file(self.repo, str(alias))

    def test_source_identity_rejects_dirty_checkout_before_request_or_inspection(self):
        answers = {("git", "rev-parse", "--show-toplevel"): str(self.repo),
                   ("git", "remote", "get-url", "origin"): "https://github.com/Vanguduza/goat-app.git",
                   ("git", "rev-parse", "HEAD"): COMMIT,
                   ("git", "status", "--porcelain=v1", "--untracked-files=all"): " M app/source.kt"}
        with patch.object(subject, "command", side_effect=lambda *argv, **_: answers[argv]):
            with self.assertRaisesRegex(ValueError, "isolate all source changes"):
                subject.source_identity(self.repo, COMMIT)

    def test_canonical_async_receipt_has_candidate_on_task_only_and_requires_review(self):
        plan, root, _, summary = self.receipt()
        self.assertNotIn("memory_candidate", json.loads(summary.read_text()))
        result = subject.verify_receipt(plan, TRACE, root)
        self.assertEqual("TRACE_CHECKS_PASSED_REVIEW_REQUIRED", result["result"])
        self.assertEqual("NOT_GRANTED", result["qualification"])
        self.assertEqual(subject.EVIDENCE_AUTHORITY, result["authority"])
        self.assertEqual(5, len(result["artifacts"]))

    def test_completed_process_with_failed_assertion_is_not_a_pass(self):
        counts = {"passed": 2, "failed": 1, "inconclusive": 0, "unchecked": 0, "retired": 0,
                  "failed_items": [{"item_text": "ENTRY-02", "kind": "assert", "evidence": "failed"}],
                  "retired_items": []}
        plan, root, _, _ = self.receipt(counts)
        self.assertEqual("TRACE_REQUIREMENTS_NOT_PASSED", subject.verify_receipt(plan, TRACE, root)["result"])

    def test_partial_task_and_retired_requirement_cannot_pass(self):
        plan, root, _, _ = self.receipt(status="partial")
        self.assertEqual("TRACE_REQUIREMENTS_NOT_PASSED", subject.verify_receipt(plan, TRACE, root)["result"])
        counts = {"passed": 2, "failed": 0, "inconclusive": 0, "unchecked": 0, "retired": 1,
                  "failed_items": [], "retired_items": [{"item_text": "ENTRY-01"}]}
        outcome = root / "android-testing/evidence/goat-app" / f"trace-{TRACE}" / "run_outcome.json"
        summary_path = outcome.with_name("summary.json")
        summary = json.loads(summary_path.read_text())
        outcome.write_text(json.dumps({"task_status": "completed", "tests": counts}))
        summary["upstream_artifacts"]["run_outcome"].update(sha256=subject.sha256(outcome),
                                                          bytes=outcome.stat().st_size)
        summary["test_summary"] = {"task_status": "completed", **counts}
        summary_path.write_text(json.dumps(summary))
        self.assertEqual("TRACE_REQUIREMENTS_NOT_PASSED", subject.verify_receipt(plan, TRACE, root)["result"])

    def test_wrong_device_source_or_apk_binding_is_rejected(self):
        plan, root, task_path, _ = self.receipt()
        original = json.loads(task_path.read_text())
        for key, value in (("device_serial", "emulator-5554"), ("repository_sha", "c" * 40),
                           ("apk_sha256", "d" * 64), ("verification_level", "off")):
            with self.subTest(field=key):
                task_path.write_text(json.dumps({**original, key: value}))
                with self.assertRaisesRegex(ValueError, f"binding differs: {key}"):
                    subject.verify_receipt(plan, TRACE, root)

    def test_unsealed_task_is_not_terminal_evidence(self):
        plan, root, task_path, _ = self.receipt()
        task = json.loads(task_path.read_text())
        task.pop("evidence")
        task_path.write_text(json.dumps(task))
        with self.assertRaisesRegex(ValueError, "not been sealed"):
            subject.verify_receipt(plan, TRACE, root)

    def test_actual_artifact_tampering_is_rejected(self):
        plan, root, _, summary_path = self.receipt()
        summary_path.with_name("logcat.txt").write_text("changed bytes")
        with self.assertRaisesRegex(ValueError, "artifact bytes differ"):
            subject.verify_receipt(plan, TRACE, root)

    def test_artifact_path_escape_and_trace_traversal_are_rejected(self):
        plan, root, _, summary_path = self.receipt()
        summary = json.loads(summary_path.read_text())
        outside = self.base / "outside.txt"
        outside.write_text("outside")
        summary["artifacts"]["logcat"] = {"file": str(outside), "sha256": subject.sha256(outside),
                                         "bytes": outside.stat().st_size}
        summary_path.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, "escapes its approved directory"):
            subject.verify_receipt(plan, TRACE, root)
        with self.assertRaisesRegex(ValueError, "Invalid canonical trace"):
            subject.verify_receipt(plan, "../../outside", root)

    def test_outcome_and_hermes_summary_must_agree(self):
        plan, root, _, summary_path = self.receipt()
        summary = json.loads(summary_path.read_text())
        summary["test_summary"]["passed"] = 99
        summary_path.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, "test_summary differs"):
            subject.verify_receipt(plan, TRACE, root)

    def test_missing_scenario_id_in_report_is_not_accepted(self):
        plan, root, _, summary_path = self.receipt()
        summary = json.loads(summary_path.read_text())
        output = summary_path.with_name("output.md")
        output.write_text("ENTRY-01 only")
        summary["upstream_artifacts"]["output"].update(sha256=subject.sha256(output),
                                                      bytes=output.stat().st_size)
        summary_path.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, "requirements are absent"):
            subject.verify_receipt(plan, TRACE, root)

    def test_output_is_private_and_never_overwrites_existing_evidence(self):
        destination = self.base / "request.json"
        subject.save_new(destination, {"fixture": True})
        self.assertEqual(0o600, destination.stat().st_mode & 0o777)
        with self.assertRaises(FileExistsError):
            subject.save_new(destination, {"fixture": False})
        self.assertEqual({"fixture": True}, json.loads(destination.read_text()))


if __name__ == "__main__":
    unittest.main()
