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
        files.forEach(::checkFile)
    }

    private fun checkFile(file: File) {
        val puzzle = PuzzleJson.decode(file.readText())
        val expected = puzzle.expected ?: return
        val result = solveAll(puzzle, max = expected + 1)
        assertEquals(expected, result.count, "${file.path}: ${result.keys}")
    }

    @Test fun demos() = check("demos")

    /** Where is the white king? Unique (c3) only with a 3-ply history incl. an e.p. capture. */
    @Test fun whiteKingNeedsEnPassantHistory() = checkFile(File(root, "chess-com-5/2.json"))

    @Test
    fun chessCom() {
        if (slowTests) check("chess-com-5")
    }

    @Test
    fun jankoShorties() {
        if (slowTests) check("janko-shorties")
    }
}
