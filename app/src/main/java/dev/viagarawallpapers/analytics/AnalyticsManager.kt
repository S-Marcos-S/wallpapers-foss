// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.analytics

import android.content.Context
import com.aptabase.Aptabase
import dev.viagarawallpapers.BuildConfig

/**
 * Manages privacy-first, anonymous usage telemetry using Aptabase.
 * Respects user preferences and never collects IP addresses or personally identifiable information.
 */
object AnalyticsManager {
    private const val PREFS_NAME = "wallpaper_analytics_prefs"
    private const val KEY_ANALYTICS_ENABLED = "anonymous_analytics_enabled"
    private const val APP_START_MIN_INTERVAL_MS = 60 * 60 * 1000L // 1 hour debounce

    private var isInitialized = false
    private var lastAppStartTimeMs = 0L
    private var lastWallpaperAppliedTimeMs = 0L

    fun isEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
        if (enabled) {
            ensureInitialized(context)
        }
    }

    fun init(context: Context) {
        if (!isEnabled(context)) return
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val appKey = BuildConfig.APTABASE_APP_KEY.trim()
        if (appKey.isBlank()) return
        runCatching {
            Aptabase.instance.initialize(context.applicationContext, appKey)
            isInitialized = true
        }
    }

    fun trackAppStart(context: Context) {
        if (!isEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastAppStartTimeMs < APP_START_MIN_INTERVAL_MS) {
            return
        }
        lastAppStartTimeMs = now
        ensureInitialized(context)
        runCatching {
            Aptabase.instance.trackEvent("app_started")
        }
    }

    fun trackWallpaperApplied(context: Context, isLive: Boolean = false) {
        if (!isEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastWallpaperAppliedTimeMs < 5_000L) {
            return
        }
        lastWallpaperAppliedTimeMs = now
        ensureInitialized(context)
        runCatching {
            Aptabase.instance.trackEvent(
                "wallpaper_applied",
                mapOf("type" to if (isLive) "live" else "static")
            )
        }
    }
}
