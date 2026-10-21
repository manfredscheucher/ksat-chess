package org.bytefred.satchess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.ui.model.ALL_OPTIONS
import org.bytefred.satchess.ui.model.EMPTY
import org.bytefred.satchess.ui.model.UNKNOWN
import org.bytefred.satchess.ui.model.isPiece
import org.bytefred.satchess.ui.model.placementText

/** Calm slate board palette. */
object BoardColors {
    val light = Color(0xFFDEE3E6)
    val dark = Color(0xFF8CA2AD)
    val lastMove = Color(0x66F2C94C)
    val selected = Color(0xFF2F5D62)
    val unknown = Color(0xFF3A4A52)
    /** Square options ("one of these stands here"): amber tint + hatching behind faded mini pieces. */
    val optionsTint = Color(0x4DF2B33D)
    val optionsHatch = Color(0x80B9771A)
    val coordinateOnLight = Color(0xFF6F8792)
    val coordinateOnDark = Color(0xFFEEF2F4)
}

/** The 12 piece painters, remembered once per composition. */
@Composable
fun rememberPiecePainters(): Map<Char, VectorPainter> =
    Piece.entries.associate { it.fen to rememberVectorPainter(PieceGraphics.of(it)) }

/**
 * A chess board drawn on a Canvas: 64 [cells] (FEN letter, '.' or '?'), White at the bottom.
 * [options] gives the allowed contents of '?' squares: a square with only some allowed is drawn
 * hatched with faded mini pieces, one with everything allowed (or not in the map) as '?'.
 * Interaction is optional: [onTap] for clicks, [onMove] / [onDropOff] for dragging pieces (a drop
 * outside the board removes the piece), [onOptions] for a long press or right click.
 * [highlights] tints squares (last move from/to).
 */
@Composable
fun BoardView(
    cells: List<Char>,
    modifier: Modifier = Modifier,
    options: Map<Int, Set<Char>> = emptyMap(),
    showCoordinates: Boolean = true,
    highlights: Set<Int> = emptySet(),
    onTap: ((sq: Int) -> Unit)? = null,
    onMove: ((from: Int, to: Int) -> Unit)? = null,
    onDropOff: ((from: Int) -> Unit)? = null,
    onOptions: ((sq: Int) -> Unit)? = null,
) {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer()
    val currentCells by rememberUpdatedState(cells)
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMove)
    val dropOff by rememberUpdatedState(onDropOff)
    val longPress by rememberUpdatedState(onOptions)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    var input = Modifier.aspectRatio(1f).semantics { contentDescription = "Board ${placementText(cells)}" }
    if (onOptions != null) {
        // Right click: consumed in the initial pass, so neither the tap nor the drag handler sees it.
        input = input.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                    val change = event.changes.firstOrNull() ?: continue
                    squareAt(change.position, size.width.toFloat())?.let { longPress?.invoke(it) }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
    if (onTap != null || onOptions != null) {
        input = input.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { pos -> squareAt(pos, size.width.toFloat())?.let { longPress?.invoke(it) } },
                onTap = { pos -> squareAt(pos, size.width.toFloat())?.let { tap?.invoke(it) } },
            )
        }
    }
    if (onMove != null) {
        // Only a press on a piece starts a drag; anything else stays unconsumed so a surrounding
        // scroll container can still scroll when the user swipes over empty squares.
        input = input.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val from = squareAt(down.position, size.width.toFloat())
                    ?.takeIf { isPiece(currentCells[it]) } ?: return@awaitEachGesture
                var pos = down.position
                val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                    pos = change.position
                } ?: return@awaitEachGesture
                dragFrom = from
                dragPos = pos
                val completed = drag(start.id) { change ->
                    dragPos = change.position
                    change.consume()
                }
                dragFrom = null
                if (completed) {
                    val to = squareAt(dragPos, size.width.toFloat())
                    if (to == null) dropOff?.invoke(from) else move?.invoke(from, to)
                }
            }
        }
    }

    Canvas(modifier.then(input)) {
        val square = size.width / 8
        drawSquares(square, highlights, showCoordinates, textMeasurer)
        for (sq in 0 until 64) {
            if (sq == dragFrom) continue
            val topLeft = Offset(Sq.col(sq) * square, Sq.row(sq) * square)
            val allowed = options[sq]
            if (cells[sq] == UNKNOWN && allowed != null && allowed.size < ALL_OPTIONS.size) {
                drawOptions(allowed, topLeft, square, painters)
            } else {
                drawCell(cells[sq], topLeft, square, painters, textMeasurer)
            }
        }
        dragFrom?.let { from ->
            drawRect(BoardColors.selected, Offset(Sq.col(from) * square, Sq.row(from) * square), Size(square, square), style = Stroke(square * 0.05f))
            val lifted = square * 1.15f
            drawCell(cells[from], dragPos - Offset(lifted / 2, lifted / 2), lifted, painters, textMeasurer)
        }
    }
}

/** The square under [pos] on a board of [boardPx] width, or null outside the board. */
private fun squareAt(pos: Offset, boardPx: Float): Int? {
    if (pos.x < 0 || pos.y < 0 || pos.x >= boardPx || pos.y >= boardPx) return null
    val square = boardPx / 8
    return Sq.of((pos.y / square).toInt().coerceAtMost(7), (pos.x / square).toInt().coerceAtMost(7))
}

private fun DrawScope.drawSquares(square: Float, highlights: Set<Int>, coordinates: Boolean, textMeasurer: TextMeasurer) {
    val style = TextStyle(fontSize = (square / 5.5f).toSp(), fontWeight = FontWeight.SemiBold)
    for (sq in 0 until 64) {
        val topLeft = Offset(Sq.col(sq) * square, Sq.row(sq) * square)
        val light = Sq.isLight(sq)
        drawRect(if (light) BoardColors.light else BoardColors.dark, topLeft, Size(square, square))
        if (sq in highlights) drawRect(BoardColors.lastMove, topLeft, Size(square, square))
        if (!coordinates) continue
        val color = if (light) BoardColors.coordinateOnLight else BoardColors.coordinateOnDark
        val pad = square * 0.06f
        if (Sq.row(sq) == 7) {
            val file = textMeasurer.measure("${'a' + Sq.col(sq)}", style.copy(color = color))
            drawText(file, topLeft = topLeft + Offset(square - file.size.width - pad, square - file.size.height))
        }
        if (Sq.col(sq) == 0) {
            drawText(textMeasurer.measure("${8 - Sq.row(sq)}", style.copy(color = color)), topLeft = topLeft + Offset(pad, 0f))
        }
    }
}

/** Draws one cell's content (piece, '?' marker or nothing) into the [square] at [topLeft]. */
fun DrawScope.drawCell(cell: Char, topLeft: Offset, square: Float, painters: Map<Char, VectorPainter>, textMeasurer: TextMeasurer) {
    val painter = painters[cell]
    if (painter != null) {
        translate(topLeft.x, topLeft.y) {
            with(painter) { draw(Size(square, square)) }
        }
    } else if (cell == UNKNOWN) {
        val center = topLeft + Offset(square / 2, square / 2)
        drawCircle(BoardColors.unknown.copy(alpha = 0.18f), square * 0.32f, center)
        val label = textMeasurer.measure(
            "?",
            TextStyle(fontSize = (square * 0.42f).toSp(), fontWeight = FontWeight.Bold, color = BoardColors.unknown),
        )
        drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
    }
}

/**
 * Draws the options of a square: amber tint, diagonal hatching and the allowed pieces as faded mini
 * icons in a grid (empty as a small crossed circle), so it never looks like a fixed piece.
 */
fun DrawScope.drawOptions(allowed: Set<Char>, topLeft: Offset, square: Float, painters: Map<Char, VectorPainter>) {
    clipRect(topLeft.x, topLeft.y, topLeft.x + square, topLeft.y + square) {
        drawRect(BoardColors.optionsTint, topLeft, Size(square, square))
        val gap = square / 5
        var x = -square
        while (x < square) {
            drawLine(BoardColors.optionsHatch, topLeft + Offset(x, square), topLeft + Offset(x + square, 0f), square * 0.025f)
            x += gap
        }
    }
    val items = ALL_OPTIONS.filter { it in allowed }
    if (items.isEmpty()) return
    var columns = 1
    while (columns * columns < items.size) columns++
    val rows = (items.size + columns - 1) / columns
    val cell = square * 0.9f / columns
    val origin = topLeft + Offset((square - cell * columns) / 2, (square - cell * rows) / 2)
    for ((i, item) in items.withIndex()) {
        val at = origin + Offset((i % columns) * cell, (i / columns) * cell)
        val painter = painters[item]
        if (painter != null) {
            translate(at.x, at.y) { with(painter) { draw(Size(cell, cell), alpha = 0.7f) } }
        } else if (item == EMPTY) {
            drawEmptyMark(at, cell)
        }
    }
}

/** The "empty" option: a crossed circle in the [square] at [topLeft]. */
fun DrawScope.drawEmptyMark(topLeft: Offset, square: Float) {
    val center = topLeft + Offset(square / 2, square / 2)
    val r = square * 0.28f
    drawCircle(BoardColors.unknown, r, center, style = Stroke(square * 0.07f))
    drawLine(BoardColors.unknown, center + Offset(-r, r), center + Offset(r, -r), square * 0.07f)
}
