// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.viagarawallpapers.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class WallpaperCheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_CHECK_WALLPAPERS &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appCtx = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(appCtx)
                val isEnabled = prefs.autoDailyWallpaperEnabled.first()

                if (isEnabled) {
                    // Re-schedule the next midnight alarm
                    scheduleDailyChange(appCtx)

                    val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
                    val now = System.currentTimeMillis()

                    if (lastTimestamp <= 0L || DailyWallpaperManager.isDifferentDay(lastTimestamp, now)) {
                        DailyWallpaperManager.advanceToNextWallpaper(appCtx, isUserDislike = false)
                    } else {
                        // Ensure tomorrow's wallpaper is pre-cached for the next midnight
                        DailyWallpaperManager.prefetchNextWallpaper(appCtx)
                    }
                }

                // Leverage the wake window to sync catalog without battery overhead
                WallpaperRepository.loadCatalog(appCtx)
            } catch (e: Exception) {
                Log.e(TAG, "Failed during daily wallpaper change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        const val ACTION_CHECK_WALLPAPERS = "dev.viagarawallpapers.action.CHECK_WALLPAPERS"
        private const val REQUEST_CODE = 4092

        /**
         * Calculates timestamp of the next local midnight (00:00:00).
         */
        fun calculateNextMidnightMs(): Long {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        /**
         * Schedules exact wakeup alarm at midnight local time using RTC_WAKEUP.
         * Falls back gracefully to inexact allow-while-idle if exact alarms are not granted.
         */
        fun scheduleDailyChange(context: Context) {
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, WallpaperCheckAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_WALLPAPERS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val triggerAtMillis = calculateNextMidnightMs()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerAtMillis,
                                pendingIntent,
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerAtMillis,
                                pendingIntent,
                            )
                        }
                    } catch (e: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent,
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent,
                        )
                    } catch (e: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent,
                        )
                    }
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            }
        }

        /**
         * Cancels daily change alarm when disabled.
         */
        fun cancelDailyChange(context: Context) {
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, WallpaperCheckAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_WALLPAPERS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }
}
