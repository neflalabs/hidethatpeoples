package com.neflalabs.hidethatpeoples.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neflalabs.hidethatpeoples.data.AppPreferences
import com.neflalabs.hidethatpeoples.privilege.PrivilegeManager
import kotlinx.coroutines.delay
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

        val targets = prefs.enabledPackages
        if (targets.isEmpty()) {
            Log.d(TAG, "No target packages configured. Skipping.")
            return Result.success()
        }

        val privilegeManager = PrivilegeManager.getInstance(applicationContext)

        // Providers in background need a moment to bind/reconnect
        var isReady = privilegeManager.isReady()
        if (!isReady) {
            Log.d(TAG, "Privilege provider not ready immediately, triggering reconnect...")
            privilegeManager.reconnect()
            val timeout = 6000L
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeout) {
                if (privilegeManager.isReady()) {
                    isReady = true
                    break
                }
                delay(250)
            }
        }

        if (!isReady) {
            Log.w(TAG, "Privilege provider is not ready after reconnect timeout. Skipping auto-clean.")
            return Result.retry()
        }

        val results = privilegeManager.clearMultipleShortcuts(targets)
        val successCount = results.values.count { it }
        Log.d(TAG, "AutoCleanWorker completed with $successCount successes out of ${targets.size}")
        if (successCount > 0) {
            prefs.lastClearedTimestamp = System.currentTimeMillis()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "AutoCleanWorker"
        const val WORK_NAME = "hide_that_peoples_auto_clean"

        fun schedule(context: Context, intervalMinutes: Long) {
            val minInterval = intervalMinutes.coerceAtLeast(15L)
            val periodicRequest = PeriodicWorkRequestBuilder<AutoCleanWorker>(minInterval, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            Log.d(TAG, "Scheduled auto clean every $minInterval minutes")

            // Immediately trigger an initial one-time sweep so user gets instant results without waiting 15-30m
            val immediateRequest = OneTimeWorkRequestBuilder<AutoCleanWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
            Log.d(TAG, "Enqueued immediate auto clean sweep")
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork("${WORK_NAME}_immediate")
            Log.d(TAG, "Cancelled periodic and immediate auto clean")
        }
    }
}
