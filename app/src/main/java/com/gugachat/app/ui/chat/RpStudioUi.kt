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
import androidx.compose.ui.platform.LocalView
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
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
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
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** One-shot tab override for deep-linking from quick-control popover without changing ChatWorkspaceScreen signature. */
internal object RpStudioNavBridge {
    var oneShotInitialTab: Int? = null
}

private val RP_WORLDBOOK_CATEGORIES = listOf(
    "character",
    "location",
    "item",
    "faction",
    "rule",
    "concept",
    "other",
)

private val RP_ROLE_KEYS = listOf(
    "companion",
    "romance",
    "ally",
    "rival",
    "mentor",
    "antagonist",
    "passerby",
    "mystery",
)

private val AFFINITY_BONDED_ROSE = Color(0xFFEC4899)

private fun rpPlayStyleLabel(style: RpPlayStyle, rpStrings: RpStrings): String = when (style) {
    RpPlayStyle.chengguang -> rpStrings.styleChengguang
    RpPlayStyle.tavern -> rpStrings.styleTavern
    RpPlayStyle.trpg -> rpStrings.styleTrpg
}

private fun affinityBadgeVariant(tierCode: String): GuBadgeVariant = when (tierCode) {
    "hostile" -> GuBadgeVariant.DANGER
    "cold", "acquaintance" -> GuBadgeVariant.NEUTRAL
    "friendly" -> GuBadgeVariant.EMERALD
    "trusted" -> GuBadgeVariant.BLUE
    "smitten", "bonded" -> GuBadgeVariant.HONEY
    else -> GuBadgeVariant.NEUTRAL
}

private fun affinityBarColor(tierCode: String, c: AppColors): Color = when (tierCode) {
    "hostile" -> c.danger
    "cold" -> c.muted
    "acquaintance" -> c.primary.copy(alpha = 0.78f)
    "friendly" -> c.success
    "trusted" -> c.primary
    "smitten" -> c.accent
    "bonded" -> AFFINITY_BONDED_ROSE
    else -> c.primary
}

private fun endingBadgeVariant(code: String): GuBadgeVariant = when (code.uppercase()) {
    "HE" -> GuBadgeVariant.EMERALD
    "TE" -> GuBadgeVariant.HONEY
    "BE" -> GuBadgeVariant.DANGER
    else -> GuBadgeVariant.BLUE
}

private fun sanitizeExportFileName(rawTitle: String, fallback: String): String {
    val clean = rawTitle.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40)
    return clean.ifBlank { fallback }
}

private val RP_ACT_PREFIX_RE = Regex(
    "^(第.{1,3}[幕回章节]|序幕|Act\\s+\\d+|Chapter\\s+\\d+|Prologue\\b)",
    RegexOption.IGNORE_CASE,
)

private fun formatRpActChapter(actNumber: Int, chapterTitle: String, isZh: Boolean): String {
    val cleanTitle = chapterTitle.trim()
    val fallbackAct = if (isZh) "第${actNumber}幕" else "Act $actNumber"
    return when {
        cleanTitle.isEmpty() -> fallbackAct
        RP_ACT_PREFIX_RE.containsMatchIn(cleanTitle) -> cleanTitle
        else -> "$fallbackAct · $cleanTitle"
    }
}

private fun stripLeadingPlus(label: String): String =
    label.trimStart('+', ' ', '＋')

private fun localizeRpLoreCategory(category: String, loreCats: Map<String, String>, isZh: Boolean): String {
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

private fun localizeRpRole(role: String, isZh: Boolean): String {
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

private data class RpAccentSwatch(
    val hex: String,
    val color: Color?,
    val zhName: String,
    val enName: String,
)

private val RP_ACCENT_SWATCHES = listOf(
    RpAccentSwatch("", null, "默认", "Default"),
    RpAccentSwatch("#C84B5B", Color(0xFFC84B5B), "宫廷绯红", "Crimson"),
    RpAccentSwatch("#D99B38", Color(0xFFD99B38), "琥珀鎏金", "Gold"),
    RpAccentSwatch("#EC4899", Color(0xFFEC4899), "樱花粉", "Sakura"),
    RpAccentSwatch("#06B6D4", Color(0xFF06B6D4), "赛博青", "Cyan"),
    RpAccentSwatch("#6366F1", Color(0xFF6366F1), "靛蓝", "Indigo"),
    RpAccentSwatch("#10B981", Color(0xFF10B981), "翡翠玉", "Jade"),
)

private val RP_DIALOG_STYLE_KEYS = listOf("glass", "classic_vn", "minimal", "cyber", "parchment")
private val RP_STAGE_SLOT_KEYS = listOf("auto", "left", "center", "right")

private data class RpPresetCoverVisual(
    val startColor: Color,
    val endColor: Color,
    val accentTint: Color,
    val icon: ImageVector,
)

private fun resolveRpPresetCoverVisual(presetId: String): RpPresetCoverVisual = when (presetId.lowercase()) {
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

private fun localizeStageSlot(slot: String, rpStrings: RpStrings): String = when (slot.trim().lowercase()) {
    "left" -> rpStrings.slotLeft
    "center" -> rpStrings.slotCenter
    "right" -> rpStrings.slotRight
    else -> rpStrings.slotAuto
}

private fun localizeRegexPlacement(placement: String, rpStrings: RpStrings): String = when (placement.trim().lowercase()) {
    "user_input" -> rpStrings.regexPlacementUser
    "both" -> rpStrings.regexPlacementBoth
    else -> rpStrings.regexPlacementAi
}

@Composable
private fun RpArtProgressBanner(
    statusLabel: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = c.primarySoft,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = c.primarySoftInk,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = statusLabel.ifBlank { strings.rp.aiGeneratingArt },
                    color = c.primarySoftInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = c.primary,
                trackColor = c.surface,
            )
        }
    }
}

@Composable
private fun RpInlineDeleteConfirmStrip(
    prompt: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = c.dangerBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = prompt,
                color = c.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(cancelText, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            GuButton(
                onClick = onConfirm,
                compact = true,
                variant = GuButtonVariant.DANGER,
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(confirmText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpStatusHudBar(
    rp: RpConfig,
    triggeredLoreCount: Int = 0,
    onToggleTheaterMode: () -> Unit,
    onQuickSave: () -> Unit,
    onOpenStudio: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
    showHeaderActions: Boolean = true,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val leadNpc = rp.cast.firstOrNull { !it.isPlayer }
    val leadTier = leadNpc?.let { RpEngine.affinityTier(it.affinity) }
    val leadTierLabel = leadTier?.let { t ->
        strings.rp.affinityTiers[t.code] ?: if (isZh) t.zhLabel else t.enLabel
    }
    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)

    var expanded by remember(rp.title) { mutableStateOf(false) }
    var editingNoteInline by remember { mutableStateOf(false) }
    var noteDraft by remember(rp.authorNote) { mutableStateOf(rp.authorNote) }

    val sceneName = rp.state.scene.ifBlank { rp.title }
    val chapterPrefix = rp.state.chapterTitle.trim().takeIf { it.isNotEmpty() }
    val headerLine = if (chapterPrefix != null) "$chapterPrefix · 📍 $sceneName" else "📍 $sceneName"

    val compactStatusLine = buildString {
        append("HP ${rp.state.hp}/${rp.state.maxHp}")
        if (leadNpc != null) {
            append(" · ♥${leadNpc.affinity}")
            if (!leadTierLabel.isNullOrBlank()) append(" $leadTierLabel")
        }
        if (leadNpc == null || rp.state.gold > 0) {
            append(" · 🪙${rp.state.gold}")
        }
        if (triggeredLoreCount > 0) {
            append(" · 📖$triggeredLoreCount")
        }
        if (rp.authorNote.isNotBlank()) {
            append(" · 📝")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.awning),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (showHeaderActions) 38.dp else 30.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = if (showHeaderActions) 4.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showHeaderActions) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = headerLine,
                        color = c.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!expanded) {
                        GuMetaText(text = compactStatusLine)
                    }
                }
                Surface(
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onToggleTheaterMode),
                    shape = RoundedCornerShape(999.dp),
                    color = if (rp.theaterMode) c.primary else c.surfaceSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Theaters,
                            contentDescription = null,
                            tint = if (rp.theaterMode) Color.White else c.ink,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                            color = if (rp.theaterMode) Color.White else c.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                if (!expanded) {
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenStudio),
                        shape = RoundedCornerShape(999.dp),
                        color = c.tag,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = strings.rp.studioBtn,
                                color = c.tagInk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = compactStatusLine,
                    color = c.ink.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.72f else 0.78f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("HP ${rp.state.hp}/${rp.state.maxHp}", color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { hpFrac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = c.danger,
                                trackColor = c.surface,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("MP ${rp.state.mp}/${rp.state.maxMp}", color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { mpFrac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = c.primary,
                                trackColor = c.surface,
                            )
                        }
                        GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.HONEY)
                    }
                    if (leadNpc != null && leadTier != null && leadTierLabel != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = affinityBarColor(leadTier.code, c),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${leadNpc.name} · ${leadNpc.affinity}",
                                color = c.ink,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            GuBadge(leadTierLabel, variant = affinityBadgeVariant(leadTier.code))
                        }
                    }
                }

                GuGroupedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 13.dp) {
                    GuSettingsRow(
                        title = strings.rp.authorNoteLabel,
                        subtitle = rp.authorNote.ifBlank { strings.rp.authorNotePlaceholder },
                        icon = Icons.Default.EditNote,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = { editingNoteInline = !editingNoteInline },
                    )
                    AnimatedVisibility(visible = editingNoteInline) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuTextField(
                                value = noteDraft,
                                onValueChange = { noteDraft = it },
                                placeholder = strings.rp.authorNotePlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                TextButton(onClick = { editingNoteInline = false }) {
                                    Text(strings.common.cancel, color = c.muted)
                                }
                                GuButton(
                                    onClick = {
                                        onUpdateAuthorNote(noteDraft.trim())
                                        editingNoteInline = false
                                    },
                                    compact = true,
                                ) {
                                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuButton(onClick = onQuickSave, compact = true, variant = GuButtonVariant.SUBTLE) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.quickSaveBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GuButton(onClick = onOpenStudio, compact = true, variant = GuButtonVariant.HONEY) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.studioBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GuButton(
                        onClick = onToggleTheaterMode,
                        compact = true,
                        variant = if (rp.theaterMode) GuButtonVariant.PRIMARY else GuButtonVariant.SECONDARY,
                    ) {
                        Icon(Icons.Default.Theaters, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = c.border.copy(alpha = c.border.alpha * 0.75f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpWizardDialog(
    onDismiss: () -> Unit,
    onCreateFromPreset: (
        presetId: String,
        title: String,
        playStyle: RpPlayStyle,
        playerName: String,
        playerPersona: String,
    ) -> Unit,
    onImportCardUri: (Uri) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val firstPreset = RpPresets.ALL.first()
    var selectedPreset by remember { mutableStateOf(firstPreset.id) }
    var selectedStyle by remember { mutableStateOf(firstPreset.config.playStyle) }
    var worldTitle by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }
    var playerPersona by remember { mutableStateOf("") }

    val cardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportCardUri(uri)
            onDismiss()
        }
    }

    val playStyles = remember {
        listOf(RpPlayStyle.chengguang, RpPlayStyle.tavern, RpPlayStyle.trpg)
    }
    val playStyleLabels = remember(strings) {
        playStyles.map { rpPlayStyleLabel(it, strings.rp) }
    }
    val active = RpPresets.findById(selectedPreset)
    val greetingCount = (if (active.config.greeting.isNotBlank()) 1 else 0) + active.config.alternateGreetings.size
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        scrimColor = Color(0x8C060912),
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
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
                .fillMaxHeight(0.90f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.rp.wizardTitle,
                subtitle = if (isZh) "挑选剧本封面或导入 SillyTavern 角色卡" else "Browse scenario cards or import a SillyTavern V2/V3 card",
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GuGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    GuSettingsRow(
                        title = strings.rp.importCardBtn,
                        subtitle = if (isZh) "支持 PNG / JSON / CHARX 角色卡、世界书与正则脚本" else "Supports PNG / JSON / CHARX character cards, lorebooks & regex",
                        icon = Icons.Default.UploadFile,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = { cardLauncher.launch(arrayOf("*/*")) },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuSectionHeader(title = strings.rp.playStyleLabel)
                    GuSegmentedControl(
                        items = playStyleLabels,
                        selectedIndex = playStyles.indexOf(selectedStyle).coerceAtLeast(0),
                        onSelect = { idx -> selectedStyle = playStyles[idx] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(
                        title = strings.rp.wizardPresetLabel,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        trailing = {
                            GuBadge("${RpPresets.ALL.size}", variant = GuBadgeVariant.BLUE)
                        },
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(RpPresets.ALL, key = { it.id }) { preset ->
                            val isSelected = selectedPreset == preset.id
                            val visual = remember(preset.id) { resolveRpPresetCoverVisual(preset.id) }
                            val presetLabel = strings.rp.presets[preset.id] ?: preset.name
                            val presetGreetings = (if (preset.config.greeting.isNotBlank()) 1 else 0) +
                                preset.config.alternateGreetings.size
                            val presetMeta = if (isZh) {
                                "${preset.config.cast.size}角 · ${presetGreetings}开场 · ${preset.starterLore.size}词条"
                            } else {
                                "${preset.config.cast.size} cast · ${presetGreetings} open · ${preset.starterLore.size} lore"
                            }

                            Surface(
                                onClick = {
                                    selectedPreset = preset.id
                                    selectedStyle = preset.config.playStyle
                                },
                                shape = RoundedCornerShape(15.dp),
                                color = c.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, c.primary) else null,
                                shadowElevation = if (isSelected && !theme.isDark) 3.dp else 0.dp,
                                modifier = Modifier
                                    .width(164.dp)
                                    .height(124.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(Brush.horizontalGradient(listOf(visual.startColor, visual.endColor)))
                                            .padding(horizontal = 10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            GuIconTile(
                                                icon = visual.icon,
                                                tint = visual.accentTint,
                                                backgroundColor = Color.White.copy(alpha = 0.14f),
                                                size = 26.dp,
                                                iconSize = 14.dp,
                                                cornerRadius = 8.dp,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.Black.copy(alpha = 0.34f),
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Text(
                                                    text = rpPlayStyleLabel(preset.config.playStyle, strings.rp),
                                                    color = Color(0xFFFDF7EC),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(c.primary),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = presetLabel,
                                            color = if (isSelected) c.primarySoftInk else c.ink,
                                            fontSize = 12.5.sp,
                                            lineHeight = 16.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        GuMetaText(text = presetMeta)
                                    }
                                }
                            }
                        }
                    }
                }

                GuGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.78f else 0.85f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = active.config.title,
                                color = c.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge(
                                text = rpPlayStyleLabel(active.config.playStyle, strings.rp),
                                variant = GuBadgeVariant.BLUE,
                            )
                        }
                        Text(
                            text = active.description,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                        if (active.config.greeting.isNotBlank()) {
                            val excerpt = remember(active.config.greeting) {
                                active.config.greeting.replace("**", "").trim()
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = c.surface.copy(alpha = if (theme.isDark) 0.65f else 0.85f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "“$excerpt”",
                                    color = c.ink.copy(alpha = 0.88f),
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(
                                text = if (isZh) "${active.config.cast.size} 位角色" else "${active.config.cast.size} NPCs",
                                variant = GuBadgeVariant.BLUE,
                            )
                            if (greetingCount > 0) {
                                GuBadge(
                                    text = strings.rp.wizardGreetingCount.fmt("count" to greetingCount),
                                    variant = GuBadgeVariant.HONEY,
                                )
                            }
                            if (active.starterLore.isNotEmpty()) {
                                GuBadge(
                                    text = strings.rp.wizardLoreCount.fmt("count" to active.starterLore.size),
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(title = strings.rp.personaSectionTitle)
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            GuTextField(
                                value = worldTitle,
                                onValueChange = { worldTitle = it },
                                label = strings.rp.worldTitleLabel,
                                placeholder = active.config.title,
                            )
                            GuTextField(
                                value = playerName,
                                onValueChange = { playerName = it },
                                label = strings.rp.wizardPlayerNameLabel,
                                placeholder = active.config.playerName,
                            )
                            GuTextField(
                                value = playerPersona,
                                onValueChange = { playerPersona = it },
                                label = strings.rp.wizardPlayerPersonaLabel,
                                placeholder = active.config.playerPersona.ifBlank { strings.rp.playerPersonaLabel },
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 42.dp)) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.Medium)
                }
                GuButton(
                    onClick = {
                        onCreateFromPreset(
                            selectedPreset,
                            worldTitle.trim(),
                            selectedStyle,
                            playerName.trim(),
                            playerPersona.trim(),
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Theaters, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.wizardStartBtn, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==================== Dark-Gold Fate Dice Panel ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpDiceDialog(
    rp: RpConfig? = null,
    onDismiss: () -> Unit,
    onRoll: (expression: String, skill: String?, difficulty: Int?, mode: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val outerTheme = LocalAppTheme.current
    val isZh = strings.language == AppLanguage.ZH
    val goldAccent = remember(rp?.visualTheme?.accentHex) {
        parseOptionalHexColor(rp?.visualTheme?.accentHex) ?: Color(0xFFF5C563)
    }
    val darkStageTheme = remember(outerTheme, goldAccent) {
        deriveRpStageAppTheme(outerTheme, goldAccent)
    }
    val isD100Ruleset = rp?.systemRules?.contains("d100", ignoreCase = true) == true

    var formula by remember(rp?.title, isD100Ruleset) {
        mutableStateOf(if (isD100Ruleset) "1d100" else "1d20")
    }
    var skill by remember(rp?.title) { mutableStateOf("") }
    var dcValue by remember(rp?.title, isD100Ruleset) {
        mutableIntStateOf(if (isD100Ruleset) 50 else 12)
    }
    var mode by remember { mutableStateOf("normal") }
    var showCustomInput by remember { mutableStateOf(false) }

    val diceModes = remember { listOf("disadvantage", "normal", "advantage") }
    val diceModeLabels = remember(isZh) {
        if (isZh) listOf("劣势", "普通", "优势") else listOf("Disadv", "Normal", "Adv")
    }
    val stats = rp?.state?.stats.orEmpty()
    val fallbackSkills = remember(isZh) {
        if (isZh) listOf("洞察" to 12, "交涉" to 12, "潜行" to 14, "体魄" to 12)
        else listOf("Insight" to 12, "Persuade" to 12, "Stealth" to 14, "Athletics" to 12)
    }
    val activeChipStats = if (stats.isNotEmpty()) stats.entries.map { it.key to it.value } else fallbackSkills
    val isD100Selected = formula.contains("d100", ignoreCase = true) || isD100Ruleset
    val maxDc = if (isD100Selected) 100 else 30

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalAppTheme provides darkStageTheme) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F1524),
                border = BorderStroke(0.8.dp, goldAccent.copy(alpha = 0.55f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(goldAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = strings.rp.diceTitle,
                                color = Color(0xFFFFF6E3),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            GuMetaText(
                                text = if (isZh) "点选属性自动代入技能修正与难度" else "Tap a stat chip to auto-fill check & DC",
                                color = Color(0xFFA7B3CB),
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.common.close,
                                tint = Color(0xFFA7B3CB),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Top Horizontal Stat / NPC Check Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuMetaText(
                            text = if (isZh) "属性 / 检定筹码" else "Stat / Check Chips",
                            color = Color(0xFFA7B3CB),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            activeChipStats.forEach { (statName, statVal) ->
                                val selected = skill == statName
                                Surface(
                                    onClick = {
                                        skill = statName
                                        if (isD100Selected) {
                                            formula = "1d100"
                                            dcValue = statVal.coerceIn(1, 100)
                                        } else {
                                            if (rp != null && rp.autoDiceSkillBonus && stats.containsKey(statName)) {
                                                formula = RpEngine.resolveSkillModifier(statName, rp).first
                                            } else {
                                                formula = "1d20"
                                            }
                                            dcValue = when {
                                                statVal in 1..30 -> statVal.coerceIn(1, 30)
                                                else -> 12
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) goldAccent.copy(alpha = 0.24f) else Color(0xFF192136),
                                    border = BorderStroke(
                                        0.6.dp,
                                        if (selected) goldAccent else Color(0x38CBD5E1),
                                    ),
                                ) {
                                    Text(
                                        text = "$statName · $statVal",
                                        color = if (selected) Color(0xFFFFF4DC) else Color(0xFFD8E1F2),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Center Visual Die Preview + DC GuInlineStepper
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF161D30),
                        border = BorderStroke(0.6.dp, goldAccent.copy(alpha = 0.34f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Large Skeuomorphic Dark-Gold Die Face
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                goldAccent.copy(alpha = 0.28f),
                                                Color(0xFF1E1712),
                                                Color(0xFF111624),
                                            ),
                                        ),
                                    )
                                    .border(1.2.dp, goldAccent.copy(alpha = 0.78f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = formula.uppercase(),
                                        color = goldAccent,
                                        fontSize = if (formula.length > 5) 14.sp else 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    GuMetaText(
                                        text = skill.ifBlank { if (isZh) "自由掷骰" else "Free Roll" },
                                        color = Color(0xFFFFF4DC),
                                    )
                                }
                            }

                            // DC Stepper + Formula Quick Switches
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isZh) "目标难度 DC" else "Target DC",
                                            color = Color(0xFFF3F6FC),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        GuMetaText(
                                            text = "1~$maxDc",
                                            color = Color(0xFFA7B3CB),
                                        )
                                    }
                                    GuInlineStepper(
                                        valueText = "DC $dcValue",
                                        canDecrement = dcValue > 1,
                                        canIncrement = dcValue < maxDc,
                                        onDecrement = { dcValue = (dcValue - 1).coerceIn(1, maxDc) },
                                        onIncrement = { dcValue = (dcValue + 1).coerceIn(1, maxDc) },
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    listOf("1d20", "1d100", "2d6+2", "1d12").forEach { f ->
                                        val selected = formula == f
                                        Surface(
                                            onClick = {
                                                formula = f
                                                dcValue = if (f.contains("d100")) dcValue.coerceIn(1, 100) else dcValue.coerceIn(1, 30)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) goldAccent.copy(alpha = 0.22f) else Color(0xFF1F2840),
                                        ) {
                                            Text(
                                                text = f,
                                                color = if (selected) goldAccent else Color(0xFFCBD5E1),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Segmented Control: [劣势 | 普通 | 优势]
                    GuSegmentedControl(
                        items = diceModeLabels,
                        selectedIndex = diceModes.indexOf(mode).coerceAtLeast(1),
                        onSelect = { idx -> mode = diceModes[idx] },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Optional Collapsed Custom Override
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomInput = !showCustomInput }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GuMetaText(
                            text = if (isZh) "自定义公式与技能名称" else "Custom formula & skill name",
                            color = Color(0xFFA7B3CB),
                        )
                        Icon(
                            imageVector = if (showCustomInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFFA7B3CB),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    AnimatedVisibility(visible = showCustomInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuTextField(
                                value = formula,
                                onValueChange = { formula = it },
                                label = strings.rp.diceFormulaLabel,
                                modifier = Modifier.weight(0.9f),
                            )
                            GuTextField(
                                value = skill,
                                onValueChange = { skill = it },
                                label = strings.rp.diceSkillLabel,
                                placeholder = strings.rp.diceSkillPlaceholder,
                                modifier = Modifier.weight(1.1f),
                            )
                        }
                    }

                    // Primary Roll CTA
                    GuButton(
                        onClick = {
                            onRoll(
                                formula.trim(),
                                skill.trim().takeIf { it.isNotEmpty() },
                                dcValue.coerceIn(1, maxDc),
                                mode,
                            )
                            onDismiss()
                        },
                        enabled = formula.isNotBlank(),
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(strings.rp.diceRollBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== Dialog-Free Push/Pop RP Studio Sheet ====================

private sealed interface StudioRoute {
    data object Main : StudioRoute
    data class CharacterDetail(val seedCharacter: RpCharacter, val isNew: Boolean) : StudioRoute
    data class LoreEditor(val entry: NovelLoreEntry?) : StudioRoute
    data class RegexEditor(val script: RpRegexScript, val isNew: Boolean) : StudioRoute
    data class CgViewer(val cgId: String) : StudioRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpStudioSheet(
    rp: RpConfig,
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    saveSlots: List<RpSaveSlot>,
    initialTab: Int = 0,
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
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val resolvedInitialTab = remember(initialTab) {
        (RpStudioNavBridge.oneShotInitialTab ?: initialTab).also {
            RpStudioNavBridge.oneShotInitialTab = null
        }.coerceIn(0, 3)
    }
    var tab by remember(resolvedInitialTab) { mutableIntStateOf(resolvedInitialTab) }
    var route by remember { mutableStateOf<StudioRoute>(StudioRoute.Main) }

    BackHandler(enabled = route !is StudioRoute.Main) {
        route = StudioRoute.Main
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        scrimColor = Color(0x8C060912),
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
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
                                    isGeneratingArt = isGeneratingArt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
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
                                )

                                2 -> RpWorldAndLoreSection(
                                    rp = rp,
                                    lore = lore,
                                    triggeredLoreIds = triggeredLoreIds,
                                    isGeneratingArt = isGeneratingArt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
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
                        )
                    }

                    is StudioRoute.LoreEditor -> {
                        RpLoreEditorSubpage(
                            initial = currentRoute.entry,
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
                }
            }
        }
    }
}

@Composable
private fun StudioSubpageTopBar(
    backLabel: String,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backLabel,
                    tint = c.primary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = backLabel,
                    color = c.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = title,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.55f))
    }
}

// ==================== Tab 0: 角色 (Cast + Collapsible Protagonist Card) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpCastSection(
    rp: RpConfig,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onUpdatePlayer: (name: String, persona: String) -> Unit,
    onAddNpc: () -> Unit,
    onOpenNpcDetail: (RpCharacter) -> Unit,
    onDeleteNpc: (String) -> Unit,
    onAdjustAffinity: (String, Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var playerExpanded by remember { mutableStateOf(false) }
    var pNameDraft by remember(rp.playerName) { mutableStateOf(rp.playerName) }
    var pPersonaDraft by remember(rp.playerPersona) { mutableStateOf(rp.playerPersona) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val npcList = remember(rp.cast) { rp.cast.filter { !it.isPlayer } }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Collapsible Protagonist Card
        item(key = "__player_card__") {
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = c.primarySoft.copy(alpha = if (theme.isDark) 0.55f else 0.70f),
                onClick = { playerExpanded = !playerExpanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = rp.playerName.ifBlank { if (isZh) "旅人" else "Traveler" },
                                color = c.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            GuBadge(localizeRpRole("protagonist", isZh), variant = GuBadgeVariant.BLUE)
                        }
                        Text(
                            text = rp.playerPersona.ifBlank {
                                if (isZh) "点击展开编辑你的角色名与自设背景" else "Tap to edit player name & persona"
                            },
                            color = c.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = if (playerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = c.muted,
                    )
                }

                AnimatedVisibility(visible = playerExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuTextField(
                            value = pNameDraft,
                            onValueChange = { pNameDraft = it },
                            label = strings.rp.playerNameLabel,
                        )
                        GuTextField(
                            value = pPersonaDraft,
                            onValueChange = { pPersonaDraft = it },
                            label = strings.rp.playerPersonaLabel,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            GuButton(
                                onClick = {
                                    onUpdatePlayer(pNameDraft.trim(), pPersonaDraft.trim())
                                    playerExpanded = false
                                },
                                enabled = pNameDraft != rp.playerName || pPersonaDraft != rp.playerPersona,
                                compact = true,
                            ) {
                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Cast Header + Add CTA
        item(key = "__cast_header__") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isZh) "登场人物" else "Cast",
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuBadge("${npcList.size}", variant = GuBadgeVariant.NEUTRAL)
                }
                GuButton(
                    onClick = onAddNpc,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stripLeadingPlus(strings.rp.addNpcBtn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (npcList.isEmpty()) {
            item(key = "__cast_empty__") {
                GuEmptyState(
                    title = if (isZh) "暂无 NPC 角色" else "No NPCs yet",
                    subtitle = if (isZh) "点击右上角新增角色，设定人设、好感与 8 表情立绘" else "Tap Add to create companions, rivals, and expression sprites",
                    icon = Icons.Default.Face,
                )
            }
        } else {
            items(npcList, key = { it.id }) { npc ->
                val resolvedPath = remember(npc.spritePath, npc.avatarPath, npc.mood, npc.expressionSprites) {
                    RpEngine.resolveCharacterSpritePath(npc)
                }
                val portraitFile = remember(resolvedPath) {
                    resolvedPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
                }
                val boundExprCount = remember(npc.expressionSprites, npc.spritePath) {
                    val exprHits = RpEngine.STANDARD_EXPRESSIONS.count { key ->
                        npc.expressionSprites.entries.any {
                            it.key.equals(key, ignoreCase = true) && it.value.isNotBlank()
                        }
                    }
                    if (exprHits > 0) exprHits else if (!npc.spritePath.isNullOrBlank()) 1 else 0
                }
                val tier = RpEngine.affinityTier(npc.affinity)
                val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel
                val roleLabel = localizeRpRole(npc.role, isZh)
                val isConfirmDelete = pendingDeleteId == npc.id

                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenNpcDetail(npc) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (portraitFile != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(portraitFile),
                                    contentDescription = npc.name,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = npc.name.trim().take(1).uppercase().ifBlank { "?" },
                                    color = c.primarySoftInk,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = npc.name,
                                    color = c.ink,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (roleLabel.isNotBlank()) {
                                    GuBadge(roleLabel, variant = GuBadgeVariant.NEUTRAL)
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                GuBadge(
                                    text = if (npc.inScene) {
                                        strings.rp.npcInSceneToggle
                                    } else {
                                        if (isZh) "暂离场" else "Away"
                                    },
                                    variant = if (npc.inScene) GuBadgeVariant.EMERALD else GuBadgeVariant.NEUTRAL,
                                )
                                if (boundExprCount > 0) {
                                    GuBadge(
                                        text = if (isZh) "立绘 $boundExprCount/8" else "Sprites $boundExprCount/8",
                                        variant = GuBadgeVariant.BLUE,
                                    )
                                }
                                if (npc.mood.isNotBlank()) {
                                    GuMetaText(
                                        text = npc.mood,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { pendingDeleteId = if (isConfirmDelete) null else npc.id },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = strings.common.delete,
                                tint = c.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (npc.personality.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = npc.personality,
                            color = c.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = affinityBarColor(tier.code, c),
                            modifier = Modifier.size(13.5.dp),
                        )
                        Text(
                            text = strings.rp.npcAffinityLabel.fmt("val" to npc.affinity),
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(tierLabel, variant = affinityBadgeVariant(tier.code))
                        Spacer(Modifier.weight(1f))
                        GuButton(
                            onClick = { onAdjustAffinity(npc.id, -5) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            modifier = Modifier.heightIn(min = 30.dp),
                        ) {
                            Text("-5", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = { onAdjustAffinity(npc.id, 5) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            modifier = Modifier.heightIn(min = 30.dp),
                        ) {
                            Text("+5", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { tier.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = affinityBarColor(tier.code, c),
                        trackColor = c.surfaceSoft,
                    )

                    if (isConfirmDelete) {
                        Spacer(Modifier.height(10.dp))
                        RpInlineDeleteConfirmStrip(
                            prompt = strings.rp.deleteNpcConfirm.fmt("name" to npc.name),
                            confirmText = strings.common.delete,
                            cancelText = strings.common.cancel,
                            onConfirm = {
                                pendingDeleteId = null
                                onDeleteNpc(npc.id)
                            },
                            onCancel = { pendingDeleteId = null },
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            GuMetaText(
                                text = if (isZh) "点击编辑档案与 8 表情立绘 ▸" else "Edit profile & 8 sprites ▸",
                                color = c.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 1: Character Profile + 8 Sprites Unified ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpCharacterUnifiedSubpage(
    rp: RpConfig,
    npc: RpCharacter,
    isNew: Boolean,
    isGeneratingArt: Boolean,
    artStatusLabel: String,
    onBack: () -> Unit,
    onSave: (RpCharacter) -> Unit,
    onAutoSaveBeforeBind: (RpCharacter) -> Unit,
    onPickAvatar: (String) -> Unit,
    onPickExpressionSprite: (charId: String, expressionKey: String) -> Unit,
    onClearExpressionSprite: (charId: String, expressionKey: String) -> Unit,
    onGenerateExpressionSprite: (charId: String, expressionKey: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var name by remember(npc.id) { mutableStateOf(npc.name) }
    var role by remember(npc.id) { mutableStateOf(npc.role.ifBlank { "companion" }) }
    var mood by remember(npc.id) { mutableStateOf(npc.mood) }
    var appearance by remember(npc.id) { mutableStateOf(npc.appearance) }
    var appearancePrompt by remember(npc.id) { mutableStateOf(npc.appearancePrompt) }
    var personality by remember(npc.id) { mutableStateOf(npc.personality) }
    var speakingStyle by remember(npc.id) { mutableStateOf(npc.speakingStyle) }
    var secret by remember(npc.id) { mutableStateOf(npc.secret) }
    var inScene by remember(npc.id) { mutableStateOf(npc.inScene) }
    var affinity by remember(npc.id) { mutableIntStateOf(npc.affinity.coerceIn(-100, 100)) }
    var stageSlot by remember(npc.id) { mutableStateOf(npc.stageSlot.ifBlank { "auto" }) }
    var spriteScale by remember(npc.id) { mutableFloatStateOf(npc.spriteScale.coerceIn(0.7f, 1.4f)) }
    var spriteOffsetY by remember(npc.id) { mutableFloatStateOf(npc.spriteOffsetY.coerceIn(-60f, 60f)) }

    fun buildDraft(): RpCharacter = npc.copy(
        id = npc.id.ifBlank { UUID.randomUUID().toString() },
        name = name.trim().ifBlank { if (isZh) "未命名角色" else "Unnamed" },
        role = role.trim().ifBlank { "companion" },
        mood = mood.trim(),
        appearance = appearance.trim(),
        appearancePrompt = appearancePrompt.trim(),
        personality = personality.trim(),
        speakingStyle = speakingStyle.trim(),
        secret = secret.trim(),
        inScene = inScene,
        affinity = affinity.coerceIn(-100, 100),
        stageSlot = stageSlot,
        spriteScale = spriteScale.coerceIn(0.7f, 1.4f),
        spriteOffsetY = spriteOffsetY.roundToInt().toFloat().coerceIn(-60f, 60f),
    )

    val previewChar = buildDraft()
    val previewPath = remember(previewChar.mood, npc.expressionSprites, npc.spritePath, npc.avatarPath) {
        RpEngine.resolveCharacterSpritePath(previewChar)
    }
    val previewFile = remember(previewPath) {
        previewPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val avatarFile = remember(npc.avatarPath) {
        npc.avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val boundCount = remember(npc.expressionSprites) {
        RpEngine.STANDARD_EXPRESSIONS.count { key ->
            npc.expressionSprites.entries.any { it.key.equals(key, ignoreCase = true) && it.value.isNotBlank() }
        }
    }
    val tier = RpEngine.affinityTier(affinity)
    val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回角色" else "Cast",
            title = if (isNew) stripLeadingPlus(strings.rp.addNpcBtn) else name.ifBlank { strings.rp.editNpcTitle },
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = { onSave(buildDraft()) },
                    enabled = name.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        AnimatedVisibility(visible = isGeneratingArt) {
            RpArtProgressBanner(
                statusLabel = artStatusLabel,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live Mini Stage Preview Window
            val stageAccent = remember(rp.visualTheme.accentHex) {
                resolveRpStageAccent(rp.visualTheme.accentHex)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                TheaterStageBackdrop(
                    rp = rp,
                    stageAccent = stageAccent,
                    activeSlot = stageSlot,
                    backdropDim = 0.28f,
                    dimForReading = true,
                    modifier = Modifier.fillMaxSize(),
                )
                val align = when (stageSlot) {
                    "left" -> Alignment.BottomStart
                    "right" -> Alignment.BottomEnd
                    else -> Alignment.BottomCenter
                }
                if (previewFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(previewFile),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .align(align)
                            .width(126.dp)
                            .fillMaxHeight(0.92f)
                            .graphicsLayer {
                                scaleX = spriteScale
                                scaleY = spriteScale
                                translationY = (spriteOffsetY * 0.45f).dp.toPx()
                                transformOrigin = TransformOrigin(0.5f, 1.0f)
                            },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(align)
                            .padding(14.dp),
                    ) {
                        RpNpcPortraitAvatar(
                            character = previewChar,
                            fallbackName = name.ifBlank { "NPC" },
                            size = 64.dp,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xBA0D1220),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "${localizeStageSlot(stageSlot, strings.rp)} · ${(spriteScale * 100f).roundToInt()}%",
                        color = Color(0xFFFFF4DC),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Profile Fields
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(c.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarFile != null) {
                            Image(
                                painter = rememberAsyncImagePainter(avatarFile),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = name.trim().take(1).uppercase().ifBlank { "?" },
                                color = c.primarySoftInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    GuButton(
                        onClick = {
                            val draft = buildDraft()
                            if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                            onPickAvatar(draft.id)
                        },
                        enabled = name.isNotBlank(),
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.rp.npcAvatarBtn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = strings.rp.npcNameLabel,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.rp.npcRoleLabel,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RP_ROLE_KEYS.forEach { roleKey ->
                        GuPill(
                            label = localizeRpRole(roleKey, isZh),
                            selected = role.equals(roleKey, ignoreCase = true),
                            onClick = { role = roleKey },
                            modifier = Modifier.heightIn(min = 32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = mood,
                    onValueChange = { mood = it },
                    label = strings.rp.npcMoodLabel,
                )
                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = strings.rp.npcAppearanceLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = appearancePrompt,
                    onValueChange = { appearancePrompt = it },
                    label = strings.rp.appearancePromptLabel,
                    placeholder = strings.rp.appearancePromptPlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = strings.rp.leadPersonalityLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = speakingStyle,
                    onValueChange = { speakingStyle = it },
                    label = strings.rp.npcSpeakingStyleLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = strings.rp.npcSecretLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(6.dp))
                GuSwitchRow(
                    label = strings.rp.npcInSceneToggle,
                    checked = inScene,
                    onCheckedChange = { inScene = it },
                )
                if (!npc.isPlayer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.rp.npcAffinityLabel.fmt("val" to affinity),
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(tierLabel, variant = affinityBadgeVariant(tier.code))
                    }
                    Slider(
                        value = affinity.toFloat(),
                        onValueChange = { affinity = it.roundToInt().coerceIn(-100, 100) },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = c.primary,
                            activeTrackColor = c.primary,
                            inactiveTrackColor = c.outline,
                        ),
                    )
                }
            }

            // Stage Transform Controls
            val slotLabels = remember(strings) {
                RP_STAGE_SLOT_KEYS.map { localizeStageSlot(it, strings.rp) }
            }
            val selectedSlotIdx = RP_STAGE_SLOT_KEYS.indexOfFirst {
                it.equals(stageSlot, ignoreCase = true)
            }.coerceAtLeast(0)
            GuSectionHeader(title = strings.rp.stageSlotLabel)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                GuSegmentedControl(
                    items = slotLabels,
                    selectedIndex = selectedSlotIdx,
                    onSelect = { idx -> stageSlot = RP_STAGE_SLOT_KEYS[idx] },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.rp.spriteScaleLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    GuBadge("${(spriteScale * 100f).roundToInt()}%", variant = GuBadgeVariant.BLUE)
                }
                Slider(
                    value = spriteScale,
                    onValueChange = { spriteScale = it.coerceIn(0.7f, 1.4f) },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.primary,
                        activeTrackColor = c.primary,
                        inactiveTrackColor = c.outline,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.rp.spriteOffsetYLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    GuBadge("${spriteOffsetY.roundToInt()} dp", variant = GuBadgeVariant.NEUTRAL)
                }
                Slider(
                    value = spriteOffsetY,
                    onValueChange = { spriteOffsetY = it.coerceIn(-60f, 60f) },
                    valueRange = -60f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.primary,
                        activeTrackColor = c.primary,
                        inactiveTrackColor = c.outline,
                    ),
                )
            }

            // 8 Expression Sprite Slots
            GuSectionHeader(
                title = strings.rp.expressionsTitle,
                trailing = { GuBadge("$boundCount / 8", variant = GuBadgeVariant.BLUE) },
            )
            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                RpEngine.STANDARD_EXPRESSIONS.forEachIndexed { idx, exprKey ->
                    val boundPath = npc.expressionSprites.entries
                        .firstOrNull { it.key.equals(exprKey, ignoreCase = true) }
                        ?.value
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    val exprFile = remember(boundPath) {
                        boundPath?.let(::File)?.takeIf { it.isFile }
                    }
                    val exprLabel = strings.rp.expressionLabels[exprKey] ?: exprKey

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surfaceSoft)
                                .clickable {
                                    val draft = buildDraft()
                                    if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                    onPickExpressionSprite(draft.id, exprKey)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (exprFile != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(exprFile),
                                    contentDescription = exprLabel,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = c.muted.copy(alpha = 0.65f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = exprLabel,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (boundPath != null) {
                                    GuBadge(if (isZh) "已设" else "Set", variant = GuBadgeVariant.EMERALD)
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                GuButton(
                                    onClick = {
                                        val draft = buildDraft()
                                        if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                        onPickExpressionSprite(draft.id, exprKey)
                                    },
                                    enabled = !isNew || name.isNotBlank(),
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 32.dp),
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.rp.pickSpriteBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                GuButton(
                                    onClick = {
                                        val draft = buildDraft()
                                        if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                        onGenerateExpressionSprite(draft.id, exprKey)
                                    },
                                    enabled = !isGeneratingArt && (!isNew || name.isNotBlank()),
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 32.dp),
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.rp.aiGenExpressionBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (boundPath != null) {
                                    GuButton(
                                        onClick = { onClearExpressionSprite(npc.id, exprKey) },
                                        compact = true,
                                        variant = GuButtonVariant.SECONDARY,
                                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 32.dp),
                                    ) {
                                        Text(strings.rp.clearSpriteBtn, color = c.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    if (idx < RpEngine.STANDARD_EXPRESSIONS.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

// ==================== Tab 1: 状态 (Act/Scene + Inline Vitals + Stepper Stats + Saves + CG/Endings) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpStateAndArchiveSection(
    rp: RpConfig,
    saveSlots: List<RpSaveSlot>,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onUpdateState: ((RpGameState) -> RpGameState) -> Unit,
    onCreateSaveSlot: (name: String, note: String) -> Unit,
    onLoadSaveSlot: (RpSaveSlot) -> Unit,
    onBranchSaveSlot: (RpSaveSlot) -> Unit,
    onOverwriteSaveSlot: (String) -> Unit,
    onDeleteSaveSlot: (String) -> Unit,
    onGenerateSceneCg: () -> Unit,
    onPickCgFromAlbum: () -> Unit,
    onSaveCurrentSceneAsCg: () -> Unit,
    onOpenCgDetail: (RpCgRecord) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    // Inline Scene & Act editor state (replaces RpVitalsEditorDialog)
    var sceneExpanded by remember { mutableStateOf(false) }
    var actDraft by remember(rp.state.actNumber) { mutableIntStateOf(rp.state.actNumber) }
    var chapterDraft by remember(rp.state.chapterTitle) { mutableStateOf(rp.state.chapterTitle) }
    var sceneDraft by remember(rp.state.scene) { mutableStateOf(rp.state.scene) }
    var timeDraft by remember(rp.state.time) { mutableStateOf(rp.state.time) }
    var weatherDraft by remember(rp.state.weather) { mutableStateOf(rp.state.weather) }
    var maxHpDraft by remember(rp.state.maxHp) { mutableStateOf(rp.state.maxHp.toString()) }
    var maxMpDraft by remember(rp.state.maxMp) { mutableStateOf(rp.state.maxMp.toString()) }

    // Inline stat editing state (replaces editingStat AlertDialog)
    var inlineEditingStatKey by remember { mutableStateOf<String?>(null) }
    var inlineEditingStatDraft by remember { mutableStateOf("") }
    var newStatName by remember { mutableStateOf("") }
    var newStatVal by remember { mutableStateOf("") }

    var newItemText by remember { mutableStateOf("") }
    var newQuestText by remember { mutableStateOf("") }
    var newFlagKey by remember { mutableStateOf("") }
    var newFlagVal by remember { mutableStateOf("") }

    var saveName by remember { mutableStateOf("") }
    var saveNote by remember { mutableStateOf("") }
    var confirmRewindSlotId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteSlotId by remember { mutableStateOf<String?>(null) }

    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Act, Scene & Vitals Card (Inline Expandable + GuInlineStepper)
        GuSectionHeader(
            title = if (isZh) "幕次场景与核心数值" else "Act, Scene & Vitals",
            trailing = {
                GuButton(
                    onClick = { sceneExpanded = !sceneExpanded },
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Icon(
                        imageVector = if (sceneExpanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(13.5.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (sceneExpanded) (if (isZh) "收起" else "Hide") else (if (isZh) "微调" else "Adjust"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { sceneExpanded = !sceneExpanded }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = formatRpActChapter(rp.state.actNumber, rp.state.chapterTitle, isZh),
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "📍 ${rp.state.scene.ifBlank { rp.title }}",
                    color = c.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                )
                if (rp.state.time.isNotBlank() || rp.state.weather.isNotBlank()) {
                    val weatherPart = rp.state.weather.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    GuMetaText(text = "${rp.state.time}$weatherPart")
                }
            }

            AnimatedVisibility(visible = sceneExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surfaceSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (isZh) "当前幕次" else "Act", color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        GuInlineStepper(
                            valueText = if (isZh) "第${actDraft}幕" else "Act $actDraft",
                            canDecrement = actDraft > 1,
                            onDecrement = { actDraft = (actDraft - 1).coerceAtLeast(1) },
                            onIncrement = { actDraft += 1 },
                        )
                    }
                    GuTextField(value = chapterDraft, onValueChange = { chapterDraft = it }, label = strings.rp.chapterLabel)
                    GuTextField(value = sceneDraft, onValueChange = { sceneDraft = it }, label = strings.rp.sceneLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(value = timeDraft, onValueChange = { timeDraft = it }, label = strings.rp.timeLabel, modifier = Modifier.weight(1f))
                        GuTextField(value = weatherDraft, onValueChange = { weatherDraft = it }, label = strings.rp.weatherLabel, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(
                            value = maxHpDraft,
                            onValueChange = { maxHpDraft = it.filter { ch -> ch.isDigit() } },
                            label = "Max HP",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        GuTextField(
                            value = maxMpDraft,
                            onValueChange = { maxMpDraft = it.filter { ch -> ch.isDigit() } },
                            label = "Max MP",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        GuButton(
                            onClick = {
                                val nextMaxHp = (maxHpDraft.toIntOrNull() ?: rp.state.maxHp).coerceAtLeast(1)
                                val nextMaxMp = (maxMpDraft.toIntOrNull() ?: rp.state.maxMp).coerceAtLeast(1)
                                onUpdateState { s ->
                                    s.copy(
                                        actNumber = actDraft.coerceAtLeast(1),
                                        chapterTitle = chapterDraft.trim(),
                                        scene = sceneDraft.trim(),
                                        time = timeDraft.trim(),
                                        weather = weatherDraft.trim(),
                                        maxHp = nextMaxHp,
                                        hp = s.hp.coerceIn(0, nextMaxHp),
                                        maxMp = nextMaxMp,
                                        mp = s.mp.coerceIn(0, nextMaxMp),
                                    )
                                }
                                sceneExpanded = false
                            },
                            compact = true,
                        ) {
                            Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // HP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("HP ${rp.state.hp}/${rp.state.maxHp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { hpFrac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = c.danger,
                            trackColor = c.surfaceSoft,
                        )
                    }
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.hp > 0,
                        canIncrement = rp.state.hp < rp.state.maxHp,
                        onDecrement = { onUpdateState { s -> s.copy(hp = (s.hp - 10).coerceIn(0, s.maxHp)) } },
                        onIncrement = { onUpdateState { s -> s.copy(hp = (s.hp + 10).coerceIn(0, s.maxHp)) } },
                    )
                }
                // MP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MP ${rp.state.mp}/${rp.state.maxMp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { mpFrac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = c.primary,
                            trackColor = c.surfaceSoft,
                        )
                    }
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.mp > 0,
                        canIncrement = rp.state.mp < rp.state.maxMp,
                        onDecrement = { onUpdateState { s -> s.copy(mp = (s.mp - 10).coerceIn(0, s.maxMp)) } },
                        onIncrement = { onUpdateState { s -> s.copy(mp = (s.mp + 10).coerceIn(0, s.maxMp)) } },
                    )
                }
                // Gold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.HONEY)
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.gold > 0,
                        onDecrement = { onUpdateState { s -> s.copy(gold = (s.gold - 10).coerceAtLeast(0)) } },
                        onIncrement = { onUpdateState { s -> s.copy(gold = s.gold + 10) } },
                    )
                }
            }
        }

        // 2. Attributes (GuInlineStepper + Tap-to-Edit In-Row)
        GuSectionHeader(
            title = strings.rp.statsTitle,
            trailing = { GuBadge("${rp.state.stats.size}", variant = GuBadgeVariant.NEUTRAL) },
        )
        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
            if (rp.state.stats.isEmpty()) {
                Text(
                    text = if (isZh) "暂无自定义属性" else "No custom attributes yet",
                    color = c.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                val statEntries = rp.state.stats.entries.toList()
                statEntries.forEachIndexed { idx, (statName, statValue) ->
                    val isInlineEditing = inlineEditingStatKey == statName
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        inlineEditingStatKey = if (isInlineEditing) null else statName
                                        inlineEditingStatDraft = statValue.toString()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = statName,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = strings.common.edit,
                                    tint = c.muted.copy(alpha = 0.65f),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            GuInlineStepper(
                                valueText = "$statValue",
                                onDecrement = {
                                    onUpdateState { s -> s.copy(stats = s.stats + (statName to (statValue - 1))) }
                                },
                                onIncrement = {
                                    onUpdateState { s -> s.copy(stats = s.stats + (statName to (statValue + 1))) }
                                },
                            )
                            IconButton(
                                onClick = { onUpdateState { s -> s.copy(stats = s.stats - statName) } },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.common.delete,
                                    tint = c.muted,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        AnimatedVisibility(visible = isInlineEditing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(c.surfaceSoft)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GuTextField(
                                    value = inlineEditingStatDraft,
                                    onValueChange = { inlineEditingStatDraft = it.filter { ch -> ch.isDigit() || ch == '-' } },
                                    placeholder = strings.rp.statValuePlaceholder,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = {
                                        val parsed = inlineEditingStatDraft.toIntOrNull()
                                        if (parsed != null) {
                                            onUpdateState { s -> s.copy(stats = s.stats + (statName to parsed)) }
                                        }
                                        inlineEditingStatKey = null
                                    },
                                    enabled = inlineEditingStatDraft.toIntOrNull() != null,
                                    compact = true,
                                ) {
                                    Text(strings.common.save, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    if (idx < statEntries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = newStatName,
                    onValueChange = { newStatName = it },
                    placeholder = strings.rp.statNamePlaceholder,
                    modifier = Modifier.weight(1.3f),
                )
                GuTextField(
                    value = newStatVal,
                    onValueChange = { newStatVal = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    placeholder = strings.rp.statValuePlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f),
                )
                GuButton(
                    onClick = {
                        val k = newStatName.trim()
                        val v = newStatVal.toIntOrNull() ?: 10
                        if (k.isNotEmpty()) {
                            onUpdateState { s -> s.copy(stats = s.stats + (k to v)) }
                            newStatName = ""
                            newStatVal = ""
                        }
                    },
                    enabled = newStatName.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addStatBtn, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 3. Lightweight Inventory, Quests & Story Flags
        GuSectionHeader(
            title = if (isZh) "随身行囊 · 任务 · 旗标" else "Inventory · Quests · Flags",
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            // Inventory
            Text(strings.rp.inventoryTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (rp.state.inventory.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.inventory.forEachIndexed { idx, item ->
                        RpRemovableChip(
                            text = item,
                            onRemove = {
                                onUpdateState { s -> s.copy(inventory = s.inventory.filterIndexed { i, _ -> i != idx }) }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder = strings.rp.addItemPlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuButton(
                    onClick = {
                        val clean = newItemText.trim()
                        if (clean.isNotEmpty()) {
                            onUpdateState { s -> s.copy(inventory = s.inventory + clean) }
                            newItemText = ""
                        }
                    },
                    enabled = newItemText.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addItemBtn, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // Quests
            Text(strings.rp.questsTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            rp.state.quests.forEachIndexed { idx, quest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "• $quest",
                        color = c.ink,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onUpdateState { s -> s.copy(quests = s.quests.filterIndexed { i, _ -> i != idx }) }
                        },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = "Complete",
                            tint = c.success,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuTextField(
                    value = newQuestText,
                    onValueChange = { newQuestText = it },
                    placeholder = strings.rp.addQuestPlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuButton(
                    onClick = {
                        val clean = newQuestText.trim()
                        if (clean.isNotEmpty()) {
                            onUpdateState { s -> s.copy(quests = s.quests + clean) }
                            newQuestText = ""
                        }
                    },
                    enabled = newQuestText.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addQuestBtn, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // Flags
            Text(strings.rp.flagsTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (rp.state.flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.flags.forEach { (k, v) ->
                        RpRemovableChip(
                            text = "$k = $v",
                            onRemove = { onUpdateState { s -> s.copy(flags = s.flags - k) } },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = newFlagKey,
                    onValueChange = { newFlagKey = it },
                    placeholder = strings.rp.flagKeyPlaceholder,
                    modifier = Modifier.weight(1.1f),
                )
                GuTextField(
                    value = newFlagVal,
                    onValueChange = { newFlagVal = it },
                    placeholder = strings.rp.flagValPlaceholder,
                    modifier = Modifier.weight(0.9f),
                )
                GuButton(
                    onClick = {
                        val k = newFlagKey.trim()
                        val v = newFlagVal.trim().ifBlank { "true" }
                        if (k.isNotEmpty()) {
                            onUpdateState { s -> s.copy(flags = s.flags + (k to v)) }
                            newFlagKey = ""
                            newFlagVal = ""
                        }
                    },
                    enabled = newFlagKey.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.common.add, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 4. Time Save Slots (Inline 2-tap confirm for Rewind / Delete — Zero Dialog!)
        GuSectionHeader(
            title = strings.rp.savesTab,
            subtitle = strings.rp.savesHint,
            trailing = { GuBadge("${saveSlots.size}", variant = GuBadgeVariant.BLUE) },
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    placeholder = strings.rp.saveNamePlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuTextField(
                    value = saveNote,
                    onValueChange = { saveNote = it },
                    placeholder = strings.rp.saveNotePlaceholder,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            GuButton(
                onClick = {
                    onCreateSaveSlot(saveName.trim().ifBlank { strings.rp.quickSaveBtn }, saveNote.trim())
                    saveName = ""
                    saveNote = ""
                },
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.rp.createSaveBtn, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            if (saveSlots.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                GuEmptyState(title = strings.rp.savesEmpty, icon = Icons.Default.Save)
            } else {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saveSlots.forEach { slot ->
                        val stateSnap = slot.rpConfig.state
                        val locationLine = buildString {
                            if (stateSnap.chapterTitle.isNotBlank()) {
                                append(stateSnap.chapterTitle)
                                append(" · ")
                            }
                            append("📍 ")
                            append(stateSnap.scene.ifBlank { slot.rpConfig.title })
                        }
                        val isRewindPending = confirmRewindSlotId == slot.id
                        val isDeletePending = confirmDeleteSlotId == slot.id

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.surfaceSoft,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        drawRect(
                                            color = c.primary.copy(alpha = 0.72f),
                                            topLeft = Offset.Zero,
                                            size = Size(3.dp.toPx(), size.height),
                                        )
                                    }
                                    .padding(start = 13.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = slot.name,
                                        color = c.ink,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuBadge("#${slot.messageSeq}", variant = GuBadgeVariant.BLUE)
                                }
                                if (slot.note.isNotBlank()) {
                                    Text(
                                        text = slot.note,
                                        color = c.ink.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                GuMetaText(text = locationLine)

                                if (isRewindPending) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = if (isZh) "回溯将清除后续消息，确定读档？" else "Rewind removes later messages?",
                                        confirmText = strings.rp.loadRewindBtn,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            confirmRewindSlotId = null
                                            onLoadSaveSlot(slot)
                                        },
                                        onCancel = { confirmRewindSlotId = null },
                                    )
                                } else if (isDeletePending) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = strings.rp.saveDeleteConfirm.fmt("name" to slot.name),
                                        confirmText = strings.common.delete,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            confirmDeleteSlotId = null
                                            onDeleteSaveSlot(slot.id)
                                        },
                                        onCancel = { confirmDeleteSlotId = null },
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuButton(
                                            onClick = {
                                                confirmDeleteSlotId = null
                                                confirmRewindSlotId = slot.id
                                            },
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.rp.loadRewindBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onBranchSaveSlot(slot) },
                                            compact = true,
                                            variant = GuButtonVariant.SUBTLE,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.rp.saveBranchBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onOverwriteSaveSlot(slot.id) },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Text(strings.rp.saveOverwriteBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = {
                                                confirmRewindSlotId = null
                                                confirmDeleteSlotId = slot.id
                                            },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. CG Gallery & Unlocked Endings
        GuSectionHeader(
            title = strings.rp.cgGalleryTitle,
            trailing = { GuBadge("${rp.state.unlockedCgs.size}", variant = GuBadgeVariant.BLUE) },
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuButton(
                    onClick = onGenerateSceneCg,
                    enabled = !isGeneratingArt,
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.aiGenSceneCgBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                GuButton(
                    onClick = onPickCgFromAlbum,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.cgAddFromAlbumBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!rp.state.sceneImagePath.isNullOrBlank()) {
                    GuButton(
                        onClick = onSaveCurrentSceneAsCg,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.cgSaveCurrentSceneBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rp.state.unlockedCgs.isEmpty()) {
                GuEmptyState(title = strings.rp.cgGalleryEmpty, icon = Icons.Default.Collections)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rp.state.unlockedCgs.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { cg ->
                                val isActive = cg.id == rp.state.activeCgId
                                val cgFile = remember(cg.imagePath) {
                                    cg.imagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
                                }
                                Surface(
                                    onClick = { onOpenCgDetail(cg) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = c.surfaceSoft,
                                    border = if (isActive) BorderStroke(1.5.dp, c.primary) else null,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(c.surfaceSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (cgFile != null) {
                                            Image(
                                                painter = rememberAsyncImagePainter(cgFile),
                                                contentDescription = cg.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Icon(Icons.Default.Landscape, contentDescription = null, tint = c.muted, modifier = Modifier.size(26.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        0.0f to Color.Transparent,
                                                        0.5f to Color.Transparent,
                                                        1.0f to Color(0xD90A0D14),
                                                    ),
                                                ),
                                        )
                                        if (isActive) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(7.dp),
                                                shape = RoundedCornerShape(999.dp),
                                                color = c.primary,
                                            ) {
                                                Text(
                                                    text = if (isZh) "当前" else "Active",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = cg.title,
                                            color = Color(0xFFFDF8EE),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        GuSectionHeader(
            title = strings.rp.endingsTitle,
            trailing = { GuBadge("${rp.state.unlockedEndings.size}", variant = GuBadgeVariant.HONEY) },
        )
        if (rp.state.unlockedEndings.isEmpty()) {
            GuEmptyState(title = strings.rp.endingsEmpty, icon = Icons.Default.EmojiEvents)
        } else {
            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                rp.state.unlockedEndings.forEachIndexed { idx, ending ->
                    RpEndingTrophyRow(ending = ending)
                    if (idx < rp.state.unlockedEndings.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 2: CG Viewer (Replaces RpCgLightboxDialog) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpCgViewerSubpage(
    cg: RpCgRecord,
    isActive: Boolean,
    onBack: () -> Unit,
    onApplyAsScene: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var confirmDelete by remember { mutableStateOf(false) }

    val cgFile = remember(cg.imagePath) {
        cg.imagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回状态" else "State",
            title = cg.title,
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = onApplyAsScene,
                    enabled = !isActive,
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(
                        text = if (isActive) (if (isZh) "当前场景" else "Active") else strings.rp.cgSetAsSceneBtn,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0E1320)),
                contentAlignment = Alignment.Center,
            ) {
                if (cgFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(cgFile),
                        contentDescription = cg.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = c.muted, modifier = Modifier.size(36.dp))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isActive) {
                    GuBadge(if (isZh) "当前场景" else "Active Scene", variant = GuBadgeVariant.EMERALD)
                }
                if (cg.scene.isNotBlank()) GuBadge("📍 ${cg.scene}", variant = GuBadgeVariant.NEUTRAL)
                if (cg.turnSeq > 0) GuBadge("#${cg.turnSeq}", variant = GuBadgeVariant.BLUE)
                cg.characterNames.forEach { charName -> GuBadge(charName, variant = GuBadgeVariant.HONEY) }
            }

            if (cg.caption.isNotBlank()) {
                Text(text = cg.caption, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }

            if (confirmDelete) {
                RpInlineDeleteConfirmStrip(
                    prompt = strings.rp.cgDeleteConfirm.fmt("title" to cg.title),
                    confirmText = strings.common.delete,
                    cancelText = strings.common.cancel,
                    onConfirm = onDelete,
                    onCancel = { confirmDelete = false },
                )
            } else {
                GuButton(
                    onClick = { confirmDelete = true },
                    variant = GuButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.common.delete, color = c.danger, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==================== Tab 2: 设定 (Worldview + Rules + High-Density Worldbook) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpWorldAndLoreSection(
    rp: RpConfig,
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onSetPlayStyle: (RpPlayStyle) -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onPickSceneImage: () -> Unit,
    onClearSceneImage: () -> Unit,
    onGenerateSceneCg: () -> Unit,
    onExportCardPng: () -> Unit,
    onExportCardJson: () -> Unit,
    onAddLore: () -> Unit,
    onEditLore: (NovelLoreEntry) -> Unit,
    onImportLorebookOrPreset: () -> Unit,
    onToggleLoreEnabled: (String) -> Unit,
    onToggleLoreAlwaysActive: (String) -> Unit,
    onDeleteLore: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("all") }
    var expandedLoreId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteLoreId by remember { mutableStateOf<String?>(null) }
    var worldSectionExpanded by remember { mutableStateOf(false) }

    val playStyles = remember { listOf(RpPlayStyle.chengguang, RpPlayStyle.tavern, RpPlayStyle.trpg) }
    val playStyleLabels = remember(strings) { playStyles.map { rpPlayStyleLabel(it, strings.rp) } }

    var worldview by remember(rp.worldview) { mutableStateOf(rp.worldview) }
    var systemRules by remember(rp.systemRules) { mutableStateOf(rp.systemRules) }
    var narratorStyle by remember(rp.narratorStyle) { mutableStateOf(rp.narratorStyle) }
    var authorNote by remember(rp.authorNote) { mutableStateOf(rp.authorNote) }
    var postHistory by remember(rp.postHistoryInstructions) { mutableStateOf(rp.postHistoryInstructions) }
    var mesExample by remember(rp.mesExample) { mutableStateOf(rp.mesExample) }
    val hasWorldUnsaved = worldview != rp.worldview ||
        systemRules != rp.systemRules ||
        narratorStyle != rp.narratorStyle ||
        authorNote != rp.authorNote ||
        postHistory != rp.postHistoryInstructions ||
        mesExample != rp.mesExample

    val sceneFile = remember(rp.state.sceneImagePath) {
        rp.state.sceneImagePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    val filteredLore = remember(lore, searchQuery, selectedCat) {
        val q = searchQuery.trim().lowercase()
        lore.filter { entry ->
            val catMatch = selectedCat == "all" || entry.category.equals(selectedCat, ignoreCase = true)
            val qMatch = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.content.lowercase().contains(q) ||
                entry.keywords.any { it.lowercase().contains(q) }
            catMatch && qMatch
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Worldbook Header + Search + Category Filter (Top Focus)
        item(key = "__wb_controls__") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuSectionHeader(
                    title = strings.rp.worldbookTab,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuButton(
                                onClick = onImportLorebookOrPreset,
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isZh) "导入" else "Import", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            GuButton(
                                onClick = onAddLore,
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(if (isZh) "新增" else "Add", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                )

                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = if (isZh) "搜索词条标题、关键词或设定内容…" else "Search worldbook title, keywords, content...",
                    clearContentDescription = strings.common.clear,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuPill(
                        label = if (isZh) "全部 (${lore.size})" else "All (${lore.size})",
                        selected = selectedCat == "all",
                        onClick = { selectedCat = "all" },
                        modifier = Modifier.heightIn(min = 30.dp),
                    )
                    RP_WORLDBOOK_CATEGORIES.forEach { cat ->
                        val cnt = lore.count { it.category.equals(cat, ignoreCase = true) }
                        if (cnt > 0 || selectedCat == cat) {
                            GuPill(
                                label = "${localizeRpLoreCategory(cat, strings.novel.loreCats, isZh)} $cnt",
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                modifier = Modifier.heightIn(min = 30.dp),
                            )
                        }
                    }
                }
            }
        }

        // High-density 2-line collapsible worldbook rows (5~6 entries per screen)
        if (filteredLore.isEmpty()) {
            item(key = "__wb_empty__") {
                GuEmptyState(
                    title = if (lore.isEmpty()) strings.rp.worldbookEmpty else (if (isZh) "无匹配词条" else "No matching entries"),
                    icon = Icons.Default.Book,
                )
            }
        } else {
            items(filteredLore, key = { it.id }) { entry ->
                val isExpanded = expandedLoreId == entry.id
                val isTriggered = entry.id in triggeredLoreIds
                val catLabel = localizeRpLoreCategory(entry.category, strings.novel.loreCats, isZh)
                val confirmDel = confirmDeleteLoreId == entry.id

                Surface(
                    onClick = { expandedLoreId = if (isExpanded) null else entry.id },
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (entry.enabled) 1f else 0.62f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (isTriggered) {
                                    drawRect(
                                        color = c.success,
                                        topLeft = Offset.Zero,
                                        size = Size(3.dp.toPx(), size.height),
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Line 1: [Cat] Title + Constant dot + ~tok + Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(catLabel, variant = GuBadgeVariant.HONEY)
                            Text(
                                text = entry.title,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (entry.alwaysActive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(c.primary),
                                )
                            }
                            GuMetaText(text = "~${entry.estimatedTokens.coerceAtLeast(1)}t")
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { onToggleLoreEnabled(entry.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = c.primary,
                                    uncheckedThumbColor = c.muted,
                                    uncheckedTrackColor = c.surfaceSoft,
                                ),
                                modifier = Modifier.graphicsLayer { scaleX = 0.78f; scaleY = 0.78f },
                            )
                        }

                        // Line 2: Compact Keywords or 1-line snippet
                        val subLine = if (entry.keywords.isNotEmpty()) {
                            "🔑 ${entry.keywords.joinToString(", ")}"
                        } else {
                            entry.content
                        }
                        Text(
                            text = subLine,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
                                Text(
                                    text = entry.content,
                                    color = c.ink.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                )
                                if (confirmDel) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = if (isZh) "确认删除词条「${entry.title}」？" else "Delete \"${entry.title}\"?",
                                        confirmText = strings.common.delete,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            confirmDeleteLoreId = null
                                            onDeleteLore(entry.id)
                                        },
                                        onCancel = { confirmDeleteLoreId = null },
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuButton(
                                            onClick = { onToggleLoreAlwaysActive(entry.id) },
                                            compact = true,
                                            variant = if (entry.alwaysActive) GuButtonVariant.SUBTLE else GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Text(strings.rp.worldbookConstantToggle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onEditLore(entry) },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.common.edit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { confirmDeleteLoreId = entry.id },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Worldview Backdrop, Rules & Export
        item(key = "__world_settings__") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuSectionHeader(title = strings.rp.playStyleLabel)
                GuSegmentedControl(
                    items = playStyleLabels,
                    selectedIndex = playStyles.indexOf(rp.playStyle).coerceAtLeast(0),
                    onSelect = { idx -> onSetPlayStyle(playStyles[idx]) },
                    modifier = Modifier.fillMaxWidth(),
                )

                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        GuSwitchRow(
                            label = if (isZh) "剧场模式（视觉小说舞台）" else "Theater Mode (VN Stage)",
                            checked = rp.theaterMode,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(theaterMode = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableChoicesToggle,
                            checked = rp.enableChoices,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableDiceToggle,
                            checked = rp.enableDiceCheck,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableDiceCheck = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableLedgerToggle,
                            checked = rp.enableLedger,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableLedger = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.autoSkillBonusToggle,
                            checked = rp.autoDiceSkillBonus,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(autoDiceSkillBonus = v) } },
                        )
                    }
                }

                // Scene Backdrop Card
                GuSectionHeader(title = if (isZh) "场景 CG 背景图" else "Scene CG Backdrop")
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    if (sceneFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(sceneFile),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuButton(
                            onClick = onGenerateSceneCg,
                            enabled = !isGeneratingArt,
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.aiGenSceneCgBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = onPickSceneImage,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.bindSceneImageBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (!rp.state.sceneImagePath.isNullOrBlank()) {
                            GuButton(
                                onClick = onClearSceneImage,
                                compact = true,
                                variant = GuButtonVariant.SECONDARY,
                                modifier = Modifier.heightIn(min = 34.dp),
                            ) {
                                Text(strings.rp.clearSceneImageBtn, color = c.danger, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Worldview & Prompt Settings Accordion
                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { worldSectionExpanded = !worldSectionExpanded },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isZh) "世界观底色、叙事规则与酒馆指令" else "Worldview, Rules & Tavern Prompts",
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            GuMetaText(
                                text = worldview.ifBlank { if (isZh) "点击展开编辑世界观、规则、导演备注" else "Tap to edit worldview, rules & notes" },
                            )
                        }
                        if (hasWorldUnsaved) {
                            GuBadge(if (isZh) "未保存" else "Unsaved", variant = GuBadgeVariant.AMBER)
                        }
                        Icon(
                            imageVector = if (worldSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = c.muted,
                        )
                    }

                    AnimatedVisibility(visible = worldSectionExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GuTextField(
                                value = worldview,
                                onValueChange = { worldview = it },
                                label = strings.rp.worldviewLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5,
                            )
                            GuTextField(
                                value = systemRules,
                                onValueChange = { systemRules = it },
                                label = strings.rp.systemRulesLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = narratorStyle,
                                onValueChange = { narratorStyle = it },
                                label = strings.rp.narratorStyleLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = authorNote,
                                onValueChange = { authorNote = it },
                                label = strings.rp.authorNoteLabel,
                                placeholder = strings.rp.authorNotePlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = postHistory,
                                onValueChange = { postHistory = it },
                                label = strings.rp.postHistoryLabel,
                                placeholder = strings.rp.postHistoryPlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5,
                            )
                            GuTextField(
                                value = mesExample,
                                onValueChange = { mesExample = it },
                                label = strings.rp.mesExampleLabel,
                                singleLine = false,
                                minLines = 3,
                                maxLines = 6,
                            )
                            GuButton(
                                onClick = {
                                    onUpdateConfig {
                                        it.copy(
                                            worldview = worldview.trim(),
                                            systemRules = systemRules.trim(),
                                            narratorStyle = narratorStyle.trim(),
                                            authorNote = authorNote.trim(),
                                            postHistoryInstructions = postHistory.trim(),
                                            mesExample = mesExample.trim(),
                                        )
                                    }
                                },
                                enabled = hasWorldUnsaved,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.common.save, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Export
                GuSectionHeader(title = strings.rp.exportTitle)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = strings.rp.exportStPngBtn,
                        subtitle = if (isZh) "含立绘封面与 V2/V3 元数据 (.png)" else "Portrait cover with V2/V3 metadata (.png)",
                        icon = Icons.Default.Image,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = true,
                        onClick = onExportCardPng,
                    )
                    GuSettingsRow(
                        title = strings.rp.exportStBtn,
                        subtitle = if (isZh) "结构化角色卡与世界书 (.json)" else "Structured character card & lorebook (.json)",
                        icon = Icons.Default.Download,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = onExportCardJson,
                    )
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 3: Lore Entry Editor ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpLoreEditorSubpage(
    initial: NovelLoreEntry?,
    onBack: () -> Unit,
    onSaveNew: (category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) -> Unit,
    onSaveExisting: (NovelLoreEntry) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH

    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "character") }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var keywords by remember(initial?.id) { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var alwaysActive by remember(initial?.id) { mutableStateOf(initial?.alwaysActive ?: false) }

    val canSave = title.isNotBlank() && content.isNotBlank()

    fun submit() {
        if (initial == null) {
            onSaveNew(category, title.trim(), keywords.trim(), content.trim(), alwaysActive)
        } else {
            val parsedKeywords = keywords
                .split(',', '，', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            onSaveExisting(
                initial.copy(
                    category = category,
                    title = title.trim(),
                    keywords = parsedKeywords.ifEmpty { listOf(title.trim()) },
                    content = content.trim(),
                    alwaysActive = alwaysActive,
                ),
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回设定" else "World",
            title = if (initial == null) stripLeadingPlus(strings.rp.worldbookAddBtn) else strings.common.edit,
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = ::submit,
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RP_WORLDBOOK_CATEGORIES.forEach { cat ->
                    GuPill(
                        label = localizeRpLoreCategory(cat, strings.novel.loreCats, isZh),
                        selected = category == cat,
                        onClick = { category = cat },
                        modifier = Modifier.heightIn(min = 34.dp),
                    )
                }
            }
            GuTextField(
                value = title,
                onValueChange = { title = it },
                label = if (isZh) "词条标题" else "Title",
                placeholder = strings.novel.loreTitlePlaceholder,
            )
            GuTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = if (isZh) "触发关键词" else "Keywords",
                placeholder = strings.rp.worldbookKeywordsPlaceholder,
            )
            GuTextField(
                value = content,
                onValueChange = { content = it },
                label = if (isZh) "设定内容" else "Content",
                placeholder = strings.novel.loreContentPlaceholder,
                singleLine = false,
                minLines = 5,
                maxLines = 12,
            )
            GuSwitchRow(
                label = strings.rp.worldbookConstantToggle,
                checked = alwaysActive,
                onCheckedChange = { alwaysActive = it },
            )
        }
    }
}

// ==================== Tab 3: 舞台 (Sticky 132dp Live Preview + Controls + Collapsed Accordions) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpStageDesignSection(
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
private fun RpLiveMiniStagePreview(
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
private fun RpRegexEditorSubpage(
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

@Composable
private fun RpRemovableChip(
    text: String,
    onRemove: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = c.surfaceSoft,
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun RpEndingTrophyRow(ending: RpEndingRecord) {
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
private fun RpMiniDialogStyleCard(
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
