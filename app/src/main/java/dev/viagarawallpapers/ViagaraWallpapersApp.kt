// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers

import android.app.Application
import dev.viagarawallpapers.data.Prefs
import dev.viagarawallpapers.update.UpdateManager
import dev.viagarawallpapers.wallpaper.DailyWallpaperManager
import dev.viagarawallpapers.wallpaper.WallpaperCheckAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ViagaraWallpapersApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        UpdateManager.init(this)
        WallpaperCheckAlarmReceiver.scheduleNextCheck(this)
        DailyWallpaperManager.checkAndAdvanceIfNewDay(this, CoroutineScope(Dispatchers.IO))
    }
}
