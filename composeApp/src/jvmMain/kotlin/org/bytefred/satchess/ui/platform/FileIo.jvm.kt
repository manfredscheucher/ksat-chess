package org.bytefred.satchess.ui.platform

import androidx.compose.runtime.Composable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Desktop: a Swing file chooser + plain java.io read/write.

@Composable
actual fun rememberFileLoader(onLoaded: (text: String) -> Unit): () -> Unit = {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import puzzle"
        fileFilter = FileNameExtensionFilter("Puzzle files (*.json)", "json")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        onLoaded(chooser.selectedFile.readText())
    }
}

@Composable
actual fun rememberFileSaver(onSaved: (fileName: String?) -> Unit): (suggestedName: String, text: String) -> Unit =
    { suggestedName, text ->
        val chooser = JFileChooser().apply {
            dialogTitle = "Export puzzle"
            selectedFile = File("$suggestedName.json")
            fileFilter = FileNameExtensionFilter("Puzzle files (*.json)", "json")
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = chooser.selectedFile
            if (!file.name.lowercase().endsWith(".json")) file = File(file.path + ".json")
            file.writeText(if (text.endsWith("\n")) text else text + "\n")
            onSaved(file.name)
        } else {
            onSaved(null)
        }
    }
