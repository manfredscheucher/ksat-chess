package org.bytefred.satchess.chess

/** Standard algebraic notation (SAN) writing and tolerant parsing, behind [Rules.san] / [Rules.parseSan]. */
internal object San {
    private const val PIECE_LETTERS = "KQRBN"
    private const val PROMOTION_LETTERS = "QRBN"

    fun format(position: Position, move: Move): String {
        val legal = Rules.legalMoves(position)
        val m = legal.firstOrNull { it.from == move.from && it.to == move.to && it.promotion == move.promotion }
            ?: throw IllegalArgumentException("illegal move ${move.uci} in ${Fen.toFen(position)}")
        val piece = position[m.from]!!
        val capture = m.enPassant || position[m.to] != null
        val body = when {
            m.castle != null -> if (m.castle.isKingSide) "O-O" else "O-O-O"
            piece.type == PieceType.PAWN -> buildString {
                if (capture) append(Sq.name(m.from)[0]).append('x')
                append(Sq.name(m.to))
                m.promotion?.let { append('=').append(it.fen.uppercaseChar()) }
            }
            else -> buildString {
                append(piece.type.fen.uppercaseChar())
                append(disambiguation(position, m, legal))
                if (capture) append('x')
                append(Sq.name(m.to))
            }
        }
        val next = Rules.apply(position, m)
        val suffix = when {
            !Rules.isInCheck(next) -> ""
            Rules.legalMoves(next).isEmpty() -> "#"
            else -> "+"
        }
        return body + suffix
    }

    /** File if it is unique among same-piece moves to the target, else rank, else both. */
    private fun disambiguation(position: Position, move: Move, legal: List<Move>): String {
        val rivals = legal.filter { it.to == move.to && it.from != move.from && position[it.from] == position[move.from] }
        if (rivals.isEmpty()) return ""
        val name = Sq.name(move.from)
        return when {
            rivals.none { Sq.col(it.from) == Sq.col(move.from) } -> name.substring(0, 1)
            rivals.none { Sq.row(it.from) == Sq.row(move.from) } -> name.substring(1)
            else -> name
        }
    }

    fun parse(position: Position, text: String): Move? {
        var s = text.trim().trimEnd('+', '#', '!', '?').replace('0', 'O')
        val legal = Rules.legalMoves(position)
        when (s.uppercase()) {
            "O-O" -> return legal.firstOrNull { it.castle?.isKingSide == true }
            "O-O-O" -> return legal.firstOrNull { it.castle?.isKingSide == false }
        }
        s = s.replace("x", "")

        var promotion: PieceType? = null
        if (s.length >= 3 && s.last().uppercaseChar() in PROMOTION_LETTERS && (s[s.length - 2] == '=' || s[s.length - 2].isDigit())) {
            promotion = pieceType(s.last())
            s = s.dropLast(1).removeSuffix("=")
        }

        var type = PieceType.PAWN
        if (s.isNotEmpty() && s.first() in PIECE_LETTERS) {
            type = pieceType(s.first())
            s = s.drop(1)
        }
        if (s.length !in 2..4) return null
        val to = Sq.parse(s.takeLast(2)) ?: return null

        var fileHint: Int? = null
        var rowHint: Int? = null
        for (c in s.dropLast(2)) {
            when (c) {
                in 'a'..'h' -> fileHint = c - 'a'
                in '1'..'8' -> rowHint = 8 - (c - '0')
                else -> return null
            }
        }
        return legal.singleOrNull {
            it.to == to && it.promotion == promotion && position[it.from]!!.type == type &&
                (fileHint == null || Sq.col(it.from) == fileHint) &&
                (rowHint == null || Sq.row(it.from) == rowHint)
        }
    }

    private fun pieceType(letter: Char): PieceType = PieceType.entries.first { it.fen == letter.lowercaseChar() }
}
