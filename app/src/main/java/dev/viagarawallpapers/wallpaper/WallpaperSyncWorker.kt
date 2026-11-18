// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.viagarawallpapers.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class WallpaperSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = Prefs(applicationContext)
            val isEnabled = prefs.wallpaperNotificationsEnabled.first()
            if (!isEnabled) {
                return@withContext Result.success()
            }

            // Quick check against lightweight notification.json (~561 bytes)
            val remoteNotif = fetchRemoteNotification()
            val lastNotifiedId = WallpaperNotificationManager.getLastNotifiedId(applicationContext)
            val isInit = WallpaperNotificationManager.isInitialized(applicationContext)

            // If already initialized and remote notification ID matches what we notified, no action needed
            if (isInit && remoteNotif != null && remoteNotif.id.isNotBlank() && remoteNotif.id == lastNotifiedId) {
                return@withContext Result.success()
            }

            // Fetch latest catalog to sync local cache and dispatch notification if new wallpapers exist
            val catalog = WallpaperRepository.loadCatalog(applicationContext)
            if (catalog.wallpapers.isNotEmpty()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper sync background check failed", e)
            Result.retry()
        }
    }

    private fun fetchRemoteNotification(): WallpaperNotificationInfo? {
        return runCatching {
            val urlWithBuster = "$REMOTE_NOTIFICATION_URL?t=${System.currentTimeMillis()}"
            val conn = (URL(urlWithBuster).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                WallpaperNotificationManager.parseNotificationJson(text)
            } else null
        }.getOrNull()
    }

    companion object {
        private const val TAG = "WallpaperSyncWorker"
        const val WORK_NAME = "dev.viagarawallpapers.wallpaper.sync_worker"
        private const val REMOTE_NOTIFICATION_URL =
            "https://raw.githubusercontent.com/S-Marcos-S/wallpapers-foss/main/wallpapers/notification.json"

        /**
         * Enqueues periodic sync with battery and network friendly constraints.
         * Runs every 3 hours with flex interval of 30 minutes.
         */
        fun schedule(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<WallpaperSyncWorker>(
                    3, TimeUnit.HOURS,
                    30, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
            }
        }

        /**
         * Cancels periodic background check.
         */
        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
