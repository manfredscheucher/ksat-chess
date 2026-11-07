package org.bytefred.satchess.ui.platform

import java.util.prefs.Preferences

// The tests set prefsNode, so they never touch the preferences of the real app.
private val prefs: Preferences? =
    try { Preferences.userRoot().node(System.getProperty("prefsNode") ?: "org/bytefred/satchess") } catch (e: Exception) { null }

actual fun loadPref(key: String): String? = try { prefs?.get(key, null) } catch (e: Exception) { null }

actual fun savePref(key: String, value: String) {
    try { prefs?.put(key, value); prefs?.flush() } catch (e: Exception) { }
}
