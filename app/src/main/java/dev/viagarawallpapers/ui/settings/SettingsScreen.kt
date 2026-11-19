// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagarawallpapers.BuildConfig
import dev.viagarawallpapers.R
import dev.viagarawallpapers.analytics.AnalyticsManager
import dev.viagarawallpapers.data.Prefs
import dev.viagarawallpapers.ui.theme.GlassCard
import dev.viagarawallpapers.ui.theme.LocalGlassThemeActive
import dev.viagarawallpapers.update.DownloadStatus
import dev.viagarawallpapers.update.RootInstaller
import dev.viagarawallpapers.update.UpdateInfo
import dev.viagarawallpapers.update.UpdateManager
import dev.viagarawallpapers.util.HapticUtil
import dev.viagarawallpapers.util.MarkdownUtil
import dev.viagarawallpapers.util.StorageStats
import dev.viagarawallpapers.util.StorageUtil
import dev.viagarawallpapers.wallpaper.DailyWallpaperManager
import dev.viagarawallpapers.wallpaper.DailyWallpaperTarget
import dev.viagarawallpapers.wallpaper.WallpaperSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isGlassActive = LocalGlassThemeActive.current
    val prefs = remember { Prefs(context) }
    val activeThemeId by prefs.activeThemeId.collectAsState(initial = "system_dynamic")
    val hapticsEnabled by prefs.hapticsEnabled.collectAsState(initial = true)
    val isAutoDailyEnabled by prefs.autoDailyWallpaperEnabled.collectAsState(initial = false)
    val dailyWallpaperTargetStr by prefs.dailyWallpaperTarget.collectAsState(initial = "both")
    val dailyWallpaperTarget = remember(dailyWallpaperTargetStr) { DailyWallpaperTarget.fromId(dailyWallpaperTargetStr) }
    val stopOnExternalChange by prefs.dailyWallpaperStopOnExternalChange.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    var storageStats by remember { mutableStateOf(StorageStats(0L, 0L)) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageStats = StorageUtil.getStorageStats(context)
    }

    val updateInfo by UpdateManager.updateAvailable.collectAsState()
    val isCheckingUpdates by UpdateManager.isCheckingUpdates.collectAsState()
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    var isBetaTester by remember { mutableStateOf(UpdateManager.isBetaTesterEnabled(context)) }
    var isAnalyticsEnabled by remember { mutableStateOf(AnalyticsManager.isEnabled(context)) }
    val wallpaperNotificationsFlow by prefs.wallpaperNotificationsEnabled.collectAsState(initial = true)
    var isWallpaperNotificationsEnabled by remember(wallpaperNotificationsFlow) { mutableStateOf(wallpaperNotificationsFlow) }

    LaunchedEffect(Unit) {
        UpdateManager.checkForUpdates(context, force = true)
    }

    val isRoot = remember { RootInstaller.isRootAvailable() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = if (isGlassActive) Color.Transparent else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Section: Appearance
            item {
                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                ) {
                    ThemeColorPaletteRow(
                        activeThemeId = activeThemeId,
                        hapticsEnabled = hapticsEnabled,
                        onSelectTheme = { id ->
                            scope.launch { prefs.setActiveThemeId(id) }
                        },
                    )
                }
            }

            // Section: Daily Wallpaper
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_section_daily_wallpaper),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                DailyWallpaperSettingsCard(
                    isEnabled = isAutoDailyEnabled,
                    target = dailyWallpaperTarget,
                    stopOnExternalChange = stopOnExternalChange,
                    onToggleEnabled = { enabled ->
                        scope.launch {
                            DailyWallpaperManager.setEnabled(context, enabled)
                        }
                    },
                    onSelectTarget = { newTarget ->
                        scope.launch {
                            prefs.setDailyWallpaperTarget(newTarget.id)
                        }
                    },
                    onToggleStopOnExternalChange = { stop ->
                        scope.launch {
                            prefs.setDailyWallpaperStopOnExternalChange(stop)
                        }
                    },
                    hapticsEnabled = hapticsEnabled,
                )
            }

            // Section: Storage
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_section_storage),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                StorageSettingsCard(
                    stats = storageStats,
                    isClearing = isClearingCache,
                    onClearCache = {
                        scope.launch {
                            isClearingCache = true
                            StorageUtil.clearCache(context)
                            storageStats = StorageUtil.getStorageStats(context)
                            isClearingCache = false
                            HapticUtil.tick(view, hapticsEnabled)
                            Toast.makeText(context, R.string.settings_storage_cache_cleared, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            // Section: Updates
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_updates),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )

                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary,
                        )
                    }
                }
            }

            item {
                UpdateStatusCard(
                    updateInfo = updateInfo,
                    isChecking = isCheckingUpdates,
                    downloadStatus = downloadStatus,
                    isRoot = isRoot,
                    onCheckAgain = {
                        UpdateManager.checkForUpdates(context, force = true)
                    },
                    onDownload = { info ->
                        UpdateManager.downloadUpdate(context, info)
                    },
                    onInstall = { uri, useRoot ->
                        UpdateManager.installUpdate(context, uri, useRoot)
                    },
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_beta_tester_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_beta_tester_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = isBetaTester,
                            onCheckedChange = { checked ->
                                isBetaTester = checked
                                UpdateManager.setBetaTesterEnabled(context, checked)
                                scope.launch {
                                    prefs.setBetaTester(checked)
                                }
                                HapticUtil.tick(view, hapticsEnabled)
                                UpdateManager.checkForUpdates(context, force = true)
                            },
                        )
                    }
                }
            }

            // Section: Privacy
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_section_privacy),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_analytics_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_analytics_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = isAnalyticsEnabled,
                            onCheckedChange = { checked ->
                                isAnalyticsEnabled = checked
                                AnalyticsManager.setEnabled(context, checked)
                                scope.launch {
                                    prefs.setAnonymousAnalyticsEnabled(checked)
                                }
                                HapticUtil.tick(view, hapticsEnabled)
                            },
                        )
                    }
                }
            }

            // Section: Notifications
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_section_notifications),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_wallpaper_notifications_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_wallpaper_notifications_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = isWallpaperNotificationsEnabled,
                            onCheckedChange = { checked ->
                                isWallpaperNotificationsEnabled = checked
                                scope.launch {
                                    prefs.setWallpaperNotificationsEnabled(checked)
                                }
                                if (checked || isAutoDailyEnabled) {
                                    WallpaperSyncWorker.schedule(context)
                                } else {
                                    WallpaperSyncWorker.cancel(context)
                                }
                                HapticUtil.tick(view, hapticsEnabled)
                            },
                        )
                    }
                }
            }

            // Section: About
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_section_about),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_app_name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )

                        if (BuildConfig.GIT_SHA.isNotBlank()) {
                            Text(
                                text = "Git SHA: ${BuildConfig.GIT_SHA.take(7)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }

                        val formattedDate = remember {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(BuildConfig.BUILD_TIME_MILLIS))
                        }
                        Text(
                            text = "Build: $formattedDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 14.dp,
                    onClick = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/S-Marcos-S/wallpapers-foss")
                            )
                            context.startActivity(intent)
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_source_code),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_source_code_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    updateInfo: UpdateInfo?,
    isChecking: Boolean,
    downloadStatus: DownloadStatus,
    isRoot: Boolean,
    onCheckAgain: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (android.net.Uri, Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var isChangelogExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (updateInfo != null && updateInfo.hasUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_available_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                        Text(
                            text = "${stringResource(R.string.update_version_label)}: ${updateInfo.displayVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (updateInfo.changelog.isNotBlank()) {
                    val formattedChangelog = remember(updateInfo.changelog, colorScheme) {
                        MarkdownUtil.formatMarkdownToAnnotated(updateInfo.changelog, colorScheme)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        if (isChangelogExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = formattedChangelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface,
                                )
                            }
                        } else {
                            Text(
                                text = formattedChangelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isChangelogExpanded = !isChangelogExpanded }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isChangelogExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(if (isChangelogExpanded) R.string.action_show_less else R.string.action_show_more),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary,
                            )
                        }
                    }
                }

                when (val status = downloadStatus) {
                    is DownloadStatus.Idle -> {
                        Button(
                            onClick = { onDownload(updateInfo) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_download_update),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    is DownloadStatus.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = "${status.progressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    is DownloadStatus.Finished -> {
                        val uri = status.fileUri
                        if (uri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isRoot) {
                                    Button(
                                        onClick = { onInstall(uri, true) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_install_root),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onInstall(uri, false) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = if (isRoot) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_install),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    is DownloadStatus.InstallingRoot -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
                        Button(
                            onClick = { onDownload(updateInfo) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isChecking) stringResource(R.string.update_checking) else stringResource(R.string.update_up_to_date),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${stringResource(R.string.update_current_version)}: v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = onCheckAgain,
                        enabled = !isChecking,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_check_updates),
                        )
                    }
                }
            }
        }
    }
}

private data class ThemeColorDotOption(
    val id: String,
    val nameRes: Int,
    val color: Color,
    val borderColor: Color,
    val checkmarkColor: Color,
)

@Composable
private fun ThemeColorPaletteRow(
    activeThemeId: String,
    hapticsEnabled: Boolean,
    onSelectTheme: (String) -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    val isDark = isSystemInDarkTheme()
    val options = remember(colorScheme, isDark) {
        listOf(
            ThemeColorDotOption(
                id = "system_dynamic",
                nameRes = R.string.theme_system_dynamic_title,
                color = colorScheme.primary,
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.5f),
                checkmarkColor = colorScheme.onPrimary,
            ),
            ThemeColorDotOption(
                id = "glass",
                nameRes = R.string.theme_glass_title,
                color = if (isDark) Color(0x667FD1E0) else Color(0x88A0D0E8),
                borderColor = Color.White.copy(alpha = 0.85f),
                checkmarkColor = Color.White,
            ),
            ThemeColorDotOption(
                id = "oled_black",
                nameRes = R.string.theme_oled_black_title,
                color = Color.Black,
                borderColor = Color(0xFF333333),
                checkmarkColor = Color(0xFFEDEDED),
            ),
            ThemeColorDotOption(
                id = "dark_modern",
                nameRes = R.string.theme_dark_modern_title,
                color = Color(0xFF1B2733),
                borderColor = Color(0xFF384755),
                checkmarkColor = Color(0xFF7FD1E0),
            ),
            ThemeColorDotOption(
                id = "light_clean",
                nameRes = R.string.theme_light_clean_title,
                color = Color(0xFFF3F6F8),
                borderColor = Color(0xFFB0BCC7),
                checkmarkColor = Color(0xFF10707F),
            ),
        )
    }

    val activeOption = options.firstOrNull { it.id == activeThemeId } ?: options.first()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(activeOption.nameRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = activeThemeId == option.id
                ThemeColorDot(
                    option = option,
                    isSelected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            HapticUtil.tick(view, hapticsEnabled)
                            onSelectTheme(option.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorDot(
    option: ThemeColorDotOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dotScale",
    )
    val ringSize by animateDpAsState(
        targetValue = if (isSelected) 36.dp else 26.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dotRingSize",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dotRingAlpha",
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ringAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .graphicsLayer { alpha = ringAlpha }
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                        shape = CircleShape,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(option.color)
                .border(1.dp, option.borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(150)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(option.nameRes),
                    tint = option.checkmarkColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun DailyWallpaperSettingsCard(
    isEnabled: Boolean,
    target: DailyWallpaperTarget,
    stopOnExternalChange: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectTarget: (DailyWallpaperTarget) -> Unit,
    onToggleStopOnExternalChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Main switch row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (isEnabled) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.daily_wallpaper_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.daily_wallpaper_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        HapticUtil.tick(view, hapticsEnabled)
                        onToggleEnabled(checked)
                    },
                )
            }

            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                    )

                    // Target screens selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.daily_wallpaper_target_title),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val options = listOf(
                                DailyWallpaperTarget.BOTH to R.string.daily_wallpaper_target_both,
                                DailyWallpaperTarget.HOME to R.string.daily_wallpaper_target_home,
                                DailyWallpaperTarget.LOCK to R.string.daily_wallpaper_target_lock,
                            )

                            options.forEach { (optionTarget, labelRes) ->
                                val isSelected = target == optionTarget
                                val bgColor by animateColorAsState(
                                    targetValue = if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                                    animationSpec = tween(150),
                                    label = "targetBgColor",
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                    animationSpec = tween(150),
                                    label = "targetTextColor",
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor)
                                        .clickable {
                                            if (!isSelected) {
                                                HapticUtil.tick(view, hapticsEnabled)
                                                onSelectTarget(optionTarget)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = textColor,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                    )

                    // Pause if changed externally switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.daily_wallpaper_stop_external_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.daily_wallpaper_stop_external_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = stopOnExternalChange,
                            onCheckedChange = { checked ->
                                HapticUtil.tick(view, hapticsEnabled)
                                onToggleStopOnExternalChange(checked)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSettingsCard(
    stats: StorageStats,
    isClearing: Boolean,
    onClearCache: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.settings_storage_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "${stringResource(R.string.settings_storage_cache_label)}: ${stats.formattedCacheSize} • ${stringResource(R.string.settings_storage_total_label)}: ${stats.formattedTotalSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onClearCache,
                enabled = !isClearing && stats.cacheSizeBytes > 0L,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.height(34.dp),
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_storage_clear_cache),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

