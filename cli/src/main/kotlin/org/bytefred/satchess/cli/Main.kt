package org.bytefred.satchess.cli

import org.bytefred.ksat.facade.Solver
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.sat.EncoderOptions
import org.bytefred.satchess.sat.Encoding
import org.bytefred.satchess.sat.MoveAmo
import org.bytefred.satchess.sat.Projection
import org.bytefred.satchess.sat.Refinement
import org.bytefred.satchess.sat.PuzzleSolver
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """usage: sat-chess <command> [options]

commands:
  solve <puzzle.json>   solve one puzzle and print its solutions
  perft <plies>         SAT perft: count all move sequences of <plies> from the initial position
  run <dir>             solve every puzzle JSON under <dir> and compare with its "expected" count

options:
  --all                 enumerate all solutions (default for perft and run)
  --max N               stop after N solutions (default for solve: 1)
  --solver S            cadical (default), minisat, kissat, microsat
  --move-amo A          src-tgt (default) or row-col: exactly-one-move encoding (benchmarks)
  --refinement R       full-state (default) or escape-move: clause excluding a non-mate final position
  --dimacs out.cnf      solve: also write the CNF (one file per side if sideToMove is "?")
  --timeout SEC         run: stop a puzzle after SEC seconds (checked between solver calls)
  --verbose             print encoding size, timing and info events"""

private class Options(
    val positional: List<String>,
    val maxSolutions: Int?,
    val solver: Solver,
    val moveAmo: MoveAmo,
    val refinement: Refinement,
    val dimacs: String?,
    val timeoutSeconds: Long?,
    val verbose: Boolean,
) {
    fun solveOptions(defaultMax: Int) = SolveOptions(solver, maxSolutions ?: defaultMax, EncoderOptions(moveAmo), refinement)
}

fun main(args: Array<String>) {
    val options = try {
        parse(args.toList())
    } catch (e: IllegalArgumentException) {
        System.err.println("error: ${e.message}\n\n$USAGE")
        exitProcess(2)
    }
    val command = options.positional.firstOrNull()
    val argument = options.positional.getOrNull(1)
    val ok = when {
        command == "solve" && argument != null -> solve(File(argument), options)
        command == "perft" && argument?.toIntOrNull() != null -> perft(argument.toInt(), options)
        command == "run" && argument != null -> runAll(File(argument), options)
        else -> {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
    exitProcess(if (ok) 0 else 1)
}

private fun parse(args: List<String>): Options {
    val positional = ArrayList<String>()
    var max: Int? = null
    var solver = Solver.CADICAL
    var moveAmo = MoveAmo.SRC_TGT
    var refinement = Refinement.FULL_STATE
    var dimacs: String? = null
    var timeout: Long? = null
    var verbose = false
    val it = args.iterator()
    fun value(flag: String): String {
        require(it.hasNext()) { "$flag needs a value" }
        return it.next()
    }
    while (it.hasNext()) {
        when (val arg = it.next()) {
            "--all" -> max = 0
            "--max" -> max = value(arg).toIntOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("--max needs a number")
            "--solver" -> solver = value(arg).let { name ->
                Solver.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: throw IllegalArgumentException("unknown solver '$name'")
            }
            "--move-amo" -> moveAmo = when (value(arg)) {
                "src-tgt" -> MoveAmo.SRC_TGT
                "row-col" -> MoveAmo.ROW_COL
                else -> throw IllegalArgumentException("--move-amo must be src-tgt or row-col")
            }
            "--refinement" -> refinement = when (value(arg)) {
                "full-state" -> Refinement.FULL_STATE
                "escape-move" -> Refinement.ESCAPE_MOVE
                else -> throw IllegalArgumentException("--refinement must be full-state or escape-move")
            }
            "--dimacs" -> dimacs = value(arg)
            "--timeout" -> timeout = value(arg).toLongOrNull() ?: throw IllegalArgumentException("--timeout needs seconds")
            "--verbose" -> verbose = true
            else -> {
                require(!arg.startsWith("--")) { "unknown option $arg" }
                positional += arg
            }
        }
    }
    return Options(positional, max, solver, moveAmo, refinement, dimacs, timeout, verbose)
}

private fun load(file: File): Puzzle = PuzzleJson.decode(file.readText())

private fun solve(file: File, options: Options): Boolean {
    val puzzle = try {
        load(file)
    } catch (e: Exception) {
        System.err.println("error: cannot read ${file.path}: ${e.message}")
        return false
    }
    options.dimacs?.let { writeDimacs(puzzle, it, options) }
    println("${puzzle.id.ifEmpty { file.name }}: ${puzzle.title}  (${puzzle.plies} plies, goal ${puzzle.goal.name.lowercase()})")
    var failed = false
    PuzzleSolver.solve(puzzle, options.solveOptions(defaultMax = 1)) { event ->
        when (event) {
            is SolveEvent.Encoded -> if (options.verbose) {
                println("encoded: ${event.variables} variables, ${event.clauses} clauses (${event.millis} ms)")
            }
            is SolveEvent.Found -> {
                val badge = if (event.solution.verified) "" else "  [NOT VERIFIED]"
                println("#${event.index + 1}: ${event.solution.projectedKey}$badge")
                if (options.verbose) println("    ${event.solution.moves.joinToString(" ") { it.uci }}  (${event.millis} ms)")
            }
            is SolveEvent.Info -> if (options.verbose) println("info: ${event.message}")
            is SolveEvent.Done -> {
                val exact = if (event.complete) "" else " (stopped, more may exist)"
                println("found ${event.count}$exact in ${event.millis} ms" + expectedNote(puzzle, event))
                event.shortest?.let { println(if (it) "shortest: yes" else "shortest: NO, a shorter solution exists") }
            }
            is SolveEvent.Failed -> {
                System.err.println("error: ${event.message}")
                failed = true
            }
        }
    }
    return !failed
}

private fun expectedNote(puzzle: Puzzle, done: SolveEvent.Done): String {
    val expected = puzzle.expected ?: return ""
    return if (done.complete) ", expected $expected" + (if (done.count == expected) " OK" else " MISMATCH") else ""
}

private fun writeDimacs(puzzle: Puzzle, path: String, options: Options) {
    val sides = PuzzleSolver.sides(puzzle)
    for (side in sides) {
        val encoding = Encoding(puzzle, side, EncoderOptions(options.moveAmo))
        val file = if (sides.size == 1) File(path) else File(path.removeSuffix(".cnf") + ".${side.fen}.cnf")
        val comments = listOf(
            "sat-chess-kotlin: ${puzzle.id.ifEmpty { "puzzle" }}, ${puzzle.plies} plies, ${side.name.lowercase()} to move at ply 0",
            "projected variables (solutions are distinct over these): see the ind lines",
        ) + Projection(encoding).variables.chunked(20).map { "ind ${it.joinToString(" ")} 0" }
        file.bufferedWriter().use { encoding.cnf.writeDimacs(it, comments) }
        println("wrote ${file.path}: ${encoding.cnf.numVars} variables, ${encoding.cnf.numClauses} clauses")
    }
}

private fun perft(plies: Int, options: Options): Boolean {
    val puzzle = Puzzle(id = "perft", plies = plies)
    var ok = true
    PuzzleSolver.solve(puzzle, options.solveOptions(defaultMax = 0)) { event ->
        when (event) {
            is SolveEvent.Encoded -> println("encoded: ${event.variables} variables, ${event.clauses} clauses (${event.millis} ms)")
            is SolveEvent.Found -> if (!event.solution.verified) {
                println("NOT VERIFIED: ${event.solution.projectedKey}")
                ok = false
            }
            is SolveEvent.Done -> println("perft($plies) = ${event.count} in ${event.millis} ms")
            is SolveEvent.Failed -> {
                System.err.println("error: ${event.message}")
                ok = false
            }
            is SolveEvent.Info -> Unit
        }
    }
    return ok
}

private fun runAll(dir: File, options: Options): Boolean {
    val files = dir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.path }.toList()
    var mismatches = 0
    for (file in files) {
        val puzzle = try {
            load(file)
        } catch (e: Exception) {
            println("${file.path}: cannot read: ${e.message}")
            mismatches++
            continue
        }
        // Enumerate one past the expected count, so a too-high count is detected without enumerating everything.
        val max = options.maxSolutions ?: puzzle.expected?.plus(1) ?: 0
        val deadline = options.timeoutSeconds?.let { System.currentTimeMillis() + it * 1000 }
        var line = "?"
        var unverified = 0
        PuzzleSolver.solve(puzzle, options.solveOptions(max), {
            deadline != null && System.currentTimeMillis() > deadline
        }) { event ->
            when (event) {
                is SolveEvent.Found -> if (!event.solution.verified) unverified++
                is SolveEvent.Done -> {
                    val count = if (event.complete) "${event.count}" else ">=${event.count}"
                    val verdict = when {
                        unverified > 0 -> "UNVERIFIED($unverified)"
                        puzzle.expected == null -> "-"
                        event.complete && event.count == puzzle.expected -> "OK"
                        !event.complete && event.count <= puzzle.expected!! -> "INCOMPLETE"
                        else -> "MISMATCH"
                    }
                    if (verdict != "OK" && verdict != "-") mismatches++
                    line = "found $count, expected ${puzzle.expected ?: "?"}  $verdict  ${event.millis} ms"
                }
                is SolveEvent.Failed -> {
                    line = "FAILED: ${event.message}"
                    mismatches++
                }
                else -> Unit
            }
        }
        println("${file.relativeTo(dir).path}: $line")
    }
    println("${files.size} puzzles, ${mismatches} not OK")
    return mismatches == 0
}
