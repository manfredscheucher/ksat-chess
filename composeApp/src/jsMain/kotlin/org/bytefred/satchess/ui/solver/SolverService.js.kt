package org.bytefred.satchess.ui.solver

import kotlinx.serialization.json.buildJsonObject
import org.bytefred.satchess.puzzle.Puzzle
import org.bytefred.satchess.puzzle.PuzzleJson
import org.bytefred.satchess.sat.SolveEvent
import org.bytefred.satchess.sat.SolveOptions
import org.w3c.dom.Worker

/**
 * Web: the solver runs in a Web Worker (solver-worker.js, built by the :worker module and copied next
 * to index.html). The worker is spawned lazily and reused while idle. Cancel = terminate it; the next
 * solve spawns a fresh one.
 *
 * Protocol (mirrors worker/src/jsMain/.../SolverWorker.kt): the request is the JSON string
 * {"puzzle": ..., "options": ...}; every reply is one SolveEvent as a JSON string.
 */
actual fun createSolverService(): SolverService = WorkerSolverService()

private class WorkerSolverService : SolverService {
    private var worker: Worker? = null
    private var busy = false

    override fun solve(puzzle: Puzzle, options: SolveOptions, onEvent: (SolveEvent) -> Unit) {
        if (busy) cancel()
        val w = worker ?: Worker(WORKER_SCRIPT).also { worker = it }
        busy = true
        w.onmessage = { message ->
            val event = try {
                PuzzleJson.json.decodeFromString(SolveEvent.serializer(), message.data as String)
            } catch (e: Exception) {
                SolveEvent.Failed("Unreadable message from the solver worker: ${e.message}")
            }
            if (event is SolveEvent.Done || event is SolveEvent.Failed) busy = false
            onEvent(event)
        }
        w.onerror = { error ->
            val detail = error.asDynamic().message as? String
            cancel()
            onEvent(SolveEvent.Failed("The solver worker failed${detail?.let { ": $it" } ?: ""}"))
        }
        val request = buildJsonObject {
            put("puzzle", PuzzleJson.json.encodeToJsonElement(Puzzle.serializer(), puzzle))
            put("options", PuzzleJson.json.encodeToJsonElement(SolveOptions.serializer(), options))
        }
        w.postMessage(request.toString())
    }

    override fun cancel() {
        worker?.terminate()
        worker = null
        busy = false
    }

    private companion object {
        const val WORKER_SCRIPT = "solver-worker.js"
    }
}
