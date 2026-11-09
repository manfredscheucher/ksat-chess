package org.bytefred.satchess.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.bytefred.satchess.ui.platform.savePref
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Help, About and Examples fill the window but a 16dp margin (as in ktoy), on desktop and phone sizes. */
@OptIn(ExperimentalTestApi::class)
class BigDialogTest {
    @BeforeTest
    fun noWelcome() = savePref(WELCOME_PREF, "off")

    private fun fills(w: Int, h: Int, open: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(w, h) {
        setContent { App(PuzzleViewModel(FakeSolverService())) }
        open()
        waitForIdle()
        val b = onNodeWithTag("big-dialog").fetchSemanticsNode().boundsInRoot
        val m = 16f * density.density + 1f
        assertTrue(b.left <= m && b.top <= m && b.right >= w * density.density - m && b.bottom >= h * density.density - m, "$b in ${w}x$h")
    }

    private fun ComposeUiTest.menu(menu: String, item: String) {
        onNodeWithTag("menu-$menu").performClick()
        onNodeWithText(item).performClick()
    }

    @Test fun helpFillsTheDesktopWindow() = fills(1280, 800) { menu("Help", "Puzzles") }
    @Test fun helpFillsThePhone() = fills(360, 640) { menu("Help", "Board input") }
    @Test fun aboutFillsTheDesktopWindow() = fills(1280, 800) { menu("Help", "About ksat-chess") }
    @Test fun aboutFillsThePhone() = fills(360, 640) { menu("Help", "About ksat-chess") }
    @Test fun examplesFillTheDesktopWindow() = fills(1280, 800) { menu("File", "Examples…") }
    @Test fun examplesFillThePhone() = fills(360, 640) { menu("File", "Examples…") }

    /** The help texts are flowing paragraphs: they wrap at the dialog width, not at fixed line breaks. */
    @Test
    fun helpTextUsesTheFullWidth() = runDesktopComposeUiTest(1280, 800) {
        setContent { App(PuzzleViewModel(FakeSolverService())) }
        menu("Help", "Puzzles")
        waitForIdle()
        val dialog = onNodeWithTag("big-dialog").fetchSemanticsNode().boundsInRoot
        val text = onNodeWithTag("helpText").fetchSemanticsNode()
        assertTrue(text.boundsInRoot.width > dialog.width * 0.8f, "${text.boundsInRoot} in $dialog")
        val lines = text.config[SemanticsProperties.Text].joinToString().lines()
        assertTrue(lines.none { it.isNotEmpty() && it.first().isLowerCase() }, "hard-wrapped line in $lines")
    }

    @Test
    fun subheadingsAreBold() {
        val t = helpText("Both modes\nLong press.\n\nView -> Board input switches the mode.")
        assertEquals(listOf("Both modes"), t.spanStyles.map { t.text.substring(it.start, it.end) })
    }

    @Test
    fun theExamplesStillLoad() = runDesktopComposeUiTest(1280, 800) {
        val vm = PuzzleViewModel(FakeSolverService())
        setContent { App(vm) }
        menu("File", "Examples…")
        onNodeWithText("Demos").performClick()
        onNodeWithText("Black moves first").performClick()
        onNodeWithTag("loadExample").performClick()
        waitForIdle()
        onNodeWithTag("big-dialog").assertDoesNotExist()
        assertTrue(vm.puzzle.title.isNotEmpty())
    }
}
