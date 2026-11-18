// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperNotificationTest {

    @Test
    fun parseNotificationJson_parsesValidPayloadCorrectly() {
        val json = """
            {
              "id": "notif_1794772064",
              "title": "Porsche e espaço chegando!",
              "message": "20 novos wallpapers para deixar seu celular incrível",
              "count": 20,
              "wallpaper_ids": [
                "tg_44252",
                "tg_44251"
              ],
              "timestamp": 1794772064269,
              "generated_by": "gemini"
            }
        """.trimIndent()

        val info = WallpaperNotificationManager.parseNotificationJson(json)
        assertNotNull(info)
        assertEquals("notif_1794772064", info?.id)
        assertEquals("Porsche e espaço chegando!", info?.title)
        assertEquals("20 novos wallpapers para deixar seu celular incrível", info?.message)
        assertEquals(20, info?.count)
        assertEquals(2, info?.wallpaperIds?.size)
        assertEquals("tg_44252", info?.wallpaperIds?.get(0))
        assertEquals(1794772064269L, info?.timestamp)
        assertEquals("gemini", info?.generatedBy)
    }

    @Test
    fun parseNotificationJson_parsesNestedLatestNotification() {
        val json = """
            {
              "latest_notification": {
                "id": "notif_999",
                "title": "Anime Drop",
                "message": "Novos wallpapers épicos",
                "count": 5,
                "wallpaper_ids": ["tg_1", "tg_2"],
                "timestamp": 123456789,
                "generated_by": "gemini"
              }
            }
        """.trimIndent()

        val info = WallpaperNotificationManager.parseNotificationJson(json)
        assertNotNull(info)
        assertEquals("notif_999", info?.id)
        assertEquals("Anime Drop", info?.title)
        assertEquals(5, info?.count)
    }

    @Test
    fun parseNotificationJson_returnsNullForInvalidOrEmptyId() {
        val emptyIdJson = """
            {
              "id": "",
              "title": "Test"
            }
        """.trimIndent()

        assertNull(WallpaperNotificationManager.parseNotificationJson(emptyIdJson))
        assertNull(WallpaperNotificationManager.parseNotificationJson("not valid json"))
    }
}
