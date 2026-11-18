// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagarawallpapers.R
import dev.viagarawallpapers.ui.theme.GlassCard
import dev.viagarawallpapers.ui.theme.dynamicBorderColor
import dev.viagarawallpapers.ui.theme.dynamicSurfaceColor
import dev.viagarawallpapers.update.DownloadStatus
import dev.viagarawallpapers.update.RootInstaller
import dev.viagarawallpapers.update.UpdateManager
import dev.viagarawallpapers.util.MarkdownUtil

@Composable
fun InAppUpdateFloatingBanner(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val updateInfo by UpdateManager.updateAvailable.collectAsState()
    val ignoredVersion by UpdateManager.ignoredVersion.collectAsState()
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()

    // Trigger condition: New version available, strictly stable release (not beta), not ignored by user, and not in full preview
    val shouldShow = isVisible &&
            updateInfo != null &&
            updateInfo!!.hasUpdate &&
            !updateInfo!!.isBeta &&
            ignoredVersion != updateInfo!!.tagName

    // Automatically prompt installation once the APK download completes successfully
    LaunchedEffect(downloadStatus) {
        val status = downloadStatus
        if (status is DownloadStatus.Finished && status.fileUri != null) {
            UpdateManager.installUpdate(context, status.fileUri, RootInstaller.isRootAvailable())
        }
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
        modifier = modifier,
    ) {
        val info = updateInfo ?: return@AnimatedVisibility
        val rawChangelog = remember(info.changelog) {
            MarkdownUtil.cleanChangelog(info.changelog)
        }
        val formattedChangelog = remember(rawChangelog, colorScheme) {
            MarkdownUtil.formatMarkdownToAnnotated(rawChangelog, colorScheme)
        }

        val isDark = isSystemInDarkTheme()
        val surfaceFallback = dynamicSurfaceColor(
            alpha = if (isDark) 0.94f else 0.96f,
            tintFraction = 0.08f,
        )
        val borderStroke = BorderStroke(
            1.dp,
            dynamicBorderColor(
                alpha = if (isDark) 0.40f else 0.45f,
                tintFraction = 0.25f,
            ),
        )
        val glassTintOpacity = if (isDark) 82 else 88

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = colorScheme.scrim.copy(alpha = 0.4f),
                    spotColor = colorScheme.scrim.copy(alpha = 0.5f),
                ),
            cornerRadiusDp = 20.dp,
            fallbackColor = surfaceFallback,
            border = borderStroke,
            tintOpacity = glassTintOpacity,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header with icon, title and version badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.inapp_update_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = info.displayVersion,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                        )
                    }
                }

                // Changelog ("O que há de novo")
                if (rawChangelog.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.surfaceContainerHighest.copy(alpha = if (isDark) 0.75f else 0.85f))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.inapp_update_whats_new),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = formattedChangelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                // Download Progress indicator if currently downloading
                when (val status = downloadStatus) {
                    is DownloadStatus.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_download_update),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                    is DownloadStatus.InstallingRoot -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.update_installing_root),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is DownloadStatus.Failed -> {
                        Text(
                            text = "${stringResource(R.string.update_download_failed)}: ${status.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    }
                    else -> {}
                }

                // Action buttons: "Ignorar" and "Instalar"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            UpdateManager.ignoreUpdate(context, info.tagName)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.inapp_update_action_ignore),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    val isDownloading = downloadStatus is DownloadStatus.Downloading
                    val isInstalling = downloadStatus is DownloadStatus.InstallingRoot

                    Button(
                        onClick = {
                            when (val status = downloadStatus) {
                                is DownloadStatus.Finished -> {
                                    val uri = status.fileUri
                                    if (uri != null) {
                                        UpdateManager.installUpdate(context, uri, RootInstaller.isRootAvailable())
                                    } else {
                                        UpdateManager.downloadUpdate(context, info)
                                    }
                                }
                                is DownloadStatus.Downloading, is DownloadStatus.InstallingRoot -> {
                                    // Busy, do nothing
                                }
                                else -> {
                                    UpdateManager.downloadUpdate(context, info)
                                }
                            }
                        },
                        enabled = !isDownloading && !isInstalling,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = when (downloadStatus) {
                                is DownloadStatus.Finished -> Icons.Filled.SystemUpdate
                                else -> Icons.Filled.Download
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (downloadStatus) {
                                is DownloadStatus.Finished -> stringResource(R.string.action_install)
                                is DownloadStatus.Failed -> stringResource(R.string.action_retry)
                                is DownloadStatus.Downloading -> "${(downloadStatus as DownloadStatus.Downloading).progressPercent}%"
                                else -> stringResource(R.string.action_install)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
