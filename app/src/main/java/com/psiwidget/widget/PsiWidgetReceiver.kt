package com.psiwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.psiwidget.work.PsiRefreshWorker

class PsiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PsiWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PsiRefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PsiRefreshWorker.cancelAll(context)
    }
}
