// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers

import android.app.Application
import dev.viagarawallpapers.analytics.AnalyticsManager
import dev.viagarawallpapers.data.Prefs
import dev.viagarawallpapers.update.UpdateManager
import dev.viagarawallpapers.wallpaper.DailyWallpaperManager
import dev.viagarawallpapers.wallpaper.WallpaperCheckAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ViagaraWallpapersApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        AnalyticsManager.init(this)
        UpdateManager.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            if (prefs.autoDailyWallpaperEnabled.first()) {
                WallpaperCheckAlarmReceiver.scheduleDailyChange(this@ViagaraWallpapersApp)
            }
        }
        DailyWallpaperManager.checkAndAdvanceIfNewDay(this, CoroutineScope(Dispatchers.IO))
    }
}
