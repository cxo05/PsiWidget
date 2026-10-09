package com.psiwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.psiwidget.data.Store
import com.psiwidget.location.LocationActivity

/** Glance state keys. METRIC is per widget; the rest is copied from [Store] into every widget. */
object Keys {
    val METRIC = stringPreferencesKey("metric")
    val SNAPSHOT = stringPreferencesKey("snapshot")
    val OFFLINE = booleanPreferencesKey("offline")
    val REGION = stringPreferencesKey("region")
    val LOCATION_GRANTED = booleanPreferencesKey("location_granted")
}

object WidgetSync {
    suspend fun pushToAll(context: Context) {
        val store = Store(context)
        val granted = LocationActivity.hasPermission(context)
        val widget = PsiWidget()
        GlanceAppWidgetManager(context).getGlanceIds(PsiWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs.setOrRemove(Keys.SNAPSHOT, store.snapshotJson)
                prefs.setOrRemove(Keys.REGION, store.regionKey.takeIf { granted })
                prefs[Keys.OFFLINE] = store.offline
                prefs[Keys.LOCATION_GRANTED] = granted
            }
            widget.update(context, id)
        }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}
