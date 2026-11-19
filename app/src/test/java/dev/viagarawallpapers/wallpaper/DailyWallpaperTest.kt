// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailyWallpaperTest {

    @Test
    fun isDifferentDay_sameDayReturnsFalse() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 10, 30, 0)
        }
        val ms1 = cal.timeInMillis

        cal.set(2026, Calendar.NOVEMBER, 19, 22, 45, 0)
        val ms2 = cal.timeInMillis

        assertFalse(DailyWallpaperManager.isDifferentDay(ms1, ms2))
    }

    @Test
    fun isDifferentDay_nextDayReturnsTrue() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 23, 59, 0)
        }
        val ms1 = cal.timeInMillis

        cal.set(2026, Calendar.NOVEMBER, 20, 0, 1, 0)
        val ms2 = cal.timeInMillis

        assertTrue(DailyWallpaperManager.isDifferentDay(ms1, ms2))
    }

    @Test
    fun isDifferentDay_zeroOrNegativeLastMsReturnsTrue() {
        val now = System.currentTimeMillis()
        assertTrue(DailyWallpaperManager.isDifferentDay(0L, now))
        assertTrue(DailyWallpaperManager.isDifferentDay(-1L, now))
    }

    @Test
    fun calculateNextMidnightMs_isWithinNext24HoursAtMidnight() {
        val midnight = WallpaperCheckAlarmReceiver.calculateNextMidnightMs()
        val now = System.currentTimeMillis()

        assertTrue(midnight > now)
        assertTrue(midnight - now <= 24 * 60 * 60 * 1000L + 5000L)

        val cal = Calendar.getInstance().apply { timeInMillis = midnight }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun selectNextWallpaper_advancesSequentiallyAndSkipsDisliked() {
        val item1 = WallpaperItem(id = "w1", name = "One", author = "A", thumbnailUrl = "", fullUrl = "")
        val item2 = WallpaperItem(id = "w2", name = "Two", author = "A", thumbnailUrl = "", fullUrl = "")
        val item3 = WallpaperItem(id = "w3", name = "Three", author = "A", thumbnailUrl = "", fullUrl = "")
        val list = listOf(item1, item2, item3)

        // Given current is w1, next should be w2
        val next1 = DailyWallpaperManager.selectNextWallpaper(
            allStatic = list,
            dislikedIds = emptySet(),
            currentId = "w1",
        )
        assertNotNull(next1)
        assertEquals("w2", next1?.id)

        // Given current is w1 and w2 is disliked, next should be w3
        val next2 = DailyWallpaperManager.selectNextWallpaper(
            allStatic = list,
            dislikedIds = setOf("w2"),
            currentId = "w1",
        )
        assertNotNull(next2)
        assertEquals("w3", next2?.id)

        // Empty list returns null
        val nextEmpty = DailyWallpaperManager.selectNextWallpaper(
            allStatic = emptyList(),
            dislikedIds = emptySet(),
            currentId = "w1",
        )
        assertNull(nextEmpty)
    }
}
