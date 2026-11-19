// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WallpaperDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WallpaperDownloadManager.ACTION_CANCEL_DOWNLOAD) {
            WallpaperDownloadManager.cancelAllDownloads(context)
        }
    }
}
