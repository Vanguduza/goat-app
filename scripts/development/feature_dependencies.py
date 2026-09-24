#!/usr/bin/env python3
"""Build and verify the canonical Farm OS feature completion dependency DAG."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from feature_catalog import catalog_features

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/realisation/FEATURE_DEPENDENCY_GRAPH.json"
DEPENDENCIES = json.loads(r'''{"FTR-GLOBAL-001":[],"FTR-GLOBAL-002":["FTR-GLOBAL-001"],"FTR-GLOBAL-003":["FTR-GLOBAL-001","FTR-GLOBAL-002"],"FTR-GLOBAL-004":["FTR-GLOBAL-003"],"FTR-GLOBAL-005":["FTR-GLOBAL-001","FTR-GLOBAL-003"],"FTR-HOME-001":["FTR-GLOBAL-003"],"FTR-HOME-002":["FTR-GLOBAL-003","FTR-HOME-001"],"FTR-HOME-003":["FTR-HOME-001","FTR-SEARCH-001"],"FTR-HOME-004":["FTR-HOME-001","FTR-ATOM-001","FTR-ATOM-002"],"FTR-HOME-005":["FTR-HOME-001","FTR-SYNC-001"],"FTR-HOME-006":["FTR-HOME-001","FTR-GLOBAL-003"],"FTR-HOME-007":["FTR-HOME-001"],"FTR-GOAT-001":["FTR-HOME-002","FTR-SEARCH-001","FTR-GROUP-001"],"FTR-GOAT-002":["FTR-GOAT-001"],"FTR-GOAT-003":["FTR-GOAT-001"],"FTR-GOAT-004":["FTR-GOAT-001","FTR-HEALTH-001","FTR-HEALTH-002"],"FTR-GOAT-005":["FTR-GOAT-001"],"FTR-GOAT-006":["FTR-GOAT-005"],"FTR-GOAT-007":["FTR-GOAT-001","FTR-GOAT-005","FTR-GOAT-006"],"FTR-GOAT-008":["FTR-GOAT-001","FTR-GROUP-001","FTR-PASTURE-002"],"FTR-GOAT-009":["FTR-GOAT-001"],"FTR-GOAT-010":["FTR-GOAT-002","FTR-GOAT-003","FTR-GOAT-004","FTR-GOAT-005","FTR-GOAT-006","FTR-GOAT-007","FTR-GOAT-008","FTR-GOAT-009"],"FTR-RABBIT-001":["FTR-HOME-002","FTR-SEARCH-001","FTR-GROUP-001"],"FTR-RABBIT-002":["FTR-RABBIT-001"],"FTR-RABBIT-003":["FTR-RABBIT-001","FTR-RABBIT-002"],"FTR-RABBIT-004":["FTR-RABBIT-003"],"FTR-RABBIT-005":["FTR-RABBIT-004"],"FTR-RABBIT-006":["FTR-RABBIT-001","FTR-RABBIT-004","FTR-HEALTH-001"],"FTR-RABBIT-007":["FTR-RABBIT-001","FTR-RABBIT-002","FTR-RABBIT-003","FTR-RABBIT-004","FTR-RABBIT-005","FTR-RABBIT-006"],"FTR-SHEEP-001":["FTR-HOME-002","FTR-SEARCH-001","FTR-GROUP-001"],"FTR-SHEEP-002":["FTR-SHEEP-001"],"FTR-SHEEP-003":["FTR-SHEEP-001"],"FTR-SHEEP-004":["FTR-SHEEP-001"],"FTR-SHEEP-005":["FTR-SHEEP-001","FTR-HEALTH-001","FTR-HEALTH-002"],"FTR-SHEEP-006":["FTR-SHEEP-001","FTR-SHEEP-003","FTR-GROUP-001","FTR-PASTURE-002"],"FTR-SHEEP-007":["FTR-SHEEP-001","FTR-SHEEP-002","FTR-SHEEP-003","FTR-SHEEP-004","FTR-SHEEP-005","FTR-SHEEP-006"],"FTR-CATTLE-001":["FTR-HOME-002","FTR-SEARCH-001","FTR-GROUP-001"],"FTR-CATTLE-002":["FTR-CATTLE-001"],"FTR-CATTLE-003":["FTR-CATTLE-001"],"FTR-CATTLE-004":["FTR-CATTLE-001"],"FTR-CATTLE-005":["FTR-CATTLE-001","FTR-FEED-002"],"FTR-CATTLE-006":["FTR-CATTLE-001","FTR-CATTLE-003","FTR-GROUP-001","FTR-HEALTH-001"],"FTR-CATTLE-007":["FTR-CATTLE-001","FTR-CATTLE-002","FTR-CATTLE-003","FTR-CATTLE-004","FTR-CATTLE-005","FTR-CATTLE-006"],"FTR-POULTRY-001":["FTR-HOME-002","FTR-SEARCH-001","FTR-GROUP-001"],"FTR-POULTRY-002":["FTR-POULTRY-001","FTR-FEED-001"],"FTR-POULTRY-003":["FTR-POULTRY-001","FTR-HEALTH-001"],"FTR-POULTRY-004":["FTR-POULTRY-001"],"FTR-POULTRY-005":["FTR-POULTRY-001","FTR-HEALTH-001"],"FTR-POULTRY-006":["FTR-POULTRY-001","FTR-POULTRY-002","FTR-POULTRY-003","FTR-POULTRY-004","FTR-POULTRY-005"],"FTR-HEALTH-001":["FTR-GLOBAL-003","FTR-HOME-001"],"FTR-HEALTH-002":["FTR-HEALTH-001","FTR-INV-003"],"FTR-HEALTH-003":["FTR-HEALTH-001","FTR-INV-003"],"FTR-HEALTH-004":["FTR-HEALTH-001"],"FTR-HEALTH-005":["FTR-HEALTH-002","FTR-HEALTH-003","FTR-HEALTH-004","FTR-TASK-001"],"FTR-HEALTH-006":["FTR-HEALTH-001"],"FTR-HEALTH-007":["FTR-HEALTH-001"],"FTR-HEALTH-008":["FTR-HEALTH-001"],"FTR-HEALTH-009":["FTR-HEALTH-001","FTR-HEALTH-002","FTR-HEALTH-003","FTR-HEALTH-006","FTR-HEALTH-007"],"FTR-TASK-001":["FTR-GLOBAL-003","FTR-HOME-001"],"FTR-TASK-002":["FTR-TASK-001"],"FTR-TASK-003":["FTR-TASK-001","FTR-ATOM-002"],"FTR-TASK-004":["FTR-TASK-001","FTR-TASK-002"],"FTR-INV-001":["FTR-GLOBAL-003","FTR-SEARCH-001"],"FTR-INV-002":["FTR-INV-001"],"FTR-INV-003":["FTR-INV-001","FTR-INV-002"],"FTR-INV-004":["FTR-INV-001"],"FTR-INV-005":["FTR-INV-001","FTR-INV-002","FTR-INV-003"],"FTR-INV-006":["FTR-INV-001","FTR-INV-005"],"FTR-FEED-001":["FTR-INV-001"],"FTR-FEED-002":["FTR-FEED-001","FTR-TASK-002"],"FTR-FEED-003":["FTR-FEED-001","FTR-INV-003"],"FTR-FEED-004":["FTR-FEED-001","FTR-FEED-002"],"FTR-FEED-005":["FTR-FEED-001","FTR-FEED-002","FTR-FEED-003","FTR-FEED-004"],"FTR-WATER-001":["FTR-GLOBAL-003"],"FTR-WATER-002":["FTR-WATER-001"],"FTR-WATER-003":["FTR-WATER-001","FTR-TASK-001"],"FTR-WATER-004":["FTR-WATER-001","FTR-WATER-002","FTR-WATER-003"],"FTR-PASTURE-001":["FTR-GLOBAL-003"],"FTR-PASTURE-002":["FTR-PASTURE-001","FTR-GROUP-001"],"FTR-PASTURE-003":["FTR-PASTURE-001","FTR-PASTURE-002"],"FTR-PASTURE-004":["FTR-PASTURE-001","FTR-PASTURE-002","FTR-PASTURE-003"],"FTR-GROUP-001":["FTR-GLOBAL-003"],"FTR-GROUP-002":["FTR-GROUP-001","FTR-HEALTH-001"],"FTR-GROUP-003":["FTR-GROUP-001","FTR-GROUP-002"],"FTR-LABOUR-001":["FTR-GLOBAL-003","FTR-ADMIN-002"],"FTR-LABOUR-002":["FTR-LABOUR-001","FTR-TASK-001"],"FTR-LABOUR-003":["FTR-LABOUR-001","FTR-LABOUR-002"],"FTR-LABOUR-004":["FTR-LABOUR-001","FTR-LABOUR-002","FTR-LABOUR-003"],"FTR-ASSET-001":["FTR-GLOBAL-003"],"FTR-ASSET-002":["FTR-ASSET-001","FTR-TASK-001"],"FTR-ASSET-003":["FTR-ASSET-001","FTR-ATOM-002"],"FTR-ASSET-004":["FTR-ASSET-001","FTR-ASSET-002","FTR-ASSET-003"],"FTR-FIN-001":["FTR-GLOBAL-003","FTR-ADMIN-003"],"FTR-FIN-002":["FTR-FIN-001"],"FTR-FIN-003":["FTR-FIN-001"],"FTR-FIN-004":["FTR-FIN-001","FTR-FIN-002","FTR-FIN-003"],"FTR-SALES-001":["FTR-GLOBAL-003"],"FTR-SALES-002":["FTR-SALES-001","FTR-FIN-001","FTR-INV-001"],"FTR-SALES-003":["FTR-SALES-002","FTR-INV-003"],"FTR-SALES-004":["FTR-SALES-002","FTR-SALES-003","FTR-FIN-001"],"FTR-PROC-001":["FTR-GLOBAL-003"],"FTR-PROC-002":["FTR-PROC-001","FTR-INV-001","FTR-FIN-001"],"FTR-PROC-003":["FTR-PROC-002","FTR-INV-002"],"FTR-PROC-004":["FTR-PROC-002","FTR-PROC-003"],"FTR-CAP-001":["FTR-FEED-001","FTR-WATER-001","FTR-PASTURE-001","FTR-ASSET-001","FTR-GOAT-001","FTR-RABBIT-001","FTR-SHEEP-001","FTR-CATTLE-001","FTR-POULTRY-001"],"FTR-CAP-002":["FTR-CAP-001"],"FTR-CAP-003":["FTR-CAP-001","FTR-CAP-002"],"FTR-GEN-001":["FTR-GOAT-001","FTR-RABBIT-001","FTR-SHEEP-001","FTR-CATTLE-001"],"FTR-GEN-002":["FTR-GEN-001"],"FTR-GEN-003":["FTR-GEN-001","FTR-GEN-002"],"FTR-GEN-004":["FTR-GEN-002","FTR-GEN-003"],"FTR-AN-001":["FTR-GOAT-010","FTR-RABBIT-007","FTR-SHEEP-007","FTR-CATTLE-007","FTR-POULTRY-006"],"FTR-AN-002":["FTR-AN-001","FTR-HEALTH-009","FTR-FEED-005"],"FTR-AN-003":["FTR-FIN-004","FTR-SALES-004","FTR-PROC-004"],"FTR-AN-004":["FTR-AN-001","FTR-AN-002","FTR-AN-003"],"FTR-AN-005":["FTR-AN-002","FTR-AN-003"],"FTR-AN-006":["FTR-AN-001","FTR-AN-005"],"FTR-SIM-001":["FTR-HOME-001"],"FTR-SIM-002":["FTR-SIM-001","FTR-CAP-002","FTR-AN-002","FTR-FIN-003","FTR-FEED-004"],"FTR-SIM-003":["FTR-SIM-002"],"FTR-SIM-004":["FTR-SIM-002","FTR-SIM-003"],"FTR-SEARCH-001":["FTR-GLOBAL-001","FTR-GLOBAL-003","FTR-ATOM-003"],"FTR-SEARCH-002":["FTR-SEARCH-001","FTR-SYNC-001"],"FTR-SEARCH-003":["FTR-SEARCH-001","FTR-ATOM-002"],"FTR-SEARCH-004":["FTR-SEARCH-001"],"FTR-AI-001":["FTR-GLOBAL-003"],"FTR-AI-002":["FTR-AI-001","FTR-AN-006"],"FTR-AI-003":["FTR-AI-001","FTR-AI-002","FTR-AN-005"],"FTR-AI-004":["FTR-AI-001","FTR-ADMIN-006"],"FTR-AI-005":["FTR-AI-001","FTR-ADMIN-006","FTR-ADMIN-007"],"FTR-REPORT-001":["FTR-GLOBAL-003"],"FTR-REPORT-002":["FTR-REPORT-001","FTR-GOAT-010","FTR-RABBIT-007","FTR-SHEEP-007","FTR-CATTLE-007","FTR-POULTRY-006","FTR-HEALTH-009","FTR-INV-006","FTR-FIN-004"],"FTR-REPORT-003":["FTR-REPORT-001","FTR-REPORT-002"],"FTR-REPORT-004":["FTR-REPORT-003","FTR-ATOM-006"],"FTR-REPORT-005":["FTR-REPORT-003","FTR-ADMIN-009"],"FTR-ADMIN-001":["FTR-GLOBAL-003"],"FTR-ADMIN-002":["FTR-GLOBAL-003","FTR-ADMIN-001"],"FTR-ADMIN-003":["FTR-GLOBAL-003","FTR-ADMIN-001"],"FTR-ADMIN-004":["FTR-ADMIN-001","FTR-SYNC-001","FTR-SEARCH-001"],"FTR-ADMIN-005":["FTR-ADMIN-001","FTR-ATOM-002"],"FTR-ADMIN-006":["FTR-ADMIN-001"],"FTR-ADMIN-007":["FTR-GLOBAL-001","FTR-ADMIN-001"],"FTR-ADMIN-008":["FTR-ADMIN-001"],"FTR-ADMIN-009":["FTR-ADMIN-001","FTR-ADMIN-002"],"FTR-ADMIN-010":["FTR-ADMIN-001"],"FTR-SYNC-001":["FTR-GLOBAL-001","FTR-GLOBAL-003"],"FTR-SYNC-002":["FTR-SYNC-001"],"FTR-SYNC-003":["FTR-GLOBAL-001","FTR-GLOBAL-003","FTR-SYNC-001"],"FTR-SYNC-004":["FTR-SYNC-001"],"FTR-SYNC-005":["FTR-SYNC-001","FTR-SYNC-002","FTR-SYNC-004"],"FTR-ATOM-001":[],"FTR-ATOM-002":[],"FTR-ATOM-003":[],"FTR-ATOM-004":[],"FTR-ATOM-005":[],"FTR-ATOM-006":[]}''')
GLOBAL_PROGRAMME_GATES = json.loads(r'''[{"gate_id":"PLATFORM_DECIMAL_SCALING_EXACT","status":"OPEN","blocking_phase":3,"evidence":["app/src/main/java/com/farmos/app/SalesModuleHost.kt","app/src/main/java/com/farmos/app/ProcurementModuleHost.kt","app/src/main/java/com/farmos/app/RabbitCommerceModuleHost.kt"],"requirement":"Replace floating-point scale-and-truncate input conversion with exact checked decimal-to-minor/milli parsing and tests."},{"gate_id":"PLATFORM_SYNC_SINGLE_OWNER_ATOMIC_CLAIM","status":"OPEN","blocking_phase":3,"evidence":["app/src/main/java/com/farmos/app/FarmSessionContent.kt","core/database/src/main/kotlin/com/farmos/core/database/FarmOsDatabase.kt","core/sync/src/main/kotlin/com/farmos/core/sync/SyncEngine.kt"],"requirement":"Serialize on-demand sync scheduling and atomically claim eligible outbox rows so concurrent drains cannot send the same mutation."},{"gate_id":"PLATFORM_SESSION_REVOCATION","status":"OPEN","blocking_phase":3,"evidence":["core/network/src/main/kotlin/com/farmos/core/network/SupabaseClients.kt"],"requirement":"Define and implement server-backed user sign-out/revocation semantics while preserving deterministic local credential clearing and offline recovery."}]''')

def build_graph() -> dict:
    features = catalog_features()
    by_id = {f["feature_id"]: f for f in features}
    ids = set(by_id)
    if set(DEPENDENCIES) != ids:
        raise SystemExit(f"dependency node set differs from feature catalogue: missing={sorted(ids-set(DEPENDENCIES))}, extra={sorted(set(DEPENDENCIES)-ids)}")
    reverse = {fid: [] for fid in ids}
    edge_count = 0
    for fid, parents in DEPENDENCIES.items():
        if len(parents) != len(set(parents)):
            raise SystemExit(f"duplicate dependency on {fid}")
        for parent in parents:
            if parent not in ids:
                raise SystemExit(f"unknown dependency {parent} on {fid}")
            if parent == fid:
                raise SystemExit(f"self dependency on {fid}")
            if by_id[parent]["completion_phase"] > by_id[fid]["completion_phase"]:
                raise SystemExit(f"phase inversion: {fid} depends on later-phase {parent}")
            reverse[parent].append(fid)
            edge_count += 1

    indegree = {fid: len(DEPENDENCIES[fid]) for fid in ids}
    queue = sorted(fid for fid, count in indegree.items() if count == 0)
    topo = []
    while queue:
        current = queue.pop(0)
        topo.append(current)
        for child in sorted(reverse[current]):
            indegree[child] -= 1
            if indegree[child] == 0:
                queue.append(child)
                queue.sort()
    if len(topo) != len(features):
        cyclic = sorted(fid for fid, count in indegree.items() if count > 0)
        raise SystemExit(f"dependency cycle detected: {cyclic}")

    roots = [fid for fid in topo if not DEPENDENCIES[fid]]
    return {
        "schema_version": 1,
        "authority": "docs/00_PROJECT_TRUTH.md + owner deterministic completion plan",
        "feature_catalog": "docs/realisation/FEATURE_REGISTRY.yaml",
        "semantics": "depends_on is a completion prerequisite, not a runtime call graph and not a green claim.",
        "summary": {
            "node_count": len(features), "edge_count": edge_count, "root_count": len(roots),
            "roots": roots, "cycle_free": True, "phase_monotonic": True,
        },
        "global_programme_gates": GLOBAL_PROGRAMME_GATES,
        "nodes": [
            {
                "feature_id": f["feature_id"], "module": f["module"], "name": f["name"],
                "completion_phase": f["completion_phase"], "depends_on": DEPENDENCIES[f["feature_id"]],
                "blocks": sorted(reverse[f["feature_id"]]),
            }
            for f in features
        ],
    }

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    graph = build_graph()
    text = json.dumps(graph, indent=2) + "\n"
    if args.check:
        if not OUT.exists() or json.loads(OUT.read_text()) != graph:
            raise SystemExit("stale feature dependency graph: run scripts/development/feature_dependencies.py")
        print(f"PASS feature dependency DAG: {graph['summary']['node_count']} nodes, {graph['summary']['edge_count']} edges, zero cycles")
    else:
        OUT.write_text(text, encoding="utf-8")
        print(f"generated feature dependency DAG: {graph['summary']['node_count']} nodes, {graph['summary']['edge_count']} edges")

if __name__ == "__main__":
    main()
