package org.bytefred.satchess.ui.platform

import kotlinx.browser.window

private external fun encodeURIComponent(text: String): String
private external fun decodeURIComponent(text: String): String

actual val shareSupported: Boolean = true

actual fun shareLink(puzzleJson: String, onResult: (copied: Boolean) -> Unit) {
    val hash = "#$SHARE_HASH_KEY${encodeURIComponent(puzzleJson)}"
    window.history.replaceState(null, "", hash)
    try {
        // The clipboard API is missing outside secure contexts and rejects without permission.
        window.navigator.clipboard.writeText(window.location.href)
            .then({ onResult(true) }, { onResult(false) })
    } catch (e: Throwable) {
        onResult(false)
    }
}

actual fun sharedPuzzleJson(): String? {
    val hash = window.location.hash.removePrefix("#")
    if (!hash.startsWith(SHARE_HASH_KEY)) return null
    return try {
        decodeURIComponent(hash.removePrefix(SHARE_HASH_KEY))
    } catch (e: Throwable) {
        // URIError from a mangled link: treat as "no shared puzzle"
        null
    }
}
