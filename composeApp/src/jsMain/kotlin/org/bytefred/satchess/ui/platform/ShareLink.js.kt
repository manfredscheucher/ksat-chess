package org.bytefred.satchess.ui.platform

import kotlinx.browser.window

private external fun encodeURIComponent(text: String): String
private external fun decodeURIComponent(text: String): String

actual val shareSupported: Boolean = true

actual fun shareLink(puzzleJson: String) {
    val hash = "#$SHARE_HASH_KEY${encodeURIComponent(puzzleJson)}"
    window.history.replaceState(null, "", hash)
    window.navigator.clipboard.writeText(window.location.href)
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
