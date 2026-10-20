package org.bytefred.satchess.puzzle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** JSON (de)serialization of puzzles; tolerant on input, pretty on output. */
object PuzzleJson {
    val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
    fun decode(text: String): Puzzle = json.decodeFromString(Puzzle.serializer(), text)
    fun encode(puzzle: Puzzle): String = json.encodeToString(Puzzle.serializer(), puzzle)
}

/** Accepts a plain JSON string (SAN) or an object for [MovePattern]. */
object MovePatternSerializer : KSerializer<MovePattern> by TODO()
