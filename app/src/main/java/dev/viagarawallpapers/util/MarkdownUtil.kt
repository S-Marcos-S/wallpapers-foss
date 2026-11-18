// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object MarkdownUtil {

    fun cleanChangelog(rawChangelog: String): String {
        return rawChangelog
            .substringBefore("### Direct APK Download")
            .substringBefore("Built automatically from commit")
            .trim()
    }

    fun formatMarkdownToAnnotated(text: String, colorScheme: ColorScheme): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.lines()
            lines.forEachIndexed { index, rawLine ->
                val trimmed = rawLine.trim()
                when {
                    trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                        // subtle spacing
                    }
                    trimmed.startsWith("###") -> {
                        val content = trimmed.removePrefix("###").trim()
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.primary)) {
                            append(content)
                        }
                    }
                    trimmed.startsWith("##") -> {
                        val content = trimmed.removePrefix("##").trim()
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.primary)) {
                            append(content)
                        }
                    }
                    trimmed.startsWith("#") -> {
                        val content = trimmed.removePrefix("#").trim()
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.primary)) {
                            append(content)
                        }
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        append("• ")
                        parseInlineMarkdown(trimmed.substring(2).trim(), colorScheme)
                    }
                    else -> {
                        parseInlineMarkdown(trimmed, colorScheme)
                    }
                }

                if (index < lines.lastIndex && trimmed.isNotBlank()) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.parseInlineMarkdown(line: String, colorScheme: ColorScheme) {
        val tokenRegex = Regex("""(\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`)""")
        var lastIndex = 0
        for (match in tokenRegex.findAll(line)) {
            if (match.range.first > lastIndex) {
                append(line.substring(lastIndex, match.range.first))
            }
            val fullMatch = match.value
            when {
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val inner = fullMatch.substring(2, fullMatch.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inner)
                    }
                }
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colorScheme.surfaceContainerHighest)) {
                        append(inner)
                    }
                }
                else -> append(fullMatch)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < line.length) {
            append(line.substring(lastIndex))
        }
    }
}
