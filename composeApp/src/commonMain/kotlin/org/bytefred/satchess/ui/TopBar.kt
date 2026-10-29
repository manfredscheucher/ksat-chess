package org.bytefred.satchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.GeneratedLicenses
import org.bytefred.satchess.GeneratedVersionInfo
import org.bytefred.satchess.ui.platform.rememberFileLoader
import org.bytefred.satchess.ui.platform.rememberFileSaver
import org.bytefred.satchess.ui.platform.shareLink
import org.bytefred.satchess.ui.platform.shareSupported

/**
 * Top bar: Examples, New, Import / Export JSON, Share (web), Settings, About. On a narrow screen
 * everything after New moves into an overflow menu.
 */
@Composable
fun TopBar(vm: PuzzleViewModel, compact: Boolean, onExamples: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val importFile = rememberFileLoader { text -> if (vm.importJson(text)) vm.showMessage("Puzzle imported") }
    val exportFile = rememberFileSaver { name -> name?.let { vm.showMessage("Exported $it") } }

    val actions = buildList {
        add("Import" to { importFile() })
        add("Export" to { exportFile(vm.suggestedFileName(), vm.exportJson()) })
        if (shareSupported) {
            add("Share link" to {
                shareLink(vm.shareJson()) { copied ->
                    vm.showMessage(if (copied) "Link copied to the clipboard" else "Could not copy: the link is in the address bar")
                }
            })
        }
        add("Settings" to { showSettings = true })
        add("About" to { showAbout = true })
    }

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "SAT chess",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = onExamples, modifier = Modifier.testTag("examples")) { Text("Examples") }
            TextButton(onClick = vm::newPuzzle) { Text("New") }
            if (compact) {
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { overflow = true }) { MoreGlyph(MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        for ((label, action) in actions) {
                            DropdownMenuItem(text = { Text(label) }, onClick = { overflow = false; action() })
                        }
                    }
                }
            } else {
                for ((label, action) in actions.dropLast(2)) TextButton(onClick = action) { Text(label) }
                Spacer(Modifier.weight(1f))
                for ((label, action) in actions.takeLast(2)) TextButton(onClick = action) { Text(label) }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun SettingsDialog(vm: PuzzleViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips("Board input", vm.inputMode, InputMode.entries, {
                    when (it) { InputMode.HYBRID -> "Palette + drag"; InputMode.DIALOG -> "Click opens a dialog" }
                }) { vm.inputMode = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.showCoordinates, onCheckedChange = { vm.showCoordinates = it })
                    Text("Show coordinates")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * About: build identity from GeneratedVersionInfo, then the full license notices of everything the app
 * bundles (GeneratedLicenses, generated from the license files), scrollable.
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About SAT chess") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).testTag("about"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val sha = GeneratedVersionInfo.COMMIT_SHA
                if (sha != "unknown") {
                    Text("commit " + if (GeneratedVersionInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
                }
                if (GeneratedVersionInfo.COMMIT_DATE != "unknown") Text("committed ${GeneratedVersionInfo.COMMIT_DATE}")
                Text("built ${GeneratedVersionInfo.BUILD_DATE}")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Retrograde chess puzzles solved with SAT: every answer is a line of play that fits all constraints, found by a SAT solver and verified by a move generator.")
                Text(
                    "The code is MIT licensed. The bundled example puzzles are the credited composers' works and are not " +
                        "covered by the MIT license (see the puzzle notice below, puzzles/NOTICE.md in the source).",
                    style = MaterialTheme.typography.bodySmall,
                )
                for (notice in GeneratedLicenses.NOTICES) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notice.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
