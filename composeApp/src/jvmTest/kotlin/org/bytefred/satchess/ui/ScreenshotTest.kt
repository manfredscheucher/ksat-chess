package org.bytefred.satchess.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.bytefred.satchess.sat.SolveEvent
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import org.bytefred.satchess.ui.platform.savePref
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the main screen at desktop (1280 x 800 dp) and phone (400 x 850 dp) size to
 * build/screenshots/ for visual review. Not a pixel comparison: it fails only if rendering crashes.
 */
class ScreenshotTest {
    @BeforeTest
    fun noWelcome() = savePref(WELCOME_PREF, "off")

    private val outDir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    private fun shoot(name: String, widthDp: Int, heightDp: Int, scale: Float, prepare: (PuzzleViewModel, FakeSolverService) -> Unit) {
        val solver = FakeSolverService()
        val vm = PuzzleViewModel(solver)
        ImageComposeScene(
            width = (widthDp * scale).toInt(),
            height = (heightDp * scale).toInt(),
            density = Density(scale),
        ) { App(vm) }.use { scene ->
            scene.render(0)
            prepare(vm, solver)
            var nanos = 0L
            repeat(5) { scene.render(nanos); nanos += 100_000_000 }
            val png = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
            outDir.mkdirs()
            File(outDir, "$name.png").writeBytes(png)
        }
        assertTrue(File(outDir, "$name.png").length() > 0)
    }

    private fun solved(vm: PuzzleViewModel, solver: FakeSolverService) {
        vm.solve()
        solver.emit(SolveEvent.Encoded(18342, 412907, 120))
        solver.emit(SolveEvent.Found(0, sampleSolution("f3", "e5", "g4", "Qh4#"), 300))
        solver.emit(SolveEvent.Found(1, sampleSolution("f4", "e5", "g4", "Qh4#"), 420))
        solver.emit(SolveEvent.Found(2, sampleSolution("g4", "e5", "f3", "Qh4#"), 430))
        solver.emit(SolveEvent.Done(3, complete = false, millis = 450))
        vm.setReplay(4)
    }

    @Test
    fun desktop() = shoot("desktop-1280x800", 1280, 800, 1f) { _, _ -> }

    @Test
    fun desktopWithResults() = shoot("desktop-results-1280x800", 1280, 800, 1f, ::solved)

    @Test
    fun desktopWithGroupedResults() = shoot("desktop-results-grouped-1280x800", 1280, 800, 1f) { vm, solver ->
        solved(vm, solver)
        vm.groupSimilar = true
    }

    @Test
    fun phone() = shoot("phone-400x850", 400, 850, 2f) { _, _ -> }

    @Test
    fun phoneResults() = shoot("phone-results-400x850", 400, 850, 2f, ::solved)

    /** chess.com #2: fixed pieces, every other square "white king or empty" (squares "?" = "K."). */
    private fun whereIsTheKing(vm: PuzzleViewModel) {
        vm.load(loadExamples().first { it.puzzle.id == "chess-com-5/2" }.puzzle)
    }

    @Test
    fun desktopSquareOptions() = shoot("desktop-options-1280x800", 1280, 800, 1f) { vm, _ -> whereIsTheKing(vm) }

    @Test
    fun desktopSquareOptionsDialog() = shoot("desktop-options-dialog-1280x800", 1280, 800, 1f) { vm, _ ->
        whereIsTheKing(vm)
        vm.openOptions(OptionsTarget.Square(0))
    }

    @Test
    fun phoneSquareOptions() = shoot("phone-options-400x850", 400, 850, 2f) { vm, _ -> whereIsTheKing(vm) }
}

