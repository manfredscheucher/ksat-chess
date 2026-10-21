package org.bytefred.satchess

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.bytefred.satchess.ui.App

/**
 * Web (JS/browser) entry point: hosts the shared [App] in a Compose viewport bound to the document
 * body. The bundle is loaded by src/jsMain/resources/index.html; the solver runs in solver-worker.js.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
