#!/usr/bin/env python3
"""Negative proof-integrity tests: passing images are not independent visual approval."""
from pathlib import Path
import copy
import hashlib
import json
import struct
import tempfile
import unittest
import zlib
import audit_native_visual_certification as audit


def png(width=360, height=10):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress((b"\0" + b"\0\0\0\xff" * width) * height)) + chunk(b"IEND", b"")


class NativeProofTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)
        self.image = self.directory / "FOS-TASK-003.png"
        self.image.write_bytes(png())
        self.record = self.directory / "FOS-TASK-003.json"
        self.cases = {("com.farmos.app.RealTest", "recorded")}
        self.data = {"schema_version": 2, "evidence_class": "OBSERVED_NATIVE_PRODUCT_OWNER", "screen_id": "FOS-TASK-003", "owner_observed_before_capture": True, "test_class": "com.farmos.app.RealTest", "test": "RealTest.recorded", "mode": "LIGHT", "width_dp": 360, "font_scale": 1.3, "actual_device_width_dp": 360, "visit": 1, "vertical_sweep_complete": True, "scroll_restored": True, "incomplete_reasons": [], "viewports": [{"image": self.image.name, "image_sha256": hashlib.sha256(self.image.read_bytes()).hexdigest(), "width_px": 360, "height_px": 10, "density": 1, "actual_font_scale": 1.3, "visible_tags": ["farm-screen:FOS-TASK-003"], "observed_owner_ids": ["FOS-TASK-003"], "layout_findings": []}]}

    def proof(self, data=None):
        self.record.write_text(json.dumps(data or self.data))
        return audit.native_proof(self.record, {"FOS-TASK-003", "FOS-TASK-004"}, self.cases)

    def test_actual_owner_and_complete_sweep_are_recorded_without_approval(self):
        p = self.proof()
        self.assertEqual(p["owner"], "FOS-TASK-003")
        self.assertTrue(p["full_vertical_sweep"])
        self.assertNotIn("visual_certified", p)

    def test_filename_or_text_cannot_supply_missing_owner(self):
        self.data["viewports"][0]["visible_tags"] = []
        with self.assertRaisesRegex(ValueError, "invented owner"):
            self.proof()

    def test_owner_outside_registry_is_rejected(self):
        self.data["screen_id"] = "FOS-TASK-999"
        with self.assertRaises(ValueError): self.proof()

    def test_mismatched_font_and_device_width_are_rejected(self):
        for field, value in [("actual_font_scale", 1.0), ("density", 0)]:
            d = copy.deepcopy(self.data); d["viewports"][0][field] = value
            with self.assertRaises(ValueError): self.proof(d)
        d = copy.deepcopy(self.data); d["actual_device_width_dp"] = 411
        with self.assertRaises(ValueError): self.proof(d)

    def test_a_smaller_dialog_is_bound_to_actual_device_configuration(self):
        self.image.write_bytes(png(280))
        self.data["viewports"][0].update(width_px=280, image_sha256=hashlib.sha256(self.image.read_bytes()).hexdigest())
        self.assertIsNotNone(self.proof())

    def test_tampered_or_truncated_png_cannot_pass(self):
        self.image.write_bytes(b"not a png")
        with self.assertRaisesRegex(ValueError, "digest"): self.proof()
        self.image.write_bytes(png()[:24])
        self.data["viewports"][0]["image_sha256"] = hashlib.sha256(self.image.read_bytes()).hexdigest()
        with self.assertRaisesRegex(ValueError, "Truncated"): self.proof()

    def test_chunk_checksum_and_end_marker_are_verified(self):
        raw = bytearray(png());raw[-1] ^= 1
        with self.assertRaisesRegex(ValueError, "checksum"): audit.png_dimensions(bytes(raw))
        with self.assertRaises(ValueError): audit.png_dimensions(png()[:-12])

    def test_capture_with_no_passing_case_is_rejected(self):
        self.cases = set()
        with self.assertRaisesRegex(ValueError, "did not pass"): self.proof()

    def test_collector_fixture_and_legacy_label_are_not_product_coverage(self):
        for kind in ["SYNTHETIC_EVIDENCE_COLLECTOR_TEST_NOT_PRODUCT", "UNOWNED_RENDERED_STATE_NOT_SCREEN_COVERAGE"]:
            d=copy.deepcopy(self.data);d["evidence_class"]=kind;self.assertIsNone(self.proof(d))
        d=copy.deepcopy(self.data);d["schema_version"]=1;self.assertIsNone(self.proof(d))

    def test_incomplete_scroll_is_not_counted_complete(self):
        self.data["vertical_sweep_complete"] = False
        self.data["incomplete_reasons"] = ["LAZY_SCROLL_REQUIRES_INDEX_AND_OFFSET_RESTORATION"]
        self.assertFalse(self.proof()["full_vertical_sweep"])

    def test_evidence_path_cannot_leave_its_bundle(self):
        self.data["viewports"][0]["image"] = "../outside.png"
        with self.assertRaises(ValueError): self.proof()

    def test_wrong_tree_or_dirty_source_bundle_is_rejected(self):
        for name, text in [("tree.txt", "a"*40), ("commit.txt", "c"*40), ("worktree-status.txt", "")]:
            (self.directory/name).write_text(text)
        with self.assertRaisesRegex(ValueError,"different source tree"):
            audit.bundle_proofs(self.directory,{"FOS-TASK-003"},"b"*40)
        (self.directory/"tree.txt").write_text("b"*40)
        (self.directory/"worktree-status.txt").write_text(" M source.kt")
        with self.assertRaisesRegex(ValueError,"dirty"):
            audit.bundle_proofs(self.directory,{"FOS-TASK-003"},"b"*40)

    def test_registry_keeps_all_545_exact_entries_and_27_cells(self):
        rows=audit.scope(audit.ROOT)
        self.assertEqual(545,len(rows));self.assertEqual(545,len({x["screen_id"] for x in rows}))
        self.assertEqual(27,len(audit.CELLS));self.assertIn(("OUTDOOR",360,2.0),audit.CELLS)


if __name__ == "__main__": unittest.main()
