#!/usr/bin/env python3
"""
Bundled example puzzles: converter + Kotlin generator.

  python3 scripts/gen_puzzles.py            regenerate ExamplePuzzles.kt from puzzles/**/*.json
  python3 scripts/gen_puzzles.py --convert  first (re)write the converted puzzles
                                            (chess-com-5, janko-shorties) from the Python
                                            sat-chess .puzzle files, then regenerate

The Python sat-chess checkout is expected as a sibling (../sat-chess), overridable
with the SAT_CHESS env var. It is only needed for --convert. The demo puzzles in
puzzles/demos/ are hand-written JSON and never touched by this script.

Only plain python3, no third-party packages.

Licensing (see puzzles/NOTICE.md): we store only the position, the number of
plies, goal/projection, composer and source. Titles and descriptions below are
our own words; never paste text from chess.com, janko.at or the Python READMEs.
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
    ("janko-shorties", "janko.at: Proof Game Shorties"),
]

CHESS_COM_URL = "https://www.chess.com/article/view/can-you-solve-these-5-easy-retrograde-puzzles"
CHESS_COM_NAME = "chess.com article by FM Mike Klein"
SHORTIES_URL = "https://www.janko.at/Retros/Shorties/0{}.htm"

# ── chess.com selection ──────────────────────────────────────────────────────
# file -> overrides on top of the plain conversion. Composer of #1-#3 is not
# named in our sources, so it stays null.
CHESS_COM = [
    ("puzzle1.puzzle", "1", {
        "title": "Knight round trip",
        "question": "proofGame",
        "description": (
            "From the initial position, reach this diagram in exactly 4.0 moves "
            "(8 plies). Both kingside knights have disappeared and Black's d-pawn "
            "stands on d5. Several move orders work."),
        "source": {"name": CHESS_COM_NAME, "url": CHESS_COM_URL, "year": 2017},
    }),
    # chess-com-5/2.json (3-ply window, "white king or empty" squares) is hand-written: not converted.
    ("puzzle3.puzzle", "3", {
        "title": "King walk into mate",
        "question": "helpmate",
        "goal": "checkmate",
        "moves": {"0": "f3", "2": "Kf2", "4": "Kg3", "6": "Kh4"},
        # The Python end position keeps all black men on their home squares,
        # which contradicts a mate; White's moves + the goal define the task.
        "drop_positions": True,
        "description": (
            "White plays f3, Kf2, Kg3 and Kh4. Find four black moves that end with "
            "the white king checkmated on h4. White may never step into check, so "
            "Black has to time the checks carefully."),
        "source": {"name": CHESS_COM_NAME, "url": CHESS_COM_URL, "year": 2017},
    }),
    ("puzzle4.puzzle", "4", {
        "title": "Where did the bishop go?",
        "question": "proofGame",
        "composer": "Tibor Orban",
        "description": (
            "Proof game in 4.0 moves (8 plies). White's light-squared bishop and "
            "Black's d-pawn are gone, and Black's c- and e-pawns have each moved one "
            "square. One solution. Same position as janko.at Proof Game "
            "Shorties I-1."),
        "source": {"name": "Die Schwalbe; reprinted in the " + CHESS_COM_NAME
                   + " and in janko.at Proof Game Shorties I-1",
                   "url": CHESS_COM_URL, "year": 1976},
    }),
    ("puzzle5.puzzle", "5", {
        "title": "The piece on h4",
        "question": "unknownPiece",
        "composer": "Raymond Smullyan",
        "project": ["square@0:h4"],
        "description": (
            "Exactly one piece stands on h4, type and colour unknown. Which one? The "
            "solver shows every piece that passes the static checks; the real answer "
            "comes from counting captures and pawn moves backwards."),
        "source": {"name": "The Chess Mysteries of Sherlock Holmes (via the "
                   + CHESS_COM_NAME + ")",
                   "url": CHESS_COM_URL, "year": 1979},
    }),
]

# ── janko.at Proof Game Shorties selection (short ones, 7-13 plies) ──────────
# file stem -> (page number, label, composer, publication or None for an
# original first shown on janko.at, year or None, extra note or None).
# Composer/publication/year read from the janko.at pages (2026-10-20).
SHORTIES = [
    ("s1-03", 1, "I-3", "Henrik Juel", "Thema Danicum", 1997, None),
    ("s1-05", 1, "I-5", "Gerd Wilts", "Retros mailing list", 1997, None),
    ("s1-06", 1, "I-6", "Gerd Wilts", "Retros mailing list", None, None),
    ("s1-07", 1, "I-7", "Gerd Wilts", "Retros mailing list", 1997, None),
    ("s1-08a", 1, "I-8a", "Gianni Donati", None, None, "Part (a) of a twin; part (b) asks for 6.0 moves."),
    ("s1-08b", 1, "I-8b", "Gianni Donati", None, None, "Part (b) of a twin; part (a) asks for 5.5 moves."),
    ("s1-09", 1, "I-9", "Gianni Donati", None, None, None),
    ("s1-10", 1, "I-10", "Gianni Donati", None, None, None),
    ("s1-11", 1, "I-11", "Gianni Donati", None, None, None),
    ("s1-12", 1, "I-12", "Mark Kirtley & Michel Caillaud", None, None, None),
    ("s1-13", 1, "I-13", "Satoshi Hashimoto", None, None, None),
    ("s2-01", 2, "II-1", "Martin W. Hoffmann", "Schweizer Schachzeitung", 1989, None),
    ("s2-03", 2, "II-3", "Gianni Donati", None, None, None),
    ("s2-04", 2, "II-4", "Peter Wong", "Phénix", 1994, None),
    ("s3-02", 3, "III-2", "Gianni Donati", None, None, None),
    ("s3-03", 3, "III-3", "Gianni Donati", None, None, None),
    ("s3-11", 3, "III-11", "Noam Elkies", None, None, None),
    ("s4-01", 4, "IV-1", "Michel Caillaud", "Problemesis", 2001,
     "Part (a) of a twin; in part (b) the black knight stands on g8 instead of b8."),
    ("s4-02", 4, "IV-2", "Joost de Heer", "Probleemblad", 2001, None),
    ("s4-09", 4, "IV-9", "Joost de Heer & Michel Caillaud", "Probleemblad", 2001, None),
]

ROMAN = {1: "I", 2: "II", 3: "III", 4: "IV"}
PIECE_NAMES = {"p": "pawn", "n": "knight", "b": "bishop", "r": "rook", "q": "queen", "k": "king"}


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
    rules = {}
    if d.get("no_king"):
        rules["oneKingEach"] = False
    if d.get("no_check"):
        rules["checkRules"] = False
    if rules:
        out["rules"] = rules
    out["expected"] = d.get("expected")
    return out


def counts(placement):
    c = {}
    for ch in placement:
        if ch.isalpha():
            c[ch] = c.get(ch, 0) + 1
    return c


def material_note(final):
    """'White is missing a knight; Black is missing two pawns.' vs the initial array."""
    a, b = counts(INITIAL), counts(final)
    parts = []
    for side, letters in (("White", "PNBRQ"), ("Black", "pnbrq")):
        miss = []
        for ch in letters:
            n = a.get(ch, 0) - b.get(ch, 0)
            if n > 0:
                name = PIECE_NAMES[ch.lower()]
                miss.append(f"a {name}" if n == 1 else f"{n} {name}s")
        if miss:
            listed = miss[0] if len(miss) == 1 else ", ".join(miss[:-1]) + " and " + miss[-1]
            parts.append(f"{side} is missing {listed}")
    if not parts:
        return "No man has been captured."
    return "; ".join(parts) + "."


def moves_text(plies):
    return f"{plies // 2}.{5 if plies % 2 else 0}"


def ordered(p):
    """Stable key order for the written JSON."""
    keys = ["id", "title", "composer", "source", "description", "question", "plies",
            "start", "positions", "squares", "moves", "goal", "shortest", "project",
            "rules", "expected"]
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

    src = os.path.join(PY_PUZZLES, "janko-shorties")
    for stem, page, label, composer, publication, year, note in SHORTIES:
        p = convert(load_python_puzzle(os.path.join(src, stem + ".puzzle")))
        final = p["positions"][str(p["plies"])]
        n = p["expected"]
        text = (f"Proof game in {moves_text(p['plies'])} moves ({p['plies']} plies): "
                f"find a game from the initial position that reaches this diagram. "
                f"{material_note(final)} ")
        text += "The composer states a unique solution." if n == 1 else (
            f"The composer states {n} solutions." if n else "")
        if note:
            text += " " + note
        series = f"janko.at Proof Game Shorties {ROMAN[page]}"
        name = f"{publication} (via {series})" if publication else f"{series} (original)"
        p.update({
            "id": f"janko-shorties/{label}",
            "title": f"Shorties {label}",
            "composer": composer,
            "source": {"name": name, "url": SHORTIES_URL.format(page), "year": year},
            "description": text.strip(),
            "question": "proofGame",
        })
        write_json(f"janko-shorties/{stem}.json", p)
    print(f"converted {len(CHESS_COM)} chess.com + {len(SHORTIES)} janko puzzles")


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
