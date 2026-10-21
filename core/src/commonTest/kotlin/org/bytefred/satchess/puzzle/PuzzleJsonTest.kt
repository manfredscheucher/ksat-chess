package org.bytefred.satchess.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PuzzleJsonTest {
    private val text = """
        {
          "id": "x",
          "plies": 8,
          "start": { "board": "startpos", "sideToMove": "?" },
          "moves": { "0": "f3", "5": { "piece": "p", "capture": true } },
          "goal": "checkmate",
          "project": ["square@0:h4"],
          "unknownField": 1
        }
    """.trimIndent()

    @Test
    fun movePatternsAsStringOrObject() {
        val puzzle = PuzzleJson.decode(text)
        assertEquals(MovePattern(san = "f3"), puzzle.moves["0"])
        assertEquals(MovePattern(piece = "p", capture = true), puzzle.moves["5"])
        assertEquals(Goal.CHECKMATE, puzzle.goal)
        assertEquals("?", puzzle.start.sideToMove)
    }

    @Test
    fun roundTrip() {
        val puzzle = PuzzleJson.decode(text)
        val written = PuzzleJson.encode(puzzle)
        assertTrue("\"0\": \"f3\"" in written, written)
        assertEquals(puzzle, PuzzleJson.decode(written))
    }

    @Test
    fun sanWithFieldsStaysAnObject() {
        val puzzle = Puzzle(moves = mapOf("1" to MovePattern(san = "Nf3", check = true)))
        assertEquals(puzzle, PuzzleJson.decode(PuzzleJson.encode(puzzle)))
    }

    @Test
    fun invalidMovePattern() {
        assertFailsWith<Exception> { PuzzleJson.decode("""{ "moves": { "0": 5 } }""") }
    }
}
