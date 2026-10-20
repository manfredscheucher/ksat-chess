package org.bytefred.satchess.chess

/** FEN conversion. Placement strings may contain '?' (unknown square) for [BoardPattern]s. */
object Fen {
    const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    const val START = "$START_PLACEMENT w KQkq - 0 1"

    /** Parses a placement field (with optional '?'); "startpos" = initial placement. */
    fun parsePattern(placement: String): BoardPattern = TODO()
    fun patternToString(pattern: BoardPattern): String = TODO()

    /** Parses a full FEN (fields 1-4 required, clocks optional) or a fixed placement only. */
    fun parsePosition(fen: String): Position = TODO()
    /** Full FEN with "0 1" clocks. */
    fun toFen(position: Position): String = TODO()
    fun placement(board: List<Piece?>): String = TODO()
}

/** Reference chess rules (legal move generation), independent of the SAT encoding. */
object Rules {
    fun legalMoves(position: Position): List<Move> = TODO()
    fun apply(position: Position, move: Move): Position = TODO()
    fun isAttacked(position: Position, sq: Int, by: Color): Boolean = TODO()
    fun isInCheck(position: Position, color: Color = position.sideToMove): Boolean = TODO()
    fun isCheckmate(position: Position): Boolean = TODO()
    fun isStalemate(position: Position): Boolean = TODO()
    fun perft(position: Position, depth: Int): Long = TODO()

    /** Standard algebraic notation incl. disambiguation, check '+' and mate '#'. */
    fun san(position: Position, move: Move): String = TODO()
    /** Parses SAN (tolerant: accepts '+', '#', 'x' optional, "0-0") into a legal move, or null. */
    fun parseSan(position: Position, san: String): Move? = TODO()
}
