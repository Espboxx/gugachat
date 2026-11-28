package com.gugachat.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuVariantSwipePill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpChoiceEval
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEndingRecord
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpTurnDelta
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpTurnDeltaStrip(
    deltas: List<RpTurnDelta>,
    modifier: Modifier = Modifier,
    onDarkStage: Boolean = false,
) {
    if (deltas.isEmpty()) return
    val c = LocalAppTheme.current.colors

    if (onDarkStage) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            deltas.forEach { delta ->
                val (bg, fg, border) = resolveDeltaColors(delta, true, c)
                val displayText = formatDeltaLabel(delta)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bg,
                    border = BorderStroke(0.6.dp, border),
                ) {
                    Text(
                        text = displayText,
                        color = fg,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                    )
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            deltas.forEach { delta ->
                val (bg, fg, border) = resolveDeltaColors(delta, false, c)
                val displayText = formatDeltaLabel(delta)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bg,
                    border = BorderStroke(0.6.dp, border),
                ) {
                    Text(
                        text = displayText,
                        color = fg,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

internal fun formatDeltaLabel(delta: RpTurnDelta): String {
    val raw = delta.label.trim()
    if (raw.isEmpty()) return raw
    return when (delta.kind.lowercase()) {
        "affinity" -> if (raw.contains("♥")) raw else "♥ $raw"
        "gold" -> if (raw.contains("🪙")) raw else "🪙 $raw"
        "ending" -> if (raw.contains("🏆")) raw else "🏆 $raw"
        "chapter" -> if (raw.contains("🎬")) raw else "🎬 $raw"
        "quest" -> if (raw.contains("📜")) raw else "📜 $raw"
        else -> raw
    }
}

internal fun resolveDeltaColors(
    delta: RpTurnDelta,
    onDarkStage: Boolean,
    c: com.gugachat.app.core.theme.AppColors,
): Triple<Color, Color, Color> {
    if (onDarkStage) {
        val baseBg = Color(0xCC121726)
        return when (delta.kind.lowercase()) {
            "affinity" -> if (delta.positive) {
                Triple(Color(0xCC3B1D2C), Color(0xFFFFB4CE), Color(0x70F472B6))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x70F87171))
            }
            "hp" -> if (delta.positive) {
                Triple(Color(0xCC122F22), Color(0xFF86EFAC), Color(0x704ADE80))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x70F87171))
            }
            "mp" -> if (delta.positive) {
                Triple(Color(0xCC172544), Color(0xFFA5C4FF), Color(0x7060A5FA))
            } else {
                Triple(Color(0xCC362510), Color(0xFFFDE68A), Color(0x70FBBF24))
            }
            "gold", "chapter" -> Triple(Color(0xCC382812), Color(0xFFFDE68A), Color(0x70F59E0B))
            "ending" -> if (delta.positive) {
                Triple(Color(0xCC3E2B10), Color(0xFFFDE047), Color(0x99F59E0B))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x99F87171))
            }
            "item_add", "quest" -> Triple(Color(0xCC132E27), Color(0xFF7DD3FC), Color(0x7038BDF8))
            "item_remove" -> Triple(Color(0xCC332414), Color(0xFFFCD34D), Color(0x66F59E0B))
            "flag" -> Triple(baseBg, Color(0xFFCBD5E1), Color(0x4494A3B8))
            else -> if (delta.positive) {
                Triple(Color(0xCC172642), Color(0xFF93C5FD), Color(0x6660A5FA))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x70F87171))
            }
        }
    }
    return when (delta.kind.lowercase()) {
        "affinity" -> if (delta.positive) {
            Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.4f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.35f))
        }
        "hp" -> if (delta.positive) {
            Triple(c.successBg, c.success, c.success.copy(alpha = 0.35f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.35f))
        }
        "mp" -> if (delta.positive) {
            Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.35f))
        } else {
            Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.35f))
        }
        "gold", "chapter" -> Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.4f))
        "ending" -> if (delta.positive) {
            Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.5f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.45f))
        }
        "item_add" -> Triple(c.successBg, c.success, c.success.copy(alpha = 0.35f))
        "item_remove" -> Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.35f))
        "quest" -> Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.32f))
        "flag" -> Triple(c.surfaceSoft, c.muted, c.border)
        else -> if (delta.positive) {
            Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.35f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.35f))
        }
    }
}

@Composable
fun RpEndingCard(
    ending: RpEndingRecord,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val code = ending.code.trim().uppercase().ifBlank { "NE" }

    val (gradientColors, accentColor, badgeVariant) = when (code) {
        "HE" -> Triple(
            listOf(Color(0xFF2B2212), Color(0xFF1A2A20)),
            Color(0xFFF5B73B),
            GuBadgeVariant.HONEY,
        )
        "TE" -> Triple(
            listOf(Color(0xFF1A2246), Color(0xFF2A1C46)),
            Color(0xFF819BFF),
            GuBadgeVariant.BLUE,
        )
        "BE" -> Triple(
            listOf(Color(0xFF34131B), Color(0xFF1C1018)),
            Color(0xFFF87171),
            GuBadgeVariant.DANGER,
        )
        else -> Triple(
            listOf(Color(0xFF202636), Color(0xFF171B26)),
            c.accent,
            GuBadgeVariant.AMBER,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.65f)),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(0.8.dp, accentColor.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuBadge(code, variant = badgeVariant)
                    Text(
                        text = strings.rp.endingUnlockedBanner,
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = ending.title,
                    color = Color(0xFFFDF8EE),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ending.summary.isNotBlank()) {
                    Text(
                        text = ending.summary,
                        color = Color(0xFFD6DCEB),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpChoiceDeck(
    choices: List<RpChoice>,
    rp: RpConfig?,
    isRefreshing: Boolean,
    onSelectChoice: (RpChoice, bypassLock: Boolean) -> Unit,
    onRefreshChoices: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val stageHighlight = accentColor ?: Color(0xFFE8B95B)
    var forceConfirmTarget by remember { mutableStateOf<Pair<RpChoice, String>?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Inline non-modal lock bypass confirm bar (no AlertDialog)
        forceConfirmTarget?.let { (choice, reason) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE826161C),
                border = BorderStroke(0.6.dp, Color(0x99F87171)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = strings.rp.choiceForceConfirmMsg.fmt("reason" to reason),
                        color = Color(0xFFFECACA),
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { forceConfirmTarget = null },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = strings.common.cancel,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Surface(
                        onClick = {
                            forceConfirmTarget = null
                            onSelectChoice(choice, true)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFDC2626),
                    ) {
                        Text(
                            text = strings.rp.choiceForceProceed,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        choices.forEachIndexed { idx, choice ->
            val eval = remember(choice, rp) {
                if (rp != null) RpEngine.evaluateChoiceCondition(choice, rp) else RpChoiceEval(unlocked = true)
            }
            val reasonText = (if (isZh) eval.reasonZh else eval.reasonEn)
                ?.takeIf { it.isNotBlank() }
                ?: choice.conditionText?.takeIf { it.isNotBlank() }
                ?: strings.rp.choiceLockedLabel
            val hasExplicitCondition = !choice.conditionText.isNullOrBlank() ||
                !choice.requiresStat.isNullOrBlank() ||
                !choice.requiresAffinityNpc.isNullOrBlank() ||
                !choice.requiresItem.isNullOrBlank() ||
                !choice.requiresFlag.isNullOrBlank()

            val toneAccent = resolveChoiceToneAccent(
                tone = choice.tone,
                unlocked = eval.unlocked,
                onDarkStage = true,
                fallback = stageHighlight,
            )
            val cardBgBrush = if (!eval.unlocked) {
                Brush.horizontalGradient(listOf(Color(0xBD151926), Color(0xAD111420)))
            } else {
                Brush.horizontalGradient(listOf(Color(0xE0192034), Color(0xD4121828)))
            }
            val titleColor = if (!eval.unlocked) Color(0xFFB3BDCE) else Color(0xFFF6F8FD)
            val subtitleColor = Color(0xFFAAB4CA)

            Surface(
                onClick = {
                    if (!enabled) return@Surface
                    if (eval.unlocked) {
                        onSelectChoice(choice, false)
                    } else {
                        forceConfirmTarget = choice to reasonText
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = if (!eval.unlocked) Color(0x4094A3B8) else toneAccent.copy(alpha = 0.42f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (eval.unlocked) 1f else 0.85f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBgBrush)
                        .drawBehind {
                            val stripW = 3.dp.toPx()
                            drawRect(
                                color = toneAccent.copy(alpha = if (eval.unlocked) 0.95f else 0.55f),
                                topLeft = Offset.Zero,
                                size = Size(stripW, size.height),
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (!eval.unlocked) Color(0xFF363F56) else toneAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!eval.unlocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = strings.rp.choiceLockedLabel,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp),
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    val toneMeta = resolveChoiceTone(choice.tone, isZh)
                    val hasSkill = !choice.skillCheck.isNullOrBlank()
                    val hasCost = (choice.costGold ?: 0) > 0
                    val showConditionPill = !eval.unlocked || hasExplicitCondition

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = choice.label,
                                color = titleColor,
                                fontSize = 13.sp,
                                lineHeight = 17.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (hasSkill) {
                                val dcSuffix = choice.difficulty?.let { " DC$it" }.orEmpty()
                                ChoiceMetaChip(
                                    text = "🎲${choice.skillCheck}$dcSuffix",
                                    variant = GuBadgeVariant.HONEY,
                                    onDarkStage = true,
                                )
                            } else if (toneMeta != null) {
                                ChoiceMetaChip(
                                    text = toneMeta.first,
                                    variant = toneMeta.second,
                                    onDarkStage = true,
                                )
                            }
                        }

                        if (!choice.consequenceHint.isNullOrBlank() || hasCost || showConditionPill || (hasSkill && toneMeta != null)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (!choice.consequenceHint.isNullOrBlank()) {
                                    Text(
                                        text = choice.consequenceHint,
                                        color = subtitleColor,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (hasSkill && toneMeta != null) {
                                    ChoiceMetaChip(
                                        text = toneMeta.first,
                                        variant = toneMeta.second,
                                        onDarkStage = true,
                                    )
                                }
                                if (hasCost) {
                                    ChoiceMetaChip(
                                        text = "🪙 ${choice.costGold}",
                                        variant = GuBadgeVariant.AMBER,
                                        onDarkStage = true,
                                    )
                                }
                                if (!eval.unlocked) {
                                    ChoiceMetaChip(
                                        text = "🔒 $reasonText",
                                        variant = GuBadgeVariant.DANGER,
                                        onDarkStage = true,
                                    )
                                } else if (hasExplicitCondition) {
                                    val metLabel = choice.conditionText?.takeIf { it.isNotBlank() }
                                        ?: if (isZh) "条件满足" else "Unlocked"
                                    ChoiceMetaChip(
                                        text = "✓ $metLabel",
                                        variant = GuBadgeVariant.EMERALD,
                                        onDarkStage = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ghostBg = Color(0x99101522)
            val ghostBorder = Color(0x40B8C7E0)
            val ghostFg = Color(0xFFDCE4F5)

            Surface(
                onClick = onRefreshChoices,
                enabled = enabled && !isRefreshing,
                shape = RoundedCornerShape(999.dp),
                color = ghostBg,
                border = BorderStroke(0.6.dp, ghostBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = stageHighlight,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = ghostFg,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = if (isRefreshing) strings.rp.refreshingChoices else strings.rp.refreshChoicesBtn,
                        color = ghostFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

internal fun resolveChoiceToneAccent(
    tone: String?,
    unlocked: Boolean,
    onDarkStage: Boolean,
    fallback: Color,
): Color {
    if (!unlocked) return Color(0xFF64748B)
    return when (tone?.trim()?.lowercase().orEmpty()) {
        "romantic", "flirt", "love", "心动", "暧昧", "情愫" ->
            if (onDarkStage) Color(0xFFF43F7A) else Color(0xFFE11D48)
        "cunning", "scheme", "intrigue", "权谋", "心机", "calm", "rational", "冷静", "理性" ->
            if (onDarkStage) Color(0xFFD98B1C) else Color(0xFFC96F0A)
        "bold", "brave", "adventure", "冒险", "果断", "mystery", "probe", "test", "试探", "悬疑", "探查" ->
            if (onDarkStage) Color(0xFF4968F2) else Color(0xFF4361EE)
        "gentle", "kind", "温柔", "安抚" ->
            if (onDarkStage) Color(0xFF22B573) else Color(0xFF15803D)
        "be", "danger", "risk", "be预警", "高危", "危险" ->
            if (onDarkStage) Color(0xFFEF4444) else Color(0xFFDC2626)
        else -> fallback
    }
}

internal fun resolveChoiceTone(tone: String?, isZh: Boolean): Pair<String, GuBadgeVariant>? {
    val raw = tone?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return when (raw.lowercase()) {
        "romantic", "flirt", "love", "心动", "暧昧", "情愫" ->
            (if (isZh) "心动" else "Romantic") to GuBadgeVariant.HONEY
        "cunning", "scheme", "intrigue", "权谋", "心机" ->
            (if (isZh) "权谋" else "Cunning") to GuBadgeVariant.AMBER
        "bold", "brave", "adventure", "冒险", "果断" ->
            (if (isZh) "冒险" else "Bold") to GuBadgeVariant.BLUE
        "mystery", "probe", "test", "试探", "悬疑", "探查" ->
            (if (isZh) "试探" else "Probe") to GuBadgeVariant.BLUE
        "gentle", "kind", "温柔", "安抚" ->
            (if (isZh) "温柔" else "Gentle") to GuBadgeVariant.EMERALD
        "calm", "rational", "冷静", "理性" ->
            (if (isZh) "冷静" else "Calm") to GuBadgeVariant.NEUTRAL
        "be", "danger", "risk", "be预警", "高危", "危险" ->
            (if (isZh) "BE预警" else "BE Risk") to GuBadgeVariant.DANGER
        else -> raw to GuBadgeVariant.NEUTRAL
    }
}

@Composable
internal fun ChoiceMetaChip(
    text: String,
    variant: GuBadgeVariant,
    onDarkStage: Boolean,
) {
    if (!onDarkStage) {
        GuBadge(text = text, variant = variant)
        return
    }
    val (bg, fg, border) = when (variant) {
        GuBadgeVariant.HONEY -> Triple(Color(0xCC3B2613), Color(0xFFFDE68A), Color(0x66F59E0B))
        GuBadgeVariant.AMBER -> Triple(Color(0xCC3A2711), Color(0xFFFCD34D), Color(0x66F59E0B))
        GuBadgeVariant.BLUE -> Triple(Color(0xCC18264A), Color(0xFF93C5FD), Color(0x6660A5FA))
        GuBadgeVariant.EMERALD -> Triple(Color(0xCC123023), Color(0xFF86EFAC), Color(0x664ADE80))
        GuBadgeVariant.DANGER -> Triple(Color(0xCC3C161D), Color(0xFFFCA5A5), Color(0x66F87171))
        GuBadgeVariant.NEUTRAL -> Triple(Color(0xAA1F273B), Color(0xFFCBD5E1), Color(0x4494A3B8))
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(0.5.dp, border),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ==================== Dialog Box Skins ====================


@Composable
internal fun TheaterCastBadgeCluster(
    focusedNpc: RpCharacter,
    allNpcs: List<RpCharacter>,
    stageAccent: Color,
    isZh: Boolean,
    onSelectNpc: (RpCharacter) -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val exprKey = remember(focusedNpc.mood) { RpEngine.resolveExpressionKey(focusedNpc.mood) }
    val exprLabel = strings.rp.expressionLabels[exprKey] ?: exprKey
    val moodBadgeText = focusedNpc.mood.trim().takeIf { it.isNotEmpty() } ?: exprLabel
    val roleLabel = localizeRpRole(focusedNpc.role, isZh)
    val pair = PORTRAIT_GRADIENTS[abs(focusedNpc.name.hashCode()) % PORTRAIT_GRADIENTS.size]
    val initial = focusedNpc.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "✦"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onOpenStudio,
            shape = RoundedCornerShape(999.dp),
            color = Color(0xC9101624),
            border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.45f)),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                        .border(0.8.dp, stageAccent.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        color = Color(0xFFFFF6E3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = focusedNpc.name,
                            color = Color(0xFFFFF8EA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (roleLabel.isNotBlank()) {
                            GuMetaText(
                                text = "· $roleLabel",
                                color = Color(0xFFD6C7AE),
                            )
                        }
                    }
                    GuMetaText(
                        text = "${expressionEmoji(exprKey)} $moodBadgeText · ♥${focusedNpc.affinity}",
                        color = if (focusedNpc.affinity < 0) Color(0xFFFCA5A5) else Color(0xFFFFB4CE),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = stageAccent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = if (isZh) "✨ 设立绘" else "✨ Sprite",
                        color = stageAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                    )
                }
            }
        }
        if (allNpcs.size > 1) {
            StageCastMiniStrip(
                allNpcs = allNpcs,
                focusedNpcId = focusedNpc.id,
                stageAccent = stageAccent,
                onSelectNpc = onSelectNpc,
            )
        }
    }
}


@Composable
internal fun StageBoxMiniIconBtn(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    accentColor: Color = Color(0xFFF5C563),
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (active) accentColor.copy(alpha = 0.24f) else Color(0xBA0E1320),
        border = BorderStroke(
            0.5.dp,
            if (active) accentColor.copy(alpha = 0.72f) else Color(0x40CBD5E1),
        ),
        modifier = Modifier.size(26.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> Color(0x5594A3B8)
                    active -> accentColor
                    else -> Color(0xFFE2E8F0)
                },
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TheaterFloatingChoiceDeck(
    choices: List<RpChoice>,
    rp: RpConfig,
    isRefreshing: Boolean,
    peekHidden: Boolean,
    stageAccent: Color,
    isZh: Boolean,
    onTogglePeek: () -> Unit,
    onSelectChoice: (RpChoice, Boolean) -> Unit,
    onRefreshChoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var forceConfirmTarget by remember { mutableStateOf<Pair<RpChoice, String>?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Deck top utility bar: [↻ Refresh] (left) + [👁 赏立绘 / 隐牌] (top-right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onRefreshChoices,
                enabled = !isRefreshing,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xBD0E1320),
                border = BorderStroke(0.5.dp, Color(0x44CBD5E1)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = stageAccent,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFDCE4F5),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = if (isRefreshing) strings.rp.refreshingChoices else strings.rp.refreshChoicesBtn,
                        color = Color(0xFFDCE4F5),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Surface(
                onClick = onTogglePeek,
                shape = RoundedCornerShape(999.dp),
                color = if (peekHidden) Color(0xCC281F12) else Color(0xBD0E1320),
                border = BorderStroke(
                    0.5.dp,
                    if (peekHidden) stageAccent.copy(alpha = 0.72f) else Color(0x48CBD5E1),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (peekHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (peekHidden) stageAccent else Color(0xFFE2E8F0),
                        modifier = Modifier.size(12.5.dp),
                    )
                    Text(
                        text = if (peekHidden) {
                            if (isZh) "展开选项 (${choices.size})" else "Show (${choices.size})"
                        } else {
                            if (isZh) "赏立绘/隐牌" else "Hide Cards"
                        },
                        color = if (peekHidden) Color(0xFFFFF4DC) else Color(0xFFE2E8F0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (!peekHidden) {
            forceConfirmTarget?.let { (choice, reason) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEB26161C),
                    border = BorderStroke(0.6.dp, Color(0x99F87171)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = strings.rp.choiceForceConfirmMsg.fmt("reason" to reason),
                            color = Color(0xFFFECACA),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { forceConfirmTarget = null },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(strings.common.cancel, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        Surface(
                            onClick = {
                                forceConfirmTarget = null
                                onSelectChoice(choice, true)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFDC2626),
                        ) {
                            Text(
                                text = strings.rp.choiceForceProceed,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                            )
                        }
                    }
                }
            }

            // Compact Floating Single-Line Left-Strip Cards (No 128dp nested scroll box!)
            choices.forEachIndexed { idx, choice ->
                val eval = remember(choice, rp) { RpEngine.evaluateChoiceCondition(choice, rp) }
                val reasonText = (if (isZh) eval.reasonZh else eval.reasonEn)
                    ?.takeIf { it.isNotBlank() }
                    ?: choice.conditionText?.takeIf { it.isNotBlank() }
                    ?: strings.rp.choiceLockedLabel
                val toneAccent = resolveChoiceToneAccent(
                    tone = choice.tone,
                    unlocked = eval.unlocked,
                    onDarkStage = true,
                    fallback = stageAccent,
                )
                val toneMeta = resolveChoiceTone(choice.tone, isZh)
                val hasSkill = !choice.skillCheck.isNullOrBlank()
                val affinityChipText = if (!choice.requiresAffinityNpc.isNullOrBlank() && choice.requiresAffinityMin != null) {
                    "♥≥${choice.requiresAffinityMin}"
                } else {
                    null
                }

                Surface(
                    onClick = {
                        if (eval.unlocked) {
                            onSelectChoice(choice, false)
                        } else {
                            forceConfirmTarget = choice to reasonText
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        0.6.dp,
                        if (eval.unlocked) toneAccent.copy(alpha = 0.48f) else Color(0x4494A3B8),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (eval.unlocked) 1f else 0.84f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    if (eval.unlocked) {
                                        listOf(Color(0xDE1B1824), Color(0xCF101524))
                                    } else {
                                        listOf(Color(0xC4131722), Color(0xB80E121B))
                                    },
                                ),
                            )
                            .drawBehind {
                                val stripW = 3.dp.toPx()
                                drawRect(
                                    color = toneAccent.copy(alpha = if (eval.unlocked) 0.95f else 0.55f),
                                    topLeft = Offset.Zero,
                                    size = Size(stripW, size.height),
                                )
                            }
                            .padding(start = 10.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(if (eval.unlocked) toneAccent else Color(0xFF363F56)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!eval.unlocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = strings.rp.choiceLockedLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.5.dp),
                                )
                            } else {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Text(
                            text = choice.label,
                            color = if (eval.unlocked) Color(0xFFF9F6EF) else Color(0xFFAEB9CC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (hasSkill) {
                                val dcSuffix = choice.difficulty?.let { "DC$it" }.orEmpty()
                                ChoiceMetaChip(
                                    text = "🎲${choice.skillCheck}$dcSuffix",
                                    variant = GuBadgeVariant.HONEY,
                                    onDarkStage = true,
                                )
                            }
                            if (affinityChipText != null) {
                                ChoiceMetaChip(
                                    text = affinityChipText,
                                    variant = if (eval.unlocked) GuBadgeVariant.EMERALD else GuBadgeVariant.DANGER,
                                    onDarkStage = true,
                                )
                            } else if (!eval.unlocked) {
                                ChoiceMetaChip(
                                    text = "🔒 $reasonText",
                                    variant = GuBadgeVariant.DANGER,
                                    onDarkStage = true,
                                )
                            } else if (toneMeta != null && !hasSkill) {
                                ChoiceMetaChip(
                                    text = toneMeta.first,
                                    variant = toneMeta.second,
                                    onDarkStage = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StageCastMiniStrip(
    allNpcs: List<RpCharacter>,
    focusedNpcId: String?,
    stageAccent: Color,
    onSelectNpc: (RpCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        allNpcs.forEach { npc ->
            val selected = npc.id == focusedNpcId
            Surface(
                onClick = { onSelectNpc(npc) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) Color(0xCC2C2113) else Color(0x99101522),
                border = BorderStroke(
                    0.6.dp,
                    when {
                        selected -> stageAccent.copy(alpha = 0.80f)
                        npc.inScene -> Color(0x44CBD5E1)
                        else -> Color(0x2894A3B8)
                    },
                ),
                modifier = Modifier.alpha(if (npc.inScene || selected) 1f else 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    RpNpcPortraitAvatar(
                        character = npc,
                        fallbackName = npc.name,
                        size = 20.dp,
                        borderColor = if (selected) stageAccent else Color(0x55CBD5E1),
                    )
                    Text(
                        text = if (selected) "${npc.name} ♥${npc.affinity}" else npc.name,
                        color = if (selected) Color(0xFFFFF4DC) else Color(0xFFD8E1F2),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (npc.inScene) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80)),
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun StageVariantSwipePill(
    index: Int,
    total: Int,
    accentColor: Color = Color(0xFFFDE68A),
    onSwitch: (Int) -> Unit,
) {
    GuVariantSwipePill(
        index = index,
        total = total,
        onSwitch = onSwitch,
        containerColor = Color(0xD4101522),
        arrowColor = Color(0xFFF3F6FC),
        disabledArrowColor = Color(0x48CBD5E1),
        labelColor = accentColor,
        border = BorderStroke(0.5.dp, Color(0x48E2E8F0)),
        monospace = true,
    )
}

@Composable
internal fun StageInlineActionChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false,
    onDarkSurface: Boolean = true,
    accentColor: Color = Color(0xFFF5C563),
) {
    val bg = when {
        !onDarkSurface && highlight && enabled -> Color(0x2E9C723C)
        !onDarkSurface -> Color(0x185C4326)
        highlight && enabled -> accentColor.copy(alpha = 0.20f)
        else -> Color(0x55222B42)
    }
    val fg = when {
        !enabled -> if (onDarkSurface) Color(0x6694A3B8) else Color(0x666B5946)
        !onDarkSurface && highlight -> Color(0xFF5A3714)
        !onDarkSurface -> Color(0xFF3E2E1E)
        highlight -> Color(0xFFFFF0C4)
        else -> Color(0xFFDCE4F5)
    }
    val border = when {
        !enabled -> if (onDarkSurface) Color(0x2864748B) else Color(0x288C7558)
        !onDarkSurface && highlight -> Color(0x669C723C)
        !onDarkSurface -> Color(0x368C7558)
        highlight -> accentColor.copy(alpha = 0.55f)
        else -> Color(0x36CBD5E1)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(0.5.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
