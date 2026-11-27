package com.gugachat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.core.storage.LocalMediaPathResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.RpStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.AppColors
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineDeleteConfirmStrip
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuLabeledSliderRow
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuRemovableChip
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuSubpageTopBar
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEndingRecord
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.WorldbookEngine
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpStageDesignSection(
    rp: RpConfig,
    modifier: Modifier = Modifier,
    onUpdateVisualTheme: ((RpVisualTheme) -> RpVisualTheme) -> Unit,
    onResetVisualTheme: () -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onApplyStatusWidgetTemplate: (String) -> Unit,
    onAddRegexScript: () -> Unit,
    onEditRegexScript: (RpRegexScript) -> Unit,
    onImportRegexJson: () -> Unit,
    onToggleRegexScript: (String) -> Unit,
    onDeleteRegexScript: (String) -> Unit,
    onApplyRegexPreset: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val vt = rp.visualTheme

    var dialogOpacity by remember(vt.dialogOpacity) {
        mutableFloatStateOf(vt.dialogOpacity.coerceIn(0.45f, 1.0f))
    }
    var backdropDim by remember(vt.backdropDim) {
        mutableFloatStateOf(vt.backdropDim.coerceIn(0.0f, 0.75f))
    }
    var fontSizeSp by remember(vt.fontSizeSp) {
        mutableFloatStateOf(vt.fontSizeSp.coerceIn(13f, 20f))
    }
    var spriteHeightRatio by remember(vt.spriteHeightRatio) {
        mutableFloatStateOf(vt.spriteHeightRatio.coerceIn(0.30f, 0.58f))
    }

    var htmlExpanded by remember { mutableStateOf(false) }
    var regexExpanded by remember { mutableStateOf(false) }
    var confirmDeleteRegexId by remember { mutableStateOf<String?>(null) }

    var htmlDraft by remember(vt.customStatusHtml) { mutableStateOf(vt.customStatusHtml) }
    var cssDraft by remember(vt.customCss) { mutableStateOf(vt.customCss) }
    val hasFrontendChanges = htmlDraft != vt.customStatusHtml || cssDraft != vt.customCss

    var debouncedHtml by remember { mutableStateOf(htmlDraft) }
    var debouncedCss by remember { mutableStateOf(cssDraft) }
    LaunchedEffect(htmlDraft, cssDraft) {
        if (htmlDraft == debouncedHtml && cssDraft == debouncedCss) return@LaunchedEffect
        delay(220L)
        debouncedHtml = htmlDraft
        debouncedCss = cssDraft
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.outline,
    )
    val monoStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    val spriteLayoutKeys = remember { listOf("stage", "portrait_card", "hidden") }
    val spriteLayoutLabels = remember(strings) {
        listOf(strings.rp.spriteLayoutStage, strings.rp.spriteLayoutCard, strings.rp.spriteLayoutHidden)
    }

    // Live preview visualTheme reflecting real-time slider dragging
    val livePreviewTheme = vt.copy(
        dialogOpacity = dialogOpacity,
        backdropDim = backdropDim,
        fontSizeSp = fontSizeSp,
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Sticky Top 132dp Live Mini Stage Preview Window (WYSIWYG)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            RpLiveMiniStagePreview(
                rp = rp,
                previewTheme = livePreviewTheme,
                isZh = isZh,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Scrollable Style Controls + Collapsed Technical Accordions below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuSectionHeader(
                title = strings.rp.visualThemeTitle,
                trailing = {
                    GuButton(
                        onClick = onResetVisualTheme,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.heightIn(min = 32.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.5.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.resetThemeBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(strings.rp.dialogStyleLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RP_DIALOG_STYLE_KEYS, key = { it }) { key ->
                        val isSelected = vt.dialogStyle.equals(key, ignoreCase = true)
                        val label = strings.rp.dialogStyles[key] ?: key
                        RpMiniDialogStyleCard(
                            styleKey = key,
                            label = label,
                            selected = isSelected,
                            onClick = { onUpdateVisualTheme { it.copy(dialogStyle = key) } },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.rp.spriteLayoutLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                GuSegmentedControl(
                    items = spriteLayoutLabels,
                    selectedIndex = spriteLayoutKeys.indexOfFirst { it.equals(vt.spriteLayout, ignoreCase = true) }.coerceAtLeast(0),
                    onSelect = { idx -> onUpdateVisualTheme { it.copy(spriteLayout = spriteLayoutKeys[idx]) } },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                val activeSwatch = RP_ACCENT_SWATCHES.firstOrNull {
                    it.hex.equals(vt.accentHex.trim(), ignoreCase = true)
                } ?: RP_ACCENT_SWATCHES.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.rp.accentColorLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    GuBadge(if (isZh) activeSwatch.zhName else activeSwatch.enName, variant = GuBadgeVariant.NEUTRAL)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RP_ACCENT_SWATCHES.forEach { swatch ->
                        val isSelected = vt.accentHex.trim().equals(swatch.hex, ignoreCase = true)
                        val fillColor = swatch.color ?: c.primary
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(fillColor)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) c.ink else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { onUpdateVisualTheme { it.copy(accentHex = swatch.hex) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }

            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.rp.dialogOpacityLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        GuBadge("${(dialogOpacity * 100f).roundToInt()}%", variant = GuBadgeVariant.BLUE)
                    }
                    Slider(
                        value = dialogOpacity,
                        onValueChange = { dialogOpacity = it.coerceIn(0.45f, 1.0f) },
                        onValueChangeFinished = {
                            val committed = dialogOpacity.coerceIn(0.45f, 1.0f)
                            onUpdateVisualTheme { it.copy(dialogOpacity = committed) }
                        },
                        valueRange = 0.45f..1.0f,
                        colors = sliderColors,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.rp.backdropDimLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        GuBadge("${(backdropDim * 100f).roundToInt()}%", variant = GuBadgeVariant.NEUTRAL)
                    }
                    Slider(
                        value = backdropDim,
                        onValueChange = { backdropDim = it.coerceIn(0.0f, 0.75f) },
                        onValueChangeFinished = {
                            val committed = backdropDim.coerceIn(0.0f, 0.75f)
                            onUpdateVisualTheme { it.copy(backdropDim = committed) }
                        },
                        valueRange = 0.0f..0.75f,
                        colors = sliderColors,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.rp.fontSizeLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        GuBadge("${String.format(Locale.US, "%.1f", fontSizeSp)} sp", variant = GuBadgeVariant.BLUE)
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = ((it * 2f).roundToInt() / 2f).coerceIn(13f, 20f) },
                        onValueChangeFinished = {
                            val committed = ((fontSizeSp * 2f).roundToInt() / 2f).coerceIn(13f, 20f)
                            onUpdateVisualTheme { it.copy(fontSizeSp = committed) }
                        },
                        valueRange = 13f..20f,
                        colors = sliderColors,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.rp.spriteHeightLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        GuBadge("${(spriteHeightRatio * 100f).roundToInt()}%", variant = GuBadgeVariant.NEUTRAL)
                    }
                    Slider(
                        value = spriteHeightRatio,
                        onValueChange = { spriteHeightRatio = it.coerceIn(0.30f, 0.58f) },
                        onValueChangeFinished = {
                            val committed = spriteHeightRatio.coerceIn(0.30f, 0.58f)
                            onUpdateVisualTheme { it.copy(spriteHeightRatio = committed) }
                        },
                        valueRange = 0.30f..0.58f,
                        colors = sliderColors,
                    )
                }

                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    GuSwitchRow(
                        label = strings.rp.useSerifToggle,
                        checked = vt.useSerif,
                        onCheckedChange = { v -> onUpdateVisualTheme { it.copy(useSerif = v) } },
                    )
                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                    GuSwitchRow(
                        label = strings.rp.showNameplateToggle,
                        checked = vt.showNameplate,
                        onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showNameplate = v) } },
                    )
                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                    GuSwitchRow(
                        label = strings.rp.showDeltasToggle,
                        checked = vt.showTurnDeltas,
                        onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showTurnDeltas = v) } },
                    )
                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                    GuSwitchRow(
                        label = strings.rp.enableHtmlCardsToggle,
                        checked = rp.enableHtmlCards,
                        onCheckedChange = { v -> onUpdateConfig { it.copy(enableHtmlCards = v) } },
                    )
                }
            }

            // Collapsed Accordion 1: HTML Status Widget Source & Templates
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { htmlExpanded = !htmlExpanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.Code,
                        tint = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(strings.rp.statusWidgetTitle, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        GuMetaText(
                            text = if (vt.showCustomStatusWidget) (if (isZh) "已启用自定义状态面板" else "Custom status widget enabled")
                            else (if (isZh) "高级 · 自定义 HTML/CSS 状态面板源码" else "Advanced · Custom HTML/CSS status widget"),
                        )
                    }
                    if (hasFrontendChanges) {
                        GuBadge(if (isZh) "未应用" else "Unsaved", variant = GuBadgeVariant.AMBER)
                    }
                    Icon(
                        imageVector = if (htmlExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = c.muted,
                    )
                }

                AnimatedVisibility(visible = htmlExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuSwitchRow(
                            label = strings.rp.showStatusWidgetToggle,
                            checked = vt.showCustomStatusWidget,
                            onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showCustomStatusWidget = v) } },
                        )
                        Text(strings.rp.statusWidgetTemplatesLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RpRegexEngine.STATUS_WIDGET_TEMPLATES.forEach { tpl ->
                                GuPill(
                                    label = if (isZh) tpl.zhName else tpl.enName,
                                    selected = htmlDraft.trim() == tpl.html.trim(),
                                    onClick = {
                                        htmlDraft = tpl.html
                                        debouncedHtml = tpl.html
                                        onApplyStatusWidgetTemplate(tpl.id)
                                    },
                                    useHoneySelected = true,
                                    modifier = Modifier.heightIn(min = 34.dp),
                                )
                            }
                        }
                        CompositionLocalProvider(LocalTextStyle provides monoStyle) {
                            GuTextField(
                                value = htmlDraft,
                                onValueChange = { htmlDraft = it },
                                label = strings.rp.statusWidgetHtmlLabel,
                                placeholder = "<div>{{scene}} · HP {{hp}}/{{maxHp}}</div>",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 7,
                            )
                            GuTextField(
                                value = cssDraft,
                                onValueChange = { cssDraft = it },
                                label = strings.rp.customCssLabel,
                                placeholder = ".my-card { border-radius: 14px; }",
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                        }
                        GuButton(
                            onClick = {
                                debouncedHtml = htmlDraft
                                debouncedCss = cssDraft
                                onUpdateVisualTheme { it.copy(customStatusHtml = htmlDraft, customCss = cssDraft) }
                            },
                            enabled = hasFrontendChanges,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.rp.saveFrontendBtn, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (htmlDraft.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = c.surfaceSoft,
                            ) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    RpHtmlCardSandbox(
                                        htmlFragment = debouncedHtml.ifBlank { htmlDraft },
                                        rp = rp,
                                        customCss = debouncedCss,
                                        minHeightDp = 56,
                                        maxHeightDp = 220,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsed Accordion 2: SillyTavern Regex Scripts
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { regexExpanded = !regexExpanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.Tune,
                        tint = c.tagInk,
                        backgroundColor = c.tag,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(strings.rp.regexScriptsTitle, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        GuMetaText(
                            text = if (isZh) "高级 · 已安装 ${rp.regexScripts.size} 条酒馆正则美化脚本" else "Advanced · ${rp.regexScripts.size} Tavern regex scripts",
                        )
                    }
                    GuBadge("${rp.regexScripts.size}", variant = GuBadgeVariant.BLUE)
                    Icon(
                        imageVector = if (regexExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = c.muted,
                    )
                }

                AnimatedVisibility(visible = regexExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuButton(
                                onClick = onAddRegexScript,
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                                modifier = Modifier.heightIn(min = 34.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stripLeadingPlus(strings.rp.regexAddBtn), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            GuButton(
                                onClick = onImportRegexJson,
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.heightIn(min = 34.dp),
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isZh) "导入 JSON" else "Import JSON", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Built-in regex presets
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RpRegexEngine.REGEX_PRESETS.forEach { preset ->
                                val alreadyInstalled = rp.regexScripts.any {
                                    it.id == preset.script.id ||
                                        it.name.equals(preset.script.name, ignoreCase = true) ||
                                        it.name.equals(preset.enName, ignoreCase = true)
                                }
                                GuPill(
                                    label = if (isZh) preset.zhName else preset.enName,
                                    selected = alreadyInstalled,
                                    onClick = { onApplyRegexPreset(preset.id) },
                                    useHoneySelected = true,
                                    modifier = Modifier.heightIn(min = 32.dp),
                                )
                            }
                        }

                        if (rp.regexScripts.isEmpty()) {
                            GuEmptyState(title = strings.rp.regexEmpty, icon = Icons.Default.Tune)
                        } else {
                            GuGroupedCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = c.surfaceSoft,
                                cornerRadius = 12.dp,
                            ) {
                                rp.regexScripts.forEachIndexed { idx, script ->
                                    val patternPreview = remember(script.findRegex, script.flags) {
                                        RpRegexEngine.formatRegexLiteral(script.findRegex, script.flags)
                                    }
                                    val confirmDel = confirmDeleteRegexId == script.id

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = script.name,
                                                    color = if (script.enabled) c.ink else c.muted,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                GuMetaText(
                                                    text = "${localizeRegexPlacement(script.placement, strings.rp)} · $patternPreview",
                                                )
                                            }
                                            Switch(
                                                checked = script.enabled,
                                                onCheckedChange = { onToggleRegexScript(script.id) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = c.primary,
                                                    uncheckedThumbColor = c.muted,
                                                    uncheckedTrackColor = c.surface,
                                                ),
                                            )
                                        }
                                        if (confirmDel) {
                                            RpInlineDeleteConfirmStrip(
                                                prompt = strings.rp.regexDeleteConfirm.fmt("name" to script.name),
                                                confirmText = strings.common.delete,
                                                cancelText = strings.common.cancel,
                                                onConfirm = {
                                                    confirmDeleteRegexId = null
                                                    onDeleteRegexScript(script.id)
                                                },
                                                onCancel = { confirmDeleteRegexId = null },
                                            )
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                GuButton(
                                                    onClick = { onEditRegexScript(script) },
                                                    compact = true,
                                                    variant = GuButtonVariant.SECONDARY,
                                                    modifier = Modifier.heightIn(min = 32.dp),
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(strings.common.edit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                GuButton(
                                                    onClick = { confirmDeleteRegexId = script.id },
                                                    compact = true,
                                                    variant = GuButtonVariant.SECONDARY,
                                                    modifier = Modifier.heightIn(min = 32.dp),
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (idx < rp.regexScripts.lastIndex) {
                                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RpLiveMiniStagePreview(
    rp: RpConfig,
    previewTheme: RpVisualTheme,
    isZh: Boolean,
    modifier: Modifier = Modifier,
) {
    val appTheme = LocalAppTheme.current
    val customAccent = remember(previewTheme.accentHex) { parseOptionalHexColor(previewTheme.accentHex) }
    val stageAccent = customAccent ?: Color(0xFFF5C563)
    val skin = remember(
        previewTheme.dialogStyle,
        previewTheme.dialogOpacity,
        previewTheme.showNameplate,
        stageAccent,
        customAccent,
        appTheme.isDark,
    ) {
        resolveDialogSkin(previewTheme, stageAccent, customAccent, appTheme.isDark)
    }
    val sampleNpc = rp.cast.firstOrNull { !it.isPlayer }
    val speakerName = sampleNpc?.name ?: if (isZh) "星见琉璃" else "Ruri"
    val sampleLine = if (isZh) {
        "“夜风穿过回廊，你掌心的温度让我短暂忘记了外面的风雨……”"
    } else {
        "\"The night wind drifts across the gallery—for a moment, the storm fades.\""
    }
    val previewFontSp = (previewTheme.fontSizeSp * 0.78f).coerceIn(10.5f, 15.5f)

    Box(modifier = modifier) {
        TheaterStageBackdrop(
            rp = rp,
            stageAccent = stageAccent,
            activeSlot = "center",
            backdropDim = previewTheme.backdropDim,
            dimForReading = true,
            modifier = Modifier.fillMaxSize(),
        )

        GuMetaText(
            text = if (isZh) "实时舞台预览" else "Live Stage Preview",
            color = Color(0xCCFFF4DC),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (previewTheme.showNameplate) {
                Surface(
                    shape = skin.nameplateShape,
                    color = skin.nameplateBg,
                    border = BorderStroke(0.6.dp, skin.nameplateBorder),
                ) {
                    Text(
                        text = "${skin.nameplateDecorPrefix}$speakerName${skin.nameplateDecorSuffix}",
                        color = skin.nameplateText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = skin.nameplateFontFamily,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Surface(
                shape = skin.boxShape,
                color = skin.boxBg,
                border = skin.boxBorder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val topAccent = skin.topStripColor ?: skin.innerTopHighlight
                    topAccent?.let { col ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(col),
                        )
                    }
                    Text(
                        text = sampleLine,
                        color = skin.bodyText,
                        fontSize = previewFontSp.sp,
                        lineHeight = (previewFontSp * 1.45f).sp,
                        fontFamily = if (previewTheme.useSerif) FontFamily.Serif else FontFamily.Default,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 4: Regex Script Editor + Built-in Live Tester ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpRegexEditorSubpage(
    rp: RpConfig,
    initial: RpRegexScript,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (RpRegexScript) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var findRegex by remember(initial.id) { mutableStateOf(initial.findRegex) }
    var replaceString by remember(initial.id) { mutableStateOf(initial.replaceString) }
    var flags by remember(initial.id) { mutableStateOf(initial.flags.ifBlank { "gms" }) }
    var placement by remember(initial.id) { mutableStateOf(initial.placement.ifBlank { "ai_output" }) }
    var markdownOnly by remember(initial.id) { mutableStateOf(initial.markdownOnly) }
    var testInput by remember { mutableStateOf("") }

    val regexSyntaxError = remember(findRegex, flags) {
        if (findRegex.isBlank()) {
            null
        } else {
            try {
                val (pattern, effectiveFlags) = RpRegexEngine.parseRegexLiteral(findRegex, flags)
                val opts = buildSet {
                    if ('i' in effectiveFlags.lowercase()) add(RegexOption.IGNORE_CASE)
                    if ('m' in effectiveFlags.lowercase()) add(RegexOption.MULTILINE)
                    if ('s' in effectiveFlags.lowercase()) add(RegexOption.DOT_MATCHES_ALL)
                }
                Regex(pattern, opts)
                null
            } catch (e: Throwable) {
                e.message ?: if (isZh) "正则表达式语法无效" else "Invalid regular expression"
            }
        }
    }

    val draftScript = initial.copy(
        id = initial.id.ifBlank { UUID.randomUUID().toString() },
        name = name.trim(),
        findRegex = findRegex,
        replaceString = replaceString,
        flags = flags.trim().ifEmpty { "gms" },
        placement = placement,
        markdownOnly = markdownOnly,
    )
    val testOutput = remember(testInput, draftScript, rp, regexSyntaxError) {
        if (testInput.isBlank() || findRegex.isBlank() || regexSyntaxError != null) {
            ""
        } else {
            RpRegexEngine.applyScripts(
                text = testInput,
                scripts = listOf(draftScript.copy(enabled = true)),
                placement = "both",
                forDisplay = true,
                rp = rp,
            )
        }
    }
    val testHtml = remember(testOutput) {
        if (testOutput.isBlank()) ""
        else RpRegexEngine.splitMarkdownAndHtml(testOutput, enableHtmlCards = true)
            .filter { it.isHtml }
            .joinToString("\n") { it.content }
    }

    val monoStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    val canSave = name.isNotBlank() && findRegex.isNotBlank() && regexSyntaxError == null

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回舞台" else "Stage",
            title = if (isNew) stripLeadingPlus(strings.rp.regexAddBtn) else strings.common.edit,
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = { onSave(draftScript) },
                    enabled = canSave,
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuTextField(
                value = name,
                onValueChange = { name = it },
                label = strings.rp.regexNameLabel,
                placeholder = if (isZh) "例如：状态栏玻璃卡片" else "e.g. Glass Status Card",
            )

            CompositionLocalProvider(LocalTextStyle provides monoStyle) {
                GuTextField(
                    value = findRegex,
                    onValueChange = { findRegex = it },
                    label = strings.rp.regexFindLabel,
                    placeholder = "<status>([\\s\\S]*?)</status>",
                    isError = regexSyntaxError != null,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                )
            }
            if (regexSyntaxError != null) {
                Text(text = regexSyntaxError, color = c.danger, fontSize = 11.5.sp)
            }

            CompositionLocalProvider(LocalTextStyle provides monoStyle) {
                GuTextField(
                    value = replaceString,
                    onValueChange = { replaceString = it },
                    label = strings.rp.regexReplaceLabel,
                    placeholder = "<div class=\"card\">$1</div>",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 7,
                )
            }

            GuTextField(
                value = flags,
                onValueChange = { raw -> flags = raw.filter { ch -> ch in "gimsuyGIMSUY" }.lowercase() },
                label = strings.rp.regexFlagsLabel,
                placeholder = "gms",
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.rp.regexPlacementLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "ai_output" to strings.rp.regexPlacementAi,
                        "user_input" to strings.rp.regexPlacementUser,
                        "both" to strings.rp.regexPlacementBoth,
                    ).forEach { (key, label) ->
                        GuPill(
                            label = label,
                            selected = placement.equals(key, ignoreCase = true),
                            onClick = { placement = key },
                            modifier = Modifier.heightIn(min = 34.dp),
                        )
                    }
                }
            }

            GuSwitchRow(
                label = strings.rp.regexDisplayOnlyToggle,
                checked = markdownOnly,
                onCheckedChange = { markdownOnly = it },
            )

            // Built-in Live Regex Testing Zone
            GuSectionHeader(title = strings.rp.regexTestTitle)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuPill(
                        label = if (isZh) "示例: <status>" else "Sample: <status>",
                        selected = false,
                        onClick = {
                            testInput = if (isZh) {
                                "<status>\n心情: 开心\n地点: 澄心亭\n好感: +5\n</status>"
                            } else {
                                "<status>\nMood: Happy\nLocation: Pavilion\nAffinity: +5\n</status>"
                            }
                        },
                        modifier = Modifier.heightIn(min = 32.dp),
                    )
                    GuPill(
                        label = if (isZh) "示例: <thought>" else "Sample: <thought>",
                        selected = false,
                        onClick = {
                            testInput = if (isZh) {
                                "<thought>原来她早就察觉了今夜的局势……</thought>"
                            } else {
                                "<thought>She noticed the shift in the room long before anyone else...</thought>"
                            }
                        },
                        modifier = Modifier.heightIn(min = 32.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    placeholder = strings.rp.regexTestInputPlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                )
                if (testInput.isNotBlank() && findRegex.isNotBlank() && regexSyntaxError == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(strings.rp.regexTestOutputLabel, color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = c.surfaceSoft,
                    ) {
                        Text(
                            text = testOutput.ifBlank { if (isZh) "（匹配后为空）" else "(Empty after replacement)" },
                            color = c.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    if (testHtml.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        RpHtmlCardSandbox(
                            htmlFragment = testHtml,
                            rp = rp,
                            minHeightDp = 56,
                            maxHeightDp = 200,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ==================== Shared Helpers ====================
