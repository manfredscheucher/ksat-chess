package org.bytefred.satchess.chess

/** Side to move / piece owner. */
enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
    val fen: Char get() = if (this == WHITE) 'w' else 'b'
}

enum class PieceType(val fen: Char) { KING('k'), QUEEN('q'), ROOK('r'), BISHOP('b'), KNIGHT('n'), PAWN('p') }

/** The 12 chess men. [fen] is the FEN letter (upper case = white). */
enum class Piece(val color: Color, val type: PieceType, val fen: Char) {
    WK(Color.WHITE, PieceType.KING, 'K'), WQ(Color.WHITE, PieceType.QUEEN, 'Q'),
    WR(Color.WHITE, PieceType.ROOK, 'R'), WB(Color.WHITE, PieceType.BISHOP, 'B'),
    WN(Color.WHITE, PieceType.KNIGHT, 'N'), WP(Color.WHITE, PieceType.PAWN, 'P'),
    BK(Color.BLACK, PieceType.KING, 'k'), BQ(Color.BLACK, PieceType.QUEEN, 'q'),
    BR(Color.BLACK, PieceType.ROOK, 'r'), BB(Color.BLACK, PieceType.BISHOP, 'b'),
    BN(Color.BLACK, PieceType.KNIGHT, 'n'), BP(Color.BLACK, PieceType.PAWN, 'p');

    companion object {
        fun of(color: Color, type: PieceType): Piece = entries.first { it.color == color && it.type == type }
        fun fromFen(c: Char): Piece? = entries.firstOrNull { it.fen == c }
    }
}

/**
 * Squares are Ints 0..63 with `sq = row * 8 + col`, row 0 = rank 8 (top, black's back rank),
 * col 0 = file a. This matches the Python encoder's (r, c) and FEN reading order.
 */
object Sq {
    fun of(row: Int, col: Int): Int = row * 8 + col
    fun row(sq: Int): Int = sq / 8
    fun col(sq: Int): Int = sq % 8
    fun name(sq: Int): String = "${'a' + col(sq)}${8 - row(sq)}"
    fun parse(name: String): Int? {
        if (name.length != 2) return null
        val c = name[0] - 'a'
        val r = 8 - (name[1] - '0')
        return if (c in 0..7 && r in 0..7) of(r, c) else null
    }
    fun isLight(sq: Int): Boolean = (row(sq) + col(sq)) % 2 == 0
}

/** Castling rights as FEN letters: 'K', 'Q' (white), 'k', 'q' (black). */
enum class CastleRight(val fen: Char, val color: Color) {
    WHITE_KING('K', Color.WHITE), WHITE_QUEEN('Q', Color.WHITE),
    BLACK_KING('k', Color.BLACK), BLACK_QUEEN('q', Color.BLACK);

    /** True for O-O (towards the h-file), false for O-O-O. */
    val isKingSide: Boolean get() = this == WHITE_KING || this == BLACK_KING
}

/**
 * A complete position. [board] has 64 entries indexed by [Sq] (null = empty).
 * [enPassant] is the e.p. TARGET square (FEN field 4, e.g. e3), or null.
 */
data class Position(
    val board: List<Piece?>,
    val sideToMove: Color = Color.WHITE,
    val castling: Set<CastleRight> = emptySet(),
    val enPassant: Int? = null,
) {
    init { require(board.size == 64) }
    operator fun get(sq: Int): Piece? = board[sq]
}

/** A move. [promotion] is set for pawn promotions; [castle] / [enPassant] flag special moves. */
data class Move(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val castle: CastleRight? = null,
    val enPassant: Boolean = false,
) {
    /** UCI-style long notation, e.g. "e2e4", "e7e8q". */
    val uci: String get() = Sq.name(from) + Sq.name(to) + (promotion?.fen?.toString() ?: "")
}

/**
 * A board CONSTRAINT: 64 cells, each '?' (unknown), '.' (empty) or a FEN piece letter.
 * Written as FEN piece placement where '?' marks unknown squares, e.g. "????????/.../???k????".
 */
data class BoardPattern(val cells: List<Char>) {
    init { require(cells.size == 64) }
    val isOpen: Boolean get() = cells.all { it == '?' }
    val isFixed: Boolean get() = cells.none { it == '?' }
}
