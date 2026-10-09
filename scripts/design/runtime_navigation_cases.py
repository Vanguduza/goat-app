"""Additional Phase 5 cases: reviewed source assertions plus exact JUnit identities.

This is a bounded evidence-consistency checker, not an approval or CI-origin authority.
The legacy 59-source inventory remains separate.
"""
from __future__ import annotations

import ast
import hashlib
import json
import re
import subprocess
from pathlib import Path

MAP_PATH = "docs/ux/runtime-navigation-cases.json"
KINDS = ("rendered_destination_traversal", "rendered_surface_owner")
SCREEN = re.compile(r"FOS-[A-Z]+(?:-[0-9]+)+(?:-[A-Z])?")
SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def exact_keys(value: dict, keys: set[str], label: str) -> None:
    require(type(value) is dict and set(value) == keys, f"{label}: unexpected or missing fields")


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def inside(root: Path, relative: str) -> Path:
    require(type(relative) is str and bool(relative), "missing relative evidence path")
    path = Path(relative)
    require(not path.is_absolute() and ".." not in path.parts, "unsafe evidence path")
    resolved = (root / path).resolve()
    require(resolved.is_relative_to(root.resolve()), "evidence path leaves repository")
    return resolved


def kotlin_mask(text: str, strings: bool = True) -> str:
    """Preserve offsets while excluding comments and, optionally, quoted text."""
    result = list(text)
    index = 0
    while index < len(text):
        start = index
        if text.startswith("//", index):
            end = text.find("\n", index)
            index = len(text) if end < 0 else end
            erase = True
        elif text.startswith("/*", index):
            depth = 1
            index += 2
            while index < len(text) and depth:
                if text.startswith("/*", index):
                    depth += 1
                    index += 2
                elif text.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            require(depth == 0, "unterminated Kotlin comment")
            erase = True
        elif text.startswith('"""', index):
            end = text.find('"""', index + 3)
            require(end >= 0, "unterminated Kotlin raw string")
            index = end + 3
            erase = strings
        elif text[index] in "\"'":
            quote = text[index]
            index += 1
            while index < len(text):
                if text[index] == "\\":
                    index += 2
                elif text[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            erase = strings
        else:
            index += 1
            erase = False
        if erase:
            for offset in range(start, index):
                if result[offset] != "\n":
                    result[offset] = " "
    return "".join(result)


def matching(mask: str, start: int, opening: str, closing: str) -> int:
    depth = 0
    for index in range(start, len(mask)):
        if mask[index] == opening:
            depth += 1
        elif mask[index] == closing:
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("unbalanced Kotlin function")


def functions(text: str) -> tuple[dict[str, str], set[str]]:
    mask = kotlin_mask(text)
    clean = kotlin_mask(text, strings=False)
    methods = {}
    ambiguous = set()
    for match in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?(\w+)\s*\(", mask):
        name = match.group(1)
        parameters = matching(mask, match.end() - 1, "(", ")")
        line_end = mask.find("\n", parameters + 1)
        line_end = len(mask) if line_end < 0 else line_end
        tail = mask[parameters + 1:line_end]
        if re.match(r"\s*(?::[^=\n]+)?\s*=\s*\S", tail) and "{" not in tail:
            # The reviewed report click helper has a one-line expression body. Retain its
            # declaration too, so argument-to-selector binding can be checked explicitly.
            if name in methods:
                ambiguous.add(name)
            methods[name] = clean[match.start():line_end]
            continue
        opening = mask.find("{", parameters + 1)
        next_function = re.search(r"\bfun\b", mask[parameters + 1:])
        if opening < 0 or (next_function and parameters + 1 + next_function.start() < opening):
            continue
        closing = matching(mask, opening, "{", "}")
        if name in methods:
            ambiguous.add(name)
        methods[name] = clean[match.start():closing + 1]
    tests = set(re.findall(r"@Test(?:\([^)]*\))?\s+(?:public\s+)?fun\s+(\w+)\s*\(", clean))
    # An unrelated overload in an execution-only test is harmless. No mapped helper may
    # borrow one of those bodies: omit ambiguous names so helper admission fails closed.
    methods = {name: body for name, body in methods.items() if name not in ambiguous}
    require(tests and tests <= methods.keys(), "mapped @Test method has no unambiguous supported function body")
    return methods, tests


def occurrence(body: str, expression: str, number: int) -> int:
    require(type(expression) is str and expression.strip() == expression and bool(expression), "empty assertion anchor")
    require(type(number) is int and number >= 0, "invalid assertion occurrence")
    mask = kotlin_mask(body)
    positions = []
    start = 0
    while True:
        index = body.find(expression, start)
        if index < 0:
            break
        if mask[index:index + 1].strip():
            positions.append(index)
        start = index + 1
    require(number < len(positions), f"assertion anchor missing from reviewed source: {expression}")
    return positions[number]


def anchor_context(anchor: dict, case: str, methods: dict[str, str], tests: set[str]) -> tuple[str, str, tuple[int, ...], str | None]:
    exact_keys(anchor, {"within", "expression", "via", "occurrence"}, "assertion anchor")
    require(type(anchor["via"]) is list and len(anchor["via"]) <= 3, "invalid helper path")
    current = case
    position = []
    kind = None
    for call in anchor["via"]:
        require(type(call) is str, "invalid helper invocation")
        position.append(occurrence(methods[current], call, 0))
        invoked = re.match(r"(\w+)\(", call)
        require(invoked is not None, "unsupported helper invocation")
        target = invoked.group(1)
        require(target in methods and target not in tests and target != current, "helper path is not a direct non-test helper")
        if target == "exerciseWeightJourney":
            bound = re.match(r"exerciseWeightJourney\(AnimalProfileKind\.(SHEEP|CATTLE),", call)
            require(bound is not None, "unreviewed species helper binding")
            kind = bound.group(1)
            require('val label = if (kind == AnimalProfileKind.SHEEP) "Sheep" else "Cattle"' in methods[target], "species helper label binding changed")
            prefix = 'val prefix = "FOS-' + "$" + '{label.uppercase()}"'
            require(prefix in methods[target], "species helper prefix binding changed")
        current = target
    require(current == anchor["within"], "assertion belongs to an unreachable helper")
    expression = anchor["expression"]
    position.append(occurrence(methods[current], expression, anchor["occurrence"]))
    return current, expression, tuple(position), kind


def code_expression(body: str, expression: str) -> None:
    """A helper's positive check must be executable source, not a comment or string."""
    occurrence(body, expression, 0)


def return_action(expression: str, methods: dict[str, str], sid: str) -> None:
    if expression == "back()":
        code_expression(methods.get("back", ""), "dispatcher.onBackPressed()")
        return
    report = re.fullmatch(r'backToHub\("(FOS-REPORT-\d+)"\)', expression)
    if report:
        require(report.group(1) == sid, "report return belongs to another entered screen")
        code_expression(methods.get("backToHub", ""), 'click("Reports")')
    else:
        require(re.fullmatch(r'click\("(?:Back|Profile|Reports)"\)', expression) is not None, "unsupported reviewed return action")
    body = methods.get("click", "")
    parameter = re.match(r"fun click\(\s*(label|text)\s*:\s*String\s*\)", body)
    require(parameter is not None, "return click helper lacks a reviewed String parameter")
    code_expression(body, "hasClickAction()")
    code_expression(body, "hasText(" + parameter.group(1) + ")")
    clickable = re.search(r"compose\.onNode\([^\n]+\.performClick\(\)", body)
    require(clickable is not None, "return click helper does not activate a visible action")
    code_expression(body, clickable.group())


def positive_id(expression: str, kind: str | None, methods: dict[str, str], returning: bool = False) -> str | None:
    require("assertDoesNotExist" not in expression, "negative assertion cannot prove a rendered destination")
    report = re.fullmatch(r'(open|backToHub)\("(FOS-REPORT-\d+)"\)', expression)
    if report:
        if report.group(1) == "open":
            code_expression(methods.get("open", ""), 'awaitTag("farm-screen:$screenId")')
            code_expression(methods.get("awaitTag", ""), "compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()")
            return report.group(2)
        require(returning, "return-away assertion cannot prove entry")
        code_expression(methods.get("backToHub", ""), 'awaitTag("farm-screen:FOS-REPORT-001")')
        code_expression(methods.get("awaitTag", ""), "compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()")
        return "FOS-REPORT-001"
    conditional = re.fullmatch(r'awaitScreen\(if \(kind == AnimalProfileKind.SHEEP\) "(FOS-SHEEP-\d+)" else "(FOS-CATTLE-\d+)"\)', expression)
    if conditional:
        positive = 'compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()'
        code_expression(methods.get("awaitScreen", ""), positive)
        require(kind in {"SHEEP", "CATTLE"}, "conditional screen lacks its reviewed case binding")
        return conditional.group(1 if kind == "SHEEP" else 2)
    direct = re.fullmatch(r'(?:awaitScreen|awaitTag)\("([^"]+)"\)', expression)
    if expression.startswith("awaitScreen("):
        positive = 'compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()'
        code_expression(methods.get("awaitScreen", ""), positive)
    if expression.startswith("awaitTag(") or report:
        positive = "compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()"
        code_expression(methods.get("awaitTag", ""), positive)
    if not direct:
        direct = re.fullmatch(r'compose\.onNodeWithTag\("([^"]+)"\)\.(?:assertExists|assertIsDisplayed)\(\)', expression)
    if direct:
        identifier = direct.group(1)
        if identifier.startswith("$prefix-"):
            require(kind in {"SHEEP", "CATTLE"}, "interpolated screen lacks its reviewed case binding")
            identifier = identifier.replace("$prefix", "FOS-" + kind, 1)
        identifier = re.sub(r"^farm-(?:screen|atom):", "", identifier)
        require(SCREEN.fullmatch(identifier) is not None, "assertion does not identify a rendered screen or atom")
        return identifier
    if returning and re.fullmatch(r'compose\.onNodeWithText\("[^"]+"\)\.assertIsDisplayed\(\)', expression):
        return None
    raise ValueError(f"unsupported positive rendered assertion: {expression}")


def load_case_map(root: Path, path: Path | None = None) -> dict:
    path = path or root / MAP_PATH
    raw = path.read_bytes()
    spec = json.loads(raw)
    exact_keys(spec, {"schema_version", "purpose", "sources"}, "case map")
    require(spec["schema_version"] == 1 and spec["purpose"] == "ADDITIONAL_PHASE5_CASE_INVENTORY_NOT_APPROVAL", "unsupported case map")
    require(type(spec["sources"]) is list and bool(spec["sources"]), "case map is empty")
    known = set(SCREEN.findall((root / "docs/ux/FARM_OS_SCREEN_REGISTRY.yaml").read_text()))
    legacy_sources = {rel for paths in legacy_groups(root).values() for rel in paths}
    sources = []
    cases = []
    seen_sources, seen_classes = set(), set()
    for source in spec["sources"]:
        exact_keys(source, {"path", "sha256", "reviewed_source_commit", "class_name", "cases"}, "case source")
        rel, class_name = source["path"], source["class_name"]
        require(type(rel) is str and type(class_name) is str, "source path and class must be strings")
        require(rel not in legacy_sources, "additional mapping cannot replace a legacy source")
        require(rel not in seen_sources and class_name not in seen_classes, "duplicate source/class mapping")
        seen_sources.add(rel)
        seen_classes.add(class_name)
        require(rel.startswith("app/src/test/") and rel.endswith(".kt"), "additional Phase5 cases must be JVM app tests; instrumentation is separate")
        require(type(source["sha256"]) is str and type(source["reviewed_source_commit"]) is str, "invalid source identity")
        require(SHA256.fullmatch(source["sha256"]) is not None and COMMIT.fullmatch(source["reviewed_source_commit"]) is not None, "invalid source identity")
        source_bytes = inside(root, rel).read_bytes()
        require(digest(source_bytes) == source["sha256"], f"stale reviewed test source: {rel}")
        text = source_bytes.decode("utf-8")
        package = re.search(r"(?m)^package ([\w.]+)\s*$", text)
        require(package is not None and class_name == package.group(1) + "." + Path(rel).stem, "mapped package/file/class mismatch")
        require(re.search(r"\bclass\s+" + re.escape(class_name.rsplit(".", 1)[1]) + r"\b", kotlin_mask(text)) is not None, "mapped test class is missing")
        methods, tests = functions(text)
        require(type(source["cases"]) is list, "invalid case list")
        require(all(type(case) is dict and type(case.get("method")) is str for case in source["cases"]), "invalid mapped case identity")
        mapped = [case["method"] for case in source["cases"]]
        require(len(mapped) == len(set(mapped)) and set(mapped) == tests, f"mapped method set differs from @Test methods: {rel}")
        sources.append({key: source[key] for key in ("path", "sha256", "reviewed_source_commit", "class_name")})
        for case in source["cases"]:
            exact_keys(case, {"method", "execution_only_reason", "claims"}, "test case")
            require(type(case["claims"]) is list, "invalid case claims")
            reason = case["execution_only_reason"]
            require((bool(case["claims"]) and reason is None) or (not case["claims"] and type(reason) is str and bool(reason.strip())), "execution-only case needs a reason and no traversal claims")
            claims = []
            seen = set()
            for claim in case["claims"]:
                exact_keys(claim, {"kind", "screen_id", "entry", "return_action", "return"}, "screen claim")
                proof_kind, sid = claim["kind"], claim["screen_id"]
                require(type(sid) is str and proof_kind in KINDS and sid in known, "unsupported proof kind or screen")
                require((proof_kind, sid) not in seen, "duplicate case screen claim")
                seen.add((proof_kind, sid))
                _, expression, entered, binding = anchor_context(claim["entry"], case["method"], methods, tests)
                require(positive_id(expression, binding, methods) == sid, "screen claim differs from its positive assertion")
                if proof_kind == "rendered_destination_traversal":
                    require(type(claim["return_action"]) is dict and type(claim["return"]) is dict, "traversal lacks a return action and positive restoration assertion")
                    _, action, acted, _ = anchor_context(claim["return_action"], case["method"], methods, tests)
                    return_action(action, methods, sid)
                    _, expression, returned, binding = anchor_context(claim["return"], case["method"], methods, tests)
                    positive_id(expression, binding, methods, returning=True)
                    require(entered < acted <= returned and returned > entered, "return action/restoration does not follow rendered entry")
                else:
                    require(claim["return_action"] is None and claim["return"] is None, "owner evidence cannot manufacture return restoration")
                claims.append({"kind": proof_kind, "screen_id": sid})
            cases.append({"source": rel, "class_name": class_name, "method": case["method"], "execution_only_reason": reason, "claims": claims})
    return {"schema_version": 1, "mapping_path": MAP_PATH, "mapping_sha256": digest(raw), "sources": sources, "expected_case_count": len(cases), "cases": cases}


def verify_reviewed_source_commits(catalog: dict, root: Path) -> None:
    """Pre-freeze check where review commits are available; CI may have a shallow checkout."""
    for source in catalog["sources"]:
        committed = subprocess.check_output(
            ["git", "show", source["reviewed_source_commit"] + ":" + source["path"]], cwd=root,
        )
        require(digest(committed) == source["sha256"], "reviewed commit does not contain the mapped test bytes")


def source_claims(catalog: dict) -> dict[str, dict[str, list[str]]]:
    result = {kind: {} for kind in KINDS}
    for case in catalog["cases"]:
        for claim in case["claims"]:
            result[claim["kind"]].setdefault(case["source"], set()).add(claim["screen_id"])
    return {kind: {source: sorted(ids) for source, ids in sorted(paths.items())} for kind, paths in result.items()}


def case_ledger(catalog: dict, execution: dict | None) -> dict:
    return {
        **catalog,
        "executed_case_count": catalog["expected_case_count"] if execution else 0,
        "execution": execution,
        "law": "Case/source/JUnit consistency only. First-party CI origin and run acceptance require independent verification. No visual, feature, module, MVP or approval claim.",
    }


def legacy_groups(root: Path) -> dict[str, tuple[str, ...]]:
    names = {
        "ENTRY_ACTION_TESTS": "entry_action_emission",
        "RENDERED_TRAVERSAL_TESTS": "rendered_destination_traversal",
        "RENDERED_OWNER_TESTS": "rendered_surface_owner",
        "ROUTE_CONTRACT_TESTS": "route_contract",
    }
    groups = {}
    tree = ast.parse((root / "scripts/design/build-runtime-navigation-evidence.py").read_text())
    for assignment in tree.body:
        if isinstance(assignment, ast.Assign):
            for target in assignment.targets:
                if isinstance(target, ast.Name) and target.id in names:
                    groups[names[target.id]] = ast.literal_eval(assignment.value)
    require(set(groups) == set(names.values()), "legacy evidence groups changed")
    return groups


def verify_case_ledger(root: Path, runtime: dict) -> None:
    legacy = legacy_groups(root)
    require(set(runtime["test_sources"]) == set(legacy), "unknown runtime proof group")
    extras = {
        source
        for kind, paths in runtime["test_sources"].items()
        for source in paths if source not in legacy[kind]
    }
    additional = runtime.get("additional_case_evidence")
    if additional is None:
        require(not extras, "additional runtime sources lack per-case evidence")
        return  # Historical 59-class ledgers retain their original scope and provenance.
    catalog = load_case_map(root)
    for key, value in catalog.items():
        require(additional.get(key) == value, f"additional case ledger differs from reviewed map: {key}")
    planned = source_claims(catalog)
    mapped_sources = {source["path"] for source in catalog["sources"]}
    require(extras <= mapped_sources, "runtime ledger includes unreviewed source classes")
    for kind, paths in runtime["test_sources"].items():
        actual = {source: ids for source, ids in paths.items() if source in mapped_sources}
        require(actual == planned.get(kind, {}), "additional route claims differ from positive case assertions")
    coverage_keys = {
        "entry_action_emission": ("entry_action_screen_ids", "entry_action_screen_count", "entry_action_emission_executed"),
        "rendered_destination_traversal": ("rendered_traversal_screen_ids", "rendered_traversal_screen_count", "runtime_reachability_executed"),
        "rendered_surface_owner": ("rendered_owner_screen_ids", "rendered_owner_screen_count", "rendered_surface_owners_executed"),
        "route_contract": ("route_contract_screen_ids", "route_contract_screen_count", "route_contracts_executed"),
    }
    executed = runtime["ci_status"] == "PASS_EXACT_HEAD_CI"
    all_ids = set()
    for kind, (ids_key, count_key, execution_key) in coverage_keys.items():
        ids = sorted({sid for values in runtime["test_sources"][kind].values() for sid in values})
        all_ids.update(ids)
        require(runtime["coverage"].get(ids_key) == ids and runtime["coverage"].get(count_key) == len(ids), "inflated runtime coverage")
        require(runtime["certification"].get(execution_key) == (len(ids) if executed else 0), "inflated runtime execution")
    require(runtime["coverage"]["combined_screen_count"] == len(all_ids), "inflated combined coverage")
    returned = runtime["coverage"]["rendered_traversal_screen_count"] if executed else 0
    require(runtime["certification"]["return_restoration_executed"] == returned, "inflated return restoration")
    require(additional["executed_case_count"] == (catalog["expected_case_count"] if executed else 0), "inflated additional execution count")
    execution = additional.get("execution")
    if not executed:
        require(execution is None, "CI_PENDING cannot carry execution evidence")
        return
    require(type(execution) is dict and execution.get("foundation_run_id") == runtime["foundation_run_id"], "additional cases have different run evidence")
    require(execution.get("candidate_commit") == runtime["tested_commit"], "additional cases have different source evidence")
    require(execution.get("task_outcome") == "EXECUTED", "cached or skipped results cannot be called newly executed")
    expected = {(case["class_name"], case["method"]) for case in catalog["cases"]}
    results = execution.get("cases", [])
    identities = [(case.get("class_name"), case.get("method")) for case in results]
    require(len(identities) == len(set(identities)) and set(identities) == expected, "additional execution case set is incomplete")
    require(all(case.get("status") == "PASS" and SHA256.fullmatch(case.get("junit_sha256", "")) for case in results), "additional execution contains failed, skipped or unhashed cases")
    require(execution.get("test_task") == ":app:testDebugUnitTest", "additional cases belong to another test task")
    for key in ("candidate_commit", "candidate_tree", "actual_checkout_commit", "actual_checkout_tree"):
        require(type(execution.get(key)) is str and COMMIT.fullmatch(execution[key]) is not None, "invalid additional checkout identity")
    require(execution["candidate_tree"] == execution["actual_checkout_tree"], "additional execution has another source tree")
    require(type(execution.get("task_log_sha256")) is str and SHA256.fullmatch(execution["task_log_sha256"]) is not None, "additional execution lacks its retained task log hash")
    reports = execution.get("reports", [])
    require(type(reports) is list and all(type(report) is dict for report in reports), "invalid additional JUnit report inventory")
    by_path = {report.get("path"): report for report in reports}
    expected_paths = {f"app/build/test-results/testDebugUnitTest/TEST-{source['class_name']}.xml" for source in catalog["sources"]}
    require(len(by_path) == len(reports) and set(by_path) == expected_paths, "additional JUnit report set is incomplete")
    for path, report in by_path.items():
        actual = [case for case in results if case.get("junit_path") == path]
        require(bool(actual) and report.get("tests") == len(actual), "additional JUnit report count differs from cases")
        require(all(case.get("junit_sha256") == report.get("sha256") and case.get("suite_timestamp") == report.get("timestamp") for case in actual), "additional case receipt differs from retained JUnit report")
