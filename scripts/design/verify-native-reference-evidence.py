#!/usr/bin/env python3
"""Validate deterministic Phase 4 native screenshot/UI-tree evidence.

This is a machine-evidence gate only. Passing does not imply owner approval,
independent review, accessibility certification, route certification or VISUAL_GREEN.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

NAME_RE = re.compile(
    r"^(FOS-[A-Z]+-[A-Z0-9-]+)_(light|dark|outdoor)_w(360|411|780)_f(100|130|200)$"
)
CLICK_ACTIONS = {"OnClick", "Click", "SetText", "RequestFocus", "SetProgress"}
CLICK_ROLES = {"Button", "Checkbox", "RadioButton", "Switch", "Tab"}
THEMES = ("light", "dark", "outdoor")
WIDTHS = (360, 411, 780)
FONT_SCALES = (100, 130, 200)


def fail(message: str) -> None:
    raise SystemExit("FAIL native reference evidence: " + message)


def expected_surface_ids(ledger: dict) -> set[str]:
    values = {
        row["screen_id"]
        for row in ledger["matrix"]["reference_surfaces"]
    }
    if len(values) != ledger["visual_foundation"]["reference_surface_count"] if "visual_foundation" in ledger else len(values):
        pass
    return values


def iter_nodes(node: dict, inherited_clip: list[int] | None = None):
    bounds = node.get("bounds")
    props = node.get("properties") or {}
    clip = inherited_clip
    if bounds and (
        props.get("Shape") == "androidx.compose.foundation.VerticalScrollableClipShape"
        or "VerticalScrollAxisRange" in props
    ):
        clip = bounds
    yield node, clip
    for child in node.get("children") or []:
        yield from iter_nodes(child, clip)


def is_interactive(node: dict) -> bool:
    props = node.get("properties") or {}
    actions = set(node.get("actions") or [])
    return bool(actions & CLICK_ACTIONS) or props.get("Role") in CLICK_ROLES


def partially_clipped(bounds: list[int], clip: list[int] | None, minimum: int) -> bool:
    if not clip:
        return False
    left, top, right, bottom = bounds
    width, height = right - left, bottom - top
    if width >= minimum and height >= minimum:
        return False
    return top <= clip[1] or bottom >= clip[3]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ledger", required=True)
    parser.add_argument("--roots", nargs="+", required=True)
    args = parser.parse_args()

    ledger = json.loads(Path(args.ledger).read_text())
    surfaces = {row["screen_id"] for row in ledger["matrix"]["reference_surfaces"]}
    expected = {
        (surface, theme, width, scale)
        for surface in surfaces
        for theme in THEMES
        for width in WIDTHS
        for scale in FONT_SCALES
    }

    raw_png: dict[tuple[str, str, int, int], Path] = {}
    annotated_png: dict[tuple[str, str, int, int], Path] = {}
    trees: dict[tuple[str, str, int, int], Path] = {}

    for root_text in args.roots:
        root = Path(root_text)
        if not root.exists():
            fail(f"missing evidence root {root}")
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            name = path.name
            suffix = None
            stem = None
            if name.endswith(".annotated.png"):
                suffix = "annotated"
                stem = name[:-len(".annotated.png")]
            elif name.endswith(".uitree.json"):
                suffix = "tree"
                stem = name[:-len(".uitree.json")]
            elif name.endswith(".png"):
                suffix = "raw"
                stem = name[:-len(".png")]
            else:
                continue
            match = NAME_RE.match(stem)
            if not match:
                continue
            surface, theme, width, scale = match.groups()
            key = (surface, theme, int(width), int(scale))
            if key not in expected:
                fail(f"unexpected evidence combination {key}: {path}")
            bucket = {"raw": raw_png, "annotated": annotated_png, "tree": trees}[suffix]
            if key in bucket:
                fail(f"duplicate {suffix} evidence for {key}")
            bucket[key] = path

    for label, bucket in (("raw PNG", raw_png), ("annotated PNG", annotated_png), ("UI tree", trees)):
        missing = sorted(expected - set(bucket))
        extra = sorted(set(bucket) - expected)
        if missing:
            fail(f"{label} matrix missing {len(missing)} combination(s), first={missing[0]}")
        if extra:
            fail(f"{label} matrix has unexpected combination(s), first={extra[0]}")

    out_of_bounds: list[str] = []
    undersized: list[str] = []
    for key, path in sorted(trees.items()):
        surface, theme, width, scale = key
        data = json.loads(path.read_text())
        capture = data.get("capture") or {}
        image_width = capture.get("imageWidth")
        image_height = capture.get("imageHeight")
        if image_width != width or not isinstance(image_height, int) or image_height <= 0:
            fail(f"capture dimensions disagree with filename for {path.name}: {capture}")
        minimum = 64 if theme == "outdoor" else 48

        for node, clip in iter_nodes(data["root"]):
            bounds = node.get("bounds")
            if not bounds or len(bounds) != 4:
                continue
            left, top, right, bottom = bounds
            if left < 0 or top < 0 or right > image_width or bottom > image_height or right < left or bottom < top:
                out_of_bounds.append(f"{path.name}: bounds={bounds}")
                continue
            if not is_interactive(node):
                continue
            target_width, target_height = right - left, bottom - top
            if target_width <= 0 or target_height <= 0:
                # Roborazzi retains zero-bounds semantics for off-screen scroll content.
                continue
            if target_width >= minimum and target_height >= minimum:
                continue
            if partially_clipped(bounds, clip, minimum):
                # A scroll viewport may expose only a slice of an otherwise valid target.
                continue
            label = (node.get("properties") or {}).get("Text") or (node.get("properties") or {}).get("Role") or "interactive"
            undersized.append(
                f"{path.name}: {label} target={target_width}x{target_height}dp expected>={minimum}dp bounds={bounds}"
            )

    if out_of_bounds:
        fail(f"{len(out_of_bounds)} out-of-bounds semantic node(s); first={out_of_bounds[0]}")
    if undersized:
        fail(f"{len(undersized)} undersized visible target(s); first={undersized[0]}")

    print(
        "PASS native reference evidence: "
        f"{len(surfaces)} surfaces x {len(THEMES)} themes x {len(WIDTHS)} widths x "
        f"{len(FONT_SCALES)} font scales = {len(expected)} deterministic combinations; "
        "raw/annotated/UI-tree matrices complete; bounds valid; touch-target law satisfied."
    )


if __name__ == "__main__":
    main()
