package org.bytefred.satchess.chess

import kotlin.math.abs

/** FEN conversion. Placement strings may contain '?' (unknown square) for [BoardPattern]s. */
object Fen {
    const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    const val START = "$START_PLACEMENT w KQkq - 0 1"

    private val WHITESPACE = Regex("\\s+")

    /** Parses a placement field (with optional '?'); "startpos" = initial placement. */
    fun parsePattern(placement: String): BoardPattern {
        val text = placement.trim().let { if (it == "startpos") START_PLACEMENT else it }
        val ranks = text.split('/')
        require(ranks.size == 8) { "FEN placement needs 8 ranks separated by '/', got ${ranks.size}: '$placement'" }
        val cells = ArrayList<Char>(64)
        for ((i, rank) in ranks.withIndex()) {
            var files = 0
            for (c in rank) {
                when {
                    c in '1'..'8' -> {
                        repeat(c - '0') { cells += '.' }
                        files += c - '0'
                    }
                    c == '?' || Piece.fromFen(c) != null -> {
                        cells += c
                        files++
                    }
                    else -> throw IllegalArgumentException("invalid character '$c' in rank ${8 - i} of '$placement'")
                }
            }
            require(files == 8) { "rank ${8 - i} has $files files instead of 8 in '$placement'" }
        }
        return BoardPattern(cells)
    }

    fun patternToString(pattern: BoardPattern): String =
        (0 until 8).joinToString("/") { row -> compressRank(pattern.cells.subList(row * 8, row * 8 + 8)) }

    /** Parses a full FEN (fields 1-4 required, clocks optional) or a fixed placement only. */
    fun parsePosition(fen: String): Position {
        val fields = fen.trim().split(WHITESPACE)
        require(fields.size == 1 || fields.size in 4..6) {
            "FEN needs a placement only or at least 4 fields (placement, side, castling, e.p.), got ${fields.size}: '$fen'"
        }
        val pattern = parsePattern(fields[0])
        require(pattern.isFixed) { "a position cannot contain '?': '$fen'" }
        val board = pattern.cells.map { if (it == '.') null else Piece.fromFen(it) }
        if (fields.size == 1) return Position(board)

        val side = when (fields[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> throw IllegalArgumentException("side to move must be 'w' or 'b', got '${fields[1]}'")
        }
        val castling = parseCastling(fields[2])
        val enPassant = parseEnPassant(fields[3], side)
        for (clock in fields.drop(4)) {
            require(clock.toIntOrNull()?.let { it >= 0 } == true) { "invalid move clock '$clock' in '$fen'" }
        }
        return Position(board, side, castling, enPassant)
    }

    /** Full FEN with "0 1" clocks. */
    fun toFen(position: Position): String {
        val castling = CastleRight.entries.filter { it in position.castling }.joinToString("") { it.fen.toString() }
        val enPassant = position.enPassant?.let { Sq.name(it) } ?: "-"
        return "${placement(position.board)} ${position.sideToMove.fen} ${castling.ifEmpty { "-" }} $enPassant 0 1"
    }

    fun placement(board: List<Piece?>): String {
        require(board.size == 64) { "board needs 64 squares, got ${board.size}" }
        return (0 until 8).joinToString("/") { row ->
            compressRank(board.subList(row * 8, row * 8 + 8).map { it?.fen ?: '.' })
        }
    }

    /** Writes one rank, replacing runs of '.' by their length. */
    private fun compressRank(cells: List<Char>): String = buildString {
        var empty = 0
        for (c in cells) {
            if (c == '.') {
                empty++
                continue
            }
            if (empty > 0) {
                append(empty)
                empty = 0
            }
            append(c)
        }
        if (empty > 0) append(empty)
    }

    private fun parseCastling(field: String): Set<CastleRight> {
        if (field == "-") return emptySet()
        val rights = field.map { c ->
            CastleRight.entries.firstOrNull { it.fen == c }
                ?: throw IllegalArgumentException("invalid castling letter '$c' in '$field'")
        }
        require(rights.size == rights.toSet().size) { "duplicate castling letter in '$field'" }
        return rights.toSet()
    }

    /** The e.p. target must be on rank 6 with White to move, or on rank 3 with Black to move. */
    private fun parseEnPassant(field: String, side: Color): Int? {
        if (field == "-") return null
        val sq = Sq.parse(field) ?: throw IllegalArgumentException("invalid en passant square '$field'")
        val expectedRank = if (side == Color.WHITE) '6' else '3'
        require(field[1] == expectedRank) { "en passant square '$field' must be on rank $expectedRank when ${side.fen} is to move" }
        return sq
    }
}

/** Reference chess rules (legal move generation), independent of the SAT encoding. */
object Rules {
    fun legalMoves(position: Position): List<Move> = MoveGen.legalMoves(position)

    /**
     * Plays [move] (assumed legal). Castling rights are lost when the king or a rook leaves its
     * home square or anything lands on a rook's home square (captures it). The e.p. target is set
     * after EVERY double pawn push, whether or not an e.p. capture is possible (classic FEN rule).
     */
    fun apply(position: Position, move: Move): Position {
        val piece = requireNotNull(position[move.from]) { "no piece on ${Sq.name(move.from)}" }
        val board = position.board.toTypedArray()
        MoveGen.play(board, move)
        val touched = setOf(move.from, move.to)
        val castling = position.castling.filterTo(mutableSetOf()) { right ->
            MoveGen.kingHome(right.color) !in touched && MoveGen.rookHome(right) !in touched
        }
        val enPassant = if (piece.type == PieceType.PAWN && abs(Sq.row(move.to) - Sq.row(move.from)) == 2) {
            Sq.of((Sq.row(move.from) + Sq.row(move.to)) / 2, Sq.col(move.from))
        } else {
            null
        }
        return Position(board.toList(), position.sideToMove.opposite, castling, enPassant)
    }

    fun isAttacked(position: Position, sq: Int, by: Color): Boolean =
        MoveGen.isAttacked(position.board.toTypedArray(), sq, by)

    /** False if [color] has no king. */
    fun isInCheck(position: Position, color: Color = position.sideToMove): Boolean {
        val board = position.board.toTypedArray()
        val king = MoveGen.kingSquare(board, color) ?: return false
        return MoveGen.isAttacked(board, king, color.opposite)
    }

    fun isCheckmate(position: Position): Boolean = isInCheck(position) && legalMoves(position).isEmpty()

    fun isStalemate(position: Position): Boolean = !isInCheck(position) && legalMoves(position).isEmpty()

    fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = legalMoves(position)
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(apply(position, it), depth - 1) }
    }

    /** Standard algebraic notation incl. disambiguation, check '+' and mate '#'. */
    fun san(position: Position, move: Move): String = San.format(position, move)

    /** Parses SAN (tolerant: accepts '+', '#', 'x' optional, "0-0") into a legal move, or null. */
    fun parseSan(position: Position, san: String): Move? = San.parse(position, san)
}
