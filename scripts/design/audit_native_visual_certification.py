#!/usr/bin/env python3
"""Inventory all registered IDs and verified rendered-owner evidence without granting approval.

Read-only on repository authority inputs. Outputs go to an explicitly selected directory.
A screenshot filename, a source-string match, successful CI or this report cannot certify a
screen. Independent contract/baseline review and the canonical release gate remain required.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import html
import itertools
import json
import re
import struct
import zlib
import subprocess
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ID = re.compile(r"FOS-[A-Z]+-[0-9]{3}(?:-[A-Z])?")
SHA = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-f]{64}")
THEMES = ("LIGHT", "DARK", "OUTDOOR")
WIDTHS = (360, 411, 780)
SCALES = (1.0, 1.3, 2.0)
CELLS = tuple(itertools.product(THEMES, WIDTHS, SCALES))
PACK = Path("docs/ux/animal-farm-visual-lock")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_dimensions(raw: bytes) -> tuple[int, int]:
    require(raw[:8] == b"\x89PNG\r\n\x1a\n", "Not a PNG")
    offset, first, ended, data = 8, True, False, False
    width = height = 0
    while offset < len(raw):
        require(offset + 12 <= len(raw), "Truncated PNG chunk")
        size = struct.unpack_from(">I", raw, offset)[0]
        require(size <= 64 * 1024 * 1024 and offset + 12 + size <= len(raw), "Truncated/oversized PNG chunk")
        kind = raw[offset + 4:offset + 8]
        body = raw[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", raw, offset + 8 + size)[0]
        require(zlib.crc32(kind + body) & 0xffffffff == crc, "PNG chunk checksum mismatch")
        if first:
            require(kind == b"IHDR" and size == 13, "PNG must begin with IHDR")
            width, height = struct.unpack_from(">II", body)
            require(0 < width <= 32768 and 0 < height <= 32768, "Invalid PNG dimensions")
            first = False
        elif kind == b"IHDR":
            raise ValueError("Duplicate PNG header")
        if kind == b"IDAT":
            data = data or bool(size)
        if kind == b"IEND":
            require(size == 0 and data, "PNG has no image data or invalid end")
            ended = True
            offset += 12
            break
        offset += size + 12
    require(ended and offset == len(raw), "Incomplete PNG or trailing data")
    return width, height


def child(root: Path, relative: str) -> Path:
    p = Path(relative)
    require(not p.is_absolute() and ".." not in p.parts, "Unsafe evidence path")
    target = (root / p).resolve(strict=True)
    require(target.is_relative_to(root.resolve()), "Evidence leaves its bundle")
    require(target.is_file(), "Evidence is not a file")
    return target


def scope(root: Path) -> list[dict[str, Any]]:
    raw = (root / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml").read_text()
    rows = [dict(screen_id=m[0], name=m[1].strip(), module=m[2].strip()) for m in re.findall(
        r"^  - screen_id: (FOS-[A-Z]+-[0-9]{3}(?:-[A-Z])?)\r?\n    name: (.+)\r?\n    module: (.+)", raw, re.M)]
    require(len(rows) == 545 and len({x["screen_id"] for x in rows}) == 545, "Exact 545-screen scope required; no silent omissions")
    require(len(re.findall(r"^  - screen_id:", raw, re.M)) == 545, "Unsupported registry shape")
    alignment = json.loads((root / PACK / "registry/page-alignment.json").read_text())["screens"]
    by_id = {x["screen_id"]: x for x in alignment}
    require(len(alignment) == 545 and set(by_id) == {x["screen_id"] for x in rows}, "Visual assignment scope differs")
    qdu = {x["screen_id"]: x for x in json.loads((root / "docs/realisation/QDU_REGISTRY.json").read_text())["qdus"]}
    require(set(qdu) == set(by_id), "QDU inventory differs from registry")
    for row in rows:
        a = by_id[row["screen_id"]]
        require(a["name"] == row["name"] and a["module"] == row["module"], "Registry identity drift")
        row.update(recipe=a["recipe"], source_candidates=a["source_paths"],
                   feature_ids=qdu[row["screen_id"]]["feature_ids"],
                   implementation_inventory=qdu[row["screen_id"]]["state"])
    return rows


def passing_cases(bundle: Path) -> set[tuple[str, str]]:
    cases: dict[tuple[str, str], str] = {}
    for path in bundle.rglob("*.xml"):
        if "test-results" not in str(path) and "androidTest-results" not in str(path):
            continue
        root = ET.fromstring(path.read_bytes())
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            for case in suite.findall("testcase"):
                key = (case.get("classname") or suite.get("name", ""), case.get("name", ""))
                result = "FAIL" if case.find("failure") is not None or case.find("error") is not None else "SKIP" if case.find("skipped") is not None else "PASS"
                require(key not in cases or cases[key] == result, "Inconsistent duplicate JUnit case")
                cases[key] = result
    require(cases and all(value != "FAIL" for value in cases.values()), "Bundle has failed tests or no retained JUnit")
    return {key for key, value in cases.items() if value == "PASS"}


def native_proof(path: Path, known: set[str], cases: set[tuple[str, str]]) -> dict[str, Any] | None:
    d = json.loads(path.read_text())
    if d.get("schema_version") != 2 or d.get("evidence_class") != "OBSERVED_NATIVE_PRODUCT_OWNER":
        return None  # Legacy filenames, collector fixtures and unowned states cannot certify coverage.
    owner = d.get("screen_id")
    require(owner in known and d.get("owner_observed_before_capture") is True, "No exact canonical owner")
    clazz = d.get("test_class", "")
    short = clazz.rsplit(".", 1)[-1] + "."
    require(d.get("test", "").startswith(short), "Capture/JUnit identity mismatch")
    method = d["test"][len(short):]
    require((clazz, method) in cases, "Capture test did not pass in retained JUnit")
    cell = (d.get("mode"), d.get("width_dp"), float(d.get("font_scale", 0)))
    require(cell in CELLS, "Capture outside required native matrix")
    require(isinstance(d.get("visit"), int) and d["visit"] >= 1, "Unidentified capture visit")
    pages = d.get("viewports")
    require(isinstance(pages, list) and pages, "No viewport evidence")
    observed: set[str] = set()
    findings: Counter[str] = Counter()
    images = []
    for page in pages:
        image = child(path.parent, page["image"])
        require(SHA256.fullmatch(page.get("image_sha256", "")) is not None and digest(image) == page["image_sha256"], "Native image digest changed")
        width, height = png_dimensions(image.read_bytes())
        require(width == page["width_px"] and height == page["height_px"] and width > 0 and height > 0, "Image dimensions mismatch")
        density = float(page["density"])
        require(d.get("actual_device_width_dp") == cell[1], "Rendered device configuration is not declared width")
        require(density > 0 and 0 < width / density <= cell[1] + 1.0, "Captured window exceeds device viewport")
        require(abs(float(page["actual_font_scale"]) - cell[2]) <= 0.001, "Rendered font scale differs from declared scale")
        tags = set(page.get("visible_tags", []))
        ids = set(page.get("observed_owner_ids", []))
        require(ids <= known, "Native proof names an unregistered owner")
        bindings = page.get("canonical_owner_bindings", [])
        def observed_owner(sid: str) -> bool:
            if "farm-screen:" + sid in tags or "farm-atom:" + sid in tags:
                return True
            if sid != "FOS-ATOM-001":
                return False
            return any(binding.get("id") == sid and binding.get("tag") in tags
                and binding.get("tag", "").startswith("farm-atom:FOS-ATOM-001:")
                and (binding.get("kind") == "SCOPED_DATE_EDITABLE_FIELD" or
                     binding.get("kind") == "SCOPED_DATE_CALENDAR" and binding.get("tag", "").endswith(":calendar"))
                for binding in bindings)
        require(all(observed_owner(sid) for sid in ids), "Filename/text invented owner identity")
        observed.update(ids)
        findings.update(x["kind"] for x in page.get("layout_findings", []))
        images.append({"path": str(image), "sha256": page["image_sha256"], "observed_ids": sorted(ids)})
    require(owner in observed, "Requested owner never appears in any captured viewport")
    full = d.get("vertical_sweep_complete") is True and d.get("scroll_restored") is True and d.get("incomplete_reasons") == []
    return {"owner": owner, "observed_ids": sorted(observed), "cell": list(cell), "test_class": clazz,
            "test_method": method, "visit": d["visit"], "variant": d.get("variant"), "manifest": str(path),
            "manifest_sha256": digest(path), "full_vertical_sweep": full, "layout_findings": dict(findings), "images": images,
            "states_and_role_attestation": "NOT_INFERRED_FROM_TEST_NAMES"}


def bundle_proofs(bundle: Path, known: set[str], expected_tree: str) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    tree_files = list(bundle.rglob("tree.txt"))
    status_files = list(bundle.rglob("worktree-status.txt"))
    commit_files = list(bundle.rglob("commit.txt"))
    require(tree_files and all(p.read_text().strip() == expected_tree for p in tree_files), "Evidence belongs to a different source tree")
    require(status_files and all(p.read_text().strip() == "" for p in status_files), "Evidence worktree was dirty or unreported")
    require(commit_files and all(SHA.fullmatch(p.read_text().strip()) for p in commit_files), "Missing checkout commit identity")
    cases = passing_cases(bundle)
    proofs, rejected = [], []
    for p in bundle.rglob("*.json"):
        if not ("native-module-review" in str(p) or "captures" in p.parts):
            continue
        try:
            proof = native_proof(p, known, cases)
            if proof is not None:
                proofs.append(proof)
        except (ValueError, KeyError, TypeError, OSError) as error:
            rejected.append({"path": str(p), "reason": str(error)})
    return proofs, rejected


def contract_findings(root: Path, sid: str) -> tuple[dict[str, Any] | None, list[str]]:
    p = root / "docs/ux/screen-contracts" / (sid + ".json")
    if not p.is_file():
        return None, ["SCREEN_CONTRACT_MISSING"]
    d = json.loads(p.read_text())
    problems = []
    if d.get("screen_id") != sid:
        problems.append("SCREEN_CONTRACT_WRONG_ID")
    if not d.get("feature_ids"):
        problems.append("SCREEN_CONTRACT_FEATURE_BINDING_MISSING")
    if not d.get("state_matrix") or not d.get("accessibility_tests"):
        problems.append("STATE_OR_ACCESSIBILITY_CONTRACT_INCOMPLETE")
    if re.search(r"REQUIRED|REPLACE_ME|REPLACE_WITH_EACH|UNREVIEWED", json.dumps(d)):
        problems.append("UNFINISHED_CONTRACT_FIELDS")
    if not d.get("review", {}).get("design_contract_reviewer") or not d.get("review", {}).get("approval_reference"):
        problems.append("DESIGN_CONTRACT_APPROVAL_MISSING")
    return {"path": str(p.relative_to(root)), "sha256": digest(p), "status": d.get("status"),
            "declared_state_matrix": d.get("state_matrix", []), "declared_roles": d.get("personas_and_grants", [])}, problems


def create_report(root: Path, bundles: list[Path], expected_commit: str) -> dict[str, Any]:
    require(SHA.fullmatch(expected_commit) is not None, "Exact expected source commit required")
    tree = subprocess.check_output(["git", "rev-parse", expected_commit + "^{tree}"], cwd=root, text=True).strip()
    records = scope(root)
    known = {x["screen_id"] for x in records}
    proofs, rejected, bundle_results = [], [], []
    for bundle in bundles:
        try:
            ps, errors = bundle_proofs(bundle, known, tree)
            proofs.extend(ps); rejected.extend(errors)
            bundle_results.append({"path": str(bundle), "accepted_proofs": len(ps), "rejected_proofs": len(errors)})
        except (ValueError, OSError, ET.ParseError) as error:
            bundle_results.append({"path": str(bundle), "rejected_bundle": str(error)})
    by_id: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for proof in proofs:
        for sid in proof["observed_ids"]:
            by_id[sid].append(proof)
    for row in records:
        sid = row["screen_id"]
        data = by_id[sid]
        observed = {tuple(x["cell"]) for x in data}
        swept = {tuple(x["cell"]) for x in data if x["full_vertical_sweep"]}
        contract, issues = contract_findings(root, sid)
        if not observed: issues.append("NATIVE_RENDERED_OWNER_EVIDENCE_MISSING")
        if swept != set(CELLS): issues.append("FULL_27_CELL_VERTICAL_COVERAGE_INCOMPLETE")
        if any(x["layout_findings"] for x in data): issues.append("LAYOUT_FINDINGS_REQUIRE_PIXEL_OR_TOUCH_REVIEW")
        evidence = root / PACK / "evidence" / (sid + ".json")
        if not evidence.is_file(): issues.append("APPROVED_CANONICAL_EVIDENCE_RECORD_MISSING")
        # No authority promotion: per-state/persona, native golden/drift, keyboard and TalkBack
        # remain independent evidence requirements, not inferred from a large screenshot count.
        issues += ["ROLE_AND_STATE_MATRIX_ACCEPTANCE_NOT_ATTESTED", "INDEPENDENT_NATIVE_REVIEW_NOT_AUTHENTICATED"]
        row.update(contract=contract, observed_matrix_cells=[list(x) for x in sorted(observed)],
                   complete_vertical_cells=[list(x) for x in sorted(swept)],
                   missing_matrix_cells=[list(x) for x in CELLS if x not in swept],
                   native_proof_references=[{"manifest": x["manifest"], "sha256": x["manifest_sha256"], "test": x["test_class"] + "." + x["test_method"], "visit": x["visit"]} for x in data],
                   blockers=issues, visual_certified=False)
    return {"schema_version": 1, "purpose": "FULL_SCOPE_CERTIFICATION_AUDIT_NOT_APPROVAL",
            "source_commit": expected_commit, "source_tree": tree,
            "scope_sha256": digest(root / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml"),
            "required_matrix": [list(x) for x in CELLS], "registry_count": 545,
            "bundle_checks": bundle_results, "rejected_native_proofs": rejected,
            "summary": {"screens_with_observed_native_owner": sum(bool(x["observed_matrix_cells"]) for x in records),
                        "screens_with_all_27_vertical_cells": sum(not x["missing_matrix_cells"] for x in records),
                        "contracts_present": sum(x["contract"] is not None for x in records),
                        "implementation_inventory_states": dict(Counter(x["implementation_inventory"] for x in records)),
                        "visual_certified_screens": 0, "full_app_visual_certification": "NOT_GRANTED"},
            "interpretation": "Implementation inventory is a static reference inventory, not proof of feature absence or runtime correctness. A visible owner is not approval. Evidence authenticity and full acceptance remain with the protected remote checks and independent owner review.",
            "screens": records}


def emit_html(report: dict[str, Any]) -> str:
    rows = []
    for r in report["screens"]:
        text = " ".join([r["screen_id"], r["name"], r["module"], *r["blockers"]])
        rows.append('<tr data-query="' + html.escape(text.lower(), quote=True) + '"><td>' + html.escape(r["screen_id"]) + '</td><td>' + html.escape(r["name"]) + '</td><td>' + html.escape(r["module"]) + '</td><td>' + str(len(r["complete_vertical_cells"])) + '/27</td><td>' + html.escape(r["contract"]["status"] if r["contract"] else "Missing") + '</td><td>' + '<br>'.join(html.escape(x.replace('_', ' ').lower()) for x in r["blockers"]) + '</td></tr>')
    return """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GOAT · full visual certification audit</title><style>body{font:16px/1.5 system-ui;background:#f3f4ed;color:#183328;margin:0}main{max-width:1500px;margin:auto;padding:28px}h1{font-size:32px}aside{background:#dcebba;padding:20px;border-radius:16px;margin:20px 0}input{font:inherit;padding:14px;width:min(620px,90%);border:1px solid #5b6a5e;border-radius:12px}table{border-collapse:collapse;width:100%;background:white;margin-top:20px}td,th{text-align:left;vertical-align:top;padding:12px;border-bottom:1px solid #dce3d7}th{position:sticky;top:0;background:#285640;color:white}td:last-child{font-size:13px}code{overflow-wrap:anywhere}.scroll{overflow:auto}</style><main><h1>Full app-wide visual certification audit</h1><p>All 545 canonical screen entries are retained. Native evidence and acceptance requirements are shown separately.</p><aside><strong>Certification not granted.</strong> Source: <code>""" + html.escape(report["source_commit"]) + """</code>. This audit never replaces an independent owner review, a state/role matrix or approved native baselines.</aside><label>Filter screen, module or missing requirement<br><input id="search" type="search" placeholder="Search all 545 entries"></label><p id="count">545 entries</p><div class="scroll"><table><thead><tr><th>Screen ID</th><th>Page</th><th>Module</th><th>Complete native cells</th><th>Contract</th><th>Outstanding acceptance requirements</th></tr></thead><tbody>""" + ''.join(rows) + """</tbody></table></div></main><script>const input=document.getElementById('search'),rows=[...document.querySelectorAll('tbody tr')];input.addEventListener('input',()=>{const q=input.value.toLowerCase().trim();let n=0;for(const r of rows){r.hidden=!r.dataset.query.includes(q);if(!r.hidden)n++}document.getElementById('count').textContent=n+' of 545 entries'});</script></html>"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--bundle", type=Path, action="append", default=[])
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-full-native-matrix", action="store_true", help="Fail if objective owner/configuration coverage is incomplete; does not grant visual approval")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report = create_report(args.root.resolve(), args.bundle, args.expected_commit)
    (args.output / "full-visual-certification-audit.json").write_text(json.dumps(report, indent=2) + "\n")
    (args.output / "index.html").write_text(emit_html(report))
    print(json.dumps(report["summary"], indent=2))
    if args.require_full_native_matrix and (
        report["summary"]["screens_with_all_27_vertical_cells"] != 545 or report["rejected_native_proofs"]
        or any("rejected_bundle" in row for row in report["bundle_checks"])
    ):
        raise SystemExit("Full native configuration evidence is incomplete; see the retained per-screen report. No certification granted.")


if __name__ == "__main__":
    main()
