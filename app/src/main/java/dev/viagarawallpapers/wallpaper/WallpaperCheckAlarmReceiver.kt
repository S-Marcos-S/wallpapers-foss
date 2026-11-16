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
        val appCtx = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(appCtx)
                val isEnabled = prefs.autoDailyWallpaperEnabled.first()

                if (isEnabled) {
                    // Reagenda imediatamente a próxima meia-noite (00:00)
                    scheduleDailyChange(appCtx)

                    val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
                    val now = System.currentTimeMillis()

                    if (lastTimestamp <= 0L || DailyWallpaperManager.isDifferentDay(lastTimestamp, now)) {
                        DailyWallpaperManager.advanceToNextWallpaper(appCtx, isUserDislike = false)
                    }
                }

                // Aproveita o disparo diário para sincronizar catálogo sem custo extra de bateria
                WallpaperRepository.loadCatalog(appCtx)
            } catch (e: Exception) {
                Log.e(TAG, "Failed during daily wallpaper change", e)
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        const val ACTION_CHECK_WALLPAPERS = "dev.viagarawallpapers.action.CHECK_WALLPAPERS"
        private const val REQUEST_CODE = 4092

        /**
         * Calcula o timestamp exato da próxima meia-noite local (00:00:00) no fuso do dispositivo.
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
         * Agenda um alarme pontual no AlarmManager para as 00:00:00 locais.
         * Utiliza AlarmManager.RTC com setAndAllowWhileIdle para mínimo consumo de bateria (zero wakeups intermediários).
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            }
        }

        /**
         * Cancela o agendamento de troca diária quando a opção é desativada, evitando qualquer despertar desnecessário.
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
