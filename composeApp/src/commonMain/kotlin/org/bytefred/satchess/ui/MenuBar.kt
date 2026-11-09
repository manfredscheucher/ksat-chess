package org.bytefred.satchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.bytefred.satchess.GeneratedLicenses
import org.bytefred.satchess.GeneratedVersionInfo
import org.bytefred.satchess.ui.platform.loadPref
import org.bytefred.satchess.ui.platform.rememberFileLoader
import org.bytefred.satchess.ui.platform.rememberFileSaver
import org.bytefred.satchess.ui.platform.savePref
import org.bytefred.satchess.ui.platform.shareLink
import org.bytefred.satchess.ui.platform.shareSupported
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The ktoy menu bar: File (examples, load, save, share), View (board input, layout), Help. */
@Composable
fun MenuBar(vm: PuzzleViewModel, onExamples: () -> Unit) {
    var openMenu by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }   // title to text
    var showAbout by remember { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(loadPref(WELCOME_PREF) != "off") }
    val barFocus = remember { FocusRequester() }
    // the popup does not take the focus, so the bar must have it for Escape to reach it
    LaunchedEffect(openMenu) { if (openMenu != null) barFocus.requestFocus() }
    val loadFromDisk = rememberFileLoader { text -> if (vm.importJson(text)) vm.showMessage("Puzzle loaded") }
    val saveToDisk = rememberFileSaver(vm::fileSaved)
    fun save() = saveToDisk(vm.suggestedFileName(), vm.exportJson())

    // a menu entry: closes the menu, then runs [onClick]
    @Composable
    fun Item(label: String, enabled: Boolean = true, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = { openMenu = null; onClick() },
    )

    Surface(color = MENU_BAR_COLOR) {
        // scrolls sideways when the menus don't fit (phones)
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
                .testTag("menu-bar")
                .focusRequester(barFocus)
                .onKeyEvent {
                    if (it.key == Key.Escape && openMenu != null) { openMenu = null; true } else false
                }
                .focusable(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton("File", openMenu, { openMenu = it }) {
                Item("Examples…", onClick = onExamples)
                Item("New") { vm.leave(vm::newPuzzle) }
                Item("Load…") { vm.leave(loadFromDisk) }
                Item("Save…") { save() }
                if (shareSupported) {
                    Item("Share link") {
                        shareLink(vm.shareJson()) { copied ->
                            vm.showMessage(if (copied) "Link copied to the clipboard" else "Could not copy: the link is in the address bar")
                        }
                    }
                }
            }
            MenuButton("Edit", openMenu, { openMenu = it }) {
                Item("Undo", enabled = vm.canUndo) { vm.undo() }
                Item("Redo", enabled = vm.canRedo) { vm.redo() }
            }
            MenuButton("View", openMenu, { openMenu = it }) {
                CheckItem("Coordinates", vm.showCoordinates) {
                    vm.showCoordinates = !vm.showCoordinates
                    savePref(COORDINATES_PREF, vm.showCoordinates.toString())
                }
                HorizontalDivider()
                MenuHeading("Board input")
                for (mode in InputMode.entries) {
                    CheckItem(mode.label, vm.inputMode == mode) {
                        openMenu = null
                        vm.inputMode = mode
                        savePref(INPUT_MODE_PREF, mode.name)
                    }
                }
                HorizontalDivider()
                MenuHeading("Solutions")
                for (view in SolutionView.entries) {
                    CheckItem(view.name.lowercase(), vm.solutionView == view) {
                        openMenu = null
                        vm.solutionView = view
                        savePref(SOLUTION_VIEW_PREF, view.name)
                    }
                }
                HorizontalDivider()
                MenuHeading("Layout")
                for (mode in LayoutMode.entries) {
                    CheckItem(mode.name.lowercase(), vm.layoutMode == mode) {
                        openMenu = null
                        vm.layoutMode = mode
                        savePref(LAYOUT_PREF, mode.name)
                    }
                }
            }
            MenuButton("Help", openMenu, { openMenu = it }) {
                Item("Puzzles") { help = "Puzzles" to PUZZLE_HELP }
                Item("Board input") { help = "Board input" to INPUT_HELP }
                Item("Results") { help = "Results" to RESULTS_HELP }
                Item("About ksat-chess") { showAbout = true }
            }
        }
    }

    if (vm.pendingLeave != null) {
        UnsavedEditsDialog(onSave = ::save, onDiscard = vm::discardAndLeave, onCancel = vm::cancelLeave)
    }
    help?.let { (title, text) -> TextDialog(title, text) { help = null } }
    if (showAbout) AboutDialog { showAbout = false }
    if (showWelcome) WelcomeDialog { showWelcome = false }
}

/**
 * A menu title with its dropdown. The popup does not take the focus, so a click outside it still
 * reaches what is underneath: clicking another menu title closes this menu and opens that one in one
 * click. A click on the open menu's own title closes it: the popup's outside-click dismissal fires
 * first (on press), so the title click right after it must not reopen the menu.
 */
@Composable
private fun MenuButton(
    label: String,
    openMenu: String?,
    setOpenMenu: (String?) -> Unit,
    items: @Composable () -> Unit,
) {
    var dismissedAt by remember { mutableStateOf<TimeMark?>(null) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .testTag("menu-$label")
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    val justClosed = dismissedAt?.let { it.elapsedNow() < REOPEN_GUARD } == true
                    setOpenMenu(if (openMenu == label || justClosed) null else label)
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        DropdownMenu(
            expanded = openMenu == label,
            // only close if this menu is still the open one: the outside click may have just opened another
            onDismissRequest = {
                if (openMenu == label) {
                    dismissedAt = TimeSource.Monotonic.markNow()
                    setOpenMenu(null)
                }
            },
            properties = PopupProperties(focusable = false),
        ) {
            items()
        }
    }
}

// a title click this soon after its menu's outside-click dismissal belongs to the same click
// (press dismissed, release clicked)
private val REOPEN_GUARD = 500.milliseconds

private val MENU_BAR_COLOR = Color(0xFFE6E8EB)

@Composable
private fun CheckItem(label: String, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text((if (checked) "✓ " else "    ") + label) },
        onClick = onToggle,
    )
}

@Composable
private fun MenuHeading(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun UnsavedEditsDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save changes?") },
        text = { Text("This puzzle has changes that are not saved to a file yet.") },
        confirmButton = { TextButton(onClick = onSave) { Text("Save…") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

/** A read-only, scrollable text dialog (Help), as big as the window. */
@Composable
private fun TextDialog(title: String, body: String, onDismiss: () -> Unit) {
    BigDialog(title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.fillMaxSize()) {
            // flowing paragraphs: they wrap to the dialog width
            Text(
                helpText(body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().verticalScroll(vScroll).padding(end = 12.dp).testTag("helpText"),
            )
        }
    }
}

/** [body] with each subheading in bold: the first line of a paragraph that has more lines. */
internal fun helpText(body: String) = buildAnnotatedString {
    body.split("\n\n").forEachIndexed { i, para ->
        if (i > 0) append("\n\n")
        val head = para.substringBefore('\n', missingDelimiterValue = "")
        if (head.isEmpty()) append(para)
        else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(head) }
            append(para.substring(head.length))
        }
    }
}

/** About: description, feedback box, the projects with their licenses, build identity, license texts. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    BigDialog("ksat-chess", onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(vScroll).padding(end = 12.dp).testTag("about")) {
                Text("Retro chess puzzles solved with SAT.")
                Spacer(Modifier.height(8.dp))
                FeedbackBox()
                Spacer(Modifier.height(8.dp))
                ProjectLine("ksat-chess", REPO_URL, "this app")
                ProjectLine("ksat", "https://github.com/manfredscheucher/ksat", "the SAT solvers")
                ProjectLine("cburnett", CBURNETT_URL, "chess pieces by Colin M.L. Burnett", license = "BSD 3-clause")
                ProjectLine("chess.com article", CHESS_COM_ARTICLE_URL, "example puzzles, by FM Mike Klein", license = null)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var showLicenses by remember { mutableStateOf(false) }
                TextButton(onClick = { showLicenses = !showLicenses }) {
                    Text(if (showLicenses) "Hide license texts" else "License texts")
                }
                if (showLicenses) {
                    for (notice in GeneratedLicenses.NOTICES) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(notice.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** One About line "[name] (license): note" (no bracket without a license); only the name is a link. */
@Composable
private fun ProjectLine(name: String, url: String, note: String, license: String? = "MIT") {
    Row {
        LinkText(name, url)
        Text((license?.let { " ($it)" } ?: "") + ": $note", Modifier.padding(vertical = 2.dp))
    }
}

private const val REPO_URL = "https://github.com/manfredscheucher/ksat-chess"
private const val CBURNETT_URL = "https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces"
private const val CHESS_COM_ARTICLE_URL = "https://www.chess.com/article/view/can-you-solve-these-5-easy-retrograde-puzzles"

/** "sha · committed <date> · built <date>"; unknown parts are left out. */
private fun buildLine(): String = buildList {
    val sha = GeneratedVersionInfo.COMMIT_SHA
    if (sha != "unknown") add(if (GeneratedVersionInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
    if (GeneratedVersionInfo.COMMIT_DATE != "unknown") add("committed ${GeneratedVersionInfo.COMMIT_DATE}")
    add("built ${GeneratedVersionInfo.BUILD_DATE}")
}.joinToString(" · ")

/** Shown at start until "Don't show again" is ticked. */
@Composable
private fun WelcomeDialog(close: () -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(WELCOME_PREF, "off"); close() }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done, modifier = Modifier.testTag("welcomeStart")) { Text("Start") } },
        title = { Text("Welcome to ksat-chess") },
        text = {
            Column {
                Text("Set up a position, and a SAT solver finds every legal way the game can have reached it.")
                Spacer(Modifier.height(6.dp))
                Row { Text("Code and puzzles: "); LinkText("github.com/manfredscheucher/ksat-chess", REPO_URL) }
                Spacer(Modifier.height(10.dp))
                FeedbackBox()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it }, Modifier.testTag("dontShowAgain"))
                    Text("Don't show again")
                }
            }
        },
    )
}

/** Yellow box with a clickable e-mail address that opens a prepared mail (About, welcome). */
@Composable
private fun FeedbackBox() {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text("ksat-chess is in early development. Feedback is very welcome:", color = Color(0xFF3A3000))
        LinkText(EMAIL, FEEDBACK_MAILTO)
    }
}

/** A clickable, underlined link that opens [url] in the browser. */
@Composable
private fun LinkText(label: String, url: String) {
    val uri = LocalUriHandler.current
    Text(
        label,
        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
        color = Color(0xFF1A4FB0),
        modifier = Modifier.clickable { openUriSafely(uri, url) }.padding(vertical = 2.dp),
    )
}

/** Open a link without ever crashing the app: if nothing can open it, nothing happens. */
internal fun openUriSafely(uri: UriHandler, url: String) {
    try { uri.openUri(url) } catch (e: Exception) { }
}

private val InputMode.label: String
    get() = when (this) {
        InputMode.HYBRID -> "Palette + drag"
        InputMode.DIALOG -> "Click opens a dialog"
    }

internal const val WELCOME_PREF = "welcome"
internal const val COORDINATES_PREF = "coordinates"
internal const val INPUT_MODE_PREF = "inputMode"
internal const val LAYOUT_PREF = "layout"
internal const val SOLUTION_VIEW_PREF = "solutionView"
private const val EMAIL = "manfred.scheucher@proton.me"
// subject "sat-chess feedback", body "hi manfred," + blank line
private const val FEEDBACK_MAILTO =
    "mailto:$EMAIL?subject=ksat-chess%20feedback&body=hi%20manfred%2C%0A%0A"

private const val PUZZLE_HELP = """A puzzle is N plies of play: board A at the start, board B at the end.

A board can be given completely, have '?' squares (unknown), or be all '?'. A square can also be one of a few things, for example white king or empty.

Goal at the end: check, checkmate or stalemate. Both sides cooperate, as in a helpmate. The solver does not look for forced mates.

N counts plies (half-moves).

File -> Save writes the puzzle as JSON, Load reads it back, Share link puts it into the address bar."""

private const val INPUT_HELP = """Palette + drag
Pick a piece, click a square to put it there. Click again with the same piece to empty the square. Nothing picked, nothing happens. Drag a piece to move it, drag it off the board to remove it.

Click opens a dialog
Click a square and pick what stands there.

Both modes
Long press or right click: allow several pieces on one square. Such squares are hatched amber.

View -> Board input switches the mode.

Edit -> Undo / Redo (Ctrl+Z, Ctrl+Shift+Z) take puzzle and board changes back."""

private const val RESULTS_HELP = """Solve lists every solution; each one is checked by playing the moves through.

Replay: Reset, Back, Step, Run, the slider, a click on a move, or the arrow keys and Home / End. "Show static" (or View -> Solutions) shows all boards of the solution at once instead.

Group similar solutions puts solutions with the same moves in a different order together."""
