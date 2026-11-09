package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Color
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.PieceType
import org.bytefred.satchess.chess.Sq
import kotlin.math.abs

/**
 * Retro accounting for the position at ply 0: it must have arisen from the initial array.
 *
 * Every man on the board is one of the 16 original men of its colour: a pawn, an original piece,
 * or a pawn that promoted. Every original man that is not on the board died somewhere. Each pawn
 * has a path from its home square (one rank per step, captures diagonal), which fixes where it
 * went, what it captured and on which squares. The rules, each sound for every legal game
 * (doc/encoding.md section 11):
 *
 * 1. Paths: a pawn visits a square at most once, leaves each visited square by exactly one step or
 *    ends there (alive, captured, or promoted on the last rank).
 * 2. Identity: each board square holds exactly one unit; a bishop (original or promoted) stays on
 *    the colour of its home / promotion square; the king never dies.
 * 3. Captures: each diagonal pawn step kills exactly one enemy unit that died on that square (en
 *    passant: a pawn that had just double-stepped to the square behind); a unit dies once.
 * 4. Home squares: a pawn still standing on its home square has never moved, so nothing else was
 *    ever on that square: no pawn visited it, no unit died there.
 * 5. Boxed bishops: a bishop whose two neighbouring pawns still stand at home never moved, so it is
 *    at home or died at home.
 * 6. Crossing: two pawns on the same file cannot pass each other. If a pawn never left its file and
 *    still stands there, an enemy pawn whose straight run down that file reaches the first two
 *    ranks of that side was below it the whole run, so the run started below the pawn's final rank.
 */
internal class History(private val cnf: Cnf, private val at: (sq: Int, piece: Piece) -> Int) {

    private class Step(val from: Int, val to: Int, val v: Int) {
        val diagonal: Boolean get() = Sq.col(from) != Sq.col(to)
    }

    private inner class Side(val color: Color) {
        val forward = Geometry.forward(color)
        val startRow = Geometry.pawnStartRow(color)
        val promoRow = Geometry.promotionRow(color)
        val backRow = startRow - forward
        /** Rows at distance >= 5 from the start row: the enemy's first two ranks. */
        fun isDeep(row: Int): Boolean = (row - startRow) * forward >= 5
        lateinit var pawns: List<Pawn>
        lateinit var originals: List<Original>
        lateinit var enemy: Side
    }

    private inner class Pawn(val side: Side, val file: Int) {
        val home = Sq.of(side.startRow, file)
        /** Visited [sq] (0 = unreachable); [end]: the path ends on sq. */
        val vis = IntArray(64)
        val end = IntArray(64)
        /** Ends on sq as a pawn that is still on the board / that was captured there (0 on the last rank). */
        val alivePawn = IntArray(64)
        val diedAt = IntArray(64)
        /** Entered sq diagonally (0 = no diagonal step leads there). */
        val capture = IntArray(64)
        val steps = ArrayList<Step>()
        val outOf = Array(64) { ArrayList<Step>() }
        val into = Array(64) { ArrayList<Step>() }
        val died = cnf.newVar()
        /** Promoted to [Encoding.PROMOTIONS] (index); the promoted piece is alive on / died on a square. */
        val promoType = IntArray(Encoding.PROMOTIONS.size) { cnf.newVar() }
        val alivePromo = IntArray(64) { cnf.newVar() }
        val deadPromoAt = IntArray(64) { cnf.newVar() }
        var promoted = 0
        /** Literal: never made a diagonal step. */
        var straight = 0

        private fun reachable(sq: Int): Boolean {
            val dist = (Sq.row(sq) - side.startRow) * side.forward
            return dist in 0..6 && abs(Sq.col(sq) - file) <= dist
        }

        init {
            for (sq in 0 until 64) {
                if (!reachable(sq)) continue
                vis[sq] = cnf.newVar()
                end[sq] = cnf.newVar()
                if (Sq.row(sq) != side.promoRow) {
                    alivePawn[sq] = cnf.newVar()
                    diedAt[sq] = cnf.newVar()
                }
            }
            for (sq in 0 until 64) {
                if (vis[sq] == 0 || Sq.row(sq) == side.promoRow) continue
                val targets = ArrayList<Int>()
                targets += sq + 8 * side.forward
                if (Sq.row(sq) == side.startRow) targets += sq + 16 * side.forward
                for (dc in intArrayOf(-1, 1)) if (Sq.col(sq) + dc in 0..7) targets += sq + 8 * side.forward + dc
                for (to in targets) {
                    val step = Step(sq, to, cnf.newVar())
                    steps += step
                    outOf[sq] += step
                    into[to] += step
                }
            }
        }
    }

    private inner class Original(val side: Side, val type: PieceType, val home: Int) {
        val alive = IntArray(64)
        val deadAt = IntArray(64)

        init {
            for (sq in 0 until 64) {
                if (type == PieceType.BISHOP && Sq.isLight(sq) != Sq.isLight(home)) continue
                alive[sq] = cnf.newVar()
                if (type != PieceType.KING) deadAt[sq] = cnf.newVar()
            }
        }
    }

    /** Victim variables per death literal (a unit dying on a square is captured by at most one pawn). */
    private val victimGroups = HashMap<Int, ArrayList<Int>>()
    private val reachMemo = HashMap<Long, Int>()

    init {
        val white = Side(Color.WHITE)
        val black = Side(Color.BLACK)
        white.enemy = black
        black.enemy = white
        for (side in listOf(white, black)) {
            side.pawns = List(8) { Pawn(side, it) }
            val homes = listOf(
                PieceType.KING to 4, PieceType.QUEEN to 3, PieceType.ROOK to 0, PieceType.ROOK to 7,
                PieceType.BISHOP to 2, PieceType.BISHOP to 5, PieceType.KNIGHT to 1, PieceType.KNIGHT to 6,
            )
            side.originals = homes.map { (type, col) -> Original(side, type, Sq.of(side.backRow, col)) }
        }
        for (side in listOf(white, black)) {
            side.pawns.forEach(::encodePawn)
            side.originals.forEach(::encodeOriginal)
            encodeBoard(side)
            encodeBoxedBishops(side)
        }
        for (side in listOf(white, black)) {
            encodeCaptures(side)
            encodeHomeSquares(side)
            encodeCrossing(side)
        }
        for (group in victimGroups.values) cnf.atMostOneLadder(group.toIntArray())
    }

    // ---- rule 1: paths, promotion -----------------------------------------------------------

    private fun encodePawn(p: Pawn) {
        cnf.add(p.vis[p.home])
        for (sq in 0 until 64) {
            val v = p.vis[sq]
            if (v == 0) continue
            val out = p.outOf[sq].map { it.v }
            cnf.add(intArrayOf(-v, p.end[sq]) + out)
            cnf.atMostOnePairwise((listOf(p.end[sq]) + out).toIntArray())
            cnf.add(-p.end[sq], v)
            for (step in p.outOf[sq]) {
                cnf.add(-step.v, v)
                cnf.add(-step.v, p.vis[step.to])
            }
            if (sq != p.home) {
                val ins = p.into[sq].map { it.v }
                cnf.add(intArrayOf(-v) + ins)
                cnf.atMostOnePairwise(ins.toIntArray())
            }
            val diagonal = p.into[sq].filter { it.diagonal }.map { it.v }
            if (diagonal.isNotEmpty()) p.capture[sq] = cnf.or(diagonal.toIntArray())
            if (p.alivePawn[sq] != 0) {
                cnf.defineAnd(p.alivePawn[sq], intArrayOf(p.end[sq], -p.died))
                cnf.defineAnd(p.diedAt[sq], intArrayOf(p.end[sq], p.died))
            } else {
                cnf.add(-p.end[sq], -p.died) // reaching the last rank is a promotion, not a death
            }
        }
        val promoSquares = (0 until 64).filter { p.end[it] != 0 && Sq.row(it) == p.side.promoRow }
        p.promoted = cnf.or(promoSquares.map { p.end[it] }.toIntArray())
        cnf.add(intArrayOf(-p.promoted) + p.promoType)
        cnf.atMostOnePairwise(p.promoType)
        for (t in p.promoType) cnf.add(-t, p.promoted)

        val fates = p.alivePromo + p.deadPromoAt
        cnf.add(intArrayOf(-p.promoted) + fates)
        for (f in fates) cnf.add(-f, p.promoted)
        cnf.atMostOneLadder(fates)

        val bishop = p.promoType[Encoding.PROMOTIONS.indexOf(PieceType.BISHOP)]
        for (light in listOf(true, false)) {
            val promotedOn = cnf.or(promoSquares.filter { Sq.isLight(it) == light }.map { p.end[it] }.toIntArray())
            for (sq in 0 until 64) {
                if (Sq.isLight(sq) == light) continue
                cnf.add(-bishop, -promotedOn, -p.alivePromo[sq])
                cnf.add(-bishop, -promotedOn, -p.deadPromoAt[sq])
            }
        }
        p.straight = -cnf.or(p.steps.filter { it.diagonal }.map { it.v }.toIntArray())
    }

    // ---- rule 2: identity -----------------------------------------------------------------

    private fun encodeOriginal(o: Original) {
        cnf.exactlyOneLadder((o.alive + o.deadAt).filter { it != 0 }.toIntArray())
    }

    private fun encodeBoard(side: Side) {
        val color = side.color
        for (sq in 0 until 64) {
            val pawns = side.pawns.filter { it.alivePawn[sq] != 0 }
            val pawnCell = at(sq, Piece.of(color, PieceType.PAWN))
            cnf.add(intArrayOf(-pawnCell) + pawns.map { it.alivePawn[sq] })
            for (p in pawns) cnf.add(-p.alivePawn[sq], pawnCell)

            for (type in PieceType.entries) {
                if (type == PieceType.PAWN) continue
                val cell = at(sq, Piece.of(color, type))
                val originals = side.originals.filter { it.type == type && it.alive[sq] != 0 }
                val promoted = if (type in Encoding.PROMOTIONS) side.pawns.map { it.alivePromo[sq] } else emptyList()
                cnf.add(intArrayOf(-cell) + originals.map { it.alive[sq] } + promoted)
                for (o in originals) cnf.add(-o.alive[sq], cell)
            }
            for (p in side.pawns) {
                val a = p.alivePromo[sq]
                cnf.add(intArrayOf(-a) + Encoding.PROMOTIONS.map { at(sq, Piece.of(color, it)) })
                for ((i, type) in Encoding.PROMOTIONS.withIndex()) {
                    val cell = at(sq, Piece.of(color, type))
                    cnf.add(-a, -p.promoType[i], cell)
                    cnf.add(-a, -cell, p.promoType[i])
                }
            }
            val units = pawns.map { it.alivePawn[sq] } +
                side.originals.mapNotNull { it.alive[sq].takeIf { v -> v != 0 } } +
                side.pawns.map { it.alivePromo[sq] }
            cnf.atMostOneLadder(units.toIntArray())
        }
    }

    // ---- rule 3: captures -----------------------------------------------------------------

    private fun encodeCaptures(side: Side) {
        val enemy = side.enemy
        val epRow = Geometry.epCaptureRow(side.color) + side.forward
        for (p in side.pawns) for (sq in 0 until 64) {
            val cap = p.capture[sq]
            if (cap == 0) continue
            val options = ArrayList<Int>()
            fun victim(death: Int, vararg also: Int) {
                val v = cnf.newVar()
                cnf.add(-v, cap)
                cnf.add(-v, death)
                for (x in also) cnf.add(-v, x)
                victimGroups.getOrPut(death) { ArrayList() } += v
                options += v
            }
            for (o in enemy.originals) if (o.deadAt[sq] != 0) victim(o.deadAt[sq])
            for (q in enemy.pawns) {
                if (q.diedAt[sq] != 0) victim(q.diedAt[sq])
                victim(q.deadPromoAt[sq])
            }
            if (Sq.row(sq) == epRow) {
                // en passant: the enemy pawn of this file had just double-stepped to the square behind
                val behind = sq - 8 * side.forward
                val q = enemy.pawns[Sq.col(sq)]
                val double = q.outOf[q.home].firstOrNull { it.to == behind }
                if (double != null && q.diedAt[behind] != 0) victim(q.diedAt[behind], double.v)
            }
            cnf.add(intArrayOf(-cap) + options)
        }
    }

    // ---- rule 4: home squares -------------------------------------------------------------

    private fun encodeHomeSquares(side: Side) {
        for (q in side.pawns) {
            val stays = q.alivePawn[q.home]
            for (p in side.enemy.pawns) if (p.vis[q.home] != 0) cnf.add(-stays, -p.vis[q.home])
            for (s in listOf(side, side.enemy)) {
                for (o in s.originals) if (o.deadAt[q.home] != 0) cnf.add(-stays, -o.deadAt[q.home])
                for (r in s.pawns) {
                    if (r !== q && r.diedAt[q.home] != 0) cnf.add(-stays, -r.diedAt[q.home])
                    cnf.add(-stays, -r.deadPromoAt[q.home])
                }
            }
        }
    }

    // ---- rule 5: boxed bishops ------------------------------------------------------------

    private fun encodeBoxedBishops(side: Side) {
        for (o in side.originals) {
            if (o.type != PieceType.BISHOP) continue
            val col = Sq.col(o.home)
            val left = side.pawns[col - 1]
            val right = side.pawns[col + 1]
            cnf.add(-left.alivePawn[left.home], -right.alivePawn[right.home], o.alive[o.home], o.deadAt[o.home])
        }
    }

    // ---- rule 6: crossing -----------------------------------------------------------------

    /**
     * Literal: [b] visited [sq] and its straight run down the file from there reaches the deep
     * zone (the enemy's first two ranks); 0 if that is impossible.
     */
    private fun reach(b: Pawn, sq: Int): Int {
        val key = (b.side.color.ordinal * 8 + b.file).toLong() * 64 + sq
        reachMemo[key]?.let { return it }
        val v = b.vis[sq]
        val result = when {
            v == 0 -> 0
            b.side.isDeep(Sq.row(sq)) -> v
            else -> {
                val continued = b.outOf[sq].filter { !it.diagonal }.mapNotNull { step ->
                    val next = reach(b, step.to)
                    if (next == 0) null else cnf.and(step.v, next)
                }
                if (continued.isEmpty()) 0 else cnf.and(v, cnf.or(continued.toIntArray()))
            }
        }
        reachMemo[key] = result
        return result
    }

    private fun encodeCrossing(side: Side) {
        for (w in side.pawns) for (sq in 0 until 64) {
            if (w.alivePawn[sq] == 0 || Sq.col(sq) != w.file) continue
            for (b in side.enemy.pawns) for (other in 0 until 64) {
                if (Sq.col(other) != w.file || (Sq.row(other) - Sq.row(sq)) * side.forward < 0) continue
                val r = reach(b, other)
                if (r != 0) cnf.add(-w.alivePawn[sq], -w.straight, -r)
            }
        }
    }
}
