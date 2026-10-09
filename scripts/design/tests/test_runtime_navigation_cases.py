"""Rejection tests for additional Phase 5 case evidence; all fixtures are synthetic."""
from __future__ import annotations

import copy
import json
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import runtime_navigation_cases as mapping
import runtime_case_junit as junit

SOURCE_PATH = "app/src/test/java/com/farmos/app/ExampleRuntimeTest.kt"
CLASS = "com.farmos.app.ExampleRuntimeTest"
SOURCE = '''package com.farmos.app

class ExampleRuntimeTest {
    @Test
    fun positiveJourney() {
        awaitScreen("FOS-HOME-006")
        back()
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()
    }

    @Test
    fun foreignFarmIsRejected() {
        compose.onNodeWithTag("farm-screen:FOS-GOAT-003").assertDoesNotExist()
    }

    private fun back() {
        compose.runOnIdle { dispatcher.onBackPressed() }
    }

    private fun awaitScreen(id: String) = compose.waitUntil(10000) {
        compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()
    }
}
'''
LEGACY = '''ENTRY_ACTION_TESTS = ()
RENDERED_TRAVERSAL_TESTS = ()
RENDERED_OWNER_TESTS = ()
ROUTE_CONTRACT_TESTS = ()
'''


class RuntimeCaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name) / "repo"
        self.evidence = pathlib.Path(self.temporary.name) / "artifact"
        self.root.mkdir()
        self.evidence.mkdir()
        self.write("scripts/design/build-runtime-navigation-evidence.py", LEGACY)
        self.write("docs/ux/FARM_OS_SCREEN_REGISTRY.yaml", "FOS-HOME-006\nFOS-HOME-012-A\nFOS-GOAT-003\n")
        self.write(SOURCE_PATH, SOURCE)
        self.spec = {
            "schema_version": 1,
            "purpose": "ADDITIONAL_PHASE5_CASE_INVENTORY_NOT_APPROVAL",
            "sources": [{
                "path": SOURCE_PATH,
                "sha256": mapping.digest(SOURCE.encode()),
                "reviewed_source_commit": "1" * 40,
                "class_name": CLASS,
                "cases": [
                    {
                        "method": "positiveJourney", "execution_only_reason": None,
                        "claims": [
                            {
                                "kind": "rendered_destination_traversal", "screen_id": "FOS-HOME-006",
                                "entry": self.anchor('awaitScreen("FOS-HOME-006")'),
                                "return_action": self.anchor("back()"),
                                "return": self.anchor('compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()'),
                            },
                            {
                                "kind": "rendered_surface_owner", "screen_id": "FOS-HOME-012-A",
                                "entry": self.anchor('compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()'),
                                "return_action": None, "return": None,
                            },
                        ],
                    },
                    {
                        "method": "foreignFarmIsRejected",
                        "execution_only_reason": "Authority refusal is case execution, not destination traversal.",
                        "claims": [],
                    },
                ],
            }],
        }
        self.save_map()
        identity = self.evidence / "app/build/test-evidence"
        identity.mkdir(parents=True)
        (identity / "commit.txt").write_text("2" * 40 + "\n")
        (identity / "tree.txt").write_text("3" * 40 + "\n")
        (identity / "worktree-status.txt").write_text("")
        self.log = pathlib.Path(self.temporary.name) / "native-job.log"
        self.log.write_text(
            "2026-10-09T15:00:00Z Checkout " + "2" * 40 + "\n"
            "2026-10-09T15:00:01Z > Task :app:testDebugUnitTest\n"
            "2026-10-09T15:01:00Z BUILD SUCCESSFUL\n"
        )
        self.xml_path = self.evidence / f"app/build/test-results/testDebugUnitTest/TEST-{CLASS}.xml"
        self.xml_path.parent.mkdir(parents=True)
        self.report = ET.Element("testsuite", {
            "name": CLASS, "tests": "2", "failures": "0", "errors": "0", "skipped": "0",
            "timestamp": "2026-10-09T15:00:02.000Z",
        })
        for method in ("positiveJourney", "foreignFarmIsRejected"):
            ET.SubElement(self.report, "testcase", {"name": method, "classname": CLASS})
        self.save_xml()

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    @staticmethod
    def anchor(expression, method="positiveJourney", within=None, via=None, occurrence=0):
        return {"within": within or method, "expression": expression, "via": via or [], "occurrence": occurrence}

    @property
    def claim(self):
        return self.spec["sources"][0]["cases"][0]["claims"][0]

    def save_map(self):
        self.write(mapping.MAP_PATH, json.dumps(self.spec, indent=2) + "\n")

    def save_source(self, text, update_hash=True):
        self.write(SOURCE_PATH, text)
        if update_hash:
            self.spec["sources"][0]["sha256"] = mapping.digest(text.encode())
            self.save_map()

    def save_xml(self):
        self.xml_path.write_bytes(ET.tostring(self.report, encoding="utf-8", xml_declaration=True))

    def catalog(self):
        self.save_map()
        return mapping.load_case_map(self.root)

    def git(self, args, cwd, text=False):
        if args == ["git", "status", "--porcelain=v1"]:
            value = ""
        elif args == ["git", "rev-parse", "HEAD"]:
            value = "4" * 40 + "\n"
        elif args == ["git", "rev-parse", "HEAD^{tree}"]:
            value = "3" * 40 + "\n"
        elif args[:2] == ["git", "show"]:
            return (self.root / args[2].split(":", 1)[1]).read_bytes()
        else:
            self.fail("unexpected git read: " + repr(args))
        return value if text else value.encode()

    def execution(self):
        with patch.object(junit.subprocess, "check_output", side_effect=self.git):
            return junit.verify_junit_cases(self.catalog(), self.root, self.evidence, self.log, 42)

    def runtime(self, execution=None):
        catalog = self.catalog()
        groups = {kind: {} for kind in mapping.legacy_groups(self.root)}
        for kind, paths in mapping.source_claims(catalog).items():
            groups[kind].update(paths)
        coverage, certification, all_ids = {}, {}, set()
        definitions = {
            "entry_action_emission": ("entry_action", "entry_action_emission_executed"),
            "rendered_destination_traversal": ("rendered_traversal", "runtime_reachability_executed"),
            "rendered_surface_owner": ("rendered_owner", "rendered_surface_owners_executed"),
            "route_contract": ("route_contract", "route_contracts_executed"),
        }
        for kind, (prefix, key) in definitions.items():
            ids = sorted({sid for values in groups[kind].values() for sid in values})
            all_ids.update(ids)
            coverage[prefix + "_screen_ids"] = ids
            coverage[prefix + "_screen_count"] = len(ids)
            certification[key] = len(ids) if execution else 0
        coverage["combined_screen_count"] = len(all_ids)
        certification["return_restoration_executed"] = coverage["rendered_traversal_screen_count"] if execution else 0
        return {
            "ci_status": "PASS_EXACT_HEAD_CI" if execution else "CI_PENDING",
            "foundation_run_id": 42 if execution else None,
            "tested_commit": "4" * 40,
            "test_sources": groups, "coverage": coverage, "certification": certification,
            "additional_case_evidence": mapping.case_ledger(catalog, execution),
        }

    def test_positive_case_and_raw_junit_receipts_match_without_claiming_the_negative_screen(self):
        catalog = self.catalog()
        self.assertEqual(2, catalog["expected_case_count"])
        claims = mapping.source_claims(catalog)
        self.assertEqual(["FOS-HOME-006"], claims["rendered_destination_traversal"][SOURCE_PATH])
        self.assertNotIn("FOS-GOAT-003", str(claims))
        executed = self.execution()
        self.assertEqual(2, len(executed["cases"]))
        self.assertEqual(mapping.digest(self.xml_path.read_bytes()), executed["reports"][0]["sha256"])
        self.assertEqual("FIRST_PARTY_CI_ORIGIN_AND_RUN_ACCEPTANCE_REQUIRED_SEPARATELY", executed["origin_verification"])
        mapping.verify_case_ledger(self.root, self.runtime(executed))

    def test_expression_bodied_report_click_binds_its_actual_parameter(self):
        source = SOURCE.replace(
            '        awaitScreen("FOS-HOME-006")\n        back()\n        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()',
            '        open("FOS-REPORT-002")\n        backToHub("FOS-REPORT-002")',
        )
        source = source.rsplit("}", 1)[0] + '''
    private fun open(screenId: String) {
        compose.onNodeWithTag("report-open:$screenId").performClick()
        awaitTag("farm-screen:$screenId")
    }
    private fun backToHub(screenId: String) {
        click("Reports")
        awaitTag("farm-screen:FOS-REPORT-001")
    }
    private fun click(text: String) = compose.onNode(hasClickAction() and hasText(text)).performScrollTo().performClick()
    private fun awaitTag(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
'''
        self.write("docs/ux/FARM_OS_SCREEN_REGISTRY.yaml", "FOS-REPORT-001\nFOS-REPORT-002\nFOS-GOAT-003\n")
        self.spec["sources"][0]["cases"][0]["claims"].pop()
        self.claim.update(
            screen_id="FOS-REPORT-002", entry=self.anchor('open("FOS-REPORT-002")'),
            return_action=self.anchor('backToHub("FOS-REPORT-002")'),
        )
        self.claim["return"] = self.anchor('backToHub("FOS-REPORT-002")')
        self.save_source(source)
        self.assertEqual(["FOS-REPORT-002"], mapping.source_claims(self.catalog())["rendered_destination_traversal"][SOURCE_PATH])
        self.save_source(source.replace("hasText(text)", "hasText(otherText)"))
        with self.assertRaisesRegex(ValueError, "anchor missing"):
            self.catalog()

    def test_pending_inventory_has_zero_execution_and_rejects_promotion_without_receipts(self):
        runtime = self.runtime()
        mapping.verify_case_ledger(self.root, runtime)
        self.assertEqual(0, runtime["additional_case_evidence"]["executed_case_count"])
        self.assertTrue(all(value == 0 for value in runtime["certification"].values()))
        runtime["additional_case_evidence"]["executed_case_count"] = 2
        with self.assertRaisesRegex(ValueError, "execution count"):
            mapping.verify_case_ledger(self.root, runtime)

    def test_stale_source_hash_is_rejected(self):
        self.save_source(SOURCE + "\n// Changed source\n", update_hash=False)
        with self.assertRaisesRegex(ValueError, "stale reviewed"):
            self.catalog()

    def test_missing_or_unmapped_test_method_is_rejected(self):
        for method in ("missingMethod", None):
            with self.subTest(method=method):
                original = copy.deepcopy(self.spec)
                if method is None:
                    self.spec["sources"][0]["cases"].pop()
                else:
                    self.spec["sources"][0]["cases"][0]["method"] = method
                with self.assertRaisesRegex(ValueError, "method set"):
                    self.catalog()
                self.spec = original

    def test_unmapped_overloads_do_not_discard_cases_but_mapped_overloads_are_refused(self):
        unrelated = SOURCE.rsplit("}", 1)[0] + '''
    private fun deliver(value: String) { use(value) }
    private fun deliver(value: Int) { use(value) }
}
'''
        self.save_source(unrelated)
        self.assertEqual(2, self.catalog()["expected_case_count"])
        ambiguous = SOURCE.rsplit("}", 1)[0] + '''
    private fun awaitScreen(id: String, timeout: Long) {
        compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()
    }
}
'''
        self.save_source(ambiguous)
        with self.assertRaises(ValueError):
            self.catalog()

    def test_exact_package_file_class_identity_is_required(self):
        self.spec["sources"][0]["class_name"] = "com.farmos.app.nested.ExampleRuntimeTest"
        with self.assertRaisesRegex(ValueError, "package/file/class"):
            self.catalog()

    def test_additional_sources_cannot_replace_legacy_class_handling(self):
        self.write("scripts/design/build-runtime-navigation-evidence.py", LEGACY.replace("ENTRY_ACTION_TESTS = ()", "ENTRY_ACTION_TESTS = (" + repr(SOURCE_PATH) + ",)"))
        with self.assertRaisesRegex(ValueError, "legacy source"):
            self.catalog()

    def test_negative_assertions_cannot_become_positive_claims(self):
        self.claim.update(
            kind="rendered_surface_owner", screen_id="FOS-GOAT-003",
            entry=self.anchor('compose.onNodeWithTag("farm-screen:FOS-GOAT-003").assertDoesNotExist()', method="foreignFarmIsRejected"),
            return_action=None,
        )
        self.claim["return"] = None
        rejected = self.spec["sources"][0]["cases"][1]
        rejected["execution_only_reason"] = None
        rejected["claims"] = [self.claim]
        self.spec["sources"][0]["cases"][0]["claims"].pop(0)
        with self.assertRaisesRegex(ValueError, "negative assertion"):
            self.catalog()

    def test_incidental_string_or_comment_is_not_an_entry_assertion(self):
        for replacement in (
            '// awaitScreen("FOS-HOME-006")',
            'val text = """awaitScreen("FOS-HOME-006")"""',
        ):
            with self.subTest(replacement=replacement):
                self.save_source(SOURCE.replace('awaitScreen("FOS-HOME-006")', replacement))
                with self.assertRaisesRegex(ValueError, "anchor missing"):
                    self.catalog()

    def test_negative_wait_helper_cannot_be_disguised_by_a_quoted_positive_check(self):
        positive = 'compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()'
        self.save_source(SOURCE.replace(positive, 'val text = """' + positive + '"""\n        ' + positive.replace("isNotEmpty", "isEmpty")))
        with self.assertRaisesRegex(ValueError, "anchor missing"):
            self.catalog()

    def test_claim_cannot_invent_a_different_screen(self):
        self.claim["screen_id"] = "FOS-GOAT-003"
        with self.assertRaisesRegex(ValueError, "differs from its positive"):
            self.catalog()

    def test_traversal_requires_action_followed_by_positive_return(self):
        for mutation in ("missing_action", "negative_return", "action_before_entry"):
            with self.subTest(mutation=mutation):
                original = copy.deepcopy(self.spec)
                if mutation == "missing_action":
                    self.claim["return_action"] = None
                elif mutation == "negative_return":
                    self.claim["return"]["expression"] = 'compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertDoesNotExist()'
                    self.save_source(SOURCE.replace('assertIsDisplayed()', 'assertDoesNotExist()'))
                    self.spec["sources"][0]["cases"][0]["claims"].pop()
                else:
                    self.save_source(SOURCE.replace('        awaitScreen("FOS-HOME-006")\n        back()', '        back()\n        awaitScreen("FOS-HOME-006")'))
                with self.assertRaises(ValueError):
                    self.catalog()
                self.spec = original
                self.save_source(SOURCE)

    def test_owner_claim_cannot_manufacture_a_return(self):
        self.claim["kind"] = "rendered_surface_owner"
        with self.assertRaisesRegex(ValueError, "owner evidence"):
            self.catalog()

    def test_reviewed_commit_must_contain_the_reviewed_bytes(self):
        catalog = self.catalog()
        with patch.object(mapping.subprocess, "check_output", side_effect=self.git):
            mapping.verify_reviewed_source_commits(catalog, self.root)
        with patch.object(mapping.subprocess, "check_output", return_value=b"unreviewed source"):
            with self.assertRaisesRegex(ValueError, "reviewed commit"):
                mapping.verify_reviewed_source_commits(catalog, self.root)

    def test_junit_report_is_required_and_case_set_must_be_exact(self):
        self.xml_path.unlink()
        with self.assertRaisesRegex(ValueError, "missing JUnit"):
            self.execution()
        self.save_xml()
        self.report.remove(self.report.findall("testcase")[0])
        self.report.set("tests", "1")
        self.save_xml()
        with self.assertRaisesRegex(ValueError, "method identity"):
            self.execution()

    def test_failed_or_skipped_cases_are_rejected_even_with_a_clean_suite_header(self):
        for tag in ("failure", "error", "skipped"):
            with self.subTest(tag=tag):
                case = self.report.findall("testcase")[0]
                child = ET.SubElement(case, tag)
                self.save_xml()
                with self.assertRaisesRegex(ValueError, "failed or was skipped"):
                    self.execution()
                case.remove(child)

    def test_failed_or_skipped_suite_is_rejected(self):
        for key in ("failures", "errors", "skipped"):
            with self.subTest(key=key):
                self.report.set(key, "1")
                self.save_xml()
                with self.assertRaisesRegex(ValueError, "failed, skipped"):
                    self.execution()
                self.report.set(key, "0")

    def test_duplicate_or_foreign_junit_method_cannot_inflate_execution(self):
        self.report.findall("testcase")[1].set("name", "positiveJourney")
        self.save_xml()
        with self.assertRaisesRegex(ValueError, "duplicate JUnit"):
            self.execution()
        self.report.findall("testcase")[1].set("name", "foreignFarmIsRejected")
        self.report.findall("testcase")[1].set("classname", "com.farmos.app.AnotherTest")
        self.save_xml()
        with self.assertRaisesRegex(ValueError, "method identity"):
            self.execution()

    def test_cached_task_and_stale_xml_are_not_current_execution(self):
        original = self.log.read_text()
        for outcome in ("FROM-CACHE", "UP-TO-DATE", "SKIPPED", "FAILED"):
            with self.subTest(outcome=outcome):
                self.log.write_text(original.replace(":app:testDebugUnitTest\n", ":app:testDebugUnitTest " + outcome + "\n"))
                with self.assertRaisesRegex(ValueError, "executed task"):
                    self.execution()
        self.log.write_text(original)
        self.report.set("timestamp", "2026-10-09T14:59:59.000Z")
        self.save_xml()
        with self.assertRaisesRegex(ValueError, "CI log window"):
            self.execution()

    def test_dirty_or_different_artifact_checkout_is_rejected(self):
        identity = self.evidence / "app/build/test-evidence"
        (identity / "worktree-status.txt").write_text(" M app/src/main/Example.kt\n")
        with self.assertRaisesRegex(ValueError, "uncommitted source"):
            self.execution()
        (identity / "worktree-status.txt").write_text("")
        (identity / "tree.txt").write_text("5" * 40 + "\n")
        with self.assertRaisesRegex(ValueError, "different candidate tree"):
            self.execution()

    def test_dirty_current_candidate_cannot_borrow_a_clean_ci_run(self):
        def dirty(args, cwd, text=False):
            if args == ["git", "status", "--porcelain=v1"]:
                return " M app/src/main/java/com/farmos/app/CurrentProduction.kt\n"
            return self.git(args, cwd, text)
        with patch.object(junit.subprocess, "check_output", side_effect=dirty):
            with self.assertRaisesRegex(ValueError, "clean current candidate"):
                junit.verify_junit_cases(self.catalog(), self.root, self.evidence, self.log, 42)

    def test_accepted_case_ledger_survives_evidence_only_descendant_without_readmitting_old_run(self):
        runtime = self.runtime(self.execution())
        accepted = copy.deepcopy(runtime)
        self.write("docs/reviews/evidence-only.json", '{"retains_original_ci_receipt": true}\n')

        def descendant(args, cwd, text=False):
            if args == ["git", "rev-parse", "HEAD"]:
                return "5" * 40 + "\n"
            if args == ["git", "rev-parse", "HEAD^{tree}"]:
                return "6" * 40 + "\n"
            return self.git(args, cwd, text)

        with patch.object(junit.subprocess, "check_output", side_effect=descendant):
            # Read-only consumers retain C's admitted identity and unchanged source/map.
            mapping.verify_case_ledger(self.root, runtime)
            self.assertEqual(accepted, runtime)
            # E cannot re-label C's old artifact as a fresh exact-tree execution.
            with self.assertRaisesRegex(ValueError, "different candidate tree"):
                junit.verify_junit_cases(self.catalog(), self.root, self.evidence, self.log, 42)
        self.save_source(SOURCE + "\n// changed mapped source\n", update_hash=False)
        with self.assertRaisesRegex(ValueError, "stale reviewed test source"):
            mapping.verify_case_ledger(self.root, runtime)

    def test_source_and_map_must_be_committed_in_the_actual_candidate(self):
        for wrong_path in (SOURCE_PATH, mapping.MAP_PATH):
            with self.subTest(path=wrong_path):
                def changed(args, cwd, text=False):
                    if args[:2] == ["git", "show"] and args[2].endswith(":" + wrong_path):
                        return b"not the candidate bytes"
                    return self.git(args, cwd, text)
                with patch.object(junit.subprocess, "check_output", side_effect=changed):
                    with self.assertRaisesRegex(ValueError, "committed candidate"):
                        junit.verify_junit_cases(self.catalog(), self.root, self.evidence, self.log, 42)

    def test_fabricated_coverage_or_traversal_counts_are_rejected(self):
        for field in ("coverage", "certification", "source"):
            with self.subTest(field=field):
                runtime = self.runtime()
                if field == "coverage":
                    runtime["coverage"]["combined_screen_count"] += 1
                elif field == "certification":
                    runtime["certification"]["return_restoration_executed"] = 1
                else:
                    runtime["test_sources"]["rendered_destination_traversal"][SOURCE_PATH].append("FOS-GOAT-003")
                with self.assertRaises(ValueError):
                    mapping.verify_case_ledger(self.root, runtime)

    def test_unreviewed_sources_require_explicit_case_evidence(self):
        runtime = self.runtime()
        del runtime["additional_case_evidence"]
        with self.assertRaisesRegex(ValueError, "lack per-case"):
            mapping.verify_case_ledger(self.root, runtime)

    def test_case_receipt_cannot_claim_a_different_junit_hash_or_run(self):
        execution = self.execution()
        for mutation in ("hash", "run", "case"):
            with self.subTest(mutation=mutation):
                runtime = self.runtime(copy.deepcopy(execution))
                proof = runtime["additional_case_evidence"]["execution"]
                if mutation == "hash":
                    proof["cases"][0]["junit_sha256"] = "9" * 64
                elif mutation == "run":
                    proof["foundation_run_id"] += 1
                else:
                    proof["cases"].pop()
                with self.assertRaises(ValueError):
                    mapping.verify_case_ledger(self.root, runtime)


if __name__ == "__main__":
    unittest.main()
