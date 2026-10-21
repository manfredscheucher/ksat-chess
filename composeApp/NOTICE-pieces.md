# Chess piece graphics

The piece images on the board (`src/commonMain/kotlin/org/bytefred/satchess/ui/board/PieceGraphics.kt`)
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

## BSD 3-clause license (as given on Wikimedia Commons)

Copyright © The author (Colin M.L. Burnett)

Redistribution and use in source and binary forms, with or without modification, are permitted
provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and
   the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
   and the following disclaimer in the documentation and/or other materials provided with the
   distribution.
3. Neither the name of The author nor the names of its contributors may be used to endorse or promote
   products derived from this software without specific prior written permission.

This software is provided by The author and contributors "as is" and any express or implied
warranties, including, but not limited to, the implied warranties of merchantability and fitness for a
particular purpose are disclaimed. In no event shall The author and contributors be liable for any
direct, indirect, incidental, special, exemplary, or consequential damages (including, but not limited
to, procurement of substitute goods or services; loss of use, data, or profits; or business
interruption) however caused and on any theory of liability, whether in contract, strict liability, or
tort (including negligence or otherwise) arising in any way out of the use of this software, even if
advised of the possibility of such damage.
