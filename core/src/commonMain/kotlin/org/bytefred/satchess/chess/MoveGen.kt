package org.bytefred.satchess.chess

import kotlin.math.abs

/**
 * Array-based move generation behind [Rules]. Boards are `Array<Piece?>` of size 64 indexed by [Sq].
 * Moves are generated pseudo-legally and filtered by playing them on a board copy and testing
 * whether the mover's king is attacked; this covers pins, check evasion and the horizontal
 * en passant pin without special cases.
 */
internal object MoveGen {
    private val KNIGHT_DELTAS = arrayOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
    private val KING_DELTAS = arrayOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

    /** Directions 0..3 are orthogonal (rook), 4..7 diagonal (bishop). */
    private val DIRECTIONS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val ROOK_DIRECTIONS = 0..3
    private val BISHOP_DIRECTIONS = 4..7
    private val QUEEN_DIRECTIONS = 0..7

    private val PROMOTIONS = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    private val knightTargets = Array(64) { sq -> steps(sq, KNIGHT_DELTAS) }
    private val kingTargets = Array(64) { sq -> steps(sq, KING_DELTAS) }

    /** `rays[d][sq]` = squares from [sq] in direction d, nearest first. */
    private val rays = Array(DIRECTIONS.size) { d -> Array(64) { sq -> ray(sq, DIRECTIONS[d]) } }

    private fun onBoard(row: Int, col: Int) = row in 0..7 && col in 0..7

    private fun steps(sq: Int, deltas: Array<Pair<Int, Int>>): IntArray =
        deltas.mapNotNull { (dr, dc) ->
            val r = Sq.row(sq) + dr
            val c = Sq.col(sq) + dc
            if (onBoard(r, c)) Sq.of(r, c) else null
        }.toIntArray()

    private fun ray(sq: Int, direction: Pair<Int, Int>): IntArray {
        val (dr, dc) = direction
        val squares = mutableListOf<Int>()
        var r = Sq.row(sq) + dr
        var c = Sq.col(sq) + dc
        while (onBoard(r, c)) {
            squares += Sq.of(r, c)
            r += dr
            c += dc
        }
        return squares.toIntArray()
    }

    /** Fast [Piece.of]: the enum lists white K..P then black K..P, in [PieceType] order. */
    fun pieceOf(color: Color, type: PieceType): Piece = Piece.entries[color.ordinal * 6 + type.ordinal]

    /** Home square of [color]'s king (e1 / e8). */
    fun kingHome(color: Color): Int = if (color == Color.WHITE) Sq.of(7, 4) else Sq.of(0, 4)

    /** Home square of the rook belonging to castling right [right] (h1, a1, h8, a8). */
    fun rookHome(right: CastleRight): Int = kingHome(right.color) + if (right.isKingSide) 3 else -4

    fun kingSquare(board: Array<Piece?>, color: Color): Int? {
        val king = pieceOf(color, PieceType.KING)
        return board.indexOf(king).takeIf { it >= 0 }
    }

    fun isAttacked(board: Array<Piece?>, sq: Int, by: Color): Boolean {
        val row = Sq.row(sq)
        val col = Sq.col(sq)
        val pawnRow = if (by == Color.WHITE) row + 1 else row - 1
        val pawn = pieceOf(by, PieceType.PAWN)
        for (dc in intArrayOf(-1, 1)) {
            if (onBoard(pawnRow, col + dc) && board[Sq.of(pawnRow, col + dc)] == pawn) return true
        }
        val knight = pieceOf(by, PieceType.KNIGHT)
        if (knightTargets[sq].any { board[it] == knight }) return true
        val king = pieceOf(by, PieceType.KING)
        if (kingTargets[sq].any { board[it] == king }) return true
        for (d in QUEEN_DIRECTIONS) {
            val slider = if (d in ROOK_DIRECTIONS) PieceType.ROOK else PieceType.BISHOP
            for (t in rays[d][sq]) {
                val p = board[t] ?: continue
                if (p.color == by && (p.type == slider || p.type == PieceType.QUEEN)) return true
                break
            }
        }
        return false
    }

    fun legalMoves(position: Position): List<Move> {
        val board = position.board.toTypedArray()
        val side = position.sideToMove
        val legal = ArrayList<Move>(48)
        for (move in pseudoMoves(position, board)) {
            val after = board.copyOf()
            play(after, move)
            val king = kingSquare(after, side)
            if (king == null || !isAttacked(after, king, side.opposite)) legal += move
        }
        return legal
    }

    /**
     * Plays [move] on [board] in place. Castling (king moves two files) and en passant
     * (pawn moves diagonally onto an empty square) are recognized from the board, so moves
     * without the [Move.castle] / [Move.enPassant] flags are handled too.
     */
    fun play(board: Array<Piece?>, move: Move) {
        val piece = requireNotNull(board[move.from]) { "no piece on ${Sq.name(move.from)}" }
        val row = Sq.row(move.from)
        val toCol = Sq.col(move.to)
        board[move.from] = null
        if (piece.type == PieceType.PAWN && Sq.col(move.from) != toCol && board[move.to] == null) {
            board[Sq.of(row, toCol)] = null
        }
        if (piece.type == PieceType.KING && abs(toCol - Sq.col(move.from)) == 2) {
            val (rookFrom, rookTo) = if (toCol == 6) Sq.of(row, 7) to Sq.of(row, 5) else Sq.of(row, 0) to Sq.of(row, 3)
            board[rookTo] = board[rookFrom]
            board[rookFrom] = null
        }
        board[move.to] = move.promotion?.let { pieceOf(piece.color, it) } ?: piece
    }

    private fun pseudoMoves(position: Position, board: Array<Piece?>): List<Move> {
        val side = position.sideToMove
        val moves = ArrayList<Move>(64)
        for (sq in 0 until 64) {
            val piece = board[sq] ?: continue
            if (piece.color != side) continue
            when (piece.type) {
                PieceType.PAWN -> pawnMoves(board, sq, side, position.enPassant, moves)
                PieceType.KNIGHT -> stepMoves(board, sq, side, knightTargets[sq], moves)
                PieceType.KING -> stepMoves(board, sq, side, kingTargets[sq], moves)
                PieceType.BISHOP -> slideMoves(board, sq, side, BISHOP_DIRECTIONS, moves)
                PieceType.ROOK -> slideMoves(board, sq, side, ROOK_DIRECTIONS, moves)
                PieceType.QUEEN -> slideMoves(board, sq, side, QUEEN_DIRECTIONS, moves)
            }
        }
        castlingMoves(position, board, moves)
        return moves
    }

    private fun pawnMoves(board: Array<Piece?>, sq: Int, side: Color, enPassant: Int?, moves: MutableList<Move>) {
        val dir = if (side == Color.WHITE) -1 else 1
        val startRow = if (side == Color.WHITE) 6 else 1
        val row = Sq.row(sq)
        val col = Sq.col(sq)
        val ahead = row + dir
        if (ahead !in 0..7) return
        val one = Sq.of(ahead, col)
        if (board[one] == null) {
            addPawnMove(sq, one, moves)
            val two = Sq.of(row + 2 * dir, col)
            if (row == startRow && board[two] == null) moves += Move(sq, two)
        }
        val enemyPawn = pieceOf(side.opposite, PieceType.PAWN)
        for (dc in intArrayOf(-1, 1)) {
            if (col + dc !in 0..7) continue
            val target = Sq.of(ahead, col + dc)
            val victim = board[target]
            if (victim != null) {
                if (victim.color != side) addPawnMove(sq, target, moves)
            } else if (target == enPassant && board[Sq.of(row, col + dc)] == enemyPawn) {
                moves += Move(sq, target, enPassant = true)
            }
        }
    }

    private fun addPawnMove(from: Int, to: Int, moves: MutableList<Move>) {
        val row = Sq.row(to)
        if (row == 0 || row == 7) {
            for (type in PROMOTIONS) moves += Move(from, to, promotion = type)
        } else {
            moves += Move(from, to)
        }
    }

    private fun stepMoves(board: Array<Piece?>, sq: Int, side: Color, targets: IntArray, moves: MutableList<Move>) {
        for (t in targets) {
            if (board[t]?.color != side) moves += Move(sq, t)
        }
    }

    private fun slideMoves(board: Array<Piece?>, sq: Int, side: Color, directions: IntRange, moves: MutableList<Move>) {
        for (d in directions) {
            for (t in rays[d][sq]) {
                val p = board[t]
                if (p == null) {
                    moves += Move(sq, t)
                    continue
                }
                if (p.color != side) moves += Move(sq, t)
                break
            }
        }
    }

    /**
     * Castling needs the right, king and rook on their home squares, the squares between them
     * empty, and the king's start and transit squares not attacked. The destination square is
     * checked by the common legality filter.
     */
    private fun castlingMoves(position: Position, board: Array<Piece?>, moves: MutableList<Move>) {
        val side = position.sideToMove
        val kingFrom = kingHome(side)
        if (board[kingFrom] != pieceOf(side, PieceType.KING)) return
        for (right in position.castling) {
            if (right.color != side) continue
            val rookFrom = rookHome(right)
            if (board[rookFrom] != pieceOf(side, PieceType.ROOK)) continue
            val between = if (rookFrom > kingFrom) kingFrom + 1 until rookFrom else rookFrom + 1 until kingFrom
            if (between.any { board[it] != null }) continue
            val step = if (right.isKingSide) 1 else -1
            if ((0..1).any { isAttacked(board, kingFrom + it * step, side.opposite) }) continue
            moves += Move(kingFrom, kingFrom + 2 * step, castle = right)
        }
    }
}
