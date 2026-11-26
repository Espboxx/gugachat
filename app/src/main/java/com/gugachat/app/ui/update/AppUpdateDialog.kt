package com.gugachat.app.ui.update

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.BuildConfig
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppUpdateInfo
import com.gugachat.app.data.api.AppUpdateService
import com.gugachat.app.data.api.UpdateDownloadState

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    onConfirmUpdate: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = c.surfaceElevated,
            border = BorderStroke(theme.borderWidth, c.outline.copy(alpha = 0.85f)),
            shadowElevation = if (theme.isDark) 12.dp else 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.SystemUpdate,
                        tint = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                        size = 44.dp,
                        iconSize = 22.dp,
                        cornerRadius = 14.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = strings.update.dialogTitle,
                            color = c.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(
                                text = strings.update.versionCompare.fmt(
                                    "current" to AppUpdateService.normalizeVersion(BuildConfig.VERSION_NAME),
                                    "latest" to info.versionName,
                                ),
                                variant = GuBadgeVariant.BLUE,
                            )
                            if (info.apkSizeBytes > 0L) {
                                GuBadge(
                                    text = strings.update.apkSizeBadge.fmt(
                                        "size" to AppUpdateService.formatBytes(info.apkSizeBytes),
                                    ),
                                    variant = GuBadgeVariant.NEUTRAL,
                                )
                            }
                        }
                    }
                }

                // Release Notes Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = strings.update.releaseNotesLabel,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        )
                        HorizontalDivider(
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.72f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp, max = 210.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = info.releaseNotes.ifBlank { strings.update.noReleaseNotes },
                                color = c.ink,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }

                // Download progress / status banner
                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        val hasTotal = downloadState.totalBytes > 0L
                        val progress = if (hasTotal) {
                            (downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val percent = (progress * 100).toInt()
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (hasTotal) {
                                        strings.update.downloadingProgress.fmt(
                                            "downloaded" to AppUpdateService.formatBytes(downloadState.downloadedBytes),
                                            "total" to AppUpdateService.formatBytes(downloadState.totalBytes),
                                            "percent" to percent,
                                        )
                                    } else {
                                        strings.update.downloadingUnknownSize.fmt(
                                            "downloaded" to AppUpdateService.formatBytes(downloadState.downloadedBytes),
                                        )
                                    },
                                    color = c.primarySoftInk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (hasTotal) {
                                    Spacer(Modifier.width(8.dp))
                                    GuBadge(text = "$percent%", variant = GuBadgeVariant.BLUE)
                                }
                            }
                            if (hasTotal) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = c.primary,
                                    trackColor = c.surfaceSoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = c.primary,
                                    trackColor = c.surfaceSoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (downloadState.needsInstallPermission) c.warningBg else c.successBg,
                            border = BorderStroke(
                                theme.borderWidth,
                                (if (downloadState.needsInstallPermission) c.warning else c.success).copy(alpha = 0.42f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (downloadState.needsInstallPermission) {
                                    strings.update.installPermHint
                                } else {
                                    strings.update.downloadReadyHint
                                },
                                color = if (downloadState.needsInstallPermission) c.warning else c.success,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp,
                                modifier = Modifier.padding(11.dp),
                            )
                        }
                    }

                    is UpdateDownloadState.Failed -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.dangerBg,
                            border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.42f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = strings.update.downloadFailedPrefix.fmt("error" to downloadState.message),
                                color = c.danger,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp,
                                modifier = Modifier.padding(11.dp),
                            )
                        }
                    }

                    UpdateDownloadState.Idle -> Unit
                }

                // Actions: 1. Confirm Update / Install  |  2. Manual Download Page + 3. Cancel
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val primaryLabel = when (downloadState) {
                        is UpdateDownloadState.ReadyToInstall -> strings.update.installNowBtn
                        is UpdateDownloadState.Failed -> strings.update.retryDownloadBtn
                        else -> strings.update.confirmUpdateBtn
                    }
                    val primaryIcon = when (downloadState) {
                        is UpdateDownloadState.ReadyToInstall -> Icons.Default.InstallMobile
                        is UpdateDownloadState.Failed -> Icons.Default.Refresh
                        else -> Icons.Default.Download
                    }

                    GuButton(
                        onClick = onConfirmUpdate,
                        enabled = !isDownloading,
                        loading = isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(primaryLabel, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuButton(
                            onClick = onOpenDownloadPage,
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = strings.update.manualDownloadBtn,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        GuButton(
                            onClick = onDismiss,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = strings.update.cancelBtn,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
