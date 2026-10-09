package com.psiwidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.psiwidget.data.PsiApi
import com.psiwidget.data.Store
import com.psiwidget.widget.WidgetSync
import java.util.concurrent.TimeUnit

class PsiRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val fetched = runCatching { PsiApi.fetch() }
        fetched
            .onSuccess { store.saveSnapshot(it) }
            .onFailure { store.offline = true }
        WidgetSync.pushToAll(applicationContext)
        return if (fetched.isSuccess || runAttemptCount >= 2) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "psi-refresh"
        private const val NOW = "psi-refresh-now"

        /** Every 15 minutes, the API's own update rate and WorkManager's minimum. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PsiRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** No network constraint, so a tap while offline fails fast and the widget can say so. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<PsiRefreshWorker>().build())
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(NOW)
            }
        }
    }
}
