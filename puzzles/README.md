# Example puzzles

One JSON file per puzzle (format: `doc/spec.md`, section 4). Licensing and
credits: [NOTICE.md](NOTICE.md).

| Folder | Content |
|---|---|
| `demos/` | Small demos of the format: helpmate goal, last move, black to move, en passant, castling, promotion, unknown piece. |
| `chess-com-5/` | The 5 puzzles from the chess.com article "Can You Solve These 5 'Easy' Retrograde Puzzles?" (2017). |

## Regenerate

The app embeds these files via the generated
`core/src/commonMain/kotlin/org/bytefred/satchess/puzzle/ExamplePuzzles.kt`.
After editing or adding a JSON file:

```bash
python3 scripts/gen_puzzles.py
```

`chess-com-5/` is converted from the Python sat-chess project
(`../sat-chess/configs/puzzles`). Titles, credits and descriptions live in the
selection list in `scripts/gen_puzzles.py`, so edit them there and re-run with
`--convert` (this overwrites that folder; `demos/` and `chess-com-5/2.json` are
hand-written and never touched):

```bash
python3 scripts/gen_puzzles.py --convert
```

A new folder needs an entry in `CATEGORIES` in the script.
