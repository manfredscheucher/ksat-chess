package org.bytefred.satchess.sat

import kotlinx.serialization.Serializable
import org.bytefred.satchess.chess.BoardPattern
import org.bytefred.satchess.chess.CastleRight
import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Sq
import org.bytefred.satchess.puzzle.Goal
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.UNKNOWN_SQUARES

/** How "exactly one move per ply" is encoded (both are kept for benchmarking, see doc/encoding.md). */
enum class MoveAmo {
    /** Python's factoring: one-hot from-row, from-col, to-row, to-col; move <=> AND of the four. */
    ROW_COL,
    /** One-hot source and target squares (each via row/col product), move => source, target. */
    SRC_TGT,
}

@Serializable
data class EncoderOptions(val moveAmo: MoveAmo = MoveAmo.SRC_TGT)

/**
 * The CNF of one puzzle for one side to move at ply 0, plus the variable maps needed to decode
 * models. Encoding plan: doc/spec.md section 5, details in doc/encoding.md. The position at ply 0
 * must be reachable from the initial array as far as [History] can tell.
 *
 * Plies t = 0..[plies]; ply t's move leads from board t to board t + 1 and is made by [side] (t).
 */
class Encoding(
    val puzzle: Puzzle,
    val sideToMove: Color,
    val options: EncoderOptions = EncoderOptions(),
) {
    val cnf = Cnf()
    val plies: Int = puzzle.plies
    val patterns: Map<Int, BoardPattern>
    /**
     * The [History] of a fully given start board, as a CNF of its own: it shares nothing with the
     * search then, so [Search] checks it once instead of carrying it through every solve. With
     * unknown squares at ply 0 the history is part of [cnf] and this is null.
     */
    val historyCheck: Cnf?

    fun side(t: Int): Color = if (t % 2 == 0) sideToMove else sideToMove.opposite

    // ---- variables ---------------------------------------------------------------------------

    private val atBase = IntArray(plies + 1) { cnf.newVars(64 * PIECES) }
    private val castlingBase = IntArray(plies + 1) { cnf.newVars(4) }
    private val epBase = IntArray(plies + 1) { cnf.newVars(8) }
    private val moveBase = IntArray(plies) { cnf.newVars(Geometry.pairs.size) }
    private val sourceBase = IntArray(plies) { cnf.newVars(64) }
    private val targetBase = IntArray(plies) { cnf.newVars(64) }
    private val promoBase = IntArray(plies) { cnf.newVars(PROMOTIONS.size) }
    /** Castling move variables; only the mover's two rights exist (others are 0). */
    private val castleMoves = Array(plies) { t -> IntArray(4) { r -> if (CastleRight.entries[r].color == side(t)) cnf.newVar() else 0 } }
    private val epCaptures = IntArray(plies)
    private val layers = arrayOfNulls<AttackLayer>(plies + 1)
    private val inCheckVars = IntArray(plies + 1)

    /** Board t has [piece] (0 = empty, 1..12 = [Piece] ordinal + 1) on [sq]. */
    fun at(t: Int, sq: Int, piece: Int): Int = atBase[t] + sq * PIECES + piece
    fun at(t: Int, sq: Int, piece: Piece): Int = at(t, sq, pieceIndex(piece))
    fun empty(t: Int, sq: Int): Int = at(t, sq, EMPTY)
    /** Castling [right] still exists at ply t (state, also at t = 0 and t = plies). */
    fun castling(t: Int, right: CastleRight): Int = castlingBase[t] + right.ordinal
    /** At ply t the side to move may capture en passant on [file] (the previous ply was a double push). */
    fun enPassant(t: Int, file: Int): Int = epBase[t] + file
    fun move(t: Int, pair: Int): Int = moveBase[t] + pair
    fun source(t: Int, sq: Int): Int = sourceBase[t] + sq
    fun target(t: Int, sq: Int): Int = targetBase[t] + sq
    /** Ply t promotes to [PROMOTIONS] (index). */
    fun promotion(t: Int, index: Int): Int = promoBase[t] + index
    /** Ply t is castling with [right], or 0 if [right] belongs to the side not moving at t. */
    fun castleMove(t: Int, right: CastleRight): Int = castleMoves[t][right.ordinal]
    /** Ply t is an en passant capture. */
    fun epCapture(t: Int): Int = epCaptures[t]

    internal fun layer(t: Int): AttackLayer =
        layers[t] ?: AttackLayer(cnf, { sq, p -> at(t, sq, p) }).also { layers[t] = it }

    fun attacked(t: Int, sq: Int, by: Color): Int = layer(t).attacked(sq, by)

    /** The side to move at ply t is in check (exact equivalence). */
    fun inCheck(t: Int): Int {
        if (inCheckVars[t] == 0) {
            val king = pieceIndex(side(t), PieceType.KING)
            val checks = IntArray(64) { sq -> cnf.and(at(t, sq, king), attacked(t, sq, side(t).opposite)) }
            inCheckVars[t] = cnf.or(checks)
        }
        return inCheckVars[t]
    }

    init {
        require(plies >= 0) { "plies must be >= 0, got $plies" }
        patterns = boardPatterns()
        for (t in 0..plies) encodeBoard(t)
        encodeStart()
        for (t in 0 until plies) encodePly(t)
        for (t in 0..plies) encodeKingSafety(t)
        historyCheck = encodeHistory()
        encodeGoal()
        encodeMovePatterns(this)
    }

    // ---- puzzle constraints ------------------------------------------------------------------

    private fun ply(key: String, what: String): Int {
        val t = key.toIntOrNull() ?: throw IllegalArgumentException("$what: ply key '$key' is not a number")
        require(t in 0..plies) { "$what: ply $t is outside 0..$plies" }
        return t
    }

    private fun boardPatterns(): Map<Int, BoardPattern> {
        val result = HashMap<Int, BoardPattern>()
        result[0] = Fen.parsePattern(puzzle.start.board)
        for ((key, fen) in puzzle.positions) {
            val t = ply(key, "positions")
            val pattern = Fen.parsePattern(fen)
            val previous = result[t]
            result[t] = if (previous == null) pattern else merge(previous, pattern, t)
        }
        return result
    }

    private fun merge(a: BoardPattern, b: BoardPattern, t: Int) = BoardPattern(List(64) { sq ->
        val x = a.cells[sq]
        val y = b.cells[sq]
        require(x == '?' || y == '?' || x == y) { "positions: two different boards for ply $t on ${Sq.name(sq)}" }
        if (x == '?') y else x
    })

    private fun cellIndex(c: Char, what: String): Int =
        if (c == '.') EMPTY else pieceIndex(Piece.fromFen(c) ?: throw IllegalArgumentException("$what: invalid piece '$c'"))

    private fun encodeBoard(t: Int) {
        for (sq in 0 until 64) {
            val cell = IntArray(PIECES) { at(t, sq, it) }
            cnf.add(cell)
            cnf.atMostOnePairwise(cell)
            if (Sq.row(sq) == 0 || Sq.row(sq) == 7) {
                cnf.add(-at(t, sq, Piece.WP))
                cnf.add(-at(t, sq, Piece.BP))
            }
        }
        // implied by History at ply 0 and by king safety afterwards; stated per ply to help propagation
        for (king in listOf(Piece.WK, Piece.BK)) cnf.exactlyOneLadder(IntArray(64) { at(t, it, king) })
        patterns[t]?.let { pattern ->
            for (sq in 0 until 64) {
                val c = pattern.cells[sq]
                if (c != '?') cnf.add(at(t, sq, cellIndex(c, "board at ply $t")))
            }
        }
        for ((key, domains) in puzzle.squares) {
            if (ply(key, "squares") != t) continue
            val explicit = domains.keys.mapNotNullTo(HashSet()) { Sq.parse(it) }
            for ((name, allowed) in domains) {
                require(allowed.isNotEmpty()) { "squares: empty domain for $name at ply $t" }
                val squares = if (name == UNKNOWN_SQUARES) {
                    (0 until 64).filter { sq -> (patterns[t]?.cells?.get(sq) ?: '?') == '?' && sq !in explicit }
                } else {
                    listOf(Sq.parse(name) ?: throw IllegalArgumentException("squares: invalid square '$name'"))
                }
                val cells = allowed.map { cellIndex(it, "squares $name") }
                for (sq in squares) cnf.add(cells.map { at(t, sq, it) })
            }
        }
        for (right in CastleRight.entries) {
            val castle = Geometry.castles.getValue(right)
            cnf.add(-castling(t, right), at(t, castle.kingFrom, Piece.of(right.color, PieceType.KING)))
            cnf.add(-castling(t, right), at(t, castle.rookFrom, Piece.of(right.color, PieceType.ROOK)))
        }
    }

    /** Castling rights and en passant state at ply 0 (from [Puzzle.start]). */
    private fun encodeStart() {
        val rights = puzzle.start.castling
        for (right in CastleRight.entries) {
            val v = castling(0, right)
            val castle = Geometry.castles.getValue(right)
            when (rights) {
                "auto" -> cnf.add(
                    v,
                    -at(0, castle.kingFrom, Piece.of(right.color, PieceType.KING)),
                    -at(0, castle.rookFrom, Piece.of(right.color, PieceType.ROOK)),
                )
                "?" -> Unit
                else -> {
                    require(rights == "-" || rights.all { c -> CastleRight.entries.any { it.fen == c } }) {
                        "start.castling must be 'auto', '?', '-' or letters of KQkq, got '$rights'"
                    }
                    cnf.add(if (right.fen in rights) v else -v)
                }
            }
        }

        val ep = puzzle.start.enPassant
        if (ep == "-") {
            for (f in 0 until 8) cnf.add(-enPassant(0, f))
            return
        }
        val target = Sq.parse(ep) ?: throw IllegalArgumentException("start.enPassant must be '-' or a square, got '$ep'")
        val pusher = when (Sq.row(target)) {
            5 -> Color.WHITE // target on rank 3
            2 -> Color.BLACK // target on rank 6
            else -> throw IllegalArgumentException("start.enPassant '$ep' must be on rank 3 or 6")
        }
        val file = Sq.col(target)
        for (f in 0 until 8) cnf.add(if (f == file) enPassant(0, f) else -enPassant(0, f))
        if (sideToMove == pusher) cnf.add() // the pushing side cannot be to move: no solution
        val forward = Geometry.forward(pusher)
        cnf.add(at(0, target + 8 * forward, Piece.of(pusher, PieceType.PAWN)))
        cnf.add(empty(0, target))
        cnf.add(empty(0, target - 8 * forward))
    }

    // ---- moves -------------------------------------------------------------------------------

    private fun encodePly(t: Int) {
        val c = side(t)
        encodeMoveChoice(t)
        val pawnPromotions = ArrayList<Int>()
        for (pair in Geometry.pairs) {
            val m = move(t, pair.index)
            val caps = capabilities[c.ordinal][pair.index]
            cnf.add(listOf(-m) + caps.map { at(t, pair.from, it.piece) }.distinct())
            for (s in pair.between) cnf.add(-m, empty(t, s))
            cnf.add(-m, empty(t + 1, pair.from))
            for (cap in caps) {
                val p = at(t, pair.from, cap.piece)
                when (cap.kind) {
                    Kind.NORMAL -> cnf.add(-m, -p, at(t + 1, pair.to, cap.piece))
                    Kind.CASTLE -> {
                        cnf.add(-m, -p, castleMove(t, castleRight(c, pair.to)))
                        cnf.add(-m, -p, at(t + 1, pair.to, cap.piece))
                    }
                    Kind.PAWN_PUSH, Kind.PAWN_CAPTURE -> {
                        if (cap.kind == Kind.PAWN_PUSH) {
                            cnf.add(-m, -p, empty(t, pair.to))
                        } else if (Sq.row(pair.from) == Geometry.epCaptureRow(c)) {
                            cnf.add(-m, -p, -empty(t, pair.to), enPassant(t, Sq.col(pair.to)))
                        } else {
                            cnf.add(-m, -p, -empty(t, pair.to))
                        }
                        if (Sq.row(pair.to) == Geometry.promotionRow(c)) {
                            val promoting = cnf.and(m, p)
                            pawnPromotions += promoting
                            for ((i, type) in PROMOTIONS.withIndex()) {
                                cnf.add(-promoting, -promotion(t, i), at(t + 1, pair.to, Piece.of(c, type)))
                            }
                        } else {
                            cnf.add(-m, -p, at(t + 1, pair.to, cap.piece))
                        }
                    }
                }
            }
        }
        encodePromotionChoice(t, pawnPromotions)

        val own = PieceType.entries.map { pieceIndex(c, it) }
        for (sq in 0 until 64) for (p in own) cnf.add(-target(t, sq), -at(t, sq, p))

        val special = Array(64) { ArrayList<Int>() }
        encodeEnPassant(t, special)
        encodeCastling(t, special)
        for (sq in 0 until 64) {
            val keep = intArrayOf(source(t, sq), target(t, sq)) + special[sq].toIntArray()
            for (p in 0 until PIECES) cnf.add(keep + intArrayOf(-at(t, sq, p), at(t + 1, sq, p)))
        }
    }

    /** Source / target squares as exact ORs of the move variables, plus exactly one move. */
    private fun encodeMoveChoice(t: Int) {
        val from = Array(64) { ArrayList<Int>() }
        val to = Array(64) { ArrayList<Int>() }
        for (pair in Geometry.pairs) {
            from[pair.from] += move(t, pair.index)
            to[pair.to] += move(t, pair.index)
        }
        for (sq in 0 until 64) {
            cnf.defineOr(source(t, sq), from[sq].toIntArray())
            cnf.defineOr(target(t, sq), to[sq].toIntArray())
        }
        when (options.moveAmo) {
            MoveAmo.ROW_COL -> {
                val factors = Array(4) { cnf.newVars(8) }
                for (f in factors) {
                    val group = IntArray(8) { f + it }
                    cnf.add(group)
                    cnf.atMostOnePairwise(group)
                }
                for (pair in Geometry.pairs) {
                    val m = move(t, pair.index)
                    val parts = intArrayOf(
                        factors[0] + Sq.row(pair.from), factors[1] + Sq.col(pair.from),
                        factors[2] + Sq.row(pair.to), factors[3] + Sq.col(pair.to),
                    )
                    cnf.defineAnd(m, parts)
                }
                cnf.add(Geometry.pairs.map { move(t, it.index) })
            }
            MoveAmo.SRC_TGT -> {
                for (squares in listOf(IntArray(64) { source(t, it) }, IntArray(64) { target(t, it) })) {
                    val rows = cnf.newVars(8)
                    val cols = cnf.newVars(8)
                    for (sq in 0 until 64) {
                        cnf.add(-squares[sq], rows + Sq.row(sq))
                        cnf.add(-squares[sq], cols + Sq.col(sq))
                    }
                    cnf.atMostOnePairwise(IntArray(8) { rows + it })
                    cnf.atMostOnePairwise(IntArray(8) { cols + it })
                    cnf.add(squares)
                }
                for (pair in Geometry.pairs) {
                    cnf.add(-source(t, pair.from), -target(t, pair.to), move(t, pair.index))
                }
            }
        }
    }

    private fun encodePromotionChoice(t: Int, promotingMoves: List<Int>) {
        val isPromotion = cnf.or(promotingMoves.toIntArray())
        val choices = IntArray(PROMOTIONS.size) { promotion(t, it) }
        cnf.add(intArrayOf(-isPromotion) + choices)
        cnf.atMostOnePairwise(choices)
        for (v in choices) cnf.add(-v, isPromotion)
    }

    /** E.p. state at t + 1 (set by a double push at t) and e.p. captures at t. */
    private fun encodeEnPassant(t: Int, special: Array<ArrayList<Int>>) {
        val c = side(t)
        val forward = Geometry.forward(c)
        val pawn = Piece.of(c, PieceType.PAWN)
        for (f in 0 until 8) {
            val start = Sq.of(Geometry.pawnStartRow(c), f)
            val push = move(t, Geometry.pairIndex(start, start + 16 * forward))
            cnf.defineAnd(enPassant(t + 1, f), intArrayOf(push, at(t, start, pawn)))
        }
        val captures = ArrayList<Int>()
        val row = Geometry.epCaptureRow(c)
        for (f in 0 until 8) for (dc in intArrayOf(-1, 1)) {
            if (f + dc !in 0..7) continue
            val from = Sq.of(row, f)
            val to = Sq.of(row + forward, f + dc)
            val capture = cnf.and(move(t, Geometry.pairIndex(from, to)), at(t, from, pawn), enPassant(t, f + dc))
            val victim = Sq.of(row, f + dc)
            cnf.add(-capture, empty(t + 1, victim))
            special[victim] += capture
            captures += capture
        }
        epCaptures[t] = cnf.or(captures.toIntArray())
    }

    /** Castling rights at t + 1 and the castling moves of the side to move at t. */
    private fun encodeCastling(t: Int, special: Array<ArrayList<Int>>) {
        val c = side(t)
        for (right in CastleRight.entries) {
            val castle = Geometry.castles.getValue(right)
            val lost = intArrayOf(source(t, castle.kingFrom), source(t, castle.rookFrom), target(t, castle.rookFrom))
            val next = castling(t + 1, right)
            cnf.add(-next, castling(t, right))
            for (x in lost) cnf.add(-next, -x)
            cnf.add(intArrayOf(next, -castling(t, right)) + lost)
            if (right.color != c) continue

            val v = castleMove(t, right)
            cnf.defineAnd(v, intArrayOf(move(t, Geometry.pairIndex(castle.kingFrom, castle.kingTo)), castling(t, right)))
            for (sq in castle.empty) cnf.add(-v, empty(t, sq))
            for (sq in castle.kingPath) cnf.add(-v, -attacked(t, sq, c.opposite))
            cnf.add(-v, at(t + 1, castle.rookTo, Piece.of(c, PieceType.ROOK)))
            cnf.add(-v, empty(t + 1, castle.rookFrom))
            special[castle.rookFrom] += v
            special[castle.rookTo] += v
        }
    }

    private fun castleRight(c: Color, kingTo: Int): CastleRight =
        CastleRight.entries.first { it.color == c && Geometry.castles.getValue(it).kingTo == kingTo }

    // ---- rules -------------------------------------------------------------------------------

    private fun encodeHistory(): Cnf? {
        val start = patterns.getValue(0)
        if (!start.isFixed) {
            History(cnf) { sq, piece -> at(0, sq, piece) }
            return null
        }
        val h = Cnf()
        val base = h.newVars(64 * PIECES)
        for (sq in 0 until 64) {
            val given = cellIndex(start.cells[sq], "board at ply 0")
            for (p in 0 until PIECES) h.add(if (p == given) base + sq * PIECES + p else -(base + sq * PIECES + p))
        }
        History(h) { sq, piece -> base + sq * PIECES + pieceIndex(piece) }
        return h
    }

    /** The side NOT to move at ply t is not in check (it just moved, or it is the start position). */
    private fun encodeKingSafety(t: Int) {
        val mover = side(t)
        val king = pieceIndex(mover.opposite, PieceType.KING)
        for (sq in 0 until 64) cnf.add(-at(t, sq, king), -attacked(t, sq, mover))
    }

    /**
     * Goal at ply N. Checkmate and stalemate are only encoded as necessary conditions (check or
     * no check, and every king flight square is own-occupied or attacked with the king itself
     * removed); the solver driver confirms candidates with the move generator (refinement).
     */
    private fun encodeGoal() {
        val n = plies
        when (puzzle.goal) {
            Goal.NONE -> return
            Goal.CHECK -> {
                cnf.add(inCheck(n))
                return
            }
            Goal.CHECKMATE -> cnf.add(inCheck(n))
            Goal.STALEMATE -> cnf.add(-inCheck(n))
        }
        val c = side(n)
        val king = pieceIndex(c, PieceType.KING)
        val flights = AttackLayer(cnf, { sq, p -> at(n, sq, p) }, transparent = king)
        val own = PieceType.entries.map { pieceIndex(c, it) }
        for (sq in 0 until 64) for (flight in Geometry.neighbours(sq, Geometry.KING_STEPS)) {
            cnf.add(listOf(-at(n, sq, king)) + own.map { at(n, flight, it) } + flights.attacked(flight, c.opposite))
        }
    }

    /** True literals over the full state at ply N (board, castling, e.p.) of a model. */
    fun finalState(model: (Int) -> Boolean): IntArray {
        val vars = (0 until 64 * PIECES).map { atBase[plies] + it } +
            CastleRight.entries.map { castling(plies, it) } + (0 until 8).map { enPassant(plies, it) }
        return vars.map { if (model(it)) it else -it }.toIntArray()
    }

    companion object {
        /** Cell values per square: empty + 12 pieces. */
        const val PIECES = 13
        val PROMOTIONS = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        internal enum class Kind { NORMAL, CASTLE, PAWN_PUSH, PAWN_CAPTURE }
        internal class Capability(val piece: Int, val kind: Kind)

        /** Pieces of each colour that can make each pair's move shape, and how. */
        internal val capabilities: List<List<List<Capability>>> = Color.entries.map { c ->
            Geometry.pairs.map { pair -> capabilitiesOf(pair, c) }
        }

        private fun capabilitiesOf(pair: Geometry.MovePair, c: Color): List<Capability> {
            fun of(type: PieceType, kind: Kind = Kind.NORMAL) = Capability(pieceIndex(c, type), kind)
            if (pair.isKnight) return listOf(of(PieceType.KNIGHT))
            val result = ArrayList<Capability>()
            result += of(PieceType.QUEEN)
            result += of(if (pair.isOrthogonal) PieceType.ROOK else PieceType.BISHOP)
            if (pair.distance == 1) result += of(PieceType.KING)
            if (Geometry.castles.values.any { it.right.color == c && it.kingFrom == pair.from && it.kingTo == pair.to }) {
                result += of(PieceType.KING, Kind.CASTLE)
            }
            val forward = Geometry.forward(c)
            val fromRow = Sq.row(pair.from)
            if (fromRow in 1..6 && pair.dRow == forward * pair.distance) {
                val push = pair.dCol == 0 &&
                    (pair.distance == 1 || (pair.distance == 2 && fromRow == Geometry.pawnStartRow(c)))
                if (push) result += of(PieceType.PAWN, Kind.PAWN_PUSH)
                if (pair.distance == 1 && pair.dCol != 0) result += of(PieceType.PAWN, Kind.PAWN_CAPTURE)
            }
            return result
        }
    }
}
