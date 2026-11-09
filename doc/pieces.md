# Chess piece graphics

The piece images on the board (`composeApp/src/commonMain/kotlin/org/bytefred/satchess/ui/board/PieceGraphics.kt`)
are the **cburnett** set by **Colin M.L. Burnett** (Wikimedia Commons user
[Cburnett](https://commons.wikimedia.org/wiki/User:Cburnett)), the same set lichess uses:

- Source: https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces
  (files `Chess_klt45.svg`, `Chess_kdt45.svg`, `Chess_qlt45.svg`, `Chess_qdt45.svg`, `Chess_rlt45.svg`,
  `Chess_rdt45.svg`, `Chess_blt45.svg`, `Chess_bdt45.svg`, `Chess_nlt45.svg`, `Chess_ndt45.svg`,
  `Chess_plt45.svg`, `Chess_pdt45.svg`; license checked on
  https://commons.wikimedia.org/wiki/File:Chess_klt45.svg, 2026-10-20).
- The files are offered under GFDL 1.2+, CC BY-SA 3.0, **BSD 3-clause** and GPL 2+, "you may select
  the license of your choice". This project uses them under the **BSD 3-clause** license (compatible
  with the project's MIT license).
- Changes: the SVG path data is embedded unchanged as Compose `ImageVector`s; SVG styles are expressed
  as path attributes and `<circle>` elements as two arcs.

The license text shipped with the app: [`licenses/LICENSE_CBURNETT.txt`](licenses/LICENSE_CBURNETT.txt).
