#!/usr/bin/env python3
import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
import re
import subprocess
import tempfile
import sys
import yaml

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / 'docs/ux/FARM_OS_SCREEN_REGISTRY.yaml'
IMPL_MAP = ROOT / 'docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml'
AUDIT = ROOT / 'docs/ux/evidence/animal-farm-visual-lock/navigation-source-audit.json'
RUNTIME_LEDGER = ROOT / 'docs/ux/evidence/animal-farm-visual-lock/phase5-runtime-navigation-ledger.json'
COMPLETION_STATE = ROOT / 'PROJECT_COMPLETION_STATE.json'
sys.path.insert(0, str(ROOT / 'scripts' / 'development'))
from feature_catalog import CATALOG_STATUS, feature_ids_for_screen


def git_sha():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()


def kotlin_ids():
    """Production Kotlin references only; tests are evidence, not implementation."""
    ids = set()
    pattern = re.compile(r'FOS-[A-Z]+-[0-9]+(?:-[A-Z])?')
    for base in ('app', 'feature', 'core'):
        for path in (ROOT / base).rglob('*.kt'):
            relative = path.relative_to(ROOT).as_posix()
            if '/src/main/' not in relative:
                continue
            ids.update(pattern.findall(path.read_text(errors='ignore')))
    return ids


def mapped_ids():
    data = yaml.safe_load(IMPL_MAP.read_text()) or {}
    ids = set()
    for surface in data.get('surfaces', []):
        ids.update(surface.get('target_screens') or [])
    return ids


def runtime_evidence():
    empty = {
        'ledger': None,
        'rendered': set(),
        'rendered_owner': set(),
        'entry': set(),
        'contract': set(),
        'parameter_scope': False,
    }
    if not RUNTIME_LEDGER.exists():
        return empty
    ledger = json.loads(RUNTIME_LEDGER.read_text())
    current_fingerprint = json.loads(COMPLETION_STATE.read_text())['source_fingerprint']
    if ledger.get('ci_status') != 'PASS_EXACT_HEAD_CI':
        return empty
    if ledger.get('source_fingerprint') != current_fingerprint:
        return empty
    coverage = ledger.get('coverage', {})
    certification = ledger.get('certification', {})
    return {
        'ledger': ledger,
        'rendered': set(coverage.get('rendered_traversal_screen_ids', [])),
        'rendered_owner': set(coverage.get('rendered_owner_screen_ids', [])),
        'entry': set(coverage.get('entry_action_screen_ids', [])),
        'contract': set(coverage.get('route_contract_screen_ids', [])),
        'parameter_scope': bool(certification.get('parameter_scope_executed')),
    }


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
    runtime = runtime_evidence()

    rows = []
    for screen in screens:
        sid = screen['screen_id']
        evidence = route_evidence(sid, mapped, code, role_ids)
        rendered = sid in runtime['rendered']
        rendered_owner = sid in runtime['rendered_owner']
        entry = sid in runtime['entry']
        contract = sid in runtime['contract']
        if rendered:
            note = 'Rendered destination traversal and return/restoration passed exact-head CI.'
        elif rendered_owner:
            note = 'Rendered surface asserted its exact canonical Screen ID under exact-head CI; return/restoration is not claimed.'
        elif entry:
            note = 'Entry action emitted the exact typed destination in CI; rendered target traversal remains unexecuted.'
        elif contract:
            note = 'Typed route ownership contract passed CI; rendered target traversal remains unexecuted.'
        elif evidence != 'NO_ROUTE_EVIDENCE':
            note = 'Static evidence only; execute route/interaction tests before treating as reachable.'
        else:
            note = 'No source-derived route evidence at checkpoint; implementation/contract discovery required.'
        rows.append({
            'screen_id': sid,
            'name': screen['name'],
            'module': screen['module'],
            'registry_status': 'MAPPED',
            'route_evidence': evidence,
            'runtime_reachability': 'EXECUTED_PASS_EXACT_HEAD_CI' if rendered else 'UNEXECUTED',
            'scoped_parameters': (
                'CONTRACT_EXECUTED_PASS_EXACT_HEAD_CI'
                if sid == 'FOS-TASK-003' and runtime['parameter_scope']
                else 'UNEXECUTED'
            ),
            'return_restoration': 'EXECUTED_PASS_EXACT_HEAD_CI' if rendered else 'UNEXECUTED',
            'authorization': 'UNEXECUTED',
            'deep_link': 'UNEXECUTED',
            'feature_ids': '|'.join(feature_ids_for_screen(sid)),
            'feature_contract': 'OPEN',
            'visual_status': 'NOT_GREEN',
            'feature_status': 'NOT_GREEN',
            'notes': note,
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

    runtime_ledger = runtime['ledger']
    summary = {
        'schema_version': 2,
        'tested_commit': sha,
        'evidence_class': 'STATIC_SOURCE_DERIVED_GAP_INVENTORY_WITH_SEPARATE_RUNTIME_EVIDENCE',
        'registry_rows': len(rows),
        'route_evidence_counts': dict(sorted(evidence_counts.items())),
        'no_route_evidence': evidence_counts['NO_ROUTE_EVIDENCE'],
        'some_static_route_evidence': len(rows) - evidence_counts['NO_ROUTE_EVIDENCE'],
        'runtime_reachability_executed': len(runtime['rendered']),
        'rendered_surface_owners_executed': len(runtime['rendered_owner']),
        'entry_action_emission_executed': len(runtime['entry']),
        'route_contracts_executed': len(runtime['contract']),
        'runtime_evidence': (
            {
                'ledger': str(RUNTIME_LEDGER.relative_to(ROOT)).replace('\\', '/'),
                'tested_commit': runtime_ledger['tested_commit'],
                'source_fingerprint': runtime_ledger['source_fingerprint'],
                'foundation_run_id': runtime_ledger['foundation_run_id'],
                'ci_status': runtime_ledger['ci_status'],
            }
            if runtime_ledger
            else None
        ),
        'feature_id_catalog_status': CATALOG_STATUS,
        'module_gap_counts': dict(sorted(module_gaps.items())),
        'known_navigation_defects': audit['findings'],
        'status_law': 'MAPPED inventory is not CONTRACT_READY, IMPLEMENTED, VISUAL_GREEN, FEATURE_GREEN, MODULE_GREEN, or MVP_GREEN. Entry-action and route-contract evidence do not substitute for rendered traversal.',
    }
    out_summary.parent.mkdir(parents=True, exist_ok=True)
    out_summary.write_text(json.dumps(summary, indent=2) + '\n')

    if args.self_test:
        assert len(rows) == 545
        assert len({row['screen_id'] for row in rows}) == 545
        assert sum(row['runtime_reachability'] == 'EXECUTED_PASS_EXACT_HEAD_CI' for row in rows) == len(runtime['rendered'])
        assert sum(row['return_restoration'] == 'EXECUTED_PASS_EXACT_HEAD_CI' for row in rows) == len(runtime['rendered'])
        assert all(row['feature_ids'].startswith('FTR-') for row in rows)
        assert all(row['visual_status'] == 'NOT_GREEN' and row['feature_status'] == 'NOT_GREEN' for row in rows)
        print(
            'PASS route-screen-feature gap inventory self-test: '
            f"{len(runtime['rendered'])} rendered runtime / "
            f"{len(runtime['rendered_owner'])} rendered-owner / "
            f"{len(runtime['entry'])} entry-action / "
            f"{len(runtime['contract'])} route-contract"
        )
        temp_dir.cleanup()


if __name__ == '__main__':
    main()
