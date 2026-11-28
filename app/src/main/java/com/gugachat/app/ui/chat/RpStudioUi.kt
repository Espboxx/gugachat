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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.RpStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.AppColors
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuInlineDeleteConfirmStrip
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuRemovableChip
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuSubpageTopBar
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEndingRecord
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import java.util.UUID

internal val RP_WORLDBOOK_CATEGORIES = listOf(
    "character",
    "location",
    "item",
    "faction",
    "rule",
    "concept",
    "other",
)

internal val RP_ROLE_KEYS = listOf(
    "companion",
    "romance",
    "ally",
    "rival",
    "mentor",
    "antagonist",
    "passerby",
    "mystery",
)

internal val AFFINITY_BONDED_ROSE = Color(0xFFEC4899)

internal fun rpPlayStyleLabel(style: RpPlayStyle, rpStrings: RpStrings): String = when (style) {
    RpPlayStyle.chengguang -> rpStrings.styleChengguang
    RpPlayStyle.tavern -> rpStrings.styleTavern
    RpPlayStyle.trpg -> rpStrings.styleTrpg
}

internal fun affinityBadgeVariant(tierCode: String): GuBadgeVariant = when (tierCode) {
    "hostile" -> GuBadgeVariant.DANGER
    "cold", "acquaintance" -> GuBadgeVariant.NEUTRAL
    "friendly" -> GuBadgeVariant.EMERALD
    "trusted" -> GuBadgeVariant.BLUE
    "smitten", "bonded" -> GuBadgeVariant.HONEY
    else -> GuBadgeVariant.NEUTRAL
}

internal fun affinityBarColor(tierCode: String, c: AppColors): Color = when (tierCode) {
    "hostile" -> c.danger
    "cold" -> c.muted
    "acquaintance" -> c.primary.copy(alpha = 0.78f)
    "friendly" -> c.success
    "trusted" -> c.primary
    "smitten" -> c.accent
    "bonded" -> AFFINITY_BONDED_ROSE
    else -> c.primary
}

internal fun endingBadgeVariant(code: String): GuBadgeVariant = when (code.uppercase()) {
    "HE" -> GuBadgeVariant.EMERALD
    "TE" -> GuBadgeVariant.HONEY
    "BE" -> GuBadgeVariant.DANGER
    else -> GuBadgeVariant.BLUE
}

private val EXPORT_FILENAME_SANITIZE_RE = Regex("[\\\\/:*?\"<>|\\s]+")

internal fun sanitizeExportFileName(rawTitle: String, fallback: String): String {
    val clean = rawTitle.trim().replace(EXPORT_FILENAME_SANITIZE_RE, "_").trim('_').take(40)
    return clean.ifBlank { fallback }
}

internal val RP_ACT_PREFIX_RE = Regex(
    "^(第.{1,3}[幕回章节]|序幕|Act\\s+\\d+|Chapter\\s+\\d+|Prologue\\b)",
    RegexOption.IGNORE_CASE,
)

internal fun formatRpActChapter(actNumber: Int, chapterTitle: String, isZh: Boolean): String {
    val cleanTitle = chapterTitle.trim()
    val fallbackAct = if (isZh) "第${actNumber}幕" else "Act $actNumber"
    return when {
        cleanTitle.isEmpty() -> fallbackAct
        RP_ACT_PREFIX_RE.containsMatchIn(cleanTitle) -> cleanTitle
        else -> "$fallbackAct · $cleanTitle"
    }
}

internal fun stripLeadingPlus(label: String): String =
    label.trimStart('+', ' ', '＋')

internal fun localizeRpLoreCategory(category: String, loreCats: Map<String, String>, isZh: Boolean): String {
    val key = category.trim().lowercase()
    loreCats[key]?.takeIf { it.isNotBlank() }?.let { return it }
    return when (key) {
        "character" -> if (isZh) "人物" else "Character"
        "location" -> if (isZh) "地点" else "Location"
        "item" -> if (isZh) "道具" else "Item"
        "faction" -> if (isZh) "势力" else "Faction"
        "rule" -> if (isZh) "规则" else "Rule"
        "event" -> if (isZh) "事件" else "Event"
        "concept" -> if (isZh) "概念" else "Concept"
        "other" -> if (isZh) "其他" else "Other"
        else -> category
    }
}

internal fun localizeRpRole(role: String, isZh: Boolean): String {
    val trimmed = role.trim()
    return when (trimmed.lowercase()) {
        "protagonist", "player", "lead" -> if (isZh) "主角" else "Protagonist"
        "companion", "partner" -> if (isZh) "核心伙伴" else "Companion"
        "romance", "heroine", "love_interest" -> if (isZh) "攻略角色" else "Romance"
        "rival" -> if (isZh) "对手" else "Rival"
        "ally" -> if (isZh) "盟友" else "Ally"
        "mentor" -> if (isZh) "导师" else "Mentor"
        "antagonist", "villain" -> if (isZh) "反派" else "Antagonist"
        "mystery" -> if (isZh) "神秘人" else "Mystery"
        "passerby", "minor", "extra", "npc" -> if (isZh) "路人" else "Passerby"
        else -> trimmed
    }
}

internal data class RpAccentSwatch(
    val hex: String,
    val color: Color?,
    val zhName: String,
    val enName: String,
)

internal val RP_ACCENT_SWATCHES = listOf(
    RpAccentSwatch("", null, "默认", "Default"),
    RpAccentSwatch("#C84B5B", Color(0xFFC84B5B), "宫廷绯红", "Crimson"),
    RpAccentSwatch("#D99B38", Color(0xFFD99B38), "琥珀鎏金", "Gold"),
    RpAccentSwatch("#EC4899", Color(0xFFEC4899), "樱花粉", "Sakura"),
    RpAccentSwatch("#06B6D4", Color(0xFF06B6D4), "赛博青", "Cyan"),
    RpAccentSwatch("#6366F1", Color(0xFF6366F1), "靛蓝", "Indigo"),
    RpAccentSwatch("#10B981", Color(0xFF10B981), "翡翠玉", "Jade"),
)

internal val RP_DIALOG_STYLE_KEYS = listOf("glass", "classic_vn", "minimal", "cyber", "parchment")
internal val RP_STAGE_SLOT_KEYS = listOf("auto", "left", "center", "right")

internal data class RpPresetCoverVisual(
    val startColor: Color,
    val endColor: Color,
    val accentTint: Color,
    val icon: ImageVector,
)

internal fun resolveRpPresetCoverVisual(presetId: String): RpPresetCoverVisual = when (presetId.lowercase()) {
    "palace_intrigue" -> RpPresetCoverVisual(
        startColor = Color(0xFF6E1D34),
        endColor = Color(0xFF2B0E1A),
        accentTint = Color(0xFFF6D38A),
        icon = Icons.Default.EmojiEvents,
    )
    "modern_tavern" -> RpPresetCoverVisual(
        startColor = Color(0xFF2B2D63),
        endColor = Color(0xFF141736),
        accentTint = Color(0xFFF6B95E),
        icon = Icons.Default.AutoStories,
    )
    "fantasy_rpg" -> RpPresetCoverVisual(
        startColor = Color(0xFF5A3718),
        endColor = Color(0xFF251A10),
        accentTint = Color(0xFFFCD34D),
        icon = Icons.Default.Security,
    )
    "cyberpunk_noir" -> RpPresetCoverVisual(
        startColor = Color(0xFF0E4A60),
        endColor = Color(0xFF091B2C),
        accentTint = Color(0xFF67E8F9),
        icon = Icons.Default.Code,
    )
    "wuxia" -> RpPresetCoverVisual(
        startColor = Color(0xFF2F3D52),
        endColor = Color(0xFF141B28),
        accentTint = Color(0xFFE2E8F0),
        icon = Icons.Default.Landscape,
    )
    "steampunk_coc" -> RpPresetCoverVisual(
        startColor = Color(0xFF453224),
        endColor = Color(0xFF1C1612),
        accentTint = Color(0xFFD9B27C),
        icon = Icons.Default.Search,
    )
    "space_mercenary" -> RpPresetCoverVisual(
        startColor = Color(0xFF1B3A63),
        endColor = Color(0xFF0B1830),
        accentTint = Color(0xFF7DD3FC),
        icon = Icons.Default.Public,
    )
    "modern_romance" -> RpPresetCoverVisual(
        startColor = Color(0xFF832E55),
        endColor = Color(0xFF3A162D),
        accentTint = Color(0xFFFBCFE8),
        icon = Icons.Default.Favorite,
    )
    else -> RpPresetCoverVisual(
        startColor = Color(0xFF334155),
        endColor = Color(0xFF1E293B),
        accentTint = Color(0xFFCBD5E1),
        icon = Icons.Default.Theaters,
    )
}

internal fun localizeStageSlot(slot: String, rpStrings: RpStrings): String = when (slot.trim().lowercase()) {
    "left" -> rpStrings.slotLeft
    "center" -> rpStrings.slotCenter
    "right" -> rpStrings.slotRight
    else -> rpStrings.slotAuto
}

internal fun localizeRegexPlacement(placement: String, rpStrings: RpStrings): String = when (placement.trim().lowercase()) {
    "user_input" -> rpStrings.regexPlacementUser
    "both" -> rpStrings.regexPlacementBoth
    else -> rpStrings.regexPlacementAi
}

@Composable
internal fun RpArtProgressBanner(
    statusLabel: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    GuStatusBanner(
        message = statusLabel.ifBlank { strings.rp.aiGeneratingArt },
        tone = GuStatusTone.INFO,
        icon = Icons.Default.AutoAwesome,
        indeterminateProgress = true,
        modifier = modifier,
    )
}

@Composable
internal fun RpInlineDeleteConfirmStrip(
    prompt: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuInlineDeleteConfirmStrip(
        prompt = prompt,
        confirmText = confirmText,
        cancelText = cancelText,
        onConfirm = onConfirm,
        onCancel = onCancel,
        modifier = modifier,
    )
}


internal sealed interface StudioRoute {
    data object Main : StudioRoute
    data class CharacterDetail(val seedCharacter: RpCharacter, val isNew: Boolean) : StudioRoute
    data class LoreEditor(val entry: NovelLoreEntry?) : StudioRoute
    data class RegexEditor(val script: RpRegexScript, val isNew: Boolean) : StudioRoute
    data class CgViewer(val cgId: String) : StudioRoute
    data class ArtModelConfig(val returnRoute: StudioRoute = Main) : StudioRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpStudioSheet(
    rp: RpConfig,
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    saveSlots: List<RpSaveSlot>,
    initialTab: Int = 0,
    isStreaming: Boolean = false,
    isGeneratingArt: Boolean = false,
    artStatusLabel: String = "",
    onAdjustAffinity: (String, Int) -> Unit,
    onUpsertNpc: (RpCharacter) -> Unit,
    onDeleteNpc: (String) -> Unit,
    onBindNpcAvatar: (charId: String, uri: Uri) -> Unit,
    onBindNpcSprite: (charId: String, expressionKey: String?, uri: Uri) -> Unit,
    onClearNpcSprite: (charId: String, expressionKey: String?) -> Unit,
    onGenerateNpcSprite: (charId: String, expressionKey: String, customHint: String) -> Unit,
    onUpdateState: ((RpGameState) -> RpGameState) -> Unit,
    onBindSceneImage: (Uri) -> Unit,
    onClearSceneImage: () -> Unit,
    onGenerateSceneCg: (customHint: String, saveToGallery: Boolean) -> Unit,
    onAddCgFromUri: (title: String, caption: String, uri: Uri) -> Unit,
    onSaveCurrentSceneAsCg: (title: String) -> Unit,
    onApplyCgAsScene: (RpCgRecord) -> Unit,
    onDeleteCg: (String) -> Unit,
    onUpdateVisualTheme: ((RpVisualTheme) -> RpVisualTheme) -> Unit,
    onResetVisualTheme: () -> Unit,
    onUpsertRegexScript: (RpRegexScript) -> Unit,
    onToggleRegexScript: (String) -> Unit,
    onDeleteRegexScript: (String) -> Unit,
    onApplyRegexPreset: (String) -> Unit,
    onApplyStatusWidgetTemplate: (String) -> Unit,
    onAddLore: (category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) -> Unit,
    onUpdateLore: (NovelLoreEntry) -> Unit,
    onToggleLoreEnabled: (String) -> Unit,
    onToggleLoreAlwaysActive: (String) -> Unit,
    onDeleteLore: (String) -> Unit,
    onImportLorebookOrPreset: (Uri) -> Unit = {},
    onImportRegexJson: (Uri) -> Unit = {},
    onCreateSaveSlot: (name: String, note: String) -> Unit,
    onOverwriteSaveSlot: (String) -> Unit,
    onDeleteSaveSlot: (String) -> Unit,
    onLoadSaveSlot: (RpSaveSlot) -> Unit,
    onBranchSaveSlot: (RpSaveSlot) -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onSetPlayStyle: (RpPlayStyle) -> Unit,
    onExportCardJson: (Uri) -> Unit,
    onExportCardPng: (Uri) -> Unit,
    onDismiss: () -> Unit,
    boundCharacterCard: CharacterCardEntity? = null,
    personas: List<PersonaEntity> = emptyList(),
    boundPersonaId: String? = null,
    worldbooks: List<WorldbookEntity> = emptyList(),
    mountedWorldbookRefs: List<ConversationWorldbookCrossRef> = emptyList(),
    assetActions: RpStudioAssetActions? = null,
    params: ChatParams = ChatParams(),
    onUpdateMinOutput: ((minChars: Int?, autoContinue: Boolean) -> Unit)? = null,
    studioArtModelState: StudioArtModelUiState = StudioArtModelUiState(),
    studioArtModelActions: StudioArtModelActions? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val resolvedInitialTab = remember(initialTab) { initialTab.coerceIn(0, 3) }
    var tab by remember(resolvedInitialTab) { mutableIntStateOf(resolvedInitialTab) }
    var route by remember { mutableStateOf<StudioRoute>(StudioRoute.Main) }

    LaunchedEffect(studioArtModelState.showPicker) {
        if (studioArtModelState.showPicker && route !is StudioRoute.ArtModelConfig) {
            route = StudioRoute.ArtModelConfig(returnRoute = route)
        } else if (!studioArtModelState.showPicker) {
            val currentArtRoute = route as? StudioRoute.ArtModelConfig
            if (currentArtRoute != null) {
                route = currentArtRoute.returnRoute
            }
        }
    }

    // Photo / file launchers (live seamlessly without opening any nested Dialog)
    var avatarTargetCharId by remember { mutableStateOf<String?>(null) }
    val npcAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val charId = avatarTargetCharId
        avatarTargetCharId = null
        if (uri != null && !charId.isNullOrBlank()) {
            onBindNpcAvatar(charId, uri)
        }
    }
    var spritePickerTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val npcSpritePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = spritePickerTarget
        spritePickerTarget = null
        if (uri != null && target != null) {
            onBindNpcSprite(target.first, target.second, uri)
        }
    }
    val sceneImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onBindSceneImage(uri)
    }
    val cgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAddCgFromUri("", "", uri)
    }
    val exportCardJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportCardJson(uri)
    }
    val exportCardPngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) onExportCardPng(uri)
    }
    val importLoreOrPresetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportLorebookOrPreset(uri)
    }
    val importRegexJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportRegexJson(uri)
    }

    val tabTitles = remember(isZh) {
        if (isZh) listOf("🎭 角色", "📜 状态", "🌍 设定", "🎨 舞台")
        else listOf("🎭 Cast", "📜 State", "🌍 World", "🎨 Stage")
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GuModalBottomSheet(
        onDismissRequest = {
            studioArtModelActions?.onDismissPicker()
            onDismiss()
        },
        sheetState = sheetState,
        scrimColor = Color(0x8C060912),
    ) {
        BackHandler(enabled = route !is StudioRoute.Main) {
            val artRoute = route as? StudioRoute.ArtModelConfig
            if (artRoute != null) {
                studioArtModelActions?.onDismissPicker()
                route = artRoute.returnRoute
            } else {
                route = StudioRoute.Main
            }
        }
        // Ensure status bar clock/icons never turn dark-on-dark above the sheet
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !theme.isDark
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState is StudioRoute.Main) {
                        (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180)))
                            .togetherWith(slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(140)))
                    } else {
                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(140)))
                    }
                },
                label = "rp_studio_push_pop",
                modifier = Modifier.fillMaxSize(),
            ) { currentRoute ->
                when (currentRoute) {
                    StudioRoute.Main -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            GuSheetHeader(
                                title = if (isZh) "剧目手账" else strings.rp.studioTitle,
                                subtitle = rp.title,
                                onClose = onDismiss,
                                closeContentDescription = strings.common.close,
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(c.surface)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                GuSegmentedControl(
                                    items = tabTitles,
                                    selectedIndex = tab,
                                    onSelect = { tab = it },
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            AnimatedVisibility(visible = isGeneratingArt) {
                                RpArtProgressBanner(
                                    statusLabel = artStatusLabel,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 8.dp),
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            when (tab) {
                                0 -> RpCastSection(
                                    rp = rp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    boundCharacterCard = boundCharacterCard,
                                    personas = personas,
                                    boundPersonaId = boundPersonaId,
                                    assetActions = assetActions,
                                    onUpdatePlayer = { pName, pPersona ->
                                        onUpdateConfig {
                                            it.copy(
                                                playerName = pName.ifBlank { it.playerName },
                                                playerPersona = pPersona,
                                            )
                                        }
                                    },
                                    onAddNpc = {
                                        val blank = RpCharacter(
                                            id = UUID.randomUUID().toString(),
                                            name = "",
                                            role = "companion",
                                            mood = if (isZh) "平静" else "Calm",
                                            inScene = true,
                                        )
                                        route = StudioRoute.CharacterDetail(blank, isNew = true)
                                    },
                                    onOpenNpcDetail = { npc ->
                                        route = StudioRoute.CharacterDetail(npc, isNew = false)
                                    },
                                    onDeleteNpc = onDeleteNpc,
                                    onAdjustAffinity = onAdjustAffinity,
                                )

                                1 -> RpStateAndArchiveSection(
                                    rp = rp,
                                    saveSlots = saveSlots,
                                    isStreaming = isStreaming,
                                    isGeneratingArt = isGeneratingArt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    onUpdateState = onUpdateState,
                                    onCreateSaveSlot = onCreateSaveSlot,
                                    onLoadSaveSlot = { slot ->
                                        onLoadSaveSlot(slot)
                                        onDismiss()
                                    },
                                    onBranchSaveSlot = { slot ->
                                        onBranchSaveSlot(slot)
                                        onDismiss()
                                    },
                                    onOverwriteSaveSlot = onOverwriteSaveSlot,
                                    onDeleteSaveSlot = onDeleteSaveSlot,
                                    onGenerateSceneCg = { onGenerateSceneCg("", true) },
                                    onPickCgFromAlbum = {
                                        cgImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onSaveCurrentSceneAsCg = { onSaveCurrentSceneAsCg(rp.state.scene) },
                                    onOpenCgDetail = { cg -> route = StudioRoute.CgViewer(cg.id) },
                                    studioArtModelState = studioArtModelState,
                                    onConfigureArtModel = { studioArtModelActions?.onOpenPicker() },
                                )

                                2 -> RpWorldAndLoreSection(
                                    rp = rp,
                                    lore = lore,
                                    triggeredLoreIds = triggeredLoreIds,
                                    isGeneratingArt = isGeneratingArt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    worldbooks = worldbooks,
                                    mountedWorldbookRefs = mountedWorldbookRefs,
                                    assetActions = assetActions,
                                    onSetPlayStyle = onSetPlayStyle,
                                    onUpdateConfig = onUpdateConfig,
                                    onPickSceneImage = {
                                        sceneImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onClearSceneImage = onClearSceneImage,
                                    onGenerateSceneCg = { onGenerateSceneCg("", true) },
                                    onExportCardPng = {
                                        val base = sanitizeExportFileName(rp.title, "character")
                                        exportCardPngLauncher.launch("$base.png")
                                    },
                                    onExportCardJson = {
                                        val base = sanitizeExportFileName(rp.title, "character")
                                        exportCardJsonLauncher.launch("$base.json")
                                    },
                                    onAddLore = { route = StudioRoute.LoreEditor(null) },
                                    onEditLore = { entry -> route = StudioRoute.LoreEditor(entry) },
                                    onImportLorebookOrPreset = { importLoreOrPresetLauncher.launch(arrayOf("*/*")) },
                                    onToggleLoreEnabled = onToggleLoreEnabled,
                                    onToggleLoreAlwaysActive = onToggleLoreAlwaysActive,
                                    onDeleteLore = onDeleteLore,
                                    params = params,
                                    onUpdateMinOutput = onUpdateMinOutput,
                                    studioArtModelState = studioArtModelState,
                                    onConfigureArtModel = { studioArtModelActions?.onOpenPicker() },
                                )

                                else -> RpStageDesignSection(
                                    rp = rp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    onUpdateVisualTheme = onUpdateVisualTheme,
                                    onResetVisualTheme = onResetVisualTheme,
                                    onUpdateConfig = onUpdateConfig,
                                    onApplyStatusWidgetTemplate = onApplyStatusWidgetTemplate,
                                    onAddRegexScript = {
                                        val blank = RpRegexScript(
                                            id = UUID.randomUUID().toString(),
                                            name = "",
                                            enabled = true,
                                            findRegex = "",
                                            replaceString = "",
                                            placement = "ai_output",
                                            markdownOnly = true,
                                            flags = "gms",
                                        )
                                        route = StudioRoute.RegexEditor(blank, isNew = true)
                                    },
                                    onEditRegexScript = { script ->
                                        route = StudioRoute.RegexEditor(script, isNew = false)
                                    },
                                    onImportRegexJson = { importRegexJsonLauncher.launch(arrayOf("*/*")) },
                                    onToggleRegexScript = onToggleRegexScript,
                                    onDeleteRegexScript = onDeleteRegexScript,
                                    onApplyRegexPreset = onApplyRegexPreset,
                                )
                            }
                        }
                    }

                    is StudioRoute.CharacterDetail -> {
                        val liveNpc = rp.cast.firstOrNull { it.id == currentRoute.seedCharacter.id }
                            ?: currentRoute.seedCharacter
                        val isExistingInCast = rp.cast.any { it.id == liveNpc.id }
                        RpCharacterUnifiedSubpage(
                            rp = rp,
                            npc = liveNpc,
                            isNew = currentRoute.isNew && !isExistingInCast,
                            isGeneratingArt = isGeneratingArt,
                            artStatusLabel = artStatusLabel,
                            onBack = { route = StudioRoute.Main },
                            onSave = { updated ->
                                onUpsertNpc(updated)
                                route = StudioRoute.Main
                            },
                            onAutoSaveBeforeBind = { draft ->
                                onUpsertNpc(draft)
                            },
                            onPickAvatar = { charId ->
                                avatarTargetCharId = charId
                                npcAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickExpressionSprite = { charId, exprKey ->
                                spritePickerTarget = charId to exprKey
                                npcSpritePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onClearExpressionSprite = { charId, exprKey ->
                                onClearNpcSprite(charId, exprKey)
                            },
                            onGenerateExpressionSprite = { charId, exprKey ->
                                onGenerateNpcSprite(charId, exprKey, "")
                            },
                            studioArtModelState = studioArtModelState,
                            onConfigureArtModel = { studioArtModelActions?.onOpenPicker() },
                        )
                    }

                    is StudioRoute.LoreEditor -> {
                        RpLoreEditorSubpage(
                            initial = currentRoute.entry,
                            onSaveV2LoreEntry = assetActions?.onSaveV2LoreEntry,
                            onBack = { route = StudioRoute.Main },
                            onSaveNew = { cat, title, keywords, content, alwaysActive ->
                                onAddLore(cat, title, keywords, content, alwaysActive)
                                route = StudioRoute.Main
                            },
                            onSaveExisting = { updated ->
                                onUpdateLore(updated)
                                route = StudioRoute.Main
                            },
                        )
                    }

                    is StudioRoute.RegexEditor -> {
                        RpRegexEditorSubpage(
                            rp = rp,
                            initial = currentRoute.script,
                            isNew = currentRoute.isNew,
                            onBack = { route = StudioRoute.Main },
                            onSave = { updated ->
                                onUpsertRegexScript(updated)
                                route = StudioRoute.Main
                            },
                        )
                    }

                    is StudioRoute.CgViewer -> {
                        val liveCg = rp.state.unlockedCgs.firstOrNull { it.id == currentRoute.cgId }
                        if (liveCg == null) {
                            LaunchedEffect(currentRoute.cgId) { route = StudioRoute.Main }
                        } else {
                            RpCgViewerSubpage(
                                cg = liveCg,
                                isActive = liveCg.id == rp.state.activeCgId,
                                onBack = { route = StudioRoute.Main },
                                onApplyAsScene = { onApplyCgAsScene(liveCg) },
                                onDelete = {
                                    onDeleteCg(liveCg.id)
                                    route = StudioRoute.Main
                                },
                            )
                        }
                    }

                    is StudioRoute.ArtModelConfig -> {
                        if (studioArtModelActions != null) {
                            StudioArtModelConfigPage(
                                state = studioArtModelState,
                                actions = studioArtModelActions,
                                onBack = {
                                    studioArtModelActions.onDismissPicker()
                                    route = currentRoute.returnRoute
                                },
                            )
                        } else {
                            LaunchedEffect(Unit) { route = currentRoute.returnRoute }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudioSubpageTopBar(
    backLabel: String,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    GuSubpageTopBar(
        title = title,
        backLabel = backLabel,
        onBack = onBack,
        trailing = trailing,
    )
}

// ==================== Tab 0: 角色 (Cast + Collapsible Protagonist Card) ====================


@Composable
internal fun RpRemovableChip(
    text: String,
    onRemove: () -> Unit,
) {
    GuRemovableChip(text = text, onRemove = onRemove)
}

@Composable
internal fun RpEndingTrophyRow(ending: RpEndingRecord) {
    val c = LocalAppTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GuBadge(ending.code.uppercase(), variant = endingBadgeVariant(ending.code))
            Text(
                text = ending.title,
                color = c.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ending.turnSeq > 0) {
                GuBadge("#${ending.turnSeq}", variant = GuBadgeVariant.NEUTRAL)
            }
        }
        if (ending.summary.isNotBlank()) {
            Text(
                text = ending.summary,
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 17.5.sp,
            )
        }
    }
}

@Composable
internal fun RpMiniDialogStyleCard(
    styleKey: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val (bg, boxBg, accent, lineColor) = when (styleKey.lowercase()) {
        "classic_vn" -> listOf(Color(0xFF281426), Color(0xFF1A101D), Color(0xFFE0B954), Color(0xFFEADBC0))
        "minimal" -> listOf(Color(0xFF101624), Color(0xFF0A0E18), Color(0xFFCBD5E1), Color(0xFFDDE5F5))
        "cyber" -> listOf(Color(0xFF0A1D2E), Color(0xFF07101B), Color(0xFF22D3EE), Color(0xFFBAE6FD))
        "parchment" -> listOf(Color(0xFFEAD9B4), Color(0xFFF7F0E2), Color(0xFF9C723C), Color(0xFF5A3F26))
        else -> listOf(Color(0xFF172038), Color(0xFF0E1320), Color(0xFF819BFF), Color(0xFFE2E8F0))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = c.surface,
        border = if (selected) BorderStroke(1.5.dp, c.primary) else null,
        modifier = Modifier.width(102.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(bg)
                    .padding(7.dp),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(accent),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(21.dp)
                            .clip(RoundedCornerShape(topEnd = 5.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                            .background(boxBg)
                            .border(
                                width = if (styleKey == "minimal") 0.dp else 0.7.dp,
                                color = if (styleKey == "minimal") Color.Transparent else accent.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(topEnd = 5.dp, bottomStart = 5.dp, bottomEnd = 5.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 4.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(lineColor.copy(alpha = 0.75f)),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(lineColor.copy(alpha = 0.45f)),
                            )
                        }
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Text(
                text = label,
                color = if (selected) c.primarySoftInk else c.ink,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
