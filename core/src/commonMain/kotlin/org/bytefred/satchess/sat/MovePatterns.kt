package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.MovePattern

/**
 * A move pattern resolved for one ply, independent of the board. SAN cannot be resolved to a
 * concrete move without the board, so it becomes constraints on the move variables instead:
 * piece letter, target square, capture flag, promotion, castling, disambiguation (as from-file /
 * from-rank), and the check flag.
 */
internal data class MoveConstraint(
    val piece: Piece? = null,
    val fromFile: Int? = null,
    val fromRow: Int? = null,
    val to: Int? = null,
    val capture: Boolean? = null,
    val promotion: PieceType? = null,
    /** Castling rights the move must use (one of them); empty list = no castling allowed; null = free. */
    val castle: List<CastleRight>? = null,
    val check: Boolean? = null,
)

private val SAN = Regex("^([KQRBN])?([a-h])?([1-8])?(x)?([a-h][1-8])(?:=?([QRBN]))?([+#])?$")
private val SAN_CASTLE = Regex("^([O0])-\\1(-\\1)?([+#])?$")

private fun type(letter: Char): PieceType =
    PieceType.entries.firstOrNull { it.fen == letter.lowercaseChar() }
        ?: throw IllegalArgumentException("invalid piece letter '$letter'")

private fun square(name: String, what: String): Int =
    Sq.parse(name) ?: throw IllegalArgumentException("move pattern: invalid $what square '$name'")

/**
 * Reads SAN as constraints for [mover]. Strict about 'x' (no 'x' = no capture), tolerant about
 * '+' / '#' (absent = check is free; '#' only requires check, mate is not encoded per ply).
 */
internal fun parseSanConstraint(san: String, mover: Color): MoveConstraint {
    val text = san.trim()
    SAN_CASTLE.matchEntire(text)?.let { m ->
        val kingSide = m.groupValues[2].isEmpty()
        val right = CastleRight.entries.first { it.color == mover && it.isKingSide == kingSide }
        return MoveConstraint(
            piece = Piece.of(mover, PieceType.KING),
            castle = listOf(right),
            check = if (m.groupValues[3].isNotEmpty()) true else null,
        )
    }
    val m = SAN.matchEntire(text) ?: throw IllegalArgumentException("move pattern: cannot read SAN '$san'")
    val (pieceLetter, file, rank, x, to, promo, check) = m.destructured
    return MoveConstraint(
        piece = Piece.of(mover, if (pieceLetter.isEmpty()) PieceType.PAWN else type(pieceLetter[0])),
        fromFile = file.firstOrNull()?.let { it - 'a' },
        fromRow = rank.firstOrNull()?.let { 8 - (it - '0') },
        to = square(to, "target"),
        capture = x.isNotEmpty(),
        promotion = promo.firstOrNull()?.let { type(it) },
        castle = emptyList(),
        check = if (check.isNotEmpty()) true else null,
    )
}

/** Reads the object fields of [pattern] (without its SAN) as constraints for [mover]. */
internal fun fieldConstraint(pattern: MovePattern, mover: Color): MoveConstraint {
    val from = pattern.from?.let { square(it, "from") }
    return MoveConstraint(
        piece = pattern.piece?.let {
            require(it.length == 1) { "move pattern: piece must be one FEN letter, got '$it'" }
            Piece.fromFen(it[0]) ?: throw IllegalArgumentException("move pattern: invalid piece '$it'")
        },
        fromFile = from?.let { Sq.col(it) },
        fromRow = from?.let { Sq.row(it) },
        to = pattern.to?.let { square(it, "to") },
        capture = pattern.capture,
        promotion = pattern.promo?.let {
            require(it.length == 1) { "move pattern: promo must be one letter, got '$it'" }
            type(it[0]).also { t -> require(t in Encoding.PROMOTIONS) { "move pattern: cannot promote to '$it'" } }
        },
        castle = pattern.castle?.let { c ->
            if (c == "any") CastleRight.entries.filter { it.color == mover }
            else listOf(CastleRight.entries.firstOrNull { it.fen.toString() == c }
                ?: throw IllegalArgumentException("move pattern: castle must be K, Q, k, q or any, got '$c'"))
        },
        check = pattern.check,
    )
}

/** Adds the clauses of every move pattern of the puzzle. */
internal fun encodeMovePatterns(e: Encoding) {
    for ((key, pattern) in e.puzzle.moves) {
        val t = key.toIntOrNull() ?: throw IllegalArgumentException("moves: ply key '$key' is not a number")
        require(t in 0 until e.plies) { "moves: ply $t is outside 0..${e.plies - 1}" }
        val mover = e.side(t)
        pattern.san?.let { e.encodeConstraint(t, parseSanConstraint(it, mover)) }
        e.encodeConstraint(t, fieldConstraint(pattern, mover))
    }
}

private fun Encoding.encodeConstraint(t: Int, k: MoveConstraint) {
    for (sq in 0 until 64) {
        val src = source(t, sq)
        k.piece?.let { cnf.add(-src, at(t, sq, it)) }
        if (k.fromFile != null && Sq.col(sq) != k.fromFile) cnf.add(-src)
        if (k.fromRow != null && Sq.row(sq) != k.fromRow) cnf.add(-src)
        if (k.to != null && sq != k.to) cnf.add(-target(t, sq))
        when (k.capture) {
            true -> cnf.add(-target(t, sq), -empty(t, sq), epCapture(t))
            false -> cnf.add(-target(t, sq), empty(t, sq))
            null -> Unit
        }
    }
    if (k.capture == false) cnf.add(-epCapture(t))
    k.promotion?.let { cnf.add(promotion(t, Encoding.PROMOTIONS.indexOf(it))) }
    k.castle?.let { rights ->
        val mine = CastleRight.entries.filter { castleMove(t, it) != 0 }
        if (rights.isEmpty()) for (r in mine) cnf.add(-castleMove(t, r))
        else cnf.add(rights.filter { it in mine }.map { castleMove(t, it) }) // empty clause if none is the mover's
    }
    k.check?.let { cnf.add(if (it) inCheck(t + 1) else -inCheck(t + 1)) }
}
