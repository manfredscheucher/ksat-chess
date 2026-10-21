package org.bytefred.satchess.sat

import org.bytefred.satchess.puzzle.PuzzleJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled puzzle files solve to their "expected" counts. */
class PuzzleFilesTest {
    private val root = File("../puzzles")

    private fun check(dir: String) {
        val files = File(root, dir).walkTopDown().filter { it.extension == "json" }.sortedBy { it.name }.toList()
        assertTrue(files.isNotEmpty(), "no puzzles in $dir")
        for (file in files) {
            val puzzle = PuzzleJson.decode(file.readText())
            val expected = puzzle.expected ?: continue
            val result = solveAll(puzzle, max = expected + 1)
            assertEquals(expected, result.count, "${file.path}: ${result.keys}")
        }
    }

    @Test fun demos() = check("demos")

    @Test
    fun chessCom() {
        if (slowTests) check("chess-com-5")
    }
}
