package org.bytefred.satchess.worker

import kotlinx.serialization.json.jsonObject
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.sat.PuzzleSolver
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import org.w3c.dom.DedicatedWorkerGlobalScope

/** The worker's global scope (`self` inside a dedicated Web Worker). */
external val self: DedicatedWorkerGlobalScope

/**
 * Web Worker entry point. Receives one JSON string {"puzzle": ..., "options": ...}, runs the
 * synchronous solver and posts every [SolveEvent] back as a JSON string (protocol mirrored in
 * composeApp's SolverService.js.kt). Cancel is done by the page terminating this worker.
 */
fun main() {
    self.onmessage = { message ->
        val json = PuzzleJson.json
        fun post(event: SolveEvent) = self.postMessage(json.encodeToString(SolveEvent.serializer(), event))
        try {
            val request = json.parseToJsonElement(message.data as String).jsonObject
            val puzzle = json.decodeFromJsonElement(Puzzle.serializer(), request.getValue("puzzle"))
            val options = json.decodeFromJsonElement(SolveOptions.serializer(), request.getValue("options"))
            PuzzleSolver.solve(puzzle, options, onEvent = ::post)
        } catch (e: Exception) {
            post(SolveEvent.Failed(e.message ?: e.toString()))
        }
    }
}
