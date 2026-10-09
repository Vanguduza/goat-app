"""Canonical working-tree inputs for source-derived evidence.

Tracked and new, non-ignored Kotlin files are source; Gradle/KSP output is not.
Both production and test sources bind the fingerprint. Coverage consumers can
separately restrict this inventory to production source sets.
"""
from __future__ import annotations

import hashlib
import subprocess
from pathlib import Path

SOURCE_ROOTS = ("app", "core", "data", "domain", "feature")


def kotlin_sources(root: Path, bases: tuple[str, ...] = SOURCE_ROOTS) -> list[Path]:
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.kt"],
        cwd=root,
    ).decode("utf-8").split("\0")
    relative_paths = {
        name for name in names
        if name and name.split("/", 1)[0] in bases and (root / name).is_file()
    }
    return [root / name for name in sorted(relative_paths)]


def source_fingerprint(root: Path) -> str:
    inputs = [
        root / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml",
        root / "docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml",
        *kotlin_sources(root),
    ]
    digest = hashlib.sha256()
    for path in inputs:
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n"))
        digest.update(b"\0")
    return "sha256:" + digest.hexdigest()
