package com.nefla.hidethatpeoples.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.shizuku.ShizukuManager
import java.util.concurrent.TimeUnit

class AutoCleanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting AutoCleanWorker execution")
        val prefs = AppPreferences(applicationContext)

        if (!prefs.isAutoCleanEnabled) {
            Log.d(TAG, "AutoClean is disabled in preferences. Skipping.")
            return Result.success()
        }

        val privilegeManager = com.nefla.hidethatpeoples.privilege.PrivilegeManager.getInstance(applicationContext)
        if (!privilegeManager.isReady()) {
            Log.w(TAG, "Privilege provider is not ready. Skipping auto-clean.")
            return Result.retry()
        }

        val targets = prefs.enabledPackages
        if (targets.isEmpty()) {
            return Result.success()
        }

        val results = privilegeManager.clearMultipleShortcuts(targets)
        Log.d(TAG, "AutoCleanWorker completed with results: $results")
        prefs.lastClearedTimestamp = System.currentTimeMillis()

        return Result.success()
    }

    companion object {
        private const val TAG = "AutoCleanWorker"
        const val WORK_NAME = "hide_that_peoples_auto_clean"

        fun schedule(context: Context, intervalMinutes: Long) {
            val minInterval = intervalMinutes.coerceAtLeast(15L)
            val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(minInterval, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Scheduled auto clean every $minInterval minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic auto clean")
        }
    }
}
