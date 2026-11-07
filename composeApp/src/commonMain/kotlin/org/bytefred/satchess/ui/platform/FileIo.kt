package org.bytefred.satchess.ui.platform

import androidx.compose.runtime.Composable

/*
 * Load / Save of puzzle JSON files, as a platform shim (same pattern as ktoy's FileIo):
 * a Swing file chooser on desktop, a hidden <input type=file> / blob download in the browser.
 */

/**
 * Remembers a file-open launcher. The returned lambda opens the platform's picker (filtered to
 * .json); on a successful pick it calls [onLoaded] with the file's text. A cancel does nothing.
 */
@Composable
expect fun rememberFileLoader(onLoaded: (text: String) -> Unit): () -> Unit

/**
 * Remembers a file-save launcher taking (suggestedName without extension, text). It writes
 * `<name>.json` (save dialog on desktop, download in the browser) and calls [onSaved] with the file
 * name, or null if cancelled.
 */
@Composable
expect fun rememberFileSaver(onSaved: (fileName: String?) -> Unit): (suggestedName: String, text: String) -> Unit
