// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSampleTest {

    @Test
    fun calculateInSampleSize_standardImageKeepsNativeResolution() {
        // Standard high-res wallpaper: 1400x3100 (17.36 MB)
        // Expected requested bounds for 1080p screen @ 2x: 2160x4800
        val sampleSize = WallpaperRepository.calculateInSampleSize(
            rawWidth = 1400,
            rawHeight = 3100,
            reqWidth = 2160,
            reqHeight = 4800,
            maxByteLimit = 80 * 1024 * 1024L,
        )
        assertEquals("Standard wallpaper should not be downsampled", 1, sampleSize)
    }

    @Test
    fun calculateInSampleSize_oversizedCrashImageDownsampledToSafeBounds() {
        // The exact wallpaper causing Canvas overflow: 5227x9293 (194,298,044 bytes, ~185.3 MB)
        val rawW = 5227
        val rawH = 9293
        val rawBytes = rawW.toLong() * rawH.toLong() * 4L
        assertEquals(194298044L, rawBytes)

        val sampleSize = WallpaperRepository.calculateInSampleSize(
            rawWidth = rawW,
            rawHeight = rawH,
            reqWidth = 2160,
            reqHeight = 4800,
            maxByteLimit = 80 * 1024 * 1024L,
        )

        assertEquals("Oversized 194MB image should use inSampleSize 2", 2, sampleSize)

        val sampledW = rawW / sampleSize
        val sampledH = rawH / sampleSize
        val sampledBytes = sampledW.toLong() * sampledH.toLong() * 4L

        // Verify sampled resolution is still higher than 4K UHD
        assertTrue("Sampled width should be >= 2160", sampledW >= 2160)
        assertTrue("Sampled height should be >= 3840", sampledH >= 3840)
        assertTrue("Sampled bytes must be <= 80MB safe limit", sampledBytes <= 80 * 1024 * 1024L)
    }

    @Test
    fun calculateInSampleSize_monsterImageGuaranteedBelowCanvasLimit() {
        // 6810x12107 (~329.8 MB)
        val rawW = 6810
        val rawH = 12107
        val sampleSize = WallpaperRepository.calculateInSampleSize(
            rawWidth = rawW,
            rawHeight = rawH,
            reqWidth = 2160,
            reqHeight = 4800,
            maxByteLimit = 80 * 1024 * 1024L,
        )

        val sampledW = rawW / sampleSize
        val sampledH = rawH / sampleSize
        val sampledBytes = sampledW.toLong() * sampledH.toLong() * 4L

        assertTrue("Sample size should be at least 2", sampleSize >= 2)
        assertTrue("Sampled bytes must be safely below 80MB", sampledBytes <= 80 * 1024 * 1024L)
    }

    @Test
    fun calculateInSampleSize_handlesInvalidOrZeroDimensions() {
        assertEquals(1, WallpaperRepository.calculateInSampleSize(0, 0, 1080, 2400))
        assertEquals(1, WallpaperRepository.calculateInSampleSize(-100, 200, 1080, 2400))
    }
}
