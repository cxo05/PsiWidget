package com.psiwidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.psiwidget.work.PsiRefreshWorker

class PsiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PsiWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PsiRefreshWorker.schedulePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // After an app update the launcher falls back to the loading layout; redraw from the cache straight away.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            PsiRefreshWorker.refreshNow(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PsiRefreshWorker.cancelAll(context)
    }
}
