package org.bytefred.satchess.ui.platform

// Desktop has no page URL to share; the Share action is hidden there.
actual val shareSupported: Boolean = false

actual fun shareLink(puzzleJson: String) = Unit

actual fun sharedPuzzleJson(): String? = null
