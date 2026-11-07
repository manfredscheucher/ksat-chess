package org.bytefred.satchess.ui.platform

import kotlinx.browser.localStorage

actual fun loadPref(key: String): String? =
    try { localStorage.getItem("satchess.$key") } catch (e: Throwable) { null }

actual fun savePref(key: String, value: String) {
    try { localStorage.setItem("satchess.$key", value) } catch (e: Throwable) { }
}
