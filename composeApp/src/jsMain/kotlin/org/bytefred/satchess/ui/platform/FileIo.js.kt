package org.bytefred.satchess.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get

// Browser: import via a hidden <input type=file> + FileReader; export via a Blob download.

@Composable
actual fun rememberFileLoader(onLoaded: (text: String) -> Unit): () -> Unit = {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".json,application/json"
    input.onchange = {
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = { onLoaded(reader.result as? String ?: "") }
            reader.readAsText(file)
        }
    }
    input.click()
}

@Composable
actual fun rememberFileSaver(onSaved: (fileName: String?) -> Unit): (suggestedName: String, text: String) -> Unit =
    { suggestedName, text ->
        val body = if (text.endsWith("\n")) text else text + "\n"
        val blob = Blob(arrayOf(body), BlobPropertyBag(type = "application/json"))
        val url = URL.createObjectURL(blob)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = "$suggestedName.json"
        anchor.click()
        URL.revokeObjectURL(url)
        onSaved("$suggestedName.json")
    }
