// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUtilTest {

    @Test
    fun cleanChangelog_stripsDirectDownloadAndCommitSha() {
        val raw = """
            ### 🌌 Wallpapers FOSS v1.2.0

            - **Nova funcionalidade:** Notificações flutuantes.
            - **Melhorias:** Desempenho otimizado.

            ### Direct APK Download
            - [Download Release APK](https://github.com/...)

            Built automatically from commit `abc1234`.
        """.trimIndent()

        val cleaned = MarkdownUtil.cleanChangelog(raw)
        val expected = """
            ### 🌌 Wallpapers FOSS v1.2.0

            - **Nova funcionalidade:** Notificações flutuantes.
            - **Melhorias:** Desempenho otimizado.
        """.trimIndent()

        assertEquals(expected, cleaned)
    }
}
