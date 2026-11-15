// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class StorageStats(
    val cacheSizeBytes: Long,
    val totalSizeBytes: Long,
) {
    val formattedCacheSize: String get() = StorageUtil.formatFileSize(cacheSizeBytes)
    val formattedTotalSize: String get() = StorageUtil.formatFileSize(totalSizeBytes)
}

object StorageUtil {

    fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    suspend fun getStorageStats(context: Context): StorageStats = withContext(Dispatchers.IO) {
        val cacheSize = calculateDirSize(context.cacheDir) + calculateDirSize(context.externalCacheDir)
        val filesSize = calculateDirSize(context.filesDir)
        StorageStats(
            cacheSizeBytes = cacheSize,
            totalSizeBytes = cacheSize + filesSize,
        )
    }

    suspend fun clearCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        var success = true
        fun deleteRecursively(dir: File?): Boolean {
            if (dir == null || !dir.exists()) return true
            val files = dir.listFiles() ?: return true
            for (file in files) {
                val ok = if (file.isDirectory) deleteRecursively(file) else file.delete()
                if (!ok) success = false
            }
            return success
        }
        deleteRecursively(context.cacheDir)
        deleteRecursively(context.externalCacheDir)
        success
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
        }
    }
}
