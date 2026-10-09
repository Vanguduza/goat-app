#!/usr/bin/env python3
"""Regression tests for source fingerprints before and after a build."""
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from source_evidence import kotlin_sources, source_fingerprint


class SourceEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="farm-source-evidence-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # An isolated, unborn repository; no checkout, staged files or commits are changed.
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True, capture_output=True)
        (self.root / ".gitignore").write_text("**/build/\n", encoding="utf-8")
        self.write("docs/ux/FARM_OS_SCREEN_REGISTRY.yaml", "screen_count: 545\n")
        self.write("docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml", "surfaces: []\n")
        self.write("app/src/main/kotlin/Main.kt", "class Main\n")

    def write(self, path: str, value: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(value, encoding="utf-8")

    def test_ksp_outputs_and_backups_do_not_change_source_identity(self) -> None:
        before = source_fingerprint(self.root)
        self.write("core/database/build/generated/ksp/debug/kotlin/Dao_Impl.kt", "class GeneratedDao\n")
        self.write("core/database/build/kspCaches/debug/backups/kotlin/Dao_Impl.kt", "class CachedDao\n")
        self.assertEqual(before, source_fingerprint(self.root))
        self.assertEqual([self.root / "app/src/main/kotlin/Main.kt"], kotlin_sources(self.root))

    def test_uncommitted_production_and_test_changes_bind_the_fingerprint(self) -> None:
        before = source_fingerprint(self.root)
        self.write("app/src/test/kotlin/MainTest.kt", "class MainTest\n")
        with_test = source_fingerprint(self.root)
        self.assertNotEqual(before, with_test)
        self.write("app/src/main/kotlin/Main.kt", "class ChangedMain\n")
        self.assertNotEqual(with_test, source_fingerprint(self.root))
        self.assertEqual(2, len(kotlin_sources(self.root)))
        self.assertEqual(
            [self.root / "app/src/main/kotlin/Main.kt", self.root / "app/src/test/kotlin/MainTest.kt"],
            kotlin_sources(self.root),
        )

    def test_registry_changes_bind_the_fingerprint_and_line_endings_are_canonical(self) -> None:
        before = source_fingerprint(self.root)
        (self.root / "app/src/main/kotlin/Main.kt").write_bytes(b"class Main\r\n")
        self.assertEqual(before, source_fingerprint(self.root))
        self.write("docs/ux/FARM_OS_SCREEN_REGISTRY.yaml", "screen_count: 544\n")
        self.assertNotEqual(before, source_fingerprint(self.root))


if __name__ == "__main__":
    unittest.main()
