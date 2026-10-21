package org.bytefred.satchess.ui.platform

/*
 * Share link (web only): the puzzle JSON travels in the URL hash, "#puzzle=<url-encoded JSON>".
 */

/** True where a shareable page URL exists (the browser). */
expect val shareSupported: Boolean

/**
 * Puts [puzzleJson] into the page's URL hash and copies the URL to the clipboard; [onResult] tells
 * whether the copy worked (the browser may refuse it).
 */
expect fun shareLink(puzzleJson: String, onResult: (copied: Boolean) -> Unit)

/** The puzzle JSON from the URL hash the page was opened with, or null. */
expect fun sharedPuzzleJson(): String?

const val SHARE_HASH_KEY = "puzzle="
