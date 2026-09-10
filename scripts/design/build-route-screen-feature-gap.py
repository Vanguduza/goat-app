#!/usr/bin/env python3
import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
import re
import subprocess
import tempfile
import yaml

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / 'docs/ux/FARM_OS_SCREEN_REGISTRY.yaml'
IMPL_MAP = ROOT / 'docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml'
AUDIT = ROOT / 'docs/ux/evidence/animal-farm-visual-lock/navigation-source-audit.json'


def git_sha():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()


def kotlin_ids():
    ids = set()
    pattern = re.compile(r'FOS-[A-Z]+-[0-9]+(?:-[A-Z])?')
    for base in ('app', 'feature', 'core'):
        for path in (ROOT / base).rglob('*.kt'):
            ids.update(pattern.findall(path.read_text(errors='ignore')))
    return ids


def mapped_ids():
    data = yaml.safe_load(IMPL_MAP.read_text()) or {}
    ids = set()
    for surface in data.get('surfaces', []):
        ids.update(surface.get('target_screens') or [])
    return ids


def role_dispatch_ids():
    return {f'FOS-HOME-012-{letter}' for letter in 'ABCDEFGH'}


def route_evidence(screen_id, mapped, code, role_ids):
    if screen_id in role_ids:
        return 'STATIC_ROLE_DISPATCH_PRESENT'
    if screen_id == 'FOS-GLOBAL-001':
        return 'STATIC_MAIN_ACTIVITY_SPLASH_BRANCH'
    if screen_id in {'FOS-GLOBAL-002', 'FOS-GLOBAL-005', 'FOS-GLOBAL-006', 'FOS-GLOBAL-018'}:
        return 'STATIC_AUTH_STATE_BRANCH'
    if screen_id == 'FOS-HOME-001':
        return 'STATIC_GENERAL_HOME_FALLBACK'
    if screen_id == 'FOS-HOME-002':
        return 'STATIC_HOME_NESTED_DESTINATION'
    if screen_id in mapped and screen_id in code:
        return 'STATIC_SOURCE_MAP_PLUS_CODE_ID_REFERENCE'
    if screen_id in mapped:
        return 'STATIC_SOURCE_MAP_HINT_ONLY'
    if screen_id in code:
        return 'STATIC_CODE_ID_REFERENCE_ONLY'
    return 'NO_ROUTE_EVIDENCE'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--out-csv',
        default='docs/ux/evidence/animal-farm-visual-lock/route-screen-feature-gap.csv',
    )
    parser.add_argument(
        '--out-summary',
        default='docs/ux/evidence/animal-farm-visual-lock/route-screen-feature-gap-summary.json',
    )
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()

    sha = git_sha()
    temp_dir = None
    if args.self_test:
        temp_dir = tempfile.TemporaryDirectory(prefix='animal-farm-gap-self-test-')
        temp_root = Path(temp_dir.name)
        audit_path = temp_root / 'navigation-source-audit.json'
        subprocess.run(
            [
                'node',
                str(ROOT / 'scripts/design/audit-navigation.cjs'),
                '--out',
                str(audit_path),
                '--quiet',
            ],
            cwd=ROOT,
            check=True,
        )
        out_csv = temp_root / 'route-screen-feature-gap.csv'
        out_summary = temp_root / 'route-screen-feature-gap-summary.json'
    else:
        audit_path = AUDIT
        out_csv = ROOT / args.out_csv
        out_summary = ROOT / args.out_summary

    registry = yaml.safe_load(REGISTRY.read_text())
    screens = registry['screens']
    mapped = mapped_ids()
    code = kotlin_ids()
    role_ids = role_dispatch_ids()
    audit = json.loads(audit_path.read_text())
    if audit['tested_commit'] != sha:
        raise SystemExit(f'navigation audit is stale: {audit["tested_commit"]} != {sha}')

    rows = []
    for screen in screens:
        sid = screen['screen_id']
        evidence = route_evidence(sid, mapped, code, role_ids)
        rows.append({
            'screen_id': sid,
            'name': screen['name'],
            'module': screen['module'],
            'registry_status': 'MAPPED',
            'route_evidence': evidence,
            'runtime_reachability': 'UNEXECUTED',
            'scoped_parameters': 'UNEXECUTED',
            'return_restoration': 'UNEXECUTED',
            'authorization': 'UNEXECUTED',
            'deep_link': 'UNEXECUTED',
            'feature_ids': 'UNRESOLVED_CANONICAL_FEATURE_ID_CATALOG',
            'feature_contract': 'OPEN',
            'visual_status': 'NOT_GREEN',
            'feature_status': 'NOT_GREEN',
            'notes': (
                'Static evidence only; execute route/interaction tests before treating as reachable.'
                if evidence != 'NO_ROUTE_EVIDENCE'
                else 'No source-derived route evidence at checkpoint; implementation/contract discovery required.'
            ),
        })

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open('w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)

    evidence_counts = Counter(row['route_evidence'] for row in rows)
    module_gaps = defaultdict(lambda: {'total': 0, 'no_route_evidence': 0})
    for row in rows:
        module_gaps[row['module']]['total'] += 1
        if row['route_evidence'] == 'NO_ROUTE_EVIDENCE':
            module_gaps[row['module']]['no_route_evidence'] += 1

    summary = {
        'schema_version': 1,
        'tested_commit': sha,
        'evidence_class': 'STATIC_SOURCE_DERIVED_GAP_INVENTORY_NOT_RUNTIME_CERTIFICATION',
        'registry_rows': len(rows),
        'route_evidence_counts': dict(sorted(evidence_counts.items())),
        'no_route_evidence': evidence_counts['NO_ROUTE_EVIDENCE'],
        'some_static_route_evidence': len(rows) - evidence_counts['NO_ROUTE_EVIDENCE'],
        'runtime_reachability_executed': 0,
        'feature_id_catalog_status': 'MISSING_OR_NOT_CANONICALLY_IDENTIFIED',
        'module_gap_counts': dict(sorted(module_gaps.items())),
        'known_navigation_defects': audit['findings'],
        'status_law': 'MAPPED inventory is not CONTRACT_READY, IMPLEMENTED, VISUAL_GREEN, FEATURE_GREEN, MODULE_GREEN, or MVP_GREEN.',
    }
    out_summary.parent.mkdir(parents=True, exist_ok=True)
    out_summary.write_text(json.dumps(summary, indent=2) + '\n')

    if args.self_test:
        assert len(rows) == 545
        assert len({row['screen_id'] for row in rows}) == 545
        assert all(row['runtime_reachability'] == 'UNEXECUTED' for row in rows)
        assert all(row['feature_ids'] == 'UNRESOLVED_CANONICAL_FEATURE_ID_CATALOG' for row in rows)
        assert all(row['visual_status'] == 'NOT_GREEN' and row['feature_status'] == 'NOT_GREEN' for row in rows)
        print('PASS route-screen-feature gap inventory self-test')
        temp_dir.cleanup()


if __name__ == '__main__':
    main()
