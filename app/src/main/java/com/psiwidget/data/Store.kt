package com.psiwidget.data

import android.content.Context
import androidx.core.content.edit

/** Device-wide cache shared by every widget instance. Per-widget choices live in Glance state. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("psi", Context.MODE_PRIVATE)

    val snapshotJson: String? get() = prefs.getString(KEY_SNAPSHOT, null)

    val snapshot: PsiSnapshot?
        get() = snapshotJson?.let { runCatching { AppJson.decodeFromString<PsiSnapshot>(it) }.getOrNull() }

    /** True when the most recent fetch failed. */
    var offline: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE, false)
        set(value) = prefs.edit { putBoolean(KEY_OFFLINE, value) }

    var regionKey: String?
        get() = prefs.getString(KEY_REGION, null)
        set(value) = prefs.edit { putString(KEY_REGION, value) }

    var locationDenials: Int
        get() = prefs.getInt(KEY_DENIALS, 0)
        set(value) = prefs.edit { putInt(KEY_DENIALS, value) }

    fun saveSnapshot(snapshot: PsiSnapshot) = prefs.edit {
        putString(KEY_SNAPSHOT, AppJson.encodeToString(PsiSnapshot.serializer(), snapshot))
        putBoolean(KEY_OFFLINE, false)
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_OFFLINE = "offline"
        const val KEY_REGION = "region"
        const val KEY_DENIALS = "location_denials"
    }
}
