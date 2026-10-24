#!/usr/bin/env python3
"""Stage-specific repair for the owner-directed app-wide UX continuation.

Run only on the authorized dial-control workspace through its existing execution
route, with exclusive writer ownership. This script is NOT an alternative VM,
CI, authorization or deployment route. No network, Git write or process launch
other than bounded read-only Git inspection is performed.

Default: inspect and print the file digest without changing source.
Apply: --apply <digest returned by the inspection>.
Tests: --self-test (pure text tests; does not read or modify the worktree).

The malformed line was observed on dial-control on 2026-10-18. The expanded
working tree had not been published at staging time. Unknown source is refused;
the published pre-expansion PR is not a valid repair target. A successful repair
receipt means only this syntax fragment changed, NOT that Kotlin compiles.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import unittest

WORKTREE = Path('/home/ubuntu/work/goat-ux-repair-20261018')
EVIDENCE = Path('/home/ubuntu/work/goat-ux-evidence-20261018/all-pages')
BRANCH = 'chatgpt/goat-ux-repair-20261018'
BASELINE = '0e3add77e016ed599c9cf2223dab944dde49a848'
TARGET = Path('feature/ops/src/main/kotlin/com/farmos/feature/ops/PoultryFlockRecordScreens.kt')
FUNCTION = b'private fun FlockTimelineContent(records: PoultryFlockRecords) {'
BEFORE = b'        recordsContent = {events.size}\") {\n'
AFTER = b'        recordsContent = {\n'
BODY = (
    b'        events.forEachIndexed { index, (_, key, text) ->\n'
    b'            if (index > 0) HorizontalDivider()\n'
    b'            FlockRow(text.first, text.second, \"flock-event:$key\")\n'
    b'        }\n'
    b'        },\n'
    b'    )\n'
    b'}\n'
)


class Refused(ValueError):
    """The observed source does not match this narrow recovery contract."""


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repaired(source: bytes) -> tuple[bytes, str]:
    """Replace only the exact observed corruption within its owning function."""
    if source.count(FUNCTION) != 1:
        raise Refused('Expected exactly one owning FlockTimelineContent function')
    prefix, body = source.split(FUNCTION, 1)
    if b'FarmWorkflowTimeline(' not in body or b'steps = events.take(6).map' not in body:
        raise Refused('Expanded timeline is absent; do not patch the older PR implementation')
    malformed = BEFORE + BODY
    corrected = AFTER + BODY
    if body.count(malformed) == 1 and source.count(BEFORE) == 1:
        result = prefix + FUNCTION + body.replace(malformed, corrected, 1)
        return result, 'REPAIR_APPLICABLE'
    if body.count(corrected) == 1 and BEFORE not in source:
        return source, 'ALREADY_FIXED'
    raise Refused('Unknown or ambiguous source; inspect current bytes rather than broadening the replacement')


def git_read(*args: str) -> str:
    result = subprocess.run(
        ['git', *args], cwd=WORKTREE, check=True,
        text=True, capture_output=True, timeout=15,
    )
    return result.stdout.strip()


def inspect() -> tuple[Path, bytes, bytes, dict[str, object]]:
    root = WORKTREE.resolve(strict=True)
    if Path(git_read('rev-parse', '--show-toplevel')).resolve() != root:
        raise Refused('Unexpected Git worktree root')
    if git_read('branch', '--show-current') != BRANCH:
        raise Refused('Unexpected branch; this utility never switches branches')
    git_read('merge-base', '--is-ancestor', BASELINE, 'HEAD')
    target = root / TARGET
    if target.is_symlink() or not target.resolve(strict=True).is_relative_to(root):
        raise Refused('Target must be a regular file within the expected worktree')
    if not stat.S_ISREG(target.stat().st_mode):
        raise Refused('Target is not a regular file')
    old = target.read_bytes()
    new, outcome = repaired(old)
    report = {
        'observed_at': dt.datetime.now(dt.timezone.utc).isoformat(),
        'host': os.uname().nodename,
        'workspace': str(root), 'branch': BRANCH,
        'head': git_read('rev-parse', 'HEAD'), 'target': str(TARGET),
        'before_sha256': digest(old), 'candidate_sha256': digest(new),
        'outcome': outcome, 'applied': False,
        'compilation': 'NOT_RUN', 'native_review': 'NOT_RUN',
    }
    return target, old, new, report


def apply(target: Path, old: bytes, new: bytes, expected: str) -> None:
    # Caller must already hold the normal exclusive writer lease. This digest
    # check catches intervening edits; it does not replace that lease.
    if not re.fullmatch(r'[a-f0-9]{64}', expected) or digest(old) != expected:
        raise Refused('Expected digest does not match current source')
    if old == new:
        return
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    backup = EVIDENCE / ('poultry-timeline-' + expected + '.before.kt')
    try:
        with backup.open('xb') as stream:
            stream.write(old)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError:
        if backup.read_bytes() != old:
            raise Refused('Existing recovery backup differs')
    fd, temporary = tempfile.mkstemp(prefix='.poultry-timeline-', dir=target.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(new)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, stat.S_IMODE(target.stat().st_mode))
        if target.read_bytes() != old:
            raise Refused('Target changed during preparation; no replacement made')
        os.replace(temporary, target)
        if target.read_bytes() != new:
            raise Refused('Post-write verification failed; retain the backup and inspect')
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


class RecoveryTests(unittest.TestCase):
    def fixture(self) -> bytes:
        return (
            b'package com.farmos.feature.ops\n' + FUNCTION + b'\n'
            b'    FarmWorkflowTimeline(\n'
            b'        steps = events.take(6).map { it },\n'
            + BEFORE + BODY
        )

    def test_exact_corruption_only(self) -> None:
        source = self.fixture()
        result, outcome = repaired(source)
        self.assertEqual(outcome, 'REPAIR_APPLICABLE')
        self.assertEqual(result, source.replace(BEFORE, AFTER, 1))
        self.assertIn(BODY, result)

    def test_idempotent(self) -> None:
        result, _ = repaired(self.fixture())
        self.assertEqual(repaired(result), (result, 'ALREADY_FIXED'))

    def test_old_published_implementation_refused(self) -> None:
        with self.assertRaises(Refused):
            repaired(FUNCTION + b'\n    FarmOperationalSection(\"Events\") {}\n}\n')

    def test_changed_record_body_refused(self) -> None:
        with self.assertRaises(Refused):
            repaired(self.fixture().replace(b'flock-event:$key', b'other-event:$key'))

    def test_duplicate_function_refused(self) -> None:
        with self.assertRaises(Refused):
            repaired(self.fixture() + self.fixture())

    def test_outside_function_corruption_refused(self) -> None:
        with self.assertRaises(Refused):
            repaired(BEFORE + self.fixture())

    def test_unknown_variant_refused(self) -> None:
        with self.assertRaises(Refused):
            repaired(self.fixture().replace(BEFORE, b'        recordsContent = BROKEN\n'))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--apply', metavar='EXPECTED_SHA256')
    group.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(RecoveryTests)
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    try:
        target, old, new, report = inspect()
        if args.apply is not None:
            apply(target, old, new, args.apply)
            report['applied'] = old != new
            report['outcome'] = 'SYNTAX_FRAGMENT_REPAIRED' if old != new else 'ALREADY_FIXED'
            report['after_sha256'] = digest(target.read_bytes())
            receipt = EVIDENCE / ('poultry-timeline-' + digest(new) + '.receipt.json')
            EVIDENCE.mkdir(parents=True, exist_ok=True)
            receipt.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report, indent=2))
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(json.dumps({'outcome': 'REFUSED', 'error': str(error), 'compilation': 'NOT_RUN'}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
