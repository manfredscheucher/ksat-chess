/*
 * Chess piece artwork: the "cburnett" set by Colin M.L. Burnett (Wikimedia Commons user Cburnett),
 * https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces (files Chess_{k,q,r,b,n,p}{l,d}t45.svg).
 * The originals are tri-licensed GFDL 1.2+ / BSD 3-clause / GPL 2+ (plus CC BY-SA 3.0); this project
 * uses them under the BSD 3-clause license:
 *
 *   Copyright © The author (Colin M.L. Burnett)
 *
 *   Redistribution and use in source and binary forms, with or without modification, are permitted
 *   provided that the following conditions are met:
 *   1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *      and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *      conditions and the following disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *   3. Neither the name of The author nor the names of its contributors may be used to endorse or
 *      promote products derived from this software without specific prior written permission.
 *
 *   This software is provided by The author and contributors "as is" and any express or implied
 *   warranties, including, but not limited to, the implied warranties of merchantability and fitness
 *   for a particular purpose are disclaimed. In no event shall The author and contributors be liable
 *   for any direct, indirect, incidental, special, exemplary, or consequential damages (including,
 *   but not limited to, procurement of substitute goods or services; loss of use, data, or profits;
 *   or business interruption) however caused and on any theory of liability, whether in contract,
 *   strict liability, or tort (including negligence or otherwise) arising in any way out of the use
 *   of this software, even if advised of the possibility of such damage.
 *
 * The SVG path data below is copied unchanged; only the SVG styles are expressed as Compose path
 * attributes (circles written as two arcs). See doc/pieces.md.
 */
package org.bytefred.satchess.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.chess.Piece

/** The cburnett vector graphic for each piece (45 x 45 viewport), built once on first use. */
object PieceGraphics {
    private val vectors: Map<Piece, ImageVector> by lazy {
        mapOf(
            Piece.WK to whiteKing(), Piece.BK to blackKing(),
            Piece.WQ to whiteQueen(), Piece.BQ to blackQueen(),
            Piece.WR to whiteRook(), Piece.BR to blackRook(),
            Piece.WB to bishop(Color.White, Color.Black), Piece.BB to bishop(Color.Black, Color.White),
            Piece.WN to whiteKnight(), Piece.BN to blackKnight(),
            Piece.WP to pawn(Color.White), Piece.BP to pawn(Color.Black),
        )
    }

    fun of(piece: Piece): ImageVector = vectors.getValue(piece)
}

private val Ink = Color.Black
private val Paper = Color.White

private fun piece(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 45.dp,
        defaultHeight = 45.dp,
        viewportWidth = 45f,
        viewportHeight = 45f,
    ).apply(content).build()

/** One SVG `<path>`; the defaults are the set's common group style (black 1.5 stroke, round). */
private fun ImageVector.Builder.p(
    d: String,
    fill: Color? = null,
    stroke: Color? = Ink,
    width: Float = 1.5f,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
    evenOdd: Boolean = true,
) {
    addPath(
        pathData = addPathNodes(d),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        fill = fill?.let(::SolidColor),
        stroke = stroke?.let(::SolidColor),
        strokeLineWidth = if (stroke == null) 0f else width,
        strokeLineCap = cap,
        strokeLineJoin = join,
        strokeLineMiter = 4f,
    )
}

/** An SVG `<circle>` as a path of two half arcs. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r},$cy A $r,$r 0 1 1 ${cx + r},$cy A $r,$r 0 1 1 ${cx - r},$cy z"

private val queenCrown =
    "M 9,26 C 17.5,24.5 30,24.5 36,26 L 38.5,13.5 L 31,25 L 30.7,10.9 L 25.5,24.5 L 22.5,10 " +
        "L 19.5,24.5 L 14.3,10.9 L 14,25 L 6.5,13.5 L 9,26 z"
private val queenBalls = listOf(6f to 12f, 14f to 9f, 22.5f to 8f, 31f to 9f, 39f to 12f)

private fun whiteKing() = piece("wK") {
    p("M22.5 11.63V6M20 8h5", join = StrokeJoin.Miter)
    p(
        "M22.5 25s4.5-7.5 3-10.5c0 0-1-2.5-3-2.5s-3 2.5-3 2.5c-1.5 3 3 10.5 3 10.5",
        fill = Paper, cap = StrokeCap.Butt, join = StrokeJoin.Miter,
    )
    p(
        "M12.5 37c5.5 3.5 14.5 3.5 20 0v-7s9-4.5 6-10.5c-4-6.5-13.5-3.5-16 4V27v-3.5" +
            "c-2.5-7.5-12-10.5-16-4-3 6 6 10.5 6 10.5v7",
        fill = Paper,
    )
    p("M12.5 30c5.5-3 14.5-3 20 0m-20 3.5c5.5-3 14.5-3 20 0m-20 3.5c5.5-3 14.5-3 20 0")
}

private fun blackKing() = piece("bK") {
    p("M 22.5,11.63 L 22.5,6", join = StrokeJoin.Miter)
    p(
        "M 22.5,25 C 22.5,25 27,17.5 25.5,14.5 C 25.5,14.5 24.5,12 22.5,12 C 20.5,12 19.5,14.5 " +
            "19.5,14.5 C 18,17.5 22.5,25 22.5,25",
        fill = Ink, cap = StrokeCap.Butt, join = StrokeJoin.Miter,
    )
    p(
        "M 12.5,37 C 18,40.5 27,40.5 32.5,37 L 32.5,30 C 32.5,30 41.5,25.5 38.5,19.5 C 34.5,13 25,16 " +
            "22.5,23.5 L 22.5,27 L 22.5,23.5 C 20,16 10.5,13 6.5,19.5 C 3.5,25.5 12.5,30 12.5,30 L 12.5,37",
        fill = Ink,
    )
    p("M 20,8 L 25,8", join = StrokeJoin.Miter)
    p(
        "M 32,29.5 C 32,29.5 40.5,25.5 38.03,19.85 C 34.15,14 25,18 22.5,24.5 L 22.5,26.6 L 22.5,24.5 " +
            "C 20,18 10.85,14 6.97,19.85 C 4.5,25.5 13,29.5 13,29.5",
        stroke = Paper,
    )
    p(
        "M 12.5,30 C 18,27 27,27 32.5,30 M 12.5,33.5 C 18,30.5 27,30.5 32.5,33.5 M 12.5,37 " +
            "C 18,34 27,34 32.5,37",
        stroke = Paper,
    )
}

private fun whiteQueen() = piece("wQ") {
    // The group sets no line cap, so the SVG default (butt) applies.
    val cap = StrokeCap.Butt
    p(queenCrown, fill = Paper, cap = cap, evenOdd = false)
    p(
        "M 9,26 C 9,28 10.5,28 11.5,30 C 12.5,31.5 12.5,31 12,33.5 C 10.5,34.5 11,36 11,36 " +
            "C 9.5,37.5 11,38.5 11,38.5 C 17.5,39.5 27.5,39.5 34,38.5 C 34,38.5 35.5,37.5 34,36 " +
            "C 34,36 34.5,34.5 33,33.5 C 32.5,31 32.5,31.5 33.5,30 C 34.5,28 36,28 36,26 " +
            "C 27.5,24.5 17.5,24.5 9,26 z",
        fill = Paper, cap = cap, evenOdd = false,
    )
    p("M 11.5,30 C 15,29 30,29 33.5,30", cap = cap, evenOdd = false)
    p("M 12,33.5 C 18,32.5 27,32.5 33,33.5", cap = cap, evenOdd = false)
    for ((cx, cy) in queenBalls) p(circle(cx, cy, 2f), fill = Paper, cap = cap, evenOdd = false)
}

private fun blackQueen() = piece("bQ") {
    p(queenCrown, fill = Ink, cap = StrokeCap.Butt, evenOdd = false)
    p(
        "m 9,26 c 0,2 1.5,2 2.5,4 1,1.5 1,1 0.5,3.5 -1.5,1 -1,2.5 -1,2.5 -1.5,1.5 0,2.5 0,2.5 " +
            "6.5,1 16.5,1 23,0 0,0 1.5,-1 0,-2.5 0,0 0.5,-1.5 -1,-2.5 -0.5,-2.5 -0.5,-2 0.5,-3.5 " +
            "1,-2 2.5,-2 2.5,-4 -8.5,-1.5 -18.5,-1.5 -27,0 z",
        fill = Ink, evenOdd = false,
    )
    p("M 11.5,30 C 15,29 30,29 33.5,30", fill = Ink, evenOdd = false)
    p("m 12,33.5 c 6,-1 15,-1 21,0", fill = Ink, evenOdd = false)
    for ((cx, cy) in queenBalls) p(circle(cx, cy, 2f), fill = Ink, evenOdd = false)
    p("M 11,38.5 A 35,35 1 0 0 34,38.5", cap = StrokeCap.Butt, evenOdd = false)
    p("M 11,29 A 35,35 1 0 1 34,29", stroke = Paper, evenOdd = false)
    p("M 12.5,31.5 L 32.5,31.5", stroke = Paper, evenOdd = false)
    p("M 11.5,34.5 A 35,35 1 0 0 33.5,34.5", stroke = Paper, evenOdd = false)
    p("M 10.5,37.5 A 35,35 1 0 0 34.5,37.5", stroke = Paper, evenOdd = false)
}

private fun whiteRook() = piece("wR") {
    group(translationY = 0.3f) {
        p("M 9,39 L 36,39 L 36,36 L 9,36 L 9,39 z", fill = Paper, cap = StrokeCap.Butt)
        p("M 12,36 L 12,32 L 33,32 L 33,36 L 12,36 z", fill = Paper, cap = StrokeCap.Butt)
        p(
            "M 11,14 L 11,9 L 15,9 L 15,11 L 20,11 L 20,9 L 25,9 L 25,11 L 30,11 L 30,9 L 34,9 L 34,14",
            fill = Paper, cap = StrokeCap.Butt,
        )
        p("M 34,14 L 31,17 L 14,17 L 11,14", fill = Paper)
        p("M 31,17 L 31,29.5 L 14,29.5 L 14,17", fill = Paper, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        p("M 31,29.5 L 32.5,32 L 12.5,32 L 14,29.5", fill = Paper)
        p("M 11,14 L 34,14", join = StrokeJoin.Miter)
    }
}

private fun blackRook() = piece("bR") {
    group(translationY = 0.3f) {
        p("M 9,39 L 36,39 L 36,36 L 9,36 L 9,39 z", fill = Ink, cap = StrokeCap.Butt)
        p("M 12.5,32 L 14,29.5 L 31,29.5 L 32.5,32 L 12.5,32 z", fill = Ink, cap = StrokeCap.Butt)
        p("M 12,36 L 12,32 L 33,32 L 33,36 L 12,36 z", fill = Ink, cap = StrokeCap.Butt)
        p(
            "M 14,29.5 L 14,16.5 L 31,16.5 L 31,29.5 L 14,29.5 z",
            fill = Ink, cap = StrokeCap.Butt, join = StrokeJoin.Miter,
        )
        p("M 14,16.5 L 11,14 L 34,14 L 31,16.5 L 14,16.5 z", fill = Ink, cap = StrokeCap.Butt)
        p(
            "M 11,14 L 11,9 L 15,9 L 15,11 L 20,11 L 20,9 L 25,9 L 25,11 L 30,11 L 30,9 L 34,9 " +
                "L 34,14 L 11,14 z",
            fill = Ink, cap = StrokeCap.Butt,
        )
        for (line in listOf(
            "M 12,35.5 L 33,35.5 L 33,35.5", "M 13,31.5 L 32,31.5", "M 14,29.5 L 31,29.5",
            "M 14,16.5 L 31,16.5", "M 11,14 L 34,14",
        )) {
            p(line, stroke = Paper, width = 1f, join = StrokeJoin.Miter)
        }
    }
}

private fun bishop(body: Color, detail: Color) = piece(if (body == Paper) "wB" else "bB") {
    group(translationY = 0.6f) {
        p(
            "M 9,36 C 12.39,35.03 19.11,36.43 22.5,34 C 25.89,36.43 32.61,35.03 36,36 C 36,36 37.65,36.54 " +
                "39,38 C 38.32,38.97 37.35,38.99 36,38.5 C 32.61,37.53 25.89,38.96 22.5,37.5 C 19.11,38.96 " +
                "12.39,37.53 9,38.5 C 7.65,38.99 6.68,38.97 6,38 C 7.35,36.54 9,36 9,36 z",
            fill = body, cap = StrokeCap.Butt,
        )
        p(
            "M 15,32 C 17.5,34.5 27.5,34.5 30,32 C 30.5,30.5 30,30 30,30 C 30,27.5 27.5,26 27.5,26 " +
                "C 33,24.5 33.5,14.5 22.5,10.5 C 11.5,14.5 12,24.5 17.5,26 C 17.5,26 15,27.5 15,30 " +
                "C 15,30 14.5,30.5 15,32 z",
            fill = body, cap = StrokeCap.Butt,
        )
        p("M 25 8 A 2.5 2.5 0 1 1  20,8 A 2.5 2.5 0 1 1  25 8 z", fill = body, cap = StrokeCap.Butt)
        p(
            "M 17.5,26 L 27.5,26 M 15,30 L 30,30 M 22.5,15.5 L 22.5,20.5 M 20,18 L 25,18",
            stroke = detail, join = StrokeJoin.Miter,
        )
    }
}

private const val KNIGHT_BACK = "M 22,10 C 32.5,11 38.5,18 38,39 L 15,39 C 15,30 25,32.5 23,18"
private const val KNIGHT_HEAD =
    "M 24,18 C 24.38,20.91 18.45,25.37 16,27 C 13,29 13.18,31.34 11,31 C 9.958,30.06 12.41,27.96 11,28 " +
        "C 10,28 11.19,29.23 10,30 C 9,30 5.997,31 6,26 C 6,24 12,14 12,14 C 12,14 13.89,12.1 14,10.5 " +
        "C 13.27,9.506 13.5,8.5 13.5,7.5 C 14.5,6.5 16.5,10 16.5,10 L 18.5,10 C 18.5,10 19.28,8.008 21,7 " +
        "C 22,7 22,10 22,10"
private const val KNIGHT_NOSTRIL = "M 9.5 25.5 A 0.5 0.5 0 1 1 8.5,25.5 A 0.5 0.5 0 1 1 9.5 25.5 z"
private const val KNIGHT_EYE = "M 15 15.5 A 0.5 1.5 0 1 1  14,15.5 A 0.5 1.5 0 1 1  15 15.5 z"

/** The knight's eye: SVG matrix(0.866,0.5,-0.5,0.866,9.693,-5.173) = rotate 30 degrees about (14.5, 15.5). */
private fun ImageVector.Builder.knightEye(color: Color) {
    group(rotate = 30f, pivotX = 14.5f, pivotY = 15.5f) {
        p(KNIGHT_EYE, fill = color, stroke = color)
    }
}

private fun whiteKnight() = piece("wN") {
    group(translationY = 0.3f) {
        p(KNIGHT_BACK, fill = Paper)
        p(KNIGHT_HEAD, fill = Paper)
        p(KNIGHT_NOSTRIL, fill = Ink)
        knightEye(Ink)
    }
}

private fun blackKnight() = piece("bN") {
    group(translationY = 0.3f) {
        p(KNIGHT_BACK, fill = Ink)
        p(KNIGHT_HEAD, fill = Ink)
        p(KNIGHT_NOSTRIL, fill = Paper, stroke = Paper)
        knightEye(Paper)
        p(
            "M 24.55,10.4 L 24.1,11.85 L 24.6,12 C 27.75,13 30.25,14.49 32.5,18.75 C 34.75,23.01 " +
                "35.75,29.06 35.25,39 L 35.2,39.5 L 37.45,39.5 L 37.5,39 C 38,28.94 36.62,22.15 " +
                "34.25,17.66 C 31.88,13.17 28.46,11.02 25.06,10.5 L 24.55,10.4 z",
            fill = Paper, stroke = null,
        )
    }
}

private fun pawn(body: Color) = piece(if (body == Paper) "wP" else "bP") {
    p(
        "m 22.5,9 c -2.21,0 -4,1.79 -4,4 0,0.89 0.29,1.71 0.78,2.38 C 17.33,16.5 16,18.59 16,21 " +
            "c 0,2.03 0.94,3.84 2.41,5.03 C 15.41,27.09 11,31.58 11,39.5 H 34 C 34,31.58 29.59,27.09 " +
            "26.59,26.03 28.06,24.84 29,23.03 29,21 29,18.59 27.67,16.5 25.72,15.38 26.21,14.71 26.5,13.89 " +
            "26.5,13 c 0,-2.21 -1.79,-4 -4,-4 z",
        fill = body, join = StrokeJoin.Miter, evenOdd = false,
    )
}
