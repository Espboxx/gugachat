package com.gugachat.app.ui.profile

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.BuildConfig
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPasswordField
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.GuTextInputDialog
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.theme.resolveAppTheme
import com.gugachat.app.core.util.FormatUtils
import com.gugachat.app.data.api.AppUpdateService
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.ui.chat.ExternalImportSheet
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderEditorSheet
import com.gugachat.app.ui.providers.ApiProviderImportSheet
import com.gugachat.app.ui.providers.ApiProviderManagerCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiProvidersManagerSheet(
    providers: List<com.gugachat.app.core.storage.ApiProviderConfig>,
    onImportClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (com.gugachat.app.core.storage.ApiProviderConfig) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.providers.managerTitle,
                subtitle = strings.providers.managerSubtitle,
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GuButton(
                            onClick = onImportClick,
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(strings.providers.importProviderBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = onAddClick,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (isZh) "添加" else strings.common.add, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ApiProviderManagerCard(
                    providers = providers,
                    onAddClick = onAddClick,
                    onEditClick = onEditClick,
                    onToggleEnabled = onToggleEnabled,
                    onDeleteClick = onDeleteClick,
                    showHeader = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsSheet(
    themeStyle: ThemeStyle,
    colorMode: ColorMode,
    onSelectThemeStyle: (ThemeStyle) -> Unit,
    onSelectColorMode: (ColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "主题与色彩" else "Theme & Colors",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.themeStyleLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ThemePreviewTile(
                            style = ThemeStyle.GUGUGA,
                            title = if (isZh) "咕咕嘎" else "Comic",
                            subtitle = if (isZh) "暖奶油 · 柔和圆角" else "Warm cream · Soft",
                            selected = themeStyle == ThemeStyle.GUGUGA,
                            isDark = theme.isDark,
                            onClick = { onSelectThemeStyle(ThemeStyle.GUGUGA) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemePreviewTile(
                            style = ThemeStyle.CLASSIC,
                            title = if (isZh) "极简" else "Classic",
                            subtitle = if (isZh) "中性灰 · 利落边框" else "Neutral · Crisp",
                            selected = themeStyle == ThemeStyle.CLASSIC,
                            isDark = theme.isDark,
                            onClick = { onSelectThemeStyle(ThemeStyle.CLASSIC) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.colorModeLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuSegmentedControl(
                        items = listOf(
                            strings.profile.colorSystem,
                            strings.profile.colorLight,
                            strings.profile.colorDark,
                        ),
                        selectedIndex = when (colorMode) {
                            ColorMode.SYSTEM -> 0
                            ColorMode.LIGHT -> 1
                            ColorMode.DARK -> 2
                        },
                        onSelect = { idx ->
                            onSelectColorMode(
                                when (idx) {
                                    1 -> ColorMode.LIGHT
                                    2 -> ColorMode.DARK
                                    else -> ColorMode.SYSTEM
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun ThemePreviewTile(
    style: ThemeStyle,
    title: String,
    subtitle: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val previewColors = remember(style, isDark) { resolveAppTheme(style, isDark).colors }

    GuCard(
        modifier = modifier,
        backgroundColor = if (selected) c.primarySoft.copy(alpha = if (isDark) 0.55f else 0.72f) else c.surface,
        borderColor = if (selected) c.primary else null,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.page),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.primary),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.accent),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = c.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = c.muted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaleAndLanguageSheet(
    uiScale: UiScalePreset,
    language: AppLanguage,
    onSelectUiScale: (UiScalePreset) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scalePresets = UiScalePreset.entries
    val scaleIndex = scalePresets.indexOf(uiScale).coerceAtLeast(0)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "界面缩放与语言" else "UI Scale & Language",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.profile.uiScaleLabel,
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(
                            text = "${(uiScale.scale * 100).toInt()}%",
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                    GuSegmentedControl(
                        items = listOf(
                            strings.profile.uiScaleCompact,
                            strings.profile.uiScaleComfortable,
                            strings.profile.uiScaleStandard,
                            strings.profile.uiScaleLarge,
                        ),
                        selectedIndex = scaleIndex,
                        onSelect = { idx ->
                            scalePresets.getOrNull(idx)?.let(onSelectUiScale)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.languageLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuSegmentedControl(
                        items = listOf(AppLanguage.ZH.displayName, AppLanguage.EN.displayName),
                        selectedIndex = if (language == AppLanguage.ZH) 0 else 1,
                        onSelect = { idx ->
                            onSelectLanguage(if (idx == 0) AppLanguage.ZH else AppLanguage.EN)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutUpdateSheet(
    updateChannel: UpdateChannel,
    isChecking: Boolean,
    hasUpdate: Boolean,
    latestVersion: String?,
    onCheckUpdate: () -> Unit,
    onSelectChannel: (UpdateChannel) -> Unit,
    onCopyQqGroup: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val updateChannels = UpdateChannel.entries
    val updateChannelIdx = updateChannels.indexOf(updateChannel).coerceAtLeast(0)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.update.aboutCardTitle,
                subtitle = strings.update.currentVersionLabel.fmt("version" to BuildConfig.VERSION_NAME),
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isChecking) strings.update.checkingUpdate else strings.update.checkUpdateBtn,
                        subtitle = if (hasUpdate && !latestVersion.isNullOrBlank()) {
                            "v${BuildConfig.VERSION_NAME} → v$latestVersion"
                        } else {
                            strings.update.currentVersionLabel.fmt("version" to BuildConfig.VERSION_NAME)
                        },
                        icon = Icons.Default.SystemUpdate,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = !isChecking,
                        showDivider = true,
                        onClick = if (isChecking) null else onCheckUpdate,
                        trailing = {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else if (hasUpdate && !latestVersion.isNullOrBlank()) {
                                GuBadge(
                                    text = "v$latestVersion",
                                    variant = GuBadgeVariant.AMBER,
                                )
                            } else {
                                GuBadge(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                        },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        GuSettingsRow(
                            title = strings.update.channelLabel,
                            subtitle = strings.update.channelSubtitle,
                            icon = Icons.Default.Public,
                            iconTint = c.success,
                            iconBg = c.successBg,
                            showDivider = false,
                        )
                        GuSegmentedControl(
                            items = listOf(
                                strings.update.channelAuto,
                                strings.update.channelCnMirror,
                                strings.update.channelGithub,
                            ),
                            selectedIndex = updateChannelIdx,
                            onSelect = { idx ->
                                updateChannels.getOrNull(idx)?.let(onSelectChannel)
                            },
                            compact = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 14.dp),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = c.border.copy(alpha = 0.65f),
                    )

                    GuSettingsRow(
                        title = strings.update.qqGroupLabel,
                        icon = Icons.Default.ContentCopy,
                        iconTint = c.muted,
                        iconBg = c.surfaceSoft,
                        valueText = strings.common.copy,
                        showDivider = true,
                        onClick = onCopyQqGroup,
                    )

                    GuSettingsRow(
                        title = strings.update.manualDownloadBtn,
                        icon = Icons.Default.OpenInBrowser,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = false,
                        onClick = onOpenDownloadPage,
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
