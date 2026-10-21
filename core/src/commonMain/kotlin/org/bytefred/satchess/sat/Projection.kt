package org.bytefred.satchess.sat

import org.bytefred.satchess.chess.Fen
import org.bytefred.satchess.chess.Piece
import org.bytefred.satchess.chess.Sq

/**
 * What makes two solutions distinct (doc/spec.md section 4, `project`). Enumeration blocks a
 * found solution over these variables only, so every projected value is reported once.
 *
 * Variables come in two kinds: one-hot groups (exactly one is true in every model, e.g. the move
 * of a ply or the content of a square), blocked by negating the true member only, and free
 * variables, blocked with their full polarity.
 */
class Projection(private val e: Encoding) {
    private sealed interface Item {
        data object Moves : Item
        data class Board(val t: Int) : Item
        data class Square(val t: Int, val sq: Int) : Item
        data class PieceAt(val t: Int, val piece: Piece) : Item
        data object SideToMove : Item
    }

    private val items: List<Item> = e.puzzle.project.ifEmpty { listOf("moves", "board@0") }.map(::parse)
    private val oneHot = ArrayList<IntArray>()
    private val free = ArrayList<Int>()
    private val names = HashMap<Int, String>()

    /** True if the side to move is part of the projection (then the two passes of "?" never collide). */
    val includesSide: Boolean = Item.SideToMove in items

    init {
        for (item in items) when (item) {
            Item.Moves -> for (t in 0 until e.plies) {
                oneHot += IntArray(Geometry.pairs.size) { name(e.move(t, it), "mv$t:$it") }
                for (i in Encoding.PROMOTIONS.indices) free += name(e.promotion(t, i), "pr$t:$i")
            }
            is Item.Board -> for (sq in 0 until 64) oneHot += squareGroup(item.t, sq)
            is Item.Square -> oneHot += squareGroup(item.t, item.sq)
            is Item.PieceAt -> for (sq in 0 until 64) {
                free += name(e.at(item.t, sq, item.piece), "at${item.t}:$sq:${pieceIndex(item.piece)}")
            }
            Item.SideToMove -> Unit
        }
    }

    private fun parse(text: String): Item {
        fun ply(s: String): Int {
            val t = s.toIntOrNull() ?: throw IllegalArgumentException("project '$text': invalid ply '$s'")
            require(t in 0..e.plies) { "project '$text': ply $t is outside 0..${e.plies}" }
            return t
        }
        val (head, arg) = text.split('@', limit = 2).let { it[0] to it.getOrNull(1) }
        return when {
            head == "moves" && arg == null -> Item.Moves
            head == "board" && arg != null -> Item.Board(ply(arg))
            head == "sideToMove" && arg == "0" -> Item.SideToMove
            (head == "square" || head == "piece") && arg != null && ':' in arg -> {
                val (t, what) = arg.split(':', limit = 2)
                if (head == "square") {
                    Item.Square(ply(t), Sq.parse(what) ?: throw IllegalArgumentException("project '$text': invalid square"))
                } else {
                    val piece = what.singleOrNull()?.let { Piece.fromFen(it) }
                        ?: throw IllegalArgumentException("project '$text': invalid piece")
                    Item.PieceAt(ply(t), piece)
                }
            }
            else -> throw IllegalArgumentException(
                "project '$text': expected moves, board@t, square@t:h4, piece@t:K or sideToMove@0",
            )
        }
    }

    private fun name(v: Int, name: String): Int = v.also { names[it] = name }

    private fun squareGroup(t: Int, sq: Int) =
        IntArray(Encoding.PIECES) { name(e.at(t, sq, it), "at$t:$sq:$it") }

    /** Variables in the projection (for DIMACS `c ind` lines). */
    val variables: List<Int> = (oneHot.flatMap { it.toList() } + free).distinct().sorted()

    /** Clause excluding every model that agrees with [model] on the projection. */
    fun block(model: (Int) -> Boolean): IntArray = blockBy(model)

    /** Names of the projected variables that are true in [model] (encoding independent). */
    fun trueNames(model: (Int) -> Boolean): Set<String> =
        variables.filter(model).mapTo(HashSet()) { names.getValue(it) }

    /** Blocking clause for a solution found with another encoding, given by [trueNames]. */
    fun blockNames(trueNames: Set<String>): IntArray = blockBy { v -> names.getValue(v) in trueNames }

    private fun blockBy(value: (Int) -> Boolean): IntArray {
        val clause = ArrayList<Int>()
        for (group in oneHot) clause += -(group.firstOrNull { value(it) } ?: continue)
        for (v in free) clause += if (value(v)) -v else v
        return clause.distinct().toIntArray()
    }

    /** Human-readable value of the projection for one decoded solution. */
    fun describe(fens: List<String>, sanLine: String): String = items.joinToString(" | ") { item ->
        when (item) {
            Item.Moves -> sanLine.ifEmpty { "(no moves)" }
            is Item.Board -> if (items.size == 1) placement(fens, item.t) else "board@${item.t}: ${placement(fens, item.t)}"
            is Item.Square -> {
                val cell = Fen.parsePattern(placement(fens, item.t)).cells[item.sq]
                "${Sq.name(item.sq)} = ${if (cell == '.') "empty" else cell.toString()}"
            }
            is Item.PieceAt -> {
                val cells = Fen.parsePattern(placement(fens, item.t)).cells
                val squares = (0 until 64).filter { cells[it] == item.piece.fen }.map { Sq.name(it) }
                "${item.piece.fen} on ${squares.ifEmpty { listOf("-") }.joinToString(",")}"
            }
            Item.SideToMove -> "${fens[0].split(' ')[1]} to move"
        }
    }

    private fun placement(fens: List<String>, t: Int) = fens[t].substringBefore(' ')
}
