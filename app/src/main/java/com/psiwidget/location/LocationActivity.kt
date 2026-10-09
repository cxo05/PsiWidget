package com.psiwidget.location

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.psiwidget.data.Store
import com.psiwidget.work.PsiRefreshWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The widget's only screen, and it has no UI of its own. It runs when the widget is added
 * (as the configure activity), when the refresh icon is tapped, and when the user taps
 * "Tap to use location". It reads a coarse location in the foreground, picks the nearest
 * region, starts a data refresh and closes.
 */
class LocationActivity : ComponentActivity() {

    private val store by lazy { Store(this) }

    private val requestPermission = registerForActivityResult(RequestPermission()) { granted ->
        if (!granted) store.locationDenials += 1
        lifecycleScope.launch {
            if (granted) updateRegion()
            finishAndRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // When adding the widget, report success up front so it is placed even if location is declined.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        if (savedInstanceState != null) return

        when {
            hasPermission(this) -> lifecycleScope.launch {
                updateRegion()
                finishAndRefresh()
            }
            // A refresh tap never asks for permission; only adding the widget or the explicit hint does.
            intent.getStringExtra(EXTRA_MODE) == MODE_REFRESH -> finishAndRefresh()
            // Android stops showing the dialog after two denials, so send the user to app settings instead.
            store.locationDenials >= 2 -> {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finishAndRefresh()
            }
            else -> requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private suspend fun updateRegion() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val location = runCatching {
            withTimeoutOrNull(5_000) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            } ?: client.lastLocation.await()
        }.getOrNull() ?: return

        val locations = store.snapshot?.regionLocations?.takeIf { it.isNotEmpty() } ?: RegionResolver.DEFAULT_LOCATIONS
        store.regionKey = RegionResolver.nearest(location.latitude, location.longitude, locations)?.key
    }

    private fun finishAndRefresh() {
        PsiRefreshWorker.refreshNow(this)
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_REFRESH = "refresh"
        const val MODE_REQUEST = "request"

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun intent(context: Context, mode: String) =
            Intent(context, LocationActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
