package org.bytefred.satchess.ui.platform

/**
 * Tiny persistent key/value store for UI preferences, same as ktoy's. Failures are swallowed: a
 * preference that can't be read or written just falls back to the default.
 */
expect fun loadPref(key: String): String?

expect fun savePref(key: String, value: String)
