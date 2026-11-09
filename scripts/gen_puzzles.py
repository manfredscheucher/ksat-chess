#!/usr/bin/env python3
"""
Bundled example puzzles: converter + Kotlin generator.

  python3 scripts/gen_puzzles.py            regenerate ExamplePuzzles.kt from puzzles/**/*.json
  python3 scripts/gen_puzzles.py --convert  first (re)write the converted puzzles
                                            (chess-com-5) from the Python
                                            sat-chess .puzzle files, then regenerate

The Python sat-chess checkout is expected as a sibling (../sat-chess), overridable
with the SAT_CHESS env var. It is only needed for --convert. The demo puzzles in
puzzles/demos/ are hand-written JSON and never touched by this script.

Only plain python3, no third-party packages.

Licensing (see puzzles/NOTICE.md): we store only the position, the number of
plies, goal/projection, composer and source. Titles and descriptions below are
our own words; never paste text from chess.com or the Python READMEs.
"""

import ast
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "puzzles")
SAT_CHESS = os.environ.get("SAT_CHESS", os.path.join(os.path.dirname(ROOT), "sat-chess"))
PY_PUZZLES = os.path.join(SAT_CHESS, "configs", "puzzles")
OUT = os.path.join(ROOT, "core", "src", "commonMain", "kotlin",
                   "org", "bytefred", "satchess", "puzzle", "ExamplePuzzles.kt")

INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

# Folder -> display category, in display order. Folders not listed are an error.
CATEGORIES = [
    ("demos", "Demos"),
    ("chess-com-5", "chess.com: 5 easy retrograde puzzles"),
]

CHESS_COM_URL = "https://www.chess.com/article/view/can-you-solve-these-5-easy-retrograde-puzzles"
CHESS_COM_NAME = "chess.com article by FM Mike Klein"

# ── chess.com selection ──────────────────────────────────────────────────────
# file -> overrides on top of the plain conversion. Composer of #1-#3 is not
# named in our sources, so it stays null. Titles and descriptions follow the
# article's questions in our own words and never give away the solution.
CHESS_COM = [
    ("puzzle1.puzzle", "1", {
        "title": "Four moves each",
        "question": "proofGame",
        "description": (
            "From the initial position, how can this position be reached after "
            "exactly four moves by each side? The moves need not be good, only "
            "legal."),
        "source": {"name": CHESS_COM_NAME, "url": CHESS_COM_URL, "year": 2017},
    }),
    # chess-com-5/2.json (3-ply window, "white king or empty" squares) is hand-written: not converted.
    ("puzzle3.puzzle", "3", {
        "title": "Mate after a king walk",
        "question": "helpmate",
        "goal": "checkmate",
        "moves": {"0": "f3", "2": "Kf2", "4": "Kg3", "6": "Kh4"},
        # The Python end position keeps all black men on their home squares,
        # which contradicts a mate; White's moves + the goal define the task.
        "drop_positions": True,
        "description": (
            "White opened with 1. f3, 2. Kf2, 3. Kg3 and 4. Kh4. Which four black "
            "moves, alternating with White's as in a real game, checkmate the white "
            "king on Black's fourth move? Black may not play anything that would "
            "make one of White's moves illegal."),
        "source": {"name": CHESS_COM_NAME, "url": CHESS_COM_URL, "year": 2017},
    }),
    ("puzzle4.puzzle", "4", {
        "title": "Four moves each, harder",
        "question": "proofGame",
        "composer": "Tibor Orban",
        "description": (
            "It looks like a copy of puzzle 1 but is much harder: from the initial "
            "position, both sides play exactly four moves to reach this position. "
            "Four for each side, not four for White and three for Black."),
        "source": {"name": "Die Schwalbe; reprinted in the " + CHESS_COM_NAME,
                   "url": CHESS_COM_URL, "year": 1976},
    }),
    # chess-com-5/5.json (2-ply window, retro history) is hand-written: not converted.
]


# ── conversion ───────────────────────────────────────────────────────────────

def load_python_puzzle(path):
    with open(path, encoding="utf-8") as fh:
        return ast.literal_eval(fh.read())


def convert(d):
    """Plain 1:1 conversion of a Python .puzzle dict (spec section 4)."""
    out = {"plies": d["plies"]}
    start = {"board": "startpos" if d.get(0) == INITIAL else d.get(0, "startpos")}
    if d.get("black_first"):
        start["sideToMove"] = "b"
    out["start"] = start
    positions = {str(k): v for k, v in sorted((k, v) for k, v in d.items() if isinstance(k, int) and k != 0)}
    if positions:
        out["positions"] = positions
    if d.get("project_piece"):
        out["project"] = ["piece@0:" + d["project_piece"]]
    out["expected"] = d.get("expected")
    return out


def ordered(p):
    """Stable key order for the written JSON."""
    keys = ["id", "title", "composer", "source", "description", "question", "plies",
            "start", "positions", "squares", "moves", "goal", "shortest", "project",
            "expected"]
    return {k: p[k] for k in keys if k in p}


def write_json(rel, puzzle):
    path = os.path.join(PUZZLES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(ordered(puzzle), indent=2, ensure_ascii=False) + "\n")


def run_convert():
    src = os.path.join(PY_PUZZLES, "chess-com-5-easy-retrograde")
    for fname, num, meta in CHESS_COM:
        p = convert(load_python_puzzle(os.path.join(src, fname)))
        meta = dict(meta)
        if meta.pop("drop_positions", False):
            p.pop("positions", None)
        p.update(meta)
        p["id"] = f"chess-com-5/{num}"
        p.setdefault("composer", None)
        write_json(f"chess-com-5/{num}.json", p)

    print(f"converted {len(CHESS_COM)} chess.com puzzles")


# ── Kotlin generation ────────────────────────────────────────────────────────

def kotlin_raw(s):
    if '"""' in s:
        raise ValueError('puzzle JSON contains a triple quote; cannot embed as raw string')
    if "$" in s:
        raise ValueError("puzzle JSON contains '$'; would break Kotlin string templates")
    return f'"""{s}"""'


def generate():
    folders = sorted(d for d in os.listdir(PUZZLES) if os.path.isdir(os.path.join(PUZZLES, d)))
    known = [f for f, _ in CATEGORIES]
    unknown = [f for f in folders if f not in known]
    if unknown:
        raise SystemExit(f"puzzle folders without a category in CATEGORIES: {unknown}")

    entries = []
    for folder, category in CATEGORIES:
        base = os.path.join(PUZZLES, folder)
        if not os.path.isdir(base):
            continue
        for fname in sorted(os.listdir(base)):
            if not fname.endswith(".json"):
                continue
            with open(os.path.join(base, fname), encoding="utf-8") as fh:
                text = fh.read().strip()
            json.loads(text)  # fail early on broken JSON
            entries.append((category, text))

    lines = [
        "// GENERATED by scripts/gen_puzzles.py — do not edit.",
        "// Source: puzzles/**/*.json. Puzzle positions are works of their credited",
        "// composers and are not covered by the MIT license; see puzzles/NOTICE.md.",
        "package org.bytefred.satchess.puzzle",
        "",
        "data class ExampleSource(val category: String, val json: String)",
        "",
        "val EXAMPLE_PUZZLE_SOURCES: List<ExampleSource> = listOf(",
    ]
    for category, text in entries:
        lines.append(f'    ExampleSource("{category}", {kotlin_raw(text)}),')
    lines += [
        ")",
        "",
        "fun examplePuzzles(): List<Pair<String, Puzzle>> =",
        "    EXAMPLE_PUZZLE_SOURCES.map { it.category to PuzzleJson.decode(it.json) }",
        "",
    ]
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({len(entries)} puzzles)")


def main():
    if "--convert" in sys.argv[1:]:
        if not os.path.isdir(PY_PUZZLES):
            raise SystemExit(f"Python puzzles not found at {PY_PUZZLES}; set SAT_CHESS.")
        run_convert()
    generate()


if __name__ == "__main__":
    main()
