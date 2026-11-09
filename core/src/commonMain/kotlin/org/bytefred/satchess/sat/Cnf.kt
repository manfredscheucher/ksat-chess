package org.bytefred.satchess.sat

/**
 * A growable CNF: variables are allocated with [newVar] (1-based DIMACS numbering), clauses are
 * stored flat in one IntArray with an offset table, so even millions of clauses stay compact.
 */
class Cnf {
    var numVars: Int = 0
        private set

    private var lits = IntArray(1 shl 16)
    private var litCount = 0
    private var starts = IntArray(1 shl 14)
    private var clauseCount = 0

    val numClauses: Int get() = clauseCount

    fun newVar(): Int = ++numVars

    /** Allocates [n] consecutive variables and returns the first one. */
    fun newVars(n: Int): Int {
        val first = numVars + 1
        numVars += n
        return first
    }

    fun add(vararg clause: Int) = add(clause, clause.size)

    fun add(clause: List<Int>) = add(clause.toIntArray())

    /** Adds the first [size] literals of [clause]. */
    fun add(clause: IntArray, size: Int = clause.size) {
        if (clauseCount + 1 >= starts.size) starts = starts.copyOf(starts.size * 2)
        while (litCount + size > lits.size) lits = lits.copyOf(lits.size * 2)
        starts[clauseCount] = litCount
        clause.copyInto(lits, litCount, 0, size)
        litCount += size
        clauseCount++
        starts[clauseCount] = litCount
    }

    fun clause(i: Int): IntArray = lits.copyOfRange(starts[i], starts[i + 1])

    inline fun forEachClause(action: (IntArray) -> Unit) {
        for (i in 0 until numClauses) action(clause(i))
    }

    /** Writes the CNF in DIMACS format; [comments] become leading `c` lines. */
    fun writeDimacs(out: Appendable, comments: List<String> = emptyList()) {
        for (c in comments) out.append("c ").append(c).append('\n')
        out.append("p cnf ").append(numVars.toString()).append(' ').append(clauseCount.toString()).append('\n')
        for (i in 0 until clauseCount) {
            for (j in starts[i] until starts[i + 1]) out.append(lits[j].toString()).append(' ')
            out.append("0\n")
        }
    }

    // ---- small encodings -------------------------------------------------------------------

    /** v <=> AND(xs). */
    fun defineAnd(v: Int, xs: IntArray) {
        for (x in xs) add(-v, x)
        add(IntArray(xs.size + 1) { if (it == 0) v else -xs[it - 1] })
    }

    /** v <=> OR(xs); an empty OR makes v false. */
    fun defineOr(v: Int, xs: IntArray) {
        for (x in xs) add(-x, v)
        add(IntArray(xs.size + 1) { if (it == 0) -v else xs[it - 1] })
    }

    /** Fresh variable equal to AND(xs). */
    fun and(vararg xs: Int): Int = newVar().also { defineAnd(it, xs) }

    /** Fresh variable equal to OR(xs). */
    fun or(xs: IntArray): Int = newVar().also { defineOr(it, xs) }

    fun atMostOnePairwise(xs: IntArray) {
        for (i in xs.indices) for (j in i + 1 until xs.size) add(-xs[i], -xs[j])
    }

    /** At most one of [xs] with a sequential ladder (about 3n clauses, n - 2 auxiliaries). */
    fun atMostOneLadder(xs: IntArray) {
        if (xs.size <= 4) { atMostOnePairwise(xs); return }
        var prev = xs[0] // prev <=> some earlier x is true (one direction is enough for at-most-one)
        for (i in 1 until xs.size) {
            val x = xs[i]
            add(-x, -prev)
            if (i == xs.size - 1) break
            val s = newVar()
            add(-prev, s)
            add(-x, s)
            prev = s
        }
    }

    /**
     * Exactly one of [xs] with a sequential ladder: s_i <=> s_(i-1) OR x_i (so the auxiliary
     * variables are functionally determined), x_i => NOT s_(i-1), and s_n must hold.
     */
    fun exactlyOneLadder(xs: IntArray) {
        if (xs.isEmpty()) { add(); return }
        var prev = 0
        for ((i, x) in xs.withIndex()) {
            if (i == 0) { prev = x; continue }
            add(-x, -prev)
            val s = newVar()
            defineOr(s, intArrayOf(prev, x))
            prev = s
        }
        add(prev)
    }

    /**
     * Unary counter over [xs]: returns r with r[k-1] <=> "at least k of xs are true", for
     * k = 1..[max] (a sequential counter whose outputs are exact, not just one-directional).
     */
    fun unaryCount(xs: IntArray, max: Int): IntArray {
        var row = IntArray(0) // row[k-1] = at least k among the inputs seen so far
        for (x in xs) {
            val width = minOf(row.size + 1, max)
            val next = IntArray(width)
            for (k in 0 until width) {
                val keep = if (k < row.size) row[k] else 0
                val step = if (k == 0) x else and(x, row[k - 1])
                next[k] = if (keep == 0) step else or(intArrayOf(keep, step))
            }
            row = next
        }
        return row
    }
}
