package com.gugachat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
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

private val RP_WORLDBOOK_CATEGORIES = listOf(
    "character",
    "location",
    "item",
    "faction",
    "rule",
    "concept",
    "other",
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
        "concept" -> if (isZh) "概念" else "Concept"
        "other" -> if (isZh) "其他" else "Other"
        else -> category
    }
}

private fun localizeRpRole(role: String, isZh: Boolean): String {
    val trimmed = role.trim()
    return when (trimmed.lowercase()) {
        "romance" -> if (isZh) "攻略" else "Romance"
        "rival" -> if (isZh) "对手" else "Rival"
        "ally" -> if (isZh) "盟友" else "Ally"
        "mentor" -> if (isZh) "导师" else "Mentor"
        "companion" -> if (isZh) "同伴" else "Companion"
        "antagonist" -> if (isZh) "反派" else "Antagonist"
        "protagonist" -> if (isZh) "主角" else "Protagonist"
        "mystery" -> if (isZh) "神秘人" else "Mystery"
        "npc" -> "NPC"
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
        border = BorderStroke(1.dp, c.primary.copy(alpha = 0.35f)),
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
                    fontWeight = FontWeight.Bold,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpStatusHudBar(
    rp: RpConfig,
    triggeredLoreCount: Int = 0,
    onToggleTheaterMode: () -> Unit,
    onQuickSave: () -> Unit,
    onOpenStudio: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val leadNpc = rp.cast.firstOrNull { !it.isPlayer }
    val leadTier = leadNpc?.let { RpEngine.affinityTier(it.affinity) }
    val leadTierLabel = leadTier?.let { t ->
        strings.rp.affinityTiers[t.code] ?: if (isZh) t.zhLabel else t.enLabel
    }
    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)

    var expanded by remember(rp.title) { mutableStateOf(false) }
    var showAuthorNoteDialog by remember { mutableStateOf(false) }

    if (showAuthorNoteDialog) {
        RpAuthorNoteDialog(
            initialNote = rp.authorNote,
            onDismiss = { showAuthorNoteDialog = false },
            onSave = { note ->
                onUpdateAuthorNote(note)
                showAuthorNoteDialog = false
            },
        )
    }

    val sceneName = rp.state.scene.ifBlank { rp.title }
    val chapterPrefix = rp.state.chapterTitle.trim().takeIf { it.isNotEmpty() }
    val headerLine = if (chapterPrefix != null) "$chapterPrefix · 📍 $sceneName" else "📍 $sceneName"

    val compactStatusLine = buildString {
        append("HP ${rp.state.hp}/${rp.state.maxHp}")
        if (leadNpc != null) {
            append(" · ♥${leadNpc.affinity}")
            if (!leadTierLabel.isNullOrBlank()) append(" $leadTierLabel")
        } else {
            append(" · 🪙${rp.state.gold}")
        }
        if (triggeredLoreCount > 0) {
            append(" · 📖$triggeredLoreCount")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceSoft.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headerLine,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded) {
                    Text(
                        text = compactStatusLine,
                        color = c.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggleTheaterMode),
                shape = RoundedCornerShape(999.dp),
                color = if (rp.theaterMode) c.primary else c.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (rp.theaterMode) c.primary else c.border,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Theaters,
                        contentDescription = null,
                        tint = if (rp.theaterMode) Color.White else c.ink,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                        color = if (rp.theaterMode) Color.White else c.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (!expanded) {
                Surface(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenStudio),
                    shape = RoundedCornerShape(999.dp),
                    color = c.tag,
                    border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.rp.studioBtn,
                            color = c.tagInk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val timeWeatherLine = buildString {
                    if (rp.state.time.isNotBlank()) append("🕒 ${rp.state.time}")
                    if (rp.state.weather.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("☁ ${rp.state.weather}")
                    }
                    if (rp.state.mood.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(rp.state.mood)
                    }
                }
                if (timeWeatherLine.isNotEmpty() || triggeredLoreCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (timeWeatherLine.isNotEmpty()) {
                            Text(
                                text = timeWeatherLine,
                                color = c.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (triggeredLoreCount > 0) {
                            GuBadge(
                                text = "📖 ${strings.rp.worldbookTriggeredCount.fmt("count" to triggeredLoreCount)}",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "HP ${rp.state.hp}/${rp.state.maxHp}",
                            color = c.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "MP ${rp.state.mp}/${rp.state.maxMp}",
                            color = c.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            GuBadge(
                                text = leadTierLabel,
                                variant = affinityBadgeVariant(leadTier.code),
                            )
                            if (leadNpc.mood.isNotBlank()) {
                                GuBadge(
                                    text = leadNpc.mood,
                                    variant = GuBadgeVariant.NEUTRAL,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { leadTier.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = affinityBarColor(leadTier.code, c),
                            trackColor = c.surface,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAuthorNoteDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = strings.rp.authorNoteLabel,
                                color = c.muted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = rp.authorNote.ifBlank { strings.rp.authorNotePlaceholder },
                                color = if (rp.authorNote.isNotBlank()) c.ink else c.muted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.common.edit,
                            tint = c.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuButton(
                        onClick = onQuickSave,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.quickSaveBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(
                        onClick = onOpenStudio,
                        compact = true,
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.studioBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(
                        onClick = onToggleTheaterMode,
                        compact = true,
                        variant = if (rp.theaterMode) GuButtonVariant.PRIMARY else GuButtonVariant.SECONDARY,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(Icons.Default.Theaters, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = c.border.copy(alpha = 0.65f))
    }
}

@Composable
private fun RpAuthorNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var draft by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = strings.rp.authorNoteLabel,
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            GuTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = strings.rp.authorNotePlaceholder,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            GuButton(
                onClick = { onSave(draft.trim()) },
                compact = true,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (draft.isNotEmpty()) {
                    TextButton(onClick = { draft = "" }) {
                        Text(strings.common.clear, color = c.danger, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
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
    val c = LocalAppTheme.current.colors
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

    val active = RpPresets.findById(selectedPreset)
    val greetingCount = (if (active.config.greeting.isNotBlank()) 1 else 0) + active.config.alternateGreetings.size
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.rp.wizardTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuButton(
                    onClick = { cardLauncher.launch(arrayOf("application/json", "image/png", "*/*")) },
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.importCardBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.rp.playStyleLabel,
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            RpPlayStyle.chengguang,
                            RpPlayStyle.tavern,
                            RpPlayStyle.trpg,
                        ).forEach { style ->
                            GuPill(
                                label = rpPlayStyleLabel(style, strings.rp),
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                useHoneySelected = false,
                                modifier = Modifier.heightIn(min = 36.dp),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.rp.wizardPresetLabel,
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RpPresets.ALL.forEach { p ->
                            GuPill(
                                label = strings.rp.presets[p.id] ?: p.name,
                                selected = selectedPreset == p.id,
                                onClick = {
                                    selectedPreset = p.id
                                    selectedStyle = p.config.playStyle
                                },
                                modifier = Modifier.heightIn(min = 36.dp),
                            )
                        }
                    }
                }

                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surfaceSoft,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = active.config.title,
                            color = c.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        GuBadge(
                            text = rpPlayStyleLabel(active.config.playStyle, strings.rp),
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = active.description,
                        color = c.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
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
                    if (active.config.state.stats.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            active.config.state.stats.forEach { (k, v) ->
                                GuBadge("$k $v", variant = GuBadgeVariant.NEUTRAL)
                            }
                        }
                    }
                }

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

            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
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
                    Text(strings.rp.wizardStartBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpDiceDialog(
    rp: RpConfig? = null,
    onDismiss: () -> Unit,
    onRoll: (expression: String, skill: String?, difficulty: Int?, mode: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val isD100Ruleset = rp?.systemRules?.contains("d100", ignoreCase = true) == true

    var formula by remember(rp?.title, isD100Ruleset) {
        mutableStateOf(if (isD100Ruleset) "1d100" else "1d20")
    }
    var skill by remember(rp?.title) { mutableStateOf("") }
    var dcText by remember(rp?.title, isD100Ruleset) {
        mutableStateOf(if (isD100Ruleset) "50" else "12")
    }
    var mode by remember { mutableStateOf("normal") }

    val stats = rp?.state?.stats.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = strings.rp.diceTitle,
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("1d20", "1d100", "2d6+2", "1d12").forEach { f ->
                        GuPill(
                            label = f,
                            selected = formula == f,
                            onClick = { formula = f },
                            modifier = Modifier.heightIn(min = 36.dp),
                        )
                    }
                }

                if (stats.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.rp.statsTitle,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            stats.forEach { (statName, statVal) ->
                                GuPill(
                                    label = "$statName $statVal",
                                    selected = skill == statName,
                                    onClick = {
                                        skill = statName
                                        if (formula.contains("d100", ignoreCase = true) || isD100Ruleset) {
                                            dcText = statVal.toString()
                                        } else if (rp != null && rp.autoDiceSkillBonus && formula.startsWith("1d20", ignoreCase = true)) {
                                            formula = RpEngine.resolveSkillModifier(statName, rp).first
                                        }
                                    },
                                    useHoneySelected = true,
                                    modifier = Modifier.heightIn(min = 36.dp),
                                )
                            }
                        }
                    }
                }

                GuTextField(
                    value = formula,
                    onValueChange = { formula = it },
                    label = strings.rp.diceFormulaLabel,
                )
                GuTextField(
                    value = skill,
                    onValueChange = { skill = it },
                    label = strings.rp.diceSkillLabel,
                    placeholder = strings.rp.diceSkillPlaceholder,
                )
                GuTextField(
                    value = dcText,
                    onValueChange = { dcText = it.filter { ch -> ch.isDigit() } },
                    label = strings.rp.diceDcLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuPill(
                        label = if (isZh) "普通" else "Normal",
                        selected = mode == "normal",
                        onClick = { mode = "normal" },
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                    GuPill(
                        label = if (isZh) "优势" else "Advantage",
                        selected = mode == "advantage",
                        onClick = { mode = "advantage" },
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                    GuPill(
                        label = if (isZh) "劣势" else "Disadvantage",
                        selected = mode == "disadvantage",
                        onClick = { mode = "disadvantage" },
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    onRoll(formula.trim(), skill.trim().takeIf { it.isNotEmpty() }, dcText.toIntOrNull(), mode)
                    onDismiss()
                },
                enabled = formula.isNotBlank(),
                compact = true,
                variant = GuButtonVariant.HONEY,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(strings.rp.diceRollBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 5)) }
    val tabListState = rememberLazyListState()

    LaunchedEffect(initialTab) {
        tabListState.scrollToItem(initialTab.coerceIn(0, 5))
    }

    // Dialog targets
    var editingNpc by remember { mutableStateOf<RpCharacter?>(null) }
    var deleteNpcTarget by remember { mutableStateOf<RpCharacter?>(null) }
    var spriteManagerCharId by remember { mutableStateOf<String?>(null) }
    var viewingCgId by remember { mutableStateOf<String?>(null) }
    var deleteCgTarget by remember { mutableStateOf<RpCgRecord?>(null) }
    var editingRegex by remember { mutableStateOf<RpRegexScript?>(null) }
    var deleteRegexTarget by remember { mutableStateOf<RpRegexScript?>(null) }
    var showVitalsDialog by remember { mutableStateOf(false) }
    var showLoreDialog by remember { mutableStateOf(false) }
    var editingLore by remember { mutableStateOf<NovelLoreEntry?>(null) }
    var deleteLoreTarget by remember { mutableStateOf<NovelLoreEntry?>(null) }
    var rewindTarget by remember { mutableStateOf<RpSaveSlot?>(null) }
    var deleteSaveTarget by remember { mutableStateOf<RpSaveSlot?>(null) }

    // Photo / file launchers
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

    // Sub-dialogs
    editingNpc?.let { target ->
        val liveTarget = rp.cast.firstOrNull { it.id == target.id } ?: target
        RpNpcEditorDialog(
            initial = liveTarget,
            isExisting = target.id.isNotBlank() && rp.cast.any { it.id == target.id },
            onPickAvatar = { charId ->
                avatarTargetCharId = charId
                npcAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSave = { updated ->
                onUpsertNpc(updated)
                editingNpc = null
            },
            onDismiss = { editingNpc = null },
        )
    }

    spriteManagerCharId?.let { charId ->
        val liveNpc = rp.cast.firstOrNull { it.id == charId }
        if (liveNpc != null) {
            RpNpcSpriteManagerDialog(
                npc = liveNpc,
                isGeneratingArt = isGeneratingArt,
                artStatusLabel = artStatusLabel,
                onSaveNpc = onUpsertNpc,
                onPickExpressionSprite = { exprKey ->
                    spritePickerTarget = liveNpc.id to exprKey
                    npcSpritePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClearExpressionSprite = { exprKey ->
                    onClearNpcSprite(liveNpc.id, exprKey)
                },
                onGenerateExpressionSprite = { exprKey ->
                    onGenerateNpcSprite(liveNpc.id, exprKey, "")
                },
                onDismiss = { spriteManagerCharId = null },
            )
        } else {
            spriteManagerCharId = null
        }
    }

    deleteNpcTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.rp.deleteNpcConfirm.fmt("name" to target.name),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteNpc(target.id)
                deleteNpcTarget = null
            },
            onDismiss = { deleteNpcTarget = null },
        )
    }

    viewingCgId?.let { cgId ->
        val liveCg = rp.state.unlockedCgs.firstOrNull { it.id == cgId }
        if (liveCg != null) {
            RpCgLightboxDialog(
                cg = liveCg,
                isActive = liveCg.id == rp.state.activeCgId,
                onApplyAsScene = { onApplyCgAsScene(liveCg) },
                onRequestDelete = { deleteCgTarget = liveCg },
                onDismiss = { viewingCgId = null },
            )
        } else {
            viewingCgId = null
        }
    }

    deleteCgTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.rp.cgDeleteConfirm.fmt("title" to target.title),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteCg(target.id)
                if (viewingCgId == target.id) viewingCgId = null
                deleteCgTarget = null
            },
            onDismiss = { deleteCgTarget = null },
        )
    }

    editingRegex?.let { target ->
        RpRegexScriptEditorDialog(
            initial = target,
            isExisting = target.id.isNotBlank() && rp.regexScripts.any { it.id == target.id },
            onSave = { updated ->
                onUpsertRegexScript(updated)
                editingRegex = null
            },
            onDismiss = { editingRegex = null },
        )
    }

    deleteRegexTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.rp.regexDeleteConfirm.fmt("name" to target.name),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteRegexScript(target.id)
                deleteRegexTarget = null
            },
            onDismiss = { deleteRegexTarget = null },
        )
    }

    if (showVitalsDialog) {
        RpVitalsEditorDialog(
            state = rp.state,
            onSave = { nextState ->
                onUpdateState { nextState }
                showVitalsDialog = false
            },
            onDismiss = { showVitalsDialog = false },
        )
    }

    if (showLoreDialog) {
        RpLoreEditorDialog(
            initial = editingLore,
            onSaveNew = { cat, title, keywords, content, alwaysActive ->
                onAddLore(cat, title, keywords, content, alwaysActive)
                showLoreDialog = false
                editingLore = null
            },
            onSaveExisting = { updated ->
                onUpdateLore(updated)
                showLoreDialog = false
                editingLore = null
            },
            onDismiss = {
                showLoreDialog = false
                editingLore = null
            },
        )
    }

    deleteLoreTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = if (isZh) "确定删除世界书词条「${target.title}」？" else "Delete worldbook entry \"${target.title}\"?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteLore(target.id)
                deleteLoreTarget = null
            },
            onDismiss = { deleteLoreTarget = null },
        )
    }

    rewindTarget?.let { target ->
        GuConfirmDialog(
            title = strings.rp.loadRewindBtn,
            message = if (isZh) {
                "确定回溯读档至「${target.name}」？该存档节点之后的对话消息将被永久清除。"
            } else {
                "Rewind to \"${target.name}\"? Messages after this save point will be permanently removed."
            },
            confirmText = strings.rp.loadRewindBtn,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onLoadSaveSlot(target)
                rewindTarget = null
                onDismiss()
            },
            onDismiss = { rewindTarget = null },
        )
    }

    deleteSaveTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.rp.saveDeleteConfirm.fmt("name" to target.name),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteSaveSlot(target.id)
                deleteSaveTarget = null
            },
            onDismiss = { deleteSaveTarget = null },
        )
    }

    val tabs = remember(strings) {
        listOf(
            0 to strings.rp.castTab,
            1 to strings.rp.rpgTab,
            2 to strings.rp.worldbookTab,
            3 to strings.rp.frontendTab,
            4 to strings.rp.savesTab,
            5 to strings.rp.worldTab,
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.rp.studioTitle,
                subtitle = rp.title,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyRow(
                state = tabListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.first }) { (idx, label) ->
                    GuPill(
                        label = label,
                        selected = tab == idx,
                        onClick = { tab = idx },
                        useHoneySelected = false,
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }

            AnimatedVisibility(visible = isGeneratingArt) {
                RpArtProgressBanner(
                    statusLabel = artStatusLabel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                )
            }

            when (tab) {
                0 -> RpCastTab(
                    cast = rp.cast,
                    isGeneratingArt = isGeneratingArt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onAddNpc = {
                        editingNpc = RpCharacter(
                            id = "",
                            name = "",
                            role = "companion",
                            mood = if (isZh) "平静" else "Calm",
                            inScene = true,
                        )
                    },
                    onManageSprites = { spriteManagerCharId = it.id },
                    onGenerateSprite = { npc -> onGenerateNpcSprite(npc.id, "neutral", "") },
                    onEditNpc = { editingNpc = it },
                    onDeleteNpc = { deleteNpcTarget = it },
                    onAdjustAffinity = onAdjustAffinity,
                )

                1 -> RpStatsTab(
                    rp = rp,
                    isGeneratingArt = isGeneratingArt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onOpenVitalsEditor = { showVitalsDialog = true },
                    onUpdateState = onUpdateState,
                    onGenerateSceneCg = { onGenerateSceneCg("", true) },
                    onPickCgFromAlbum = {
                        cgImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSaveCurrentSceneAsCg = { onSaveCurrentSceneAsCg(rp.state.scene) },
                    onViewCg = { cg -> viewingCgId = cg.id },
                )

                2 -> RpWorldbookTab(
                    lore = lore,
                    triggeredLoreIds = triggeredLoreIds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onAddEntry = {
                        editingLore = null
                        showLoreDialog = true
                    },
                    onEditEntry = { entry ->
                        editingLore = entry
                        showLoreDialog = true
                    },
                    onToggleEnabled = onToggleLoreEnabled,
                    onToggleAlwaysActive = onToggleLoreAlwaysActive,
                    onRequestDelete = { deleteLoreTarget = it },
                )

                3 -> RpFrontendTab(
                    rp = rp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onUpdateVisualTheme = onUpdateVisualTheme,
                    onResetVisualTheme = onResetVisualTheme,
                    onUpdateConfig = onUpdateConfig,
                    onApplyStatusWidgetTemplate = onApplyStatusWidgetTemplate,
                    onAddRegexScript = {
                        editingRegex = RpRegexScript(
                            id = "",
                            name = "",
                            enabled = true,
                            findRegex = "",
                            replaceString = "",
                            placement = "ai_output",
                            markdownOnly = true,
                            flags = "gms",
                        )
                    },
                    onEditRegexScript = { editingRegex = it },
                    onToggleRegexScript = onToggleRegexScript,
                    onRequestDeleteRegex = { deleteRegexTarget = it },
                    onApplyRegexPreset = onApplyRegexPreset,
                )

                4 -> RpSavesTab(
                    saveSlots = saveSlots,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onCreateSaveSlot = onCreateSaveSlot,
                    onRequestRewind = { rewindTarget = it },
                    onBranchSaveSlot = { slot ->
                        onBranchSaveSlot(slot)
                        onDismiss()
                    },
                    onOverwriteSaveSlot = onOverwriteSaveSlot,
                    onRequestDelete = { deleteSaveTarget = it },
                )

                else -> RpWorldAndExportTab(
                    rp = rp,
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
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpCastTab(
    cast: List<RpCharacter>,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onAddNpc: () -> Unit,
    onManageSprites: (RpCharacter) -> Unit,
    onGenerateSprite: (RpCharacter) -> Unit,
    onEditNpc: (RpCharacter) -> Unit,
    onDeleteNpc: (RpCharacter) -> Unit,
    onAdjustAffinity: (String, Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__cast_add__") {
            GuButton(
                onClick = onAddNpc,
                variant = GuButtonVariant.SUBTLE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stripLeadingPlus(strings.rp.addNpcBtn), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (cast.isEmpty()) {
            item(key = "__cast_empty__") {
                GuEmptyState(
                    title = if (isZh) "暂无角色" else "No characters yet",
                    subtitle = if (isZh) "添加同伴或关键 NPC 以追踪好感、立绘与秘密" else "Add companions or key NPCs to track portraits and affinity",
                )
            }
        } else {
            items(cast, key = { it.id }) { npc ->
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
                val slotLabel = localizeStageSlot(npc.stageSlot, strings.rp)
                val tier = RpEngine.affinityTier(npc.affinity)
                val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel

                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.primarySoft)
                                .border(1.5.dp, c.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable { onManageSprites(npc) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (portraitFile != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(portraitFile),
                                    contentDescription = npc.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = npc.name.trim().take(1).uppercase().ifBlank { "?" },
                                    color = c.primarySoftInk,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
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
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (npc.role.isNotBlank()) {
                                    Text(
                                        text = localizeRpRole(npc.role, isZh),
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (npc.isPlayer) {
                                    GuBadge(if (isZh) "玩家角色" else "Player", variant = GuBadgeVariant.BLUE)
                                } else {
                                    GuBadge(
                                        text = if (npc.inScene) {
                                            strings.rp.npcInSceneToggle
                                        } else {
                                            if (isZh) "暂离场" else "Away"
                                        },
                                        variant = if (npc.inScene) GuBadgeVariant.EMERALD else GuBadgeVariant.NEUTRAL,
                                    )
                                    GuBadge(
                                        text = slotLabel,
                                        variant = if (npc.stageSlot.equals("auto", ignoreCase = true)) {
                                            GuBadgeVariant.NEUTRAL
                                        } else {
                                            GuBadgeVariant.HONEY
                                        },
                                    )
                                    if (boundExprCount > 0) {
                                        GuBadge(
                                            text = if (isZh) "立绘 $boundExprCount/8" else "Sprites $boundExprCount/8",
                                            variant = GuBadgeVariant.BLUE,
                                        )
                                    }
                                    if (npc.mood.isNotBlank()) {
                                        GuBadge(npc.mood, variant = GuBadgeVariant.NEUTRAL)
                                    }
                                }
                            }
                        }
                    }

                    if (npc.personality.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = npc.personality,
                            color = c.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (npc.speakingStyle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "💬 ${npc.speakingStyle}",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (npc.secret.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🔒 ${npc.secret}",
                            color = c.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!npc.isPlayer) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = affinityBarColor(tier.code, c),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = strings.rp.npcAffinityLabel.fmt("val" to npc.affinity),
                                color = c.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            GuBadge(tierLabel, variant = affinityBadgeVariant(tier.code))
                            Spacer(Modifier.weight(1f))
                            GuButton(
                                onClick = { onAdjustAffinity(npc.id, -5) },
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp),
                            ) {
                                Text("-5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = { onAdjustAffinity(npc.id, 5) },
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp),
                            ) {
                                Text("+5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { tier.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = affinityBarColor(tier.code, c),
                            trackColor = c.surfaceSoft,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuButton(
                            onClick = { onManageSprites(npc) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isZh) "立绘与差分" else strings.rp.expressionsTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        GuButton(
                            onClick = { onGenerateSprite(npc) },
                            enabled = !isGeneratingArt,
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.aiGenSpriteBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onEditNpc(npc) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.common.edit, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onDeleteNpc(npc) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = c.danger,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = strings.common.delete,
                                color = c.danger,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpNpcEditorDialog(
    initial: RpCharacter,
    isExisting: Boolean,
    onPickAvatar: (String) -> Unit,
    onSave: (RpCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var role by remember(initial.id) { mutableStateOf(initial.role) }
    var mood by remember(initial.id) { mutableStateOf(initial.mood) }
    var appearance by remember(initial.id) { mutableStateOf(initial.appearance) }
    var appearancePrompt by remember(initial.id) { mutableStateOf(initial.appearancePrompt) }
    var personality by remember(initial.id) { mutableStateOf(initial.personality) }
    var speakingStyle by remember(initial.id) { mutableStateOf(initial.speakingStyle) }
    var secret by remember(initial.id) { mutableStateOf(initial.secret) }
    var inScene by remember(initial.id) { mutableStateOf(initial.inScene) }
    var affinity by remember(initial.id) { mutableIntStateOf(initial.affinity.coerceIn(-100, 100)) }

    val avatarFile = remember(initial.avatarPath) {
        initial.avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val tier = RpEngine.affinityTier(affinity)
    val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = if (isExisting) strings.rp.editNpcTitle else stripLeadingPlus(strings.rp.addNpcBtn),
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isExisting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(c.primarySoft)
                                .border(1.5.dp, c.primary.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarFile != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(avatarFile),
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = name.trim().take(1).uppercase().ifBlank { "?" },
                                    color = c.primarySoftInk,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        GuButton(
                            onClick = { onPickAvatar(initial.id) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(strings.rp.npcAvatarBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                GuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = strings.rp.npcNameLabel,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = strings.rp.npcRoleLabel,
                        modifier = Modifier.weight(1f),
                    )
                    GuTextField(
                        value = mood,
                        onValueChange = { mood = it },
                        label = strings.rp.npcMoodLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
                GuTextField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = strings.rp.npcAppearanceLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                GuTextField(
                    value = appearancePrompt,
                    onValueChange = { appearancePrompt = it },
                    label = strings.rp.appearancePromptLabel,
                    placeholder = strings.rp.appearancePromptPlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                GuTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = strings.rp.leadPersonalityLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                GuTextField(
                    value = speakingStyle,
                    onValueChange = { speakingStyle = it },
                    label = strings.rp.npcSpeakingStyleLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                GuTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = strings.rp.npcSecretLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                GuSwitchRow(
                    label = strings.rp.npcInSceneToggle,
                    checked = inScene,
                    onCheckedChange = { inScene = it },
                )
                if (!initial.isPlayer) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.rp.npcAffinityLabel.fmt("val" to affinity),
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
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
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    val saved = initial.copy(
                        id = initial.id.ifBlank { UUID.randomUUID().toString() },
                        name = name.trim(),
                        role = role.trim().ifBlank { "companion" },
                        mood = mood.trim(),
                        appearance = appearance.trim(),
                        appearancePrompt = appearancePrompt.trim(),
                        personality = personality.trim(),
                        speakingStyle = speakingStyle.trim(),
                        secret = secret.trim(),
                        inScene = inScene,
                        affinity = affinity.coerceIn(-100, 100),
                    )
                    onSave(saved)
                },
                enabled = name.isNotBlank(),
                compact = true,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpNpcSpriteManagerDialog(
    npc: RpCharacter,
    isGeneratingArt: Boolean,
    artStatusLabel: String,
    onSaveNpc: (RpCharacter) -> Unit,
    onPickExpressionSprite: (expressionKey: String) -> Unit,
    onClearExpressionSprite: (expressionKey: String) -> Unit,
    onGenerateExpressionSprite: (expressionKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var stageSlot by remember(npc.id, npc.stageSlot) { mutableStateOf(npc.stageSlot.ifBlank { "auto" }) }
    var spriteScale by remember(npc.id, npc.spriteScale) { mutableFloatStateOf(npc.spriteScale.coerceIn(0.7f, 1.4f)) }
    var spriteOffsetY by remember(npc.id, npc.spriteOffsetY) { mutableFloatStateOf(npc.spriteOffsetY.coerceIn(-60f, 60f)) }
    var appearancePrompt by remember(npc.id, npc.appearancePrompt) { mutableStateOf(npc.appearancePrompt) }

    val hasStageChanges = stageSlot != npc.stageSlot ||
        kotlin.math.abs(spriteScale - npc.spriteScale) > 0.005f ||
        kotlin.math.abs(spriteOffsetY - npc.spriteOffsetY) > 0.5f ||
        appearancePrompt.trim() != npc.appearancePrompt.trim()

    val boundCount = remember(npc.expressionSprites) {
        RpEngine.STANDARD_EXPRESSIONS.count { key ->
            npc.expressionSprites.entries.any {
                it.key.equals(key, ignoreCase = true) && it.value.isNotBlank()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            color = c.page,
            border = BorderStroke(1.dp, c.outline),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GuSheetHeader(
                    title = "${npc.name} · ${strings.rp.expressionsTitle}",
                    subtitle = if (isZh) "已绑定 $boundCount / 8 种表情差分" else "$boundCount / 8 expression sprites bound",
                    onClose = onDismiss,
                    closeContentDescription = strings.common.close,
                )

                AnimatedVisibility(visible = isGeneratingArt) {
                    RpArtProgressBanner(
                        statusLabel = artStatusLabel,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Stage positioning & AI art appearance anchor
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.rp.stageSlotLabel,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (hasStageChanges) {
                                GuBadge(if (isZh) "未保存" else "Unsaved", variant = GuBadgeVariant.AMBER)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RP_STAGE_SLOT_KEYS.forEach { key ->
                                GuPill(
                                    label = localizeStageSlot(key, strings.rp),
                                    selected = stageSlot.equals(key, ignoreCase = true),
                                    onClick = { stageSlot = key },
                                    modifier = Modifier.heightIn(min = 36.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.rp.spriteScaleLabel,
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            GuBadge(
                                text = "${(spriteScale * 100f).roundToInt()}%",
                                variant = GuBadgeVariant.BLUE,
                            )
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
                            Text(
                                text = strings.rp.spriteOffsetYLabel,
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            GuBadge(
                                text = "${spriteOffsetY.roundToInt()} dp",
                                variant = GuBadgeVariant.NEUTRAL,
                            )
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

                        Spacer(Modifier.height(6.dp))
                        GuTextField(
                            value = appearancePrompt,
                            onValueChange = { appearancePrompt = it },
                            label = strings.rp.appearancePromptLabel,
                            placeholder = strings.rp.appearancePromptPlaceholder,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                        Spacer(Modifier.height(10.dp))
                        GuButton(
                            onClick = {
                                onSaveNpc(
                                    npc.copy(
                                        stageSlot = stageSlot,
                                        spriteScale = spriteScale.coerceIn(0.7f, 1.4f),
                                        spriteOffsetY = spriteOffsetY.roundToInt().toFloat().coerceIn(-60f, 60f),
                                        appearancePrompt = appearancePrompt.trim(),
                                    ),
                                )
                            },
                            enabled = hasStageChanges,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.common.save, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 8 Expression Sprite Slots
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.rp.expressionsTitle,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge("$boundCount / 8", variant = GuBadgeVariant.BLUE)
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RpEngine.STANDARD_EXPRESSIONS.forEach { exprKey ->
                                val boundPath = npc.expressionSprites.entries
                                    .firstOrNull { it.key.equals(exprKey, ignoreCase = true) }
                                    ?.value
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                val exprFile = remember(boundPath) {
                                    boundPath?.let(::File)?.takeIf { it.isFile }
                                }
                                val exprLabel = strings.rp.expressionLabels[exprKey] ?: exprKey

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (exprFile != null) c.primary.copy(alpha = 0.45f) else c.border,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 50.dp, height = 64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(c.surface)
                                                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                                                .clickable { onPickExpressionSprite(exprKey) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (exprFile != null) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(exprFile),
                                                    contentDescription = exprLabel,
                                                    contentScale = ContentScale.Crop,
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
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (boundPath != null) {
                                                    GuBadge(
                                                        text = if (isZh) "已设" else "Set",
                                                        variant = GuBadgeVariant.EMERALD,
                                                    )
                                                }
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                GuButton(
                                                    onClick = { onPickExpressionSprite(exprKey) },
                                                    compact = true,
                                                    variant = GuButtonVariant.SUBTLE,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.heightIn(min = 34.dp),
                                                ) {
                                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(strings.rp.pickSpriteBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                                GuButton(
                                                    onClick = { onGenerateExpressionSprite(exprKey) },
                                                    enabled = !isGeneratingArt,
                                                    compact = true,
                                                    variant = GuButtonVariant.HONEY,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.heightIn(min = 34.dp),
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(strings.rp.aiGenExpressionBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                                if (boundPath != null) {
                                                    GuButton(
                                                        onClick = { onClearExpressionSprite(exprKey) },
                                                        compact = true,
                                                        variant = GuButtonVariant.SECONDARY,
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.heightIn(min = 34.dp),
                                                    ) {
                                                        Text(
                                                            text = strings.rp.clearSpriteBtn,
                                                            color = c.danger,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        )
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
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpStatsTab(
    rp: RpConfig,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onOpenVitalsEditor: () -> Unit,
    onUpdateState: ((RpGameState) -> RpGameState) -> Unit,
    onGenerateSceneCg: () -> Unit,
    onPickCgFromAlbum: () -> Unit,
    onSaveCurrentSceneAsCg: () -> Unit,
    onViewCg: (RpCgRecord) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var newStatName by remember { mutableStateOf("") }
    var newStatVal by remember { mutableStateOf("") }
    var editingStat by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var newItemText by remember { mutableStateOf("") }
    var newQuestText by remember { mutableStateOf("") }
    var newFlagKey by remember { mutableStateOf("") }
    var newFlagVal by remember { mutableStateOf("") }

    editingStat?.let { (statKey, statCurrentVal) ->
        var draftVal by remember(statKey, statCurrentVal) { mutableStateOf(statCurrentVal.toString()) }
        AlertDialog(
            onDismissRequest = { editingStat = null },
            containerColor = c.surfaceElevated,
            title = { Text(statKey, color = c.ink, fontWeight = FontWeight.ExtraBold) },
            text = {
                GuTextField(
                    value = draftVal,
                    onValueChange = { draftVal = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = strings.rp.statValuePlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                GuButton(
                    onClick = {
                        val parsed = draftVal.toIntOrNull()
                        if (parsed != null) {
                            onUpdateState { s -> s.copy(stats = s.stats + (statKey to parsed)) }
                        }
                        editingStat = null
                    },
                    enabled = draftVal.toIntOrNull() != null,
                    compact = true,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStat = null }) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Scene, Chapter, Time, Weather, HP, MP, Gold
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val chapterText = formatRpActChapter(rp.state.actNumber, rp.state.chapterTitle, isZh)
                    Text(
                        text = chapterText,
                        color = c.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "📍 ${rp.state.scene.ifBlank { rp.title }}",
                        color = c.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                    )
                    if (rp.state.time.isNotBlank() || rp.state.weather.isNotBlank()) {
                        val weatherPart = rp.state.weather.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                        Text(
                            text = "${rp.state.time}$weatherPart",
                            color = c.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                GuButton(
                    onClick = onOpenVitalsEditor,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.common.edit, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("HP ${rp.state.hp}/${rp.state.maxHp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(hp = (s.hp - 10).coerceIn(0, s.maxHp)) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("-10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(hp = (s.hp + 10).coerceIn(0, s.maxHp)) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("+10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("MP ${rp.state.mp}/${rp.state.maxMp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(mp = (s.mp - 10).coerceIn(0, s.maxMp)) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("-10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(mp = (s.mp + 10).coerceIn(0, s.maxMp)) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("+10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.HONEY)
                Spacer(Modifier.weight(1f))
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(gold = (s.gold - 10).coerceAtLeast(0)) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("-10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = { onUpdateState { s -> s.copy(gold = s.gold + 10) } },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("+10", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 1b. Event & Scene CG Gallery
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.rp.cgGalleryTitle,
                    color = c.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f),
                )
                GuBadge("${rp.state.unlockedCgs.size}", variant = GuBadgeVariant.BLUE)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuButton(
                    onClick = onGenerateSceneCg,
                    enabled = !isGeneratingArt,
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.aiGenSceneCgBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = onPickCgFromAlbum,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.cgAddFromAlbumBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                if (!rp.state.sceneImagePath.isNullOrBlank()) {
                    GuButton(
                        onClick = onSaveCurrentSceneAsCg,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.cgSaveCurrentSceneBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rp.state.unlockedCgs.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Text(
                        text = strings.rp.cgGalleryEmpty,
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
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
                                    onClick = { onViewCg(cg) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) c.primary else c.border,
                                    ),
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(104.dp)
                                                .background(c.surface),
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
                                                Icon(
                                                    imageVector = Icons.Default.Landscape,
                                                    contentDescription = null,
                                                    tint = c.muted,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                            if (isActive) {
                                                Surface(
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(6.dp),
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = c.primary,
                                                ) {
                                                    Text(
                                                        text = if (isZh) "当前场景" else "Active",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = cg.title,
                                                color = c.ink,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val subtitle = cg.scene.takeIf { it.isNotBlank() && it != cg.title }
                                                ?: cg.caption.takeIf { it.isNotBlank() }
                                            if (subtitle != null) {
                                                Text(
                                                    text = subtitle,
                                                    color = c.muted,
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 2. Attributes (stats)
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.rp.statsTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(8.dp))
            if (rp.state.stats.isEmpty()) {
                Text(
                    text = if (isZh) "暂无自定义属性" else "No custom attributes yet",
                    color = c.muted,
                    fontSize = 12.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rp.state.stats.forEach { (statName, statValue) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.surfaceSoft,
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = statName,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = {
                                        onUpdateState { s ->
                                            s.copy(stats = s.stats + (statName to (statValue - 1)))
                                        }
                                    },
                                    compact = true,
                                    variant = GuButtonVariant.SECONDARY,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 36.dp),
                                ) {
                                    Text("-1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                GuBadge(
                                    text = "$statValue",
                                    variant = GuBadgeVariant.BLUE,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { editingStat = statName to statValue },
                                )
                                GuButton(
                                    onClick = {
                                        onUpdateState { s ->
                                            s.copy(stats = s.stats + (statName to (statValue + 1)))
                                        }
                                    },
                                    compact = true,
                                    variant = GuButtonVariant.SECONDARY,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 36.dp),
                                ) {
                                    Text("+1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { onUpdateState { s -> s.copy(stats = s.stats - statName) } },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.common.delete,
                                        tint = c.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
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

        // 3. Inventory
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.rp.inventoryTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(8.dp))
            if (rp.state.inventory.isEmpty()) {
                Text(
                    text = if (isZh) "背包空空如也" else "No items in inventory",
                    color = c.muted,
                    fontSize = 12.sp,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.inventory.forEachIndexed { idx, item ->
                        RpRemovableChip(
                            text = item,
                            onRemove = {
                                onUpdateState { s ->
                                    s.copy(inventory = s.inventory.filterIndexed { i, _ -> i != idx })
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
        }

        // 4. Quests
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.rp.questsTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(8.dp))
            if (rp.state.quests.isEmpty()) {
                Text(
                    text = if (isZh) "暂无进行中的任务" else "No active quests",
                    color = c.muted,
                    fontSize = 12.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rp.state.quests.forEachIndexed { idx, quest ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.surfaceSoft,
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "• $quest",
                                    color = c.ink,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        onUpdateState { s ->
                                            s.copy(quests = s.quests.filterIndexed { i, _ -> i != idx })
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircleOutline,
                                        contentDescription = strings.rp.questStatuses["completed"] ?: "Complete",
                                        tint = c.success,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
        }

        // 5. Story Flags
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.rp.flagsTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            Spacer(Modifier.height(8.dp))
            if (rp.state.flags.isEmpty()) {
                Text(
                    text = if (isZh) "暂无剧情旗标" else "No story flags set",
                    color = c.muted,
                    fontSize = 12.sp,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.flags.forEach { (k, v) ->
                        RpRemovableChip(
                            text = "$k = $v",
                            onRemove = {
                                onUpdateState { s -> s.copy(flags = s.flags - k) }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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

        // 6. Unlocked Endings
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.rp.endingsTitle,
                    color = c.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f),
                )
                GuBadge("${rp.state.unlockedEndings.size}", variant = GuBadgeVariant.HONEY)
            }
            Spacer(Modifier.height(8.dp))
            if (rp.state.unlockedEndings.isEmpty()) {
                Text(
                    text = strings.rp.endingsEmpty,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rp.state.unlockedEndings.forEach { ending ->
                        RpEndingTrophyRow(ending = ending)
                    }
                }
            }
        }
    }
}

@Composable
private fun RpRemovableChip(
    text: String,
    onRemove: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = c.surfaceSoft,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RpEndingTrophyRow(ending: RpEndingRecord) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceSoft,
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
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
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun RpVitalsEditorDialog(
    state: RpGameState,
    onSave: (RpGameState) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var chapterTitle by remember(state) { mutableStateOf(state.chapterTitle) }
    var actNumber by remember(state) { mutableStateOf(state.actNumber.toString()) }
    var scene by remember(state) { mutableStateOf(state.scene) }
    var time by remember(state) { mutableStateOf(state.time) }
    var weather by remember(state) { mutableStateOf(state.weather) }
    var hp by remember(state) { mutableStateOf(state.hp.toString()) }
    var maxHp by remember(state) { mutableStateOf(state.maxHp.toString()) }
    var mp by remember(state) { mutableStateOf(state.mp.toString()) }
    var maxMp by remember(state) { mutableStateOf(state.maxMp.toString()) }
    var gold by remember(state) { mutableStateOf(state.gold.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = if (isZh) "编辑场景与基础数值" else "Edit Scene & Vitals",
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuTextField(
                        value = actNumber,
                        onValueChange = { actNumber = it.filter { ch -> ch.isDigit() } },
                        label = if (isZh) "幕次" else "Act",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f),
                    )
                    GuTextField(
                        value = chapterTitle,
                        onValueChange = { chapterTitle = it },
                        label = strings.rp.chapterLabel,
                        modifier = Modifier.weight(0.65f),
                    )
                }
                GuTextField(value = scene, onValueChange = { scene = it }, label = strings.rp.sceneLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = strings.rp.timeLabel,
                        modifier = Modifier.weight(1f),
                    )
                    GuTextField(
                        value = weather,
                        onValueChange = { weather = it },
                        label = strings.rp.weatherLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuTextField(
                        value = hp,
                        onValueChange = { hp = it.filter { ch -> ch.isDigit() } },
                        label = "HP",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    GuTextField(
                        value = maxHp,
                        onValueChange = { maxHp = it.filter { ch -> ch.isDigit() } },
                        label = "Max HP",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuTextField(
                        value = mp,
                        onValueChange = { mp = it.filter { ch -> ch.isDigit() } },
                        label = "MP",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    GuTextField(
                        value = maxMp,
                        onValueChange = { maxMp = it.filter { ch -> ch.isDigit() } },
                        label = "Max MP",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                GuTextField(
                    value = gold,
                    onValueChange = { gold = it.filter { ch -> ch.isDigit() } },
                    label = if (isZh) "金币 / 信用点" else "Gold / Credits",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    val nextMaxHp = (maxHp.toIntOrNull() ?: state.maxHp).coerceAtLeast(1)
                    val nextMaxMp = (maxMp.toIntOrNull() ?: state.maxMp).coerceAtLeast(1)
                    onSave(
                        state.copy(
                            actNumber = (actNumber.toIntOrNull() ?: state.actNumber).coerceAtLeast(1),
                            chapterTitle = chapterTitle.trim(),
                            scene = scene.trim(),
                            time = time.trim(),
                            weather = weather.trim(),
                            maxHp = nextMaxHp,
                            hp = (hp.toIntOrNull() ?: state.hp).coerceIn(0, nextMaxHp),
                            maxMp = nextMaxMp,
                            mp = (mp.toIntOrNull() ?: state.mp).coerceIn(0, nextMaxMp),
                            gold = (gold.toIntOrNull() ?: state.gold).coerceAtLeast(0),
                        ),
                    )
                },
                compact = true,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpWorldbookTab(
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    modifier: Modifier = Modifier,
    onAddEntry: () -> Unit,
    onEditEntry: (NovelLoreEntry) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onToggleAlwaysActive: (String) -> Unit,
    onRequestDelete: (NovelLoreEntry) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val constantCount = remember(lore) { lore.count { it.enabled && it.alwaysActive } }
    val triggeredCount = remember(lore, triggeredLoreIds) { lore.count { it.id in triggeredLoreIds } }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__wb_summary__") {
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = c.surfaceSoft,
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuBadge(
                        text = if (isZh) "共 ${lore.size} 条" else "${lore.size} total",
                        variant = GuBadgeVariant.NEUTRAL,
                    )
                    GuBadge(
                        text = "${strings.rp.worldbookConstantToggle} $constantCount",
                        variant = GuBadgeVariant.BLUE,
                    )
                    GuBadge(
                        text = strings.rp.worldbookTriggeredCount.fmt("count" to triggeredCount),
                        variant = if (triggeredCount > 0) GuBadgeVariant.EMERALD else GuBadgeVariant.NEUTRAL,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GuButton(
                    onClick = onAddEntry,
                    variant = GuButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stripLeadingPlus(strings.rp.worldbookAddBtn), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (lore.isEmpty()) {
            item(key = "__wb_empty__") {
                GuEmptyState(title = strings.rp.worldbookEmpty)
            }
        } else {
            items(lore, key = { it.id }) { entry ->
                val isTriggered = entry.id in triggeredLoreIds
                val catLabel = localizeRpLoreCategory(entry.category, strings.novel.loreCats, isZh)

                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isTriggered) c.success.copy(alpha = 0.6f) else c.border,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuBadge(catLabel, variant = GuBadgeVariant.HONEY)
                        Text(
                            text = entry.title,
                            color = if (entry.enabled) c.ink else c.muted,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { onToggleEnabled(entry.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.primary,
                                checkedBorderColor = c.primary,
                                uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                                uncheckedTrackColor = c.surfaceSoft,
                                uncheckedBorderColor = if (theme.isDark) Color(0xFF526280) else c.outline,
                            ),
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (entry.alwaysActive) {
                            GuBadge(strings.rp.worldbookConstantToggle, variant = GuBadgeVariant.BLUE)
                        }
                        if (isTriggered) {
                            GuBadge(
                                text = if (isZh) "已触发" else "Triggered",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                        GuBadge(
                            text = "~${entry.estimatedTokens.coerceAtLeast(1)} tok",
                            variant = GuBadgeVariant.NEUTRAL,
                        )
                    }

                    if (entry.keywords.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "🔑 ${entry.keywords.joinToString(", ")}",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = entry.content,
                        color = if (entry.enabled) c.ink.copy(alpha = 0.85f) else c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuButton(
                            onClick = { onToggleAlwaysActive(entry.id) },
                            compact = true,
                            variant = if (entry.alwaysActive) GuButtonVariant.SUBTLE else GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Text(strings.rp.worldbookConstantToggle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onEditEntry(entry) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.common.edit, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onRequestDelete(entry) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = c.danger,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = strings.common.delete,
                                color = c.danger,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpLoreEditorDialog(
    initial: NovelLoreEntry?,
    onSaveNew: (category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) -> Unit,
    onSaveExisting: (NovelLoreEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "character") }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var keywords by remember(initial?.id) { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var alwaysActive by remember(initial?.id) { mutableStateOf(initial?.alwaysActive ?: false) }

    val canSave = title.isNotBlank() && content.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = if (initial == null) stripLeadingPlus(strings.rp.worldbookAddBtn) else strings.common.edit,
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            modifier = Modifier.heightIn(min = 36.dp),
                        )
                    }
                }
                GuTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = strings.novel.loreTitlePlaceholder,
                )
                GuTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    placeholder = strings.rp.worldbookKeywordsPlaceholder,
                )
                GuTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = strings.novel.loreContentPlaceholder,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
                GuSwitchRow(
                    label = strings.rp.worldbookConstantToggle,
                    checked = alwaysActive,
                    onCheckedChange = { alwaysActive = it },
                )
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
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
                },
                enabled = canSave,
                compact = true,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpSavesTab(
    saveSlots: List<RpSaveSlot>,
    modifier: Modifier = Modifier,
    onCreateSaveSlot: (name: String, note: String) -> Unit,
    onRequestRewind: (RpSaveSlot) -> Unit,
    onBranchSaveSlot: (RpSaveSlot) -> Unit,
    onOverwriteSaveSlot: (String) -> Unit,
    onRequestDelete: (RpSaveSlot) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var saveName by remember { mutableStateOf("") }
    var saveNote by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__save_form__") {
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = c.surfaceSoft,
            ) {
                Text(
                    text = strings.rp.savesHint,
                    color = c.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    placeholder = strings.rp.saveNamePlaceholder,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = saveNote,
                    onValueChange = { saveNote = it },
                    placeholder = strings.rp.saveNotePlaceholder,
                )
                Spacer(Modifier.height(10.dp))
                GuButton(
                    onClick = {
                        onCreateSaveSlot(
                            saveName.trim().ifBlank { strings.rp.quickSaveBtn },
                            saveNote.trim(),
                        )
                        saveName = ""
                        saveNote = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.createSaveBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (saveSlots.isEmpty()) {
            item(key = "__saves_empty__") {
                GuEmptyState(title = strings.rp.savesEmpty)
            }
        } else {
            items(saveSlots, key = { it.id }) { slot ->
                val stateSnap = slot.rpConfig.state
                val locationLine = buildString {
                    if (stateSnap.chapterTitle.isNotBlank()) {
                        append(stateSnap.chapterTitle)
                        append(" · ")
                    }
                    append("📍 ")
                    append(stateSnap.scene.ifBlank { slot.rpConfig.title })
                }
                val topNpcs = remember(slot.rpConfig.cast) {
                    slot.rpConfig.cast.filter { !it.isPlayer }.take(3)
                }

                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = slot.name,
                            color = c.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        GuBadge("Seq #${slot.messageSeq}", variant = GuBadgeVariant.BLUE)
                    }
                    if (slot.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = slot.note,
                            color = c.ink.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = locationLine,
                        color = c.muted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuBadge("HP ${stateSnap.hp}/${stateSnap.maxHp}", variant = GuBadgeVariant.NEUTRAL)
                        topNpcs.forEach { npc ->
                            GuBadge("♥ ${npc.name} ${npc.affinity}", variant = GuBadgeVariant.HONEY)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuButton(
                            onClick = { onRequestRewind(slot) },
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.loadRewindBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onBranchSaveSlot(slot) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.saveBranchBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onOverwriteSaveSlot(slot.id) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Text(strings.rp.saveOverwriteBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        GuButton(
                            onClick = { onRequestDelete(slot) },
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = c.danger,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = strings.common.delete,
                                color = c.danger,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpWorldAndExportTab(
    rp: RpConfig,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onSetPlayStyle: (RpPlayStyle) -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onPickSceneImage: () -> Unit,
    onClearSceneImage: () -> Unit,
    onGenerateSceneCg: () -> Unit,
    onExportCardPng: () -> Unit,
    onExportCardJson: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var playerName by remember(rp.playerName) { mutableStateOf(rp.playerName) }
    var playerPersona by remember(rp.playerPersona) { mutableStateOf(rp.playerPersona) }
    var worldview by remember(rp.worldview) { mutableStateOf(rp.worldview) }
    var systemRules by remember(rp.systemRules) { mutableStateOf(rp.systemRules) }
    var narratorStyle by remember(rp.narratorStyle) { mutableStateOf(rp.narratorStyle) }
    var authorNote by remember(rp.authorNote) { mutableStateOf(rp.authorNote) }
    var postHistory by remember(rp.postHistoryInstructions) { mutableStateOf(rp.postHistoryInstructions) }
    var mesExample by remember(rp.mesExample) { mutableStateOf(rp.mesExample) }

    val hasUnsavedChanges = playerName != rp.playerName ||
        playerPersona != rp.playerPersona ||
        worldview != rp.worldview ||
        systemRules != rp.systemRules ||
        narratorStyle != rp.narratorStyle ||
        authorNote != rp.authorNote ||
        postHistory != rp.postHistoryInstructions ||
        mesExample != rp.mesExample

    val sceneFile = remember(rp.state.sceneImagePath) {
        rp.state.sceneImagePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Play style & feature switches
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.rp.playStyleLabel, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    RpPlayStyle.chengguang,
                    RpPlayStyle.tavern,
                    RpPlayStyle.trpg,
                ).forEach { style ->
                    GuPill(
                        label = rpPlayStyleLabel(style, strings.rp),
                        selected = rp.playStyle == style,
                        onClick = { onSetPlayStyle(style) },
                        useHoneySelected = false,
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = if (isZh) "剧场模式（视觉小说舞台）" else "Theater Mode (Visual Novel Stage)",
                checked = rp.theaterMode,
                onCheckedChange = { v -> onUpdateConfig { it.copy(theaterMode = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.enableHudToggle,
                checked = rp.enableStateHud,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableStateHud = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.enableChoicesToggle,
                checked = rp.enableChoices,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.enableDiceToggle,
                checked = rp.enableDiceCheck,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableDiceCheck = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.enableLedgerToggle,
                checked = rp.enableLedger,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableLedger = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.autoSkillBonusToggle,
                checked = rp.autoDiceSkillBonus,
                onCheckedChange = { v -> onUpdateConfig { it.copy(autoDiceSkillBonus = v) } },
            )
        }

        // Scene CG Backdrop card
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isZh) "场景 CG 背景图" else "Scene CG Backdrop",
                color = c.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            if (sceneFile != null) {
                Image(
                    painter = rememberAsyncImagePainter(sceneFile),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, c.border, RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSoft)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Landscape,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (isZh) "未设置自定义场景背景" else "No custom scene backdrop",
                            color = c.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuButton(
                    onClick = onGenerateSceneCg,
                    enabled = !isGeneratingArt,
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(strings.rp.aiGenSceneCgBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = onPickSceneImage,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(strings.rp.bindSceneImageBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!rp.state.sceneImagePath.isNullOrBlank()) {
                    GuButton(
                        onClick = onClearSceneImage,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = c.danger,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = strings.rp.clearSceneImageBtn,
                            color = c.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Editable World & SillyTavern Prompt fields
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.rp.personaSectionTitle,
                    color = c.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (hasUnsavedChanges) {
                    GuBadge(if (isZh) "未保存" else "Unsaved", variant = GuBadgeVariant.AMBER)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = strings.rp.playerNameLabel,
                )
                GuTextField(
                    value = playerPersona,
                    onValueChange = { playerPersona = it },
                    label = strings.rp.playerPersonaLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
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
                                playerName = playerName.trim().ifBlank { it.playerName },
                                playerPersona = playerPersona.trim(),
                                worldview = worldview.trim(),
                                systemRules = systemRules.trim(),
                                narratorStyle = narratorStyle.trim(),
                                authorNote = authorNote.trim(),
                                postHistoryInstructions = postHistory.trim(),
                                mesExample = mesExample.trim(),
                            )
                        }
                    },
                    enabled = hasUnsavedChanges,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.common.save, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Card Export Section
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.rp.exportTitle,
                color = c.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuButton(
                    onClick = onExportCardPng,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.exportStPngBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = onExportCardJson,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.exportStBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpCgLightboxDialog(
    cg: RpCgRecord,
    isActive: Boolean,
    onApplyAsScene: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    val cgFile = remember(cg.imagePath) {
        cg.imagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = c.surfaceElevated,
            border = BorderStroke(1.dp, c.outline),
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GuSheetHeader(
                    title = cg.title,
                    subtitle = cg.scene.takeIf { it.isNotBlank() && it != cg.title },
                    onClose = onDismiss,
                    closeContentDescription = strings.common.close,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0E1320))
                            .border(1.dp, c.border, RoundedCornerShape(14.dp)),
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
                            Icon(
                                imageVector = Icons.Default.Landscape,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isActive) {
                            GuBadge(
                                text = if (isZh) "当前场景" else "Active Scene",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                        if (cg.scene.isNotBlank()) {
                            GuBadge("📍 ${cg.scene}", variant = GuBadgeVariant.NEUTRAL)
                        }
                        if (cg.turnSeq > 0) {
                            GuBadge("#${cg.turnSeq}", variant = GuBadgeVariant.BLUE)
                        }
                        cg.characterNames.forEach { charName ->
                            GuBadge(charName, variant = GuBadgeVariant.HONEY)
                        }
                    }

                    if (cg.caption.isNotBlank()) {
                        Text(
                            text = cg.caption,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                HorizontalDivider(color = c.border.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuButton(
                        onClick = onApplyAsScene,
                        enabled = !isActive,
                        variant = GuButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Check else Icons.Default.Landscape,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isActive) {
                                if (isZh) "已设为当前场景" else "Active Scene"
                            } else {
                                strings.rp.cgSetAsSceneBtn
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    GuButton(
                        onClick = onRequestDelete,
                        variant = GuButtonVariant.SECONDARY,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = c.danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = strings.common.delete,
                            color = c.danger,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpFrontendTab(
    rp: RpConfig,
    modifier: Modifier = Modifier,
    onUpdateVisualTheme: ((RpVisualTheme) -> RpVisualTheme) -> Unit,
    onResetVisualTheme: () -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onApplyStatusWidgetTemplate: (String) -> Unit,
    onAddRegexScript: () -> Unit,
    onEditRegexScript: (RpRegexScript) -> Unit,
    onToggleRegexScript: (String) -> Unit,
    onRequestDeleteRegex: (RpRegexScript) -> Unit,
    onApplyRegexPreset: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val vt = rp.visualTheme

    // Slider draft states synced from visualTheme
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

    // HTML / CSS code editor drafts synced when template applied or theme reset
    var htmlDraft by remember(vt.customStatusHtml) { mutableStateOf(vt.customStatusHtml) }
    var cssDraft by remember(vt.customCss) { mutableStateOf(vt.customCss) }
    val hasFrontendChanges = htmlDraft != vt.customStatusHtml || cssDraft != vt.customCss

    // Debounced sandbox preview state so live typing stays silky smooth
    var debouncedHtml by remember { mutableStateOf(htmlDraft) }
    var debouncedCss by remember { mutableStateOf(cssDraft) }
    LaunchedEffect(htmlDraft, cssDraft) {
        if (htmlDraft == debouncedHtml && cssDraft == debouncedCss) return@LaunchedEffect
        delay(220L)
        debouncedHtml = htmlDraft
        debouncedCss = cssDraft
    }

    // Regex presets & live tester UI state
    var showRegexPresets by remember { mutableStateOf(rp.regexScripts.isEmpty()) }
    var showRegexTester by remember { mutableStateOf(false) }
    var regexTestInput by remember { mutableStateOf("") }

    val regexTestOutput = remember(regexTestInput, rp.regexScripts, rp) {
        if (regexTestInput.isBlank()) {
            ""
        } else {
            RpRegexEngine.applyScripts(
                text = regexTestInput,
                scripts = rp.regexScripts,
                placement = "both",
                forDisplay = true,
                rp = rp,
            )
        }
    }
    val regexTestHtml = remember(regexTestOutput) {
        if (regexTestOutput.isBlank()) {
            ""
        } else {
            RpRegexEngine.splitMarkdownAndHtml(regexTestOutput, enableHtmlCards = true)
                .filter { it.isHtml }
                .joinToString("\n") { it.content }
        }
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card A — Stage & UI Visual Theme
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.rp.visualThemeTitle,
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.rp.dialogStyleLabel,
                color = c.ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RP_DIALOG_STYLE_KEYS.forEach { key ->
                    GuPill(
                        label = strings.rp.dialogStyles[key] ?: key,
                        selected = vt.dialogStyle.equals(key, ignoreCase = true),
                        onClick = { onUpdateVisualTheme { it.copy(dialogStyle = key) } },
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.rp.spriteLayoutLabel,
                color = c.ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "stage" to strings.rp.spriteLayoutStage,
                    "portrait_card" to strings.rp.spriteLayoutCard,
                    "hidden" to strings.rp.spriteLayoutHidden,
                ).forEach { (key, label) ->
                    GuPill(
                        label = label,
                        selected = vt.spriteLayout.equals(key, ignoreCase = true),
                        onClick = { onUpdateVisualTheme { it.copy(spriteLayout = key) } },
                        useHoneySelected = true,
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            val activeSwatch = RP_ACCENT_SWATCHES.firstOrNull {
                it.hex.equals(vt.accentHex.trim(), ignoreCase = true)
            } ?: RP_ACCENT_SWATCHES.first()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.rp.accentColorLabel,
                    color = c.ink,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                GuBadge(
                    text = if (isZh) activeSwatch.zhName else activeSwatch.enName,
                    variant = GuBadgeVariant.NEUTRAL,
                )
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(fillColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) c.ink else c.outline,
                                shape = CircleShape,
                            )
                            .clickable {
                                onUpdateVisualTheme { it.copy(accentHex = swatch.hex) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = if (isZh) swatch.zhName else swatch.enName,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))

            // Slider 1: Dialog Opacity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(strings.rp.dialogOpacityLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
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

            // Slider 2: Backdrop Dim
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(strings.rp.backdropDimLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
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

            // Slider 3: Body Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(strings.rp.fontSizeLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
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

            // Slider 4: Sprite Stage Height
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(strings.rp.spriteHeightLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
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

            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.useSerifToggle,
                checked = vt.useSerif,
                onCheckedChange = { v -> onUpdateVisualTheme { it.copy(useSerif = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.showNameplateToggle,
                checked = vt.showNameplate,
                onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showNameplate = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.showDeltasToggle,
                checked = vt.showTurnDeltas,
                onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showTurnDeltas = v) } },
            )
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            GuSwitchRow(
                label = strings.rp.enableHtmlCardsToggle,
                checked = rp.enableHtmlCards,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableHtmlCards = v) } },
            )

            Spacer(Modifier.height(8.dp))
            GuButton(
                onClick = onResetVisualTheme,
                compact = true,
                variant = GuButtonVariant.SUBTLE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.rp.resetThemeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Card B — Custom HTML/CSS Frontend Status Widget
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.rp.statusWidgetTitle,
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (hasFrontendChanges) {
                    GuBadge(if (isZh) "未应用" else "Unsaved", variant = GuBadgeVariant.AMBER)
                }
            }

            Spacer(Modifier.height(6.dp))
            GuSwitchRow(
                label = strings.rp.showStatusWidgetToggle,
                checked = vt.showCustomStatusWidget,
                onCheckedChange = { v -> onUpdateVisualTheme { it.copy(showCustomStatusWidget = v) } },
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.rp.statusWidgetTemplatesLabel,
                color = c.ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        modifier = Modifier.heightIn(min = 36.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalTextStyle provides monoStyle) {
                GuTextField(
                    value = htmlDraft,
                    onValueChange = { htmlDraft = it },
                    label = strings.rp.statusWidgetHtmlLabel,
                    placeholder = "<div>{{scene}} · HP {{hp}}/{{maxHp}}</div>",
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                )
                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = cssDraft,
                    onValueChange = { cssDraft = it },
                    label = strings.rp.customCssLabel,
                    placeholder = ".my-card { border-radius: 14px; }",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                )
            }

            Spacer(Modifier.height(10.dp))
            GuButton(
                onClick = {
                    debouncedHtml = htmlDraft
                    debouncedCss = cssDraft
                    onUpdateVisualTheme {
                        it.copy(
                            customStatusHtml = htmlDraft,
                            customCss = cssDraft,
                        )
                    }
                },
                enabled = hasFrontendChanges,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.rp.saveFrontendBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (htmlDraft.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = strings.rp.previewLiveLabel,
                    color = c.muted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(1.dp, c.outline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        RpHtmlCardSandbox(
                            htmlFragment = debouncedHtml.ifBlank { htmlDraft },
                            rp = rp,
                            customCss = debouncedCss,
                            minHeightDp = 64,
                            maxHeightDp = 240,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Card C — SillyTavern Regex Beautification Scripts
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.rp.regexScriptsTitle,
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                GuBadge("${rp.regexScripts.size}", variant = GuBadgeVariant.BLUE)
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuButton(
                    onClick = onAddRegexScript,
                    compact = true,
                    variant = GuButtonVariant.PRIMARY,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stripLeadingPlus(strings.rp.regexAddBtn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(
                    onClick = { showRegexPresets = !showRegexPresets },
                    compact = true,
                    variant = if (showRegexPresets) GuButtonVariant.HONEY else GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.regexPresetsBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = showRegexPresets) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (isZh) "点击一键添加内置酒馆正则模板" else "Tap to add a built-in Tavern regex preset",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                                    modifier = Modifier.heightIn(min = 36.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (rp.regexScripts.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Text(
                        text = strings.rp.regexEmpty,
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rp.regexScripts.forEach { script ->
                        val placementLabel = localizeRegexPlacement(script.placement, strings.rp)
                        val patternPreview = remember(script.findRegex, script.flags) {
                            RpRegexEngine.formatRegexLiteral(script.findRegex, script.flags)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = c.surfaceSoft,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (script.enabled) c.primary.copy(alpha = 0.35f) else c.border,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = script.name,
                                            color = if (script.enabled) c.ink else c.muted,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            GuBadge(placementLabel, variant = GuBadgeVariant.BLUE)
                                            if (script.markdownOnly) {
                                                GuBadge(
                                                    text = if (isZh) "仅显示" else "Display Only",
                                                    variant = GuBadgeVariant.HONEY,
                                                )
                                            }
                                        }
                                    }
                                    Switch(
                                        checked = script.enabled,
                                        onCheckedChange = { onToggleRegexScript(script.id) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = c.primary,
                                            checkedBorderColor = c.primary,
                                            uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                                            uncheckedTrackColor = c.surface,
                                            uncheckedBorderColor = if (theme.isDark) Color(0xFF526280) else c.outline,
                                        ),
                                    )
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = c.surface,
                                    border = BorderStroke(1.dp, c.border),
                                ) {
                                    Text(
                                        text = patternPreview,
                                        color = c.muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GuButton(
                                        onClick = { onEditRegexScript(script) },
                                        compact = true,
                                        variant = GuButtonVariant.SECONDARY,
                                        modifier = Modifier.heightIn(min = 34.dp),
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.common.edit, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    GuButton(
                                        onClick = { onRequestDeleteRegex(script) },
                                        compact = true,
                                        variant = GuButtonVariant.SECONDARY,
                                        modifier = Modifier.heightIn(min = 34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = c.danger,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = strings.common.delete,
                                            color = c.danger,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Inline Live Regex Tester
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clickable { showRegexTester = !showRegexTester }
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.rp.regexTestTitle,
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (showRegexTester) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = showRegexTester) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuPill(
                            label = if (isZh) "示例: <status>" else "Sample: <status>",
                            selected = false,
                            onClick = {
                                regexTestInput = if (isZh) {
                                    "<status>\n心情: 开心\n地点: 澄心亭\n好感: +5\n</status>"
                                } else {
                                    "<status>\nMood: Happy\nLocation: Pavilion\nAffinity: +5\n</status>"
                                }
                            },
                            modifier = Modifier.heightIn(min = 34.dp),
                        )
                        GuPill(
                            label = if (isZh) "示例: <thought>" else "Sample: <thought>",
                            selected = false,
                            onClick = {
                                regexTestInput = if (isZh) {
                                    "<thought>原来她早就察觉了今夜的局势……</thought>"
                                } else {
                                    "<thought>She noticed the shift in the room long before anyone else...</thought>"
                                }
                            },
                            modifier = Modifier.heightIn(min = 34.dp),
                        )
                    }

                    GuTextField(
                        value = regexTestInput,
                        onValueChange = { regexTestInput = it },
                        placeholder = strings.rp.regexTestInputPlaceholder,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 5,
                    )

                    if (regexTestInput.isNotBlank()) {
                        Text(
                            text = strings.rp.regexTestOutputLabel,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = c.surfaceSoft,
                            border = BorderStroke(1.dp, c.border),
                        ) {
                            Text(
                                text = regexTestOutput.ifBlank { if (isZh) "（匹配后为空）" else "(Empty after replacement)" },
                                color = c.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp),
                            )
                        }

                        if (regexTestHtml.isNotBlank()) {
                            Text(
                                text = strings.rp.previewLiveLabel,
                                color = c.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = c.surfaceSoft,
                                border = BorderStroke(1.dp, c.outline),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                ) {
                                    RpHtmlCardSandbox(
                                        htmlFragment = regexTestHtml,
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpRegexScriptEditorDialog(
    initial: RpRegexScript,
    isExisting: Boolean,
    onSave: (RpRegexScript) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var findRegex by remember(initial.id) { mutableStateOf(initial.findRegex) }
    var replaceString by remember(initial.id) { mutableStateOf(initial.replaceString) }
    var flags by remember(initial.id) { mutableStateOf(initial.flags.ifBlank { "gms" }) }
    var placement by remember(initial.id) { mutableStateOf(initial.placement.ifBlank { "ai_output" }) }
    var markdownOnly by remember(initial.id) { mutableStateOf(initial.markdownOnly) }

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

    val monoStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    val canSave = name.isNotBlank() && findRegex.isNotBlank() && regexSyntaxError == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            color = c.surfaceElevated,
            border = BorderStroke(1.dp, c.outline),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GuSheetHeader(
                    title = if (isExisting) strings.common.edit else stripLeadingPlus(strings.rp.regexAddBtn),
                    onClose = onDismiss,
                    closeContentDescription = strings.common.close,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        Text(
                            text = regexSyntaxError,
                            color = c.danger,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                        )
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
                        Text(
                            text = strings.rp.regexPlacementLabel,
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
                                    modifier = Modifier.heightIn(min = 36.dp),
                                )
                            }
                        }
                    }

                    GuSwitchRow(
                        label = strings.rp.regexDisplayOnlyToggle,
                        checked = markdownOnly,
                        onCheckedChange = { markdownOnly = it },
                    )
                }

                HorizontalDivider(color = c.border.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                    }
                    GuButton(
                        onClick = {
                            onSave(
                                initial.copy(
                                    id = initial.id.ifBlank { UUID.randomUUID().toString() },
                                    name = name.trim(),
                                    findRegex = findRegex,
                                    replaceString = replaceString,
                                    flags = flags.trim().ifEmpty { "gms" },
                                    placement = placement,
                                    markdownOnly = markdownOnly,
                                ),
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(strings.common.save, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
