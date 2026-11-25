package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpChoiceEval
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEndingRecord
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpTurnDelta
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.activeVariant
import java.io.File
import kotlin.math.abs

// ==================== Shared RP 3.0 Visual Components ====================

private val PORTRAIT_GRADIENTS = listOf(
    Color(0xFF8B3A62) to Color(0xFF34162E),
    Color(0xFF3A5BA0) to Color(0xFF1A254B),
    Color(0xFF9C662B) to Color(0xFF3E2610),
    Color(0xFF2E7D6F) to Color(0xFF143530),
    Color(0xFF6B4E9B) to Color(0xFF271B44),
    Color(0xFFA04747) to Color(0xFF3D171E),
)

private fun expressionEmoji(exprKey: String): String = when (exprKey.lowercase()) {
    "happy" -> "😊"
    "shy" -> "🌸"
    "angry" -> "💢"
    "sad" -> "💧"
    "surprised" -> "❗"
    "smirk" -> "😏"
    "dark" -> "🖤"
    else -> "✦"
}

internal fun resolveRpSpeaker(
    msg: ChatMessage?,
    rp: RpConfig?,
    fallbackTitle: String,
): Pair<String, RpCharacter?> {
    val cast = rp?.cast.orEmpty()
    val explicitSpeaker = msg?.activeVariant()?.rpSpeakerName?.trim()?.takeIf { it.isNotEmpty() }
    val matchedNpc = if (explicitSpeaker != null) {
        cast.firstOrNull { c ->
            c.name.trim().equals(explicitSpeaker, ignoreCase = true) ||
                c.aliases.any { it.trim().equals(explicitSpeaker, ignoreCase = true) }
        } ?: cast.firstOrNull { c ->
            !c.isPlayer && (
                c.name.contains(explicitSpeaker, ignoreCase = true) ||
                    explicitSpeaker.contains(c.name, ignoreCase = true)
                )
        }
    } else {
        cast.firstOrNull { !it.isPlayer && it.inScene }
            ?: cast.firstOrNull { !it.isPlayer }
    }
    val displayName = explicitSpeaker
        ?: matchedNpc?.name?.takeIf { it.isNotBlank() }
        ?: rp?.title?.takeIf { it.isNotBlank() }
        ?: fallbackTitle
    return displayName to matchedNpc
}

@Composable
fun RpNpcPortraitAvatar(
    character: RpCharacter?,
    fallbackName: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    borderColor: Color? = null,
) {
    val c = LocalAppTheme.current.colors
    val displayName = character?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val shape = if (cornerRadius != null) RoundedCornerShape(cornerRadius) else CircleShape
    val effectiveBorder = borderColor ?: c.accent.copy(alpha = 0.55f)
    val resolvedPath = remember(
        character?.mood,
        character?.expressionSprites,
        character?.spritePath,
        character?.avatarPath,
    ) {
        character?.let { RpEngine.resolveCharacterSpritePath(it) }
    }
    val avatarFile = remember(resolvedPath) {
        resolvedPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    if (avatarFile != null) {
        Image(
            painter = rememberAsyncImagePainter(avatarFile),
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(1.2.dp, effectiveBorder, shape),
        )
    } else {
        val pair = PORTRAIT_GRADIENTS[abs(displayName.hashCode()) % PORTRAIT_GRADIENTS.size]
        val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "✦"
        val fontSize = (size.value * 0.42f).coerceIn(11f, 28f).sp
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                .border(1.2.dp, effectiveBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = Color(0xFFFFF5E1),
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpTurnDeltaStrip(
    deltas: List<RpTurnDelta>,
    modifier: Modifier = Modifier,
    onDarkStage: Boolean = false,
) {
    if (deltas.isEmpty()) return
    val c = LocalAppTheme.current.colors

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        deltas.forEach { delta ->
            val (bg, fg, border) = resolveDeltaColors(delta, onDarkStage, c)
            val displayText = formatDeltaLabel(delta)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Text(
                    text = displayText,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                )
            }
        }
    }
}

private fun formatDeltaLabel(delta: RpTurnDelta): String {
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

private fun resolveDeltaColors(
    delta: RpTurnDelta,
    onDarkStage: Boolean,
    c: com.gugachat.app.core.theme.AppColors,
): Triple<Color, Color, Color> {
    if (onDarkStage) {
        val baseBg = Color(0xCC121726)
        return when (delta.kind.lowercase()) {
            "affinity" -> if (delta.positive) {
                Triple(Color(0xCC3B1D2C), Color(0xFFFFB4CE), Color(0x99F472B6))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x99F87171))
            }
            "hp" -> if (delta.positive) {
                Triple(Color(0xCC122F22), Color(0xFF86EFAC), Color(0x994ADE80))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x99F87171))
            }
            "mp" -> if (delta.positive) {
                Triple(Color(0xCC172544), Color(0xFFA5C4FF), Color(0x9960A5FA))
            } else {
                Triple(Color(0xCC362510), Color(0xFFFDE68A), Color(0x99FBBF24))
            }
            "gold", "chapter" -> Triple(Color(0xCC382812), Color(0xFFFDE68A), Color(0x99F59E0B))
            "ending" -> if (delta.positive) {
                Triple(Color(0xCC3E2B10), Color(0xFFFDE047), Color(0xCCF59E0B))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0xCCF87171))
            }
            "item_add", "quest" -> Triple(Color(0xCC132E27), Color(0xFF7DD3FC), Color(0x9938BDF8))
            "item_remove" -> Triple(Color(0xCC332414), Color(0xFFFCD34D), Color(0x88F59E0B))
            "flag" -> Triple(baseBg, Color(0xFFCBD5E1), Color(0x6694A3B8))
            else -> if (delta.positive) {
                Triple(Color(0xCC172642), Color(0xFF93C5FD), Color(0x8860A5FA))
            } else {
                Triple(Color(0xCC3B151B), Color(0xFFFCA5A5), Color(0x99F87171))
            }
        }
    }
    return when (delta.kind.lowercase()) {
        "affinity" -> if (delta.positive) {
            Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.55f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.45f))
        }
        "hp" -> if (delta.positive) {
            Triple(c.successBg, c.success, c.success.copy(alpha = 0.45f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.45f))
        }
        "mp" -> if (delta.positive) {
            Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.45f))
        } else {
            Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.45f))
        }
        "gold", "chapter" -> Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.5f))
        "ending" -> if (delta.positive) {
            Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.7f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.6f))
        }
        "item_add" -> Triple(c.successBg, c.success, c.success.copy(alpha = 0.45f))
        "item_remove" -> Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.45f))
        "quest" -> Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.4f))
        "flag" -> Triple(c.surfaceSoft, c.muted, c.border)
        else -> if (delta.positive) {
            Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.42f))
        } else {
            Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.45f))
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
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.72f)),
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
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
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    text = ending.title,
                    color = Color(0xFFFDF8EE),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
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
    onDarkStage: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val stageHighlight = accentColor ?: Color(0xFFE8B95B)
    var forceConfirmTarget by remember { mutableStateOf<Pair<RpChoice, String>?>(null) }

    forceConfirmTarget?.let { (choice, reason) ->
        GuConfirmDialog(
            title = strings.rp.choiceForceConfirmTitle,
            message = strings.rp.choiceForceConfirmMsg.fmt("reason" to reason),
            confirmText = strings.rp.choiceForceProceed,
            cancelText = strings.common.cancel,
            onConfirm = {
                forceConfirmTarget = null
                onSelectChoice(choice, true)
            },
            onDismiss = { forceConfirmTarget = null },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

            val cardBgBrush = when {
                onDarkStage && !eval.unlocked -> Brush.horizontalGradient(
                    listOf(Color(0xB8181B28), Color(0xA6131622)),
                )
                onDarkStage -> Brush.horizontalGradient(
                    listOf(Color(0xDB1C2338), Color(0xCF141A2B)),
                )
                !eval.unlocked -> Brush.horizontalGradient(
                    listOf(c.surfaceSoft.copy(alpha = 0.72f), c.surfaceSoft.copy(alpha = 0.55f)),
                )
                else -> Brush.horizontalGradient(
                    listOf(c.primarySoft.copy(alpha = 0.68f), c.surfaceSoft),
                )
            }
            val cardBorderColor = when {
                !eval.unlocked -> if (onDarkStage) Color(0x6694A3B8) else c.outline.copy(alpha = 0.7f)
                onDarkStage -> stageHighlight.copy(alpha = 0.58f)
                else -> c.primary.copy(alpha = 0.42f)
            }
            val titleColor = when {
                onDarkStage && !eval.unlocked -> Color(0xFFB3BDCE)
                onDarkStage -> Color(0xFFF6F8FD)
                !eval.unlocked -> c.muted
                else -> c.ink
            }
            val subtitleColor = when {
                onDarkStage -> Color(0xFFAAB4CA)
                else -> c.muted
            }

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
                shape = RoundedCornerShape(13.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (eval.unlocked) 1f else 0.84f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBgBrush)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !eval.unlocked -> if (onDarkStage) Color(0xFF363F56) else c.outline
                                    onDarkStage -> stageHighlight
                                    else -> c.primary
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!eval.unlocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = strings.rp.choiceLockedLabel,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = choice.label,
                            color = titleColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        if (!choice.consequenceHint.isNullOrBlank()) {
                            Text(
                                text = choice.consequenceHint,
                                color = subtitleColor,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val toneMeta = resolveChoiceTone(choice.tone, isZh)
                        val hasSkill = !choice.skillCheck.isNullOrBlank()
                        val hasCost = (choice.costGold ?: 0) > 0
                        val showConditionPill = !eval.unlocked || hasExplicitCondition

                        if (toneMeta != null || hasSkill || hasCost || showConditionPill) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (toneMeta != null) {
                                    ChoiceMetaChip(
                                        text = toneMeta.first,
                                        variant = toneMeta.second,
                                        onDarkStage = onDarkStage,
                                    )
                                }
                                if (hasSkill) {
                                    val dcSuffix = choice.difficulty?.let { " DC$it" }.orEmpty()
                                    ChoiceMetaChip(
                                        text = "🎲 ${choice.skillCheck}$dcSuffix",
                                        variant = GuBadgeVariant.HONEY,
                                        onDarkStage = onDarkStage,
                                    )
                                }
                                if (hasCost) {
                                    ChoiceMetaChip(
                                        text = "🪙 ${choice.costGold}",
                                        variant = GuBadgeVariant.AMBER,
                                        onDarkStage = onDarkStage,
                                    )
                                }
                                if (!eval.unlocked) {
                                    ChoiceMetaChip(
                                        text = "🔒 $reasonText",
                                        variant = GuBadgeVariant.DANGER,
                                        onDarkStage = onDarkStage,
                                    )
                                } else if (hasExplicitCondition) {
                                    val metLabel = choice.conditionText?.takeIf { it.isNotBlank() }
                                        ?: if (isZh) "条件满足" else "Unlocked"
                                    ChoiceMetaChip(
                                        text = "✓ $metLabel",
                                        variant = GuBadgeVariant.EMERALD,
                                        onDarkStage = onDarkStage,
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
            val ghostBg = if (onDarkStage) Color(0x88121726) else Color.Transparent
            val ghostBorder = if (onDarkStage) Color(0x55A7B5D1) else c.border
            val ghostFg = if (onDarkStage) Color(0xFFD8E1F2) else c.muted

            Surface(
                onClick = onRefreshChoices,
                enabled = enabled && !isRefreshing,
                shape = RoundedCornerShape(999.dp),
                color = ghostBg,
                border = BorderStroke(1.dp, ghostBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = if (onDarkStage) stageHighlight else c.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = ghostFg,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (isRefreshing) strings.rp.refreshingChoices else strings.rp.refreshChoicesBtn,
                        color = ghostFg,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun resolveChoiceTone(tone: String?, isZh: Boolean): Pair<String, GuBadgeVariant>? {
    val raw = tone?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return when (raw.lowercase()) {
        "romantic", "flirt", "love", "心动", "暧昧", "情愫" ->
            (if (isZh) "心动" else "Romantic") to GuBadgeVariant.HONEY
        "cunning", "scheme", "intrigue", "权谋", "心机" ->
            (if (isZh) "权谋" else "Cunning") to GuBadgeVariant.AMBER
        "bold", "brave", "adventure", "冒险", "果断" ->
            (if (isZh) "冒险" else "Bold") to GuBadgeVariant.BLUE
        "mystery", "probe", "test", "试探", "悬疑" ->
            (if (isZh) "试探" else "Probe") to GuBadgeVariant.BLUE
        "gentle", "kind", "温柔", "安抚" ->
            (if (isZh) "温柔" else "Gentle") to GuBadgeVariant.EMERALD
        "calm", "rational", "冷静", "理性" ->
            (if (isZh) "冷静" else "Calm") to GuBadgeVariant.NEUTRAL
        "be", "danger", "risk", "be预警", "高危" ->
            (if (isZh) "BE预警" else "BE Risk") to GuBadgeVariant.DANGER
        else -> raw to GuBadgeVariant.NEUTRAL
    }
}

@Composable
private fun ChoiceMetaChip(
    text: String,
    variant: GuBadgeVariant,
    onDarkStage: Boolean,
) {
    if (!onDarkStage) {
        GuBadge(text = text, variant = variant)
        return
    }
    val (bg, fg, border) = when (variant) {
        GuBadgeVariant.HONEY -> Triple(Color(0xCC3B2613), Color(0xFFFDE68A), Color(0x88F59E0B))
        GuBadgeVariant.AMBER -> Triple(Color(0xCC3A2711), Color(0xFFFCD34D), Color(0x88F59E0B))
        GuBadgeVariant.BLUE -> Triple(Color(0xCC18264A), Color(0xFF93C5FD), Color(0x8860A5FA))
        GuBadgeVariant.EMERALD -> Triple(Color(0xCC123023), Color(0xFF86EFAC), Color(0x884ADE80))
        GuBadgeVariant.DANGER -> Triple(Color(0xCC3C161D), Color(0xFFFCA5A5), Color(0x88F87171))
        GuBadgeVariant.NEUTRAL -> Triple(Color(0xAA1F273B), Color(0xFFCBD5E1), Color(0x6694A3B8))
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
        )
    }
}

// ==================== Dialog Box Skins ====================

private data class DialogSkinSpec(
    val boxBg: Color,
    val boxBorder: BorderStroke?,
    val boxShape: RoundedCornerShape,
    val topStripColor: Color?,
    val nameplateBg: Color,
    val nameplateBorder: Color,
    val nameplateText: Color,
    val nameplateMoodText: Color,
    val nameplateDotColor: Color,
    val nameplateDecorPrefix: String,
    val nameplateFontFamily: FontFamily,
    val bodyText: Color,
    val mutedText: Color,
    val dividerColor: Color,
    val markdownPalette: MarkdownPalette,
    val isDarkSurface: Boolean,
)

private fun resolveDialogSkin(
    theme: RpVisualTheme,
    stageAccent: Color,
    customAccent: Color?,
    appIsDark: Boolean,
): DialogSkinSpec {
    val opacity = theme.dialogOpacity.coerceIn(0.45f, 1.0f)
    val style = theme.dialogStyle.trim().lowercase()
    val hasTopLeftTab = theme.showNameplate

    fun darkMarkdownPalette(accent: Color, text: Color = Color(0xFFF3F6FC)): MarkdownPalette = MarkdownPalette(
        text = text,
        muted = Color(0xFFB3BED4),
        accent = accent,
        border = Color(0x44CBD5E1),
        quoteBg = Color(0x551F2940),
        inlineCodeBg = Color(0x6627334E),
        inlineCodeInk = Color(0xFFFDE68A),
        codeBg = Color(0xCC0A0F1A),
        codeHeaderBg = Color(0xCC141D2E),
        codeInk = Color(0xFFE6EDF3),
        codeMeta = Color(0xFFA6B4CA),
        tableHeaderBg = Color(0x661C263B),
    )

    return when (style) {
        "classic_vn" -> {
            val gold = customAccent ?: Color(0xFFD4AF37)
            DialogSkinSpec(
                boxBg = Color(0xFF1A111D).copy(alpha = opacity),
                boxBorder = BorderStroke(1.6.dp, gold.copy(alpha = 0.88f)),
                boxShape = RoundedCornerShape(
                    topStart = if (hasTopLeftTab) 2.dp else 14.dp,
                    topEnd = 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp,
                ),
                topStripColor = gold.copy(alpha = 0.55f),
                nameplateBg = Color(0xFF2B1723).copy(alpha = (opacity + 0.10f).coerceAtMost(0.98f)),
                nameplateBorder = gold,
                nameplateText = Color(0xFFFFF4D6),
                nameplateMoodText = Color(0xFFE3C996),
                nameplateDotColor = gold,
                nameplateDecorPrefix = "❖ ",
                nameplateFontFamily = FontFamily.Serif,
                bodyText = Color(0xFFFBF5E8),
                mutedText = Color(0xFFD3C5AF),
                dividerColor = gold.copy(alpha = 0.30f),
                markdownPalette = darkMarkdownPalette(accent = gold, text = Color(0xFFFBF5E8)),
                isDarkSurface = true,
            )
        }

        "minimal" -> {
            val minimalAccent = customAccent ?: Color(0xFFE2E8F0)
            DialogSkinSpec(
                boxBg = Color(0xFF0A0E18).copy(alpha = (opacity * 0.86f).coerceIn(0.38f, 0.92f)),
                boxBorder = null,
                boxShape = RoundedCornerShape(18.dp),
                topStripColor = Color.White.copy(alpha = 0.14f),
                nameplateBg = Color(0xFF0F1624).copy(alpha = opacity),
                nameplateBorder = Color(0x33FFFFFF),
                nameplateText = Color(0xFFF8FAFC),
                nameplateMoodText = Color(0xFFCBD5E1),
                nameplateDotColor = minimalAccent,
                nameplateDecorPrefix = "",
                nameplateFontFamily = FontFamily.Default,
                bodyText = Color(0xFFF4F7FD),
                mutedText = Color(0xFFB5C2D9),
                dividerColor = Color(0x26FFFFFF),
                markdownPalette = darkMarkdownPalette(accent = stageAccent),
                isDarkSurface = true,
            )
        }

        "cyber" -> {
            val neon = customAccent ?: Color(0xFF06B6D4)
            DialogSkinSpec(
                boxBg = Color(0xFF08101B).copy(alpha = opacity),
                boxBorder = BorderStroke(1.5.dp, neon.copy(alpha = 0.90f)),
                boxShape = RoundedCornerShape(
                    topStart = 2.dp,
                    topEnd = 10.dp,
                    bottomStart = 10.dp,
                    bottomEnd = 2.dp,
                ),
                topStripColor = neon,
                nameplateBg = Color(0xFF0B1C2B).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                nameplateBorder = neon,
                nameplateText = Color(0xFFE0FAFF),
                nameplateMoodText = Color(0xFF7DD3FC),
                nameplateDotColor = neon,
                nameplateDecorPrefix = "▸ ",
                nameplateFontFamily = FontFamily.Monospace,
                bodyText = Color(0xFFE5F6FC),
                mutedText = Color(0xFF99C8DC),
                dividerColor = neon.copy(alpha = 0.34f),
                markdownPalette = darkMarkdownPalette(accent = neon, text = Color(0xFFE5F6FC)),
                isDarkSurface = true,
            )
        }

        "parchment" -> {
            if (appIsDark) {
                val sepiaAccent = customAccent ?: Color(0xFFCFA76E)
                DialogSkinSpec(
                    boxBg = Color(0xFF261D15).copy(alpha = opacity),
                    boxBorder = BorderStroke(1.2.dp, sepiaAccent.copy(alpha = 0.68f)),
                    boxShape = RoundedCornerShape(
                        topStart = if (hasTopLeftTab) 4.dp else 14.dp,
                        topEnd = 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp,
                    ),
                    topStripColor = sepiaAccent.copy(alpha = 0.35f),
                    nameplateBg = Color(0xFF35281C).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                    nameplateBorder = sepiaAccent.copy(alpha = 0.8f),
                    nameplateText = Color(0xFFFCEBC9),
                    nameplateMoodText = Color(0xFFD6BE9A),
                    nameplateDotColor = sepiaAccent,
                    nameplateDecorPrefix = "❦ ",
                    nameplateFontFamily = FontFamily.Serif,
                    bodyText = Color(0xFFF6ECDA),
                    mutedText = Color(0xFFCBBBA0),
                    dividerColor = sepiaAccent.copy(alpha = 0.28f),
                    markdownPalette = darkMarkdownPalette(accent = sepiaAccent, text = Color(0xFFF6ECDA)),
                    isDarkSurface = true,
                )
            } else {
                val sepiaAccent = customAccent ?: Color(0xFF9C723C)
                val paperInk = Color(0xFF2B2016)
                val paperMuted = Color(0xFF6B5946)
                DialogSkinSpec(
                    boxBg = Color(0xFFF7F0E2).copy(alpha = opacity),
                    boxBorder = BorderStroke(1.2.dp, sepiaAccent.copy(alpha = 0.65f)),
                    boxShape = RoundedCornerShape(
                        topStart = if (hasTopLeftTab) 4.dp else 14.dp,
                        topEnd = 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp,
                    ),
                    topStripColor = sepiaAccent.copy(alpha = 0.35f),
                    nameplateBg = Color(0xFFEDE0C8).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                    nameplateBorder = sepiaAccent.copy(alpha = 0.78f),
                    nameplateText = Color(0xFF3A2613),
                    nameplateMoodText = Color(0xFF7B6347),
                    nameplateDotColor = sepiaAccent,
                    nameplateDecorPrefix = "❦ ",
                    nameplateFontFamily = FontFamily.Serif,
                    bodyText = paperInk,
                    mutedText = paperMuted,
                    dividerColor = sepiaAccent.copy(alpha = 0.28f),
                    markdownPalette = MarkdownPalette(
                        text = paperInk,
                        muted = paperMuted,
                        accent = sepiaAccent,
                        border = Color(0x559C723C),
                        quoteBg = Color(0x33C8A878),
                        inlineCodeBg = Color(0x33B08D57),
                        inlineCodeInk = Color(0xFF5A3918),
                        codeBg = Color(0xFFEBE1CC),
                        codeHeaderBg = Color(0xFFDFD1B5),
                        codeInk = Color(0xFF2B2016),
                        codeMeta = Color(0xFF6B5946),
                        tableHeaderBg = Color(0x44C8A878),
                    ),
                    isDarkSurface = false,
                )
            }
        }

        else -> {
            // Default: "glass"
            DialogSkinSpec(
                boxBg = Color(0xFF101522).copy(alpha = opacity),
                boxBorder = BorderStroke(1.dp, stageAccent.copy(alpha = 0.56f)),
                boxShape = RoundedCornerShape(
                    topStart = if (hasTopLeftTab) 4.dp else 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                topStripColor = null,
                nameplateBg = Color(0xFF211C30).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                nameplateBorder = stageAccent.copy(alpha = 0.78f),
                nameplateText = Color(0xFFFFF4DA),
                nameplateMoodText = Color(0xFFD6C29B),
                nameplateDotColor = stageAccent,
                nameplateDecorPrefix = "",
                nameplateFontFamily = FontFamily.Default,
                bodyText = Color(0xFFF3F6FC),
                mutedText = Color(0xFFB8C4DC),
                dividerColor = Color(0x33CBD5E1),
                markdownPalette = darkMarkdownPalette(accent = stageAccent),
                isDarkSurface = true,
            )
        }
    }
}

// ==================== Chengguang Interactive Visual Novel Stage View ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpTheaterStageView(
    rp: RpConfig,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    isRefreshingChoices: Boolean,
    hasModelSelected: Boolean,
    isGeneratingArt: Boolean = false,
    artStatusLabel: String = "",
    onOpenModelSheet: () -> Unit,
    onExitTheater: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenFrontendEditor: () -> Unit = onOpenStudio,
    onQuickSave: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    onSelectChoice: (RpChoice, Boolean) -> Unit,
    onRefreshChoices: () -> Unit,
    onContinue: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEditAssistantMessage: (String, String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onFrontendAction: (String) -> Unit = {},
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val appTheme = LocalAppTheme.current
    val c = appTheme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    val theme = rp.visualTheme
    val customAccent = remember(theme.accentHex) { parseOptionalHexColor(theme.accentHex) }
    val stageAccent = customAccent ?: Color(0xFFF5C563)
    val skin = remember(
        theme.dialogStyle,
        theme.dialogOpacity,
        theme.showNameplate,
        stageAccent,
        customAccent,
        appTheme.isDark,
    ) {
        resolveDialogSkin(theme, stageAccent, customAccent, appTheme.isDark)
    }

    val activeMessages = remember(messages) { TimelineReconciler.activeMessages(messages) }
    val latestAssistant = remember(activeMessages) { activeMessages.lastOrNull { it.role == "assistant" } }
    val latestVariant = latestAssistant?.activeVariant()

    var hideUi by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var editBuffer by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    val (resolvedSpeakerName, resolvedSpeakerNpc) = remember(
        latestAssistant?.id,
        latestAssistant?.activeVariantIndex,
        rp.cast,
        rp.title,
    ) {
        resolveRpSpeaker(latestAssistant, rp, strings.rp.badge)
    }
    var focusedNpcId by remember(latestAssistant?.id, latestAssistant?.activeVariantIndex) {
        mutableStateOf<String?>(null)
    }
    val nonPlayerCast = remember(rp.cast) { rp.cast.filter { !it.isPlayer } }
    val stageCharacters = remember(rp.cast, resolvedSpeakerName) {
        RpEngine.resolveStageCharacters(rp.cast, resolvedSpeakerName)
    }
    val displayedNpc = remember(focusedNpcId, resolvedSpeakerNpc, nonPlayerCast) {
        focusedNpcId?.let { id -> nonPlayerCast.firstOrNull { it.id == id } }
            ?: resolvedSpeakerNpc
            ?: nonPlayerCast.firstOrNull { it.inScene }
            ?: nonPlayerCast.firstOrNull()
    }

    val (parsedReasoning, cleanBody) = remember(latestAssistant?.content, latestAssistant?.reasoning) {
        if (latestAssistant != null) {
            extractMessageThinkAndBody(
                explicitReasoning = latestAssistant.reasoning,
                rawContent = latestAssistant.content,
                mode = ConversationMode.rp,
            )
        } else {
            "" to ""
        }
    }

    val dialogueScrollState = rememberScrollState()
    LaunchedEffect(latestAssistant?.id) {
        dialogueScrollState.scrollTo(0)
    }
    LaunchedEffect(cleanBody.length, isStreaming) {
        if (isStreaming && dialogueScrollState.maxValue > 0) {
            dialogueScrollState.scrollTo(dialogueScrollState.maxValue)
        }
    }

    if (showEditDialog && latestAssistant != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text(strings.rp.editReplyTitle, color = c.ink, fontWeight = FontWeight.ExtraBold) },
            text = {
                GuTextField(
                    value = editBuffer,
                    onValueChange = { editBuffer = it },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 10,
                )
            },
            confirmButton = {
                GuButton(
                    onClick = {
                        onEditAssistantMessage(latestAssistant.id, editBuffer)
                        showEditDialog = false
                    },
                    enabled = editBuffer.isNotBlank(),
                    compact = true,
                ) {
                    Text(strings.common.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(strings.common.cancel, color = c.muted)
                }
            },
        )
    }

    if (showHistorySheet) {
        TheaterHistoryLogSheet(
            rp = rp,
            messages = activeMessages,
            onDismiss = { showHistorySheet = false },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = hideUi) { hideUi = false },
    ) {
        // 1. Full-Bleed Scene Backdrop (Custom CG or Curated Atmosphere + Live backdropDim)
        TheaterStageBackdrop(
            rp = rp,
            backdropDim = theme.backdropDim,
            dimForReading = !hideUi,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Immersive CG Appreciation Mode: Show UI Floating Pill Only
        AnimatedVisibility(
            visible = hideUi,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            StageGlassPill(
                label = strings.rp.theaterShowUi,
                icon = Icons.Default.Visibility,
                accent = true,
                accentColor = stageAccent,
                onClick = { hideUi = false },
            )
        }

        // 3. Full Interactive Chengguang Stage Chrome (avoid AnimatedVisibility graphicsLayer wrapper so transparent WebView paints immediately on cold start)
        if (!hideUi) {
            val hasCustomStatusWidget = theme.showCustomStatusWidget && theme.customStatusHtml.isNotBlank()
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Stage Bar (with 1-tap Frontend Customizer button)
                TheaterTopStageBar(
                    rp = rp,
                    isZh = isZh,
                    stageAccent = stageAccent,
                    hideSecondaryEnvRow = hasCustomStatusWidget,
                    onExitTheater = onExitTheater,
                    onOpenHistory = { showHistorySheet = true },
                    onOpenFrontendEditor = onOpenFrontendEditor,
                    onHideUi = { hideUi = true },
                    onOpenStudio = onOpenStudio,
                )

                // Floating AI Art Generation Status Chip
                AnimatedVisibility(
                    visible = isGeneratingArt,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xD9101522),
                            border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.72f)),
                            shadowElevation = 4.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.8.dp,
                                    color = stageAccent,
                                )
                                Text(
                                    text = "✨ ${artStatusLabel.ifBlank { strings.rp.aiGeneratingArt }}",
                                    color = Color(0xFFFFF4DC),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (activeMessages.isEmpty()) {
                    // Optional Custom HTML/CSS Frontend Status Widget even before first turn
                    if (hasCustomStatusWidget) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            RpHtmlCardSandboxInternal(
                                htmlFragment = theme.customStatusHtml,
                                rp = rp,
                                customCss = theme.customCss,
                                minHeightDp = 52,
                                maxHeightDp = 200,
                                forceDark = true,
                                onFrontendAction = onFrontendAction,
                                onFrontendSetFlag = onFrontendSetFlag,
                                onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            )
                        }
                    }
                    // Opening Curtain Card
                    TheaterOpeningCurtain(
                        rp = rp,
                        hasModelSelected = hasModelSelected,
                        isZh = isZh,
                        stageAccent = stageAccent,
                        onOpenModelSheet = onOpenModelSheet,
                        onStarterPrompt = onStarterPrompt,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    // Scrollable Stage Area: Custom Status Widget + Big-Sprite Stage + Deltas + Dialogue Frame + Choice Deck
                    val stageScroll = rememberScrollState()
                    val spriteLayout = theme.spriteLayout.trim().lowercase()
                    val bodyFontSizeSp = theme.fontSizeSp.coerceIn(12f, 22f)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(stageScroll)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Optional Custom HTML/CSS Frontend Status Widget right below Top Stage Bar
                        if (hasCustomStatusWidget) {
                            RpHtmlCardSandboxInternal(
                                htmlFragment = theme.customStatusHtml,
                                rp = rp,
                                customCss = theme.customCss,
                                minHeightDp = 52,
                                maxHeightDp = 200,
                                forceDark = true,
                                onFrontendAction = onFrontendAction,
                                onFrontendSetFlag = onFrontendSetFlag,
                                onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            )
                        }

                        // Multi-Character Big-Sprite Stage / Compact Portrait Card / Hidden
                        when (spriteLayout) {
                            "hidden" -> Unit
                            "portrait_card" -> {
                                if (displayedNpc != null) {
                                    TheaterCastPortraitShowcase(
                                        focusedNpc = displayedNpc,
                                        allNpcs = nonPlayerCast,
                                        stageAccent = stageAccent,
                                        onSelectNpc = { npc -> focusedNpcId = npc.id },
                                        onOpenStudio = onOpenStudio,
                                    )
                                }
                            }
                            else -> {
                                if (stageCharacters.isNotEmpty() || displayedNpc != null) {
                                    TheaterChengguangSpriteStage(
                                        stageCharacters = stageCharacters,
                                        activeHighlightId = focusedNpcId
                                            ?: resolvedSpeakerNpc?.id
                                            ?: stageCharacters.firstOrNull()?.second?.id,
                                        focusedNpc = displayedNpc,
                                        allNpcs = nonPlayerCast,
                                        spriteHeightRatio = theme.spriteHeightRatio,
                                        hasCustomStatusWidget = hasCustomStatusWidget,
                                        stageAccent = stageAccent,
                                        onSelectNpc = { npc -> focusedNpcId = npc.id },
                                        onOpenStudio = onOpenStudio,
                                    )
                                }
                            }
                        }

                        // Floating Turn Deltas & Ending Card
                        val deltas = latestVariant?.rpDeltas.orEmpty()
                        if (theme.showTurnDeltas && deltas.isNotEmpty() && !isStreaming) {
                            RpTurnDeltaStrip(
                                deltas = deltas,
                                onDarkStage = true,
                            )
                        }
                        latestVariant?.rpEndingUnlocked?.let { ending ->
                            RpEndingCard(ending = ending)
                        }

                        // Chengguang Dialogue Frame (5 Live-Editable Skins)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            // Anchored Nameplate + Variant Swipe Row
                            val variantAssistant = latestAssistant?.takeIf { it.variants.size > 1 }
                            if (theme.showNameplate || variantAssistant != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = if (theme.showNameplate) {
                                        Arrangement.SpaceBetween
                                    } else {
                                        Arrangement.End
                                    },
                                ) {
                                    if (theme.showNameplate) {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = 2.dp,
                                                bottomEnd = 2.dp,
                                            ),
                                            color = skin.nameplateBg,
                                            border = BorderStroke(1.dp, skin.nameplateBorder),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (skin.nameplateDecorPrefix.isEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(skin.nameplateDotColor),
                                                    )
                                                }
                                                Text(
                                                    text = "${skin.nameplateDecorPrefix}$resolvedSpeakerName",
                                                    color = skin.nameplateText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = skin.nameplateFontFamily,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                resolvedSpeakerNpc?.mood?.takeIf { it.isNotBlank() }?.let { mood ->
                                                    Text(
                                                        text = "· $mood",
                                                        color = skin.nameplateMoodText,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (variantAssistant != null) {
                                        StageVariantSwipePill(
                                            index = variantAssistant.activeVariantIndex,
                                            total = variantAssistant.variants.size,
                                            accentColor = stageAccent,
                                            onSwitch = { idx -> onSwitchVariant(variantAssistant.id, idx) },
                                        )
                                    }
                                }
                            }

                            // Dialogue Box Surface
                            Surface(
                                shape = skin.boxShape,
                                color = skin.boxBg,
                                border = skin.boxBorder,
                                shadowElevation = if (skin.boxBorder != null) 6.dp else 0.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    // Optional top neon/gilded accent strip
                                    skin.topStripColor?.let { stripCol ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            stripCol.copy(alpha = 0.25f),
                                                            stripCol,
                                                            stripCol.copy(alpha = 0.25f),
                                                        ),
                                                    ),
                                                ),
                                        )
                                    }

                                    // Scrollable Narrative Viewport (capped height so long text never clips or hides choices)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 96.dp, max = 300.dp)
                                            .verticalScroll(dialogueScrollState)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (parsedReasoning.isNotBlank()) {
                                            ReasoningCard(
                                                reasoning = parsedReasoning,
                                                isStreaming = latestAssistant?.status == "streaming" && cleanBody.isEmpty(),
                                            )
                                        }

                                        if (cleanBody.isNotBlank()) {
                                            RpRichMessageContentThemed(
                                                rawMarkdown = cleanBody,
                                                rp = rp,
                                                fontSize = bodyFontSizeSp.sp,
                                                lineHeight = (bodyFontSizeSp * 1.6f).sp,
                                                useSerif = theme.useSerif,
                                                textColor = skin.bodyText,
                                                palette = skin.markdownPalette,
                                                forceDarkSandbox = skin.isDarkSurface,
                                                onCopyToast = onToast,
                                                onFrontendAction = onFrontendAction,
                                                onFrontendSetFlag = onFrontendSetFlag,
                                                onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                                            )
                                        } else if (isStreaming || latestAssistant?.status == "streaming") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = stageAccent,
                                                )
                                                Text(
                                                    text = strings.chat.streamingWriting,
                                                    color = skin.mutedText,
                                                    fontSize = 13.sp,
                                                )
                                            }
                                        } else {
                                            val lastUser = activeMessages.lastOrNull { it.role == "user" }
                                            Text(
                                                text = lastUser?.content?.takeIf { it.isNotBlank() } ?: strings.chat.streamingWriting,
                                                color = skin.mutedText,
                                                fontSize = 13.5.sp,
                                                lineHeight = 20.sp,
                                            )
                                        }

                                        val failedMsg = latestAssistant
                                        val errText = failedMsg?.error
                                        if (failedMsg != null && !errText.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (skin.isDarkSurface) Color(0xCC3C161D) else c.dangerBg,
                                                border = BorderStroke(1.dp, Color(0x99F87171)),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.ErrorOutline,
                                                        contentDescription = null,
                                                        tint = if (skin.isDarkSurface) Color(0xFFFCA5A5) else c.danger,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        text = errText,
                                                        color = if (skin.isDarkSurface) Color(0xFFFECACA) else c.danger,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    if (!isStreaming) {
                                                        TextButton(onClick = { onRegenerate(failedMsg.id) }) {
                                                            Text(
                                                                strings.common.retry,
                                                                color = if (skin.isDarkSurface) Color(0xFFFCA5A5) else c.danger,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Compact Quick-Action Bar inside Dialogue Frame
                                    HorizontalDivider(color = skin.dividerColor)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        StageInlineActionChip(
                                            label = strings.rp.continueBtn,
                                            icon = Icons.Default.PlayArrow,
                                            enabled = !isStreaming && latestAssistant != null,
                                            highlight = true,
                                            onDarkSurface = skin.isDarkSurface,
                                            accentColor = stageAccent,
                                            onClick = onContinue,
                                        )
                                        StageInlineActionChip(
                                            label = strings.rp.reroll,
                                            icon = Icons.Default.Refresh,
                                            enabled = !isStreaming && latestAssistant != null,
                                            onDarkSurface = skin.isDarkSurface,
                                            accentColor = stageAccent,
                                            onClick = { latestAssistant?.let { onRegenerate(it.id) } },
                                        )
                                        StageInlineActionChip(
                                            label = strings.common.edit,
                                            icon = Icons.Default.Edit,
                                            enabled = !isStreaming && latestAssistant != null,
                                            onDarkSurface = skin.isDarkSurface,
                                            accentColor = stageAccent,
                                            onClick = {
                                                if (latestAssistant != null) {
                                                    editBuffer = cleanBody.ifBlank { latestAssistant.content }
                                                    showEditDialog = true
                                                }
                                            },
                                        )
                                        StageInlineActionChip(
                                            label = strings.rp.quickSaveBtn,
                                            icon = Icons.Default.Save,
                                            enabled = true,
                                            onDarkSurface = skin.isDarkSurface,
                                            accentColor = stageAccent,
                                            onClick = onQuickSave,
                                        )
                                        if (cleanBody.isNotBlank()) {
                                            StageInlineActionChip(
                                                label = strings.common.copy,
                                                icon = Icons.Default.ContentCopy,
                                                enabled = true,
                                                onDarkSurface = skin.isDarkSurface,
                                                accentColor = stageAccent,
                                                onClick = {
                                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("reply", cleanBody))
                                                    onToast(strings.common.copied)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Dramatic Choice Deck right below dialogue frame
                        val stageChoices = remember(latestAssistant?.rpChoices, rp.pendingChoices) {
                            latestAssistant?.rpChoices?.takeIf { it.isNotEmpty() } ?: rp.pendingChoices
                        }
                        if (!isStreaming && (stageChoices.isNotEmpty() || isRefreshingChoices || (rp.enableChoices && latestAssistant != null))) {
                            RpChoiceDeck(
                                choices = stageChoices,
                                rp = rp,
                                isRefreshing = isRefreshingChoices,
                                onSelectChoice = onSelectChoice,
                                onRefreshChoices = onRefreshChoices,
                                onDarkStage = true,
                                enabled = !isStreaming,
                                accentColor = stageAccent,
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ==================== True Chengguang Multi-Character Big-Sprite Stage ====================

@Composable
private fun TheaterChengguangSpriteStage(
    stageCharacters: List<Pair<String, RpCharacter>>,
    activeHighlightId: String?,
    focusedNpc: RpCharacter?,
    allNpcs: List<RpCharacter>,
    spriteHeightRatio: Float,
    hasCustomStatusWidget: Boolean,
    stageAccent: Color,
    onSelectNpc: (RpCharacter) -> Unit,
    onOpenStudio: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH

    // Map spriteHeightRatio (0.25..0.62, default 0.44) -> 150dp..270dp (~211dp default; ~185dp when custom HUD is shown)
    val clampedRatio = spriteHeightRatio.coerceIn(0.25f, 0.62f)
    val baseStageHeight = 150f + ((clampedRatio - 0.25f) / 0.37f) * 120f
    val stageHeight: Dp = (baseStageHeight - if (hasCustomStatusWidget) 26f else 0f)
        .coerceAtLeast(148f)
        .dp
    val compactHeight = stageHeight < 205.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (stageCharacters.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
            ) {
                val count = stageCharacters.size
                val slotMaxWidth = when (count) {
                    1 -> (maxWidth * 0.58f).coerceAtMost(220.dp)
                    2 -> (maxWidth * 0.475f).coerceAtMost(192.dp)
                    else -> (maxWidth * 0.365f).coerceAtMost(160.dp)
                }
                val centerWidth = if (count >= 3) (maxWidth * 0.405f).coerceAtMost(176.dp) else slotMaxWidth

                stageCharacters.forEach { (slot, char) ->
                    val isActive = char.id == activeHighlightId
                    val alignment = when (slot) {
                        "left" -> Alignment.BottomStart
                        "right" -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    }
                    val cardWidth = if (slot == "center") centerWidth else slotMaxWidth
                    val zOrder = when {
                        isActive -> 2f
                        slot == "center" -> 1f
                        else -> 0f
                    }
                    val targetScale = (if (isActive) 1.04f else 0.90f) * char.spriteScale.coerceIn(0.7f, 1.4f)
                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = tween(durationMillis = 260),
                        label = "stageSpriteScale",
                    )
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.62f,
                        animationSpec = tween(durationMillis = 260),
                        label = "stageSpriteAlpha",
                    )

                    ChengguangSpriteActorCard(
                        character = char,
                        isActive = isActive,
                        compactMode = count >= 3,
                        compactHeight = compactHeight,
                        stageAccent = stageAccent,
                        isZh = isZh,
                        onTap = { onSelectNpc(char) },
                        onOpenStudio = onOpenStudio,
                        modifier = Modifier
                            .align(alignment)
                            .zIndex(zOrder)
                            .width(cardWidth)
                            .fillMaxHeight(0.96f)
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                alpha = animatedAlpha
                                translationY = char.spriteOffsetY.dp.toPx()
                                transformOrigin = TransformOrigin(0.5f, 1.0f)
                            },
                    )
                }
            }
        }

        // Clickable horizontal mini-strip for inspecting & switching all cast members
        if (allNpcs.size > 1) {
            StageCastMiniStrip(
                allNpcs = allNpcs,
                focusedNpcId = focusedNpc?.id,
                stageAccent = stageAccent,
                onSelectNpc = onSelectNpc,
            )
        }
    }
}

@Composable
private fun ChengguangSpriteActorCard(
    character: RpCharacter,
    isActive: Boolean,
    compactMode: Boolean,
    compactHeight: Boolean,
    stageAccent: Color,
    isZh: Boolean,
    onTap: () -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val resolvedPath = remember(
        character.mood,
        character.expressionSprites,
        character.spritePath,
        character.avatarPath,
    ) {
        RpEngine.resolveCharacterSpritePath(character)
    }
    val spriteFile = remember(resolvedPath) {
        resolvedPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val exprKey = remember(character.mood) { RpEngine.resolveExpressionKey(character.mood) }
    val exprLabel = strings.rp.expressionLabels[exprKey] ?: exprKey
    val moodBadgeText = character.mood.trim().takeIf { it.isNotEmpty() } ?: exprLabel
    val tier = remember(character.affinity) { RpEngine.affinityTier(character.affinity) }
    val tierLabel = strings.rp.affinityTiers[tier.code]
        ?: if (isZh) tier.zhLabel else tier.enLabel
    val cardShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    val rimColor = if (isActive) stageAccent.copy(alpha = 0.88f) else Color(0x44CBD5E1)

    Surface(
        onClick = onTap,
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(if (isActive) 1.8.dp else 1.dp, rimColor),
        shadowElevation = if (isActive) 10.dp else 2.dp,
        modifier = modifier,
    ) {
        if (spriteFile != null) {
            // Real half-body VN Sprite Image with top-anchored crop and bottom atmospheric fade
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberAsyncImagePainter(spriteFile),
                    contentDescription = character.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                // Soft bottom fade blending into the dialogue frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.56f to Color.Transparent,
                                0.82f to Color(0x8A0A0E1A),
                                1.0f to Color(0xE0080C16),
                            ),
                        ),
                )
                // Active speaker top-right expression chip
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xB80E1422),
                    border = BorderStroke(1.dp, if (isActive) stageAccent.copy(alpha = 0.72f) else Color(0x44FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = if (compactMode) expressionEmoji(exprKey) else "${expressionEmoji(exprKey)} $moodBadgeText",
                        color = Color(0xFFFDF6E7),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // Compact name & affinity footer overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = character.name,
                        color = Color(0xFFFFF8EA),
                        fontSize = if (compactMode) 11.5.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (compactMode) "♥ ${character.affinity}" else "♥ ${character.affinity} · $tierLabel",
                        color = if (character.affinity < 0) Color(0xFFFCA5A5) else Color(0xFFFFB4CE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        } else {
            // Artistic Chengguang Character Standee Card (clean & balanced at both compact and tall stage heights)
            val pair = PORTRAIT_GRADIENTS[abs(character.name.hashCode()) % PORTRAIT_GRADIENTS.size]
            val initial = character.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "✦"
            val roleLabel = formatRpRoleLabel(character.role, isZh)
            val tight = compactMode || compactHeight

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                pair.first.copy(alpha = 0.92f),
                                pair.second.copy(alpha = 0.94f),
                                Color(0xE80C101B),
                            ),
                        ),
                    )
                    .drawBehind {
                        if (isActive) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(stageAccent.copy(alpha = 0.24f), Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.30f),
                                    radius = size.maxDimension * 0.65f,
                                ),
                            )
                        }
                    },
            ) {
                // Inner gilded hairline frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .border(
                            width = 0.8.dp,
                            color = if (isActive) stageAccent.copy(alpha = 0.45f) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 7.dp, bottomEnd = 7.dp),
                        ),
                )

                // Top-left subtle "Set Sprite" shortcut pill (never squeezes center stack)
                if (!compactMode) {
                    Surface(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x780B0F1A),
                        border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.38f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 8.dp),
                    ) {
                        Text(
                            text = if (isZh) "✨ 立绘" else "✨ Art",
                            color = Color(0xFFE6D8BC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Top-right expression / mood badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0x99101522),
                    border = BorderStroke(1.dp, if (isActive) stageAccent.copy(alpha = 0.65f) else Color(0x44FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 8.dp),
                ) {
                    Text(
                        text = if (compactMode) expressionEmoji(exprKey) else "${expressionEmoji(exprKey)} $moodBadgeText",
                        color = Color(0xFFFFF4DC),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Center Standee Emblem + Character Info
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (tight) 4.dp else 7.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (tight) 44.dp else 58.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        stageAccent.copy(alpha = if (isActive) 0.34f else 0.16f),
                                        Color(0x660B0F1A),
                                    ),
                                ),
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) stageAccent.copy(alpha = 0.85f) else Color(0x55FFFFFF),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial,
                            color = Color(0xFFFFF6E3),
                            fontSize = if (tight) 19.sp else 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Serif,
                        )
                    }

                    Text(
                        text = character.name,
                        color = Color(0xFFFFF8EA),
                        fontSize = if (compactMode) 12.sp else if (tight) 13.5.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    if (roleLabel.isNotBlank() && !compactMode) {
                        Text(
                            text = "❖ $roleLabel",
                            color = Color(0xFFD6C7AE),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Affinity heart pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x88141826),
                        border = BorderStroke(
                            1.dp,
                            if (character.affinity < 0) Color(0x88F87171) else Color(0x88F472B6),
                        ),
                    ) {
                        Text(
                            text = if (compactMode) "♥ ${character.affinity}" else "♥ ${character.affinity} · $tierLabel",
                            color = if (character.affinity < 0) Color(0xFFFCA5A5) else Color(0xFFFFB4CE),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageCastMiniStrip(
    allNpcs: List<RpCharacter>,
    focusedNpcId: String?,
    stageAccent: Color,
    onSelectNpc: (RpCharacter) -> Unit,
) {
    Row(
        modifier = Modifier
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
                    1.dp,
                    when {
                        selected -> stageAccent.copy(alpha = 0.85f)
                        npc.inScene -> Color(0x55CBD5E1)
                        else -> Color(0x3394A3B8)
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
                        borderColor = if (selected) stageAccent else Color(0x66CBD5E1),
                    )
                    Text(
                        text = if (selected) "${npc.name} ♥${npc.affinity}" else npc.name,
                        color = if (selected) Color(0xFFFFF4DC) else Color(0xFFD8E1F2),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
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
private fun TheaterStageBackdrop(
    rp: RpConfig,
    backdropDim: Float,
    dimForReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val sceneFile = remember(rp.state.sceneImagePath) {
        rp.state.sceneImagePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val effectiveDim = backdropDim.coerceIn(0f, 0.78f)

    Box(modifier = modifier) {
        if (sceneFile != null) {
            Image(
                painter = rememberAsyncImagePainter(sceneFile),
                contentDescription = rp.state.scene,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Live-editable darkness scrim + gentle bottom vignette for readability
            if (dimForReading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveDim)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x55070A13),
                                    Color.Transparent,
                                    Color(0x66070A13),
                                    Color(0xB8070A13),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x33070A13),
                                    Color.Transparent,
                                    Color(0x22070A13),
                                ),
                            ),
                        ),
                )
            }
        } else {
            val (topCol, midCol, botCol, glowCol) = remember(rp.playStyle, rp.state.weather, rp.state.mood, rp.state.time) {
                resolveStageAtmosphere(rp)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(topCol, midCol, botCol)))
                    .drawBehind {
                        val glowRadius = size.maxDimension * 0.68f
                        if (glowRadius > 0f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowCol.copy(alpha = 0.30f), Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.24f),
                                    radius = glowRadius,
                                ),
                            )
                        }
                    },
            )
            if (dimForReading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveDim)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x2605070E),
                                    Color.Transparent,
                                    Color(0x7305070E),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

private data class StageAtmosphere(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glow: Color,
)

private fun resolveStageAtmosphere(rp: RpConfig): StageAtmosphere {
    val cue = "${rp.state.weather} ${rp.state.mood} ${rp.state.time} ${rp.state.scene}".lowercase()
    return when {
        cue.contains("雨") || cue.contains("rain") || cue.contains("雷") || cue.contains("storm") ->
            StageAtmosphere(Color(0xFF141E30), Color(0xFF1E3247), Color(0xFF0C121E), Color(0xFF60A5FA))
        cue.contains("雪") || cue.contains("snow") || cue.contains("霜") || cue.contains("冰") ->
            StageAtmosphere(Color(0xFF1C2942), Color(0xFF283D5B), Color(0xFF101829), Color(0xFFBAE6FD))
        cue.contains("夕阳") || cue.contains("黄昏") || cue.contains("落日") || cue.contains("dusk") || cue.contains("sunset") ->
            StageAtmosphere(Color(0xFF3A1D38), Color(0xFF4F2836), Color(0xFF18111E), Color(0xFFF59E0B))
        cue.contains("诡") || cue.contains("恐") || cue.contains("阴") || cue.contains("血") || cue.contains("horror") ->
            StageAtmosphere(Color(0xFF1E0F18), Color(0xFF2B1421), Color(0xFF0D080E), Color(0xFFEF4444))
        rp.playStyle == RpPlayStyle.chengguang ->
            StageAtmosphere(Color(0xFF2A1633), Color(0xFF391D3D), Color(0xFF120E1D), Color(0xFFF4A950))
        rp.playStyle == RpPlayStyle.tavern ->
            StageAtmosphere(Color(0xFF2B1D16), Color(0xFF38241A), Color(0xFF140F0E), Color(0xFFF5B74B))
        else ->
            StageAtmosphere(Color(0xFF111C2D), Color(0xFF1A2A3E), Color(0xFF0B111C), Color(0xFF4ADE80))
    }
}

private val ACT_PREFIX_RE = Regex("^(第.{1,3}幕|Act\\s+\\d+)", RegexOption.IGNORE_CASE)

private fun formatRpActHeader(actNumber: Int, chapterTitle: String, isZh: Boolean): String {
    val cleanTitle = chapterTitle.trim()
    if (cleanTitle.isNotEmpty() && ACT_PREFIX_RE.containsMatchIn(cleanTitle)) {
        return cleanTitle
    }
    val prefix = if (isZh) "第${actNumber}幕" else "Act $actNumber"
    return if (cleanTitle.isNotEmpty()) "$prefix · $cleanTitle" else prefix
}

private fun formatRpRoleLabel(role: String, isZh: Boolean): String {
    val trimmed = role.trim()
    if (trimmed.isEmpty()) return ""
    return when (trimmed.lowercase()) {
        "romance" -> if (isZh) "攻略" else "Romance"
        "rival" -> if (isZh) "对手" else "Rival"
        "ally" -> if (isZh) "盟友" else "Ally"
        "mentor" -> if (isZh) "导师" else "Mentor"
        "companion" -> if (isZh) "同伴" else "Companion"
        "antagonist" -> if (isZh) "反派" else "Antagonist"
        "npc" -> "NPC"
        else -> trimmed
    }
}

@Composable
private fun TheaterTopStageBar(
    rp: RpConfig,
    isZh: Boolean,
    stageAccent: Color,
    hideSecondaryEnvRow: Boolean,
    onExitTheater: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFrontendEditor: () -> Unit,
    onHideUi: () -> Unit,
    onOpenStudio: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val actLabel = formatRpActHeader(rp.state.actNumber, rp.state.chapterTitle, isZh)
    val envParts = listOf(rp.state.scene, rp.state.time, rp.state.weather)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val envText = envParts.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Left: Chapter / Act pill gets full remaining width before ellipsizing
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xB8181D2E),
                    border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.62f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = stageAccent,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = actLabel,
                            color = Color(0xFFFFF4DC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Right: Stream / History / Frontend Appearance / Hide UI / Studio pills
            StageGlassPill(
                label = strings.rp.chatModeBtn,
                icon = Icons.Default.Forum,
                accentColor = stageAccent,
                onClick = onExitTheater,
            )
            StageGlassIconPill(
                icon = Icons.Default.History,
                contentDescription = strings.rp.theaterLogBtn,
                accentColor = stageAccent,
                onClick = onOpenHistory,
            )
            StageGlassIconPill(
                icon = Icons.Default.Palette,
                contentDescription = strings.rp.frontendTab,
                accent = true,
                accentColor = stageAccent,
                onClick = onOpenFrontendEditor,
            )
            StageGlassIconPill(
                icon = Icons.Default.VisibilityOff,
                contentDescription = strings.rp.theaterHideUi,
                accentColor = stageAccent,
                onClick = onHideUi,
            )
            StageGlassIconPill(
                icon = Icons.Default.Tune,
                contentDescription = strings.rp.studioBtn,
                accent = true,
                accentColor = stageAccent,
                onClick = onOpenStudio,
            )
        }

        if (!hideSecondaryEnvRow && (envText.isNotBlank() || rp.state.gold > 0 || rp.state.hp < rp.state.maxHp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (envText.isNotBlank()) {
                    Surface(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x990D121F),
                        border = BorderStroke(1.dp, Color(0x44CBD5E1)),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = "📍 $envText",
                            color = Color(0xFFDDE5F5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                        )
                    }
                }
                if (rp.playStyle == RpPlayStyle.trpg || rp.state.gold > 0 || rp.state.hp < rp.state.maxHp) {
                    Surface(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x990D121F),
                        border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.44f)),
                    ) {
                        Text(
                            text = "♥${rp.state.hp}/${rp.state.maxHp} · 🪙${rp.state.gold}",
                            color = Color(0xFFFDE68A),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageGlassPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Boolean = false,
    accentColor: Color = Color(0xFFF5C563),
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (accent) Color(0xCC2C2113) else Color(0xAA0E1320),
        border = BorderStroke(1.dp, if (accent) accentColor.copy(alpha = 0.72f) else Color(0x55CBD5E1)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) accentColor else Color(0xFFE2E8F0),
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                color = if (accent) Color(0xFFFFF4DC) else Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StageGlassIconPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    accentColor: Color = Color(0xFFF5C563),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (accent) Color(0xCC2C2113) else Color(0xAA0E1320),
        border = BorderStroke(1.dp, if (accent) accentColor.copy(alpha = 0.72f) else Color(0x55CBD5E1)),
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (accent) accentColor else Color(0xFFE2E8F0),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TheaterCastPortraitShowcase(
    focusedNpc: RpCharacter,
    allNpcs: List<RpCharacter>,
    stageAccent: Color,
    onSelectNpc: (RpCharacter) -> Unit,
    onOpenStudio: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val exprKey = remember(focusedNpc.mood) { RpEngine.resolveExpressionKey(focusedNpc.mood) }
    val tier = remember(focusedNpc.affinity) { RpEngine.affinityTier(focusedNpc.affinity) }
    val tierLabel = strings.rp.affinityTiers[tier.code]
        ?: if (isZh) tier.zhLabel else tier.enLabel

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            onClick = onOpenStudio,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xB8111726),
            border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RpNpcPortraitAvatar(
                    character = focusedNpc,
                    fallbackName = focusedNpc.name,
                    size = 74.dp,
                    cornerRadius = 14.dp,
                    borderColor = stageAccent.copy(alpha = 0.75f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = focusedNpc.name,
                            color = Color(0xFFFFF8EB),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (focusedNpc.mood.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0x66273452),
                                border = BorderStroke(1.dp, Color(0x55A7B9DC)),
                            ) {
                                Text(
                                    text = "${expressionEmoji(exprKey)} ${focusedNpc.mood}",
                                    color = Color(0xFFDCE6FA),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    val roleLabel = formatRpRoleLabel(focusedNpc.role, isZh)
                    val subtitle = listOf(roleLabel, focusedNpc.status.trim())
                        .filter { it.isNotEmpty() }
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color(0xFFB5C0D6),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Affinity Heart Progress Bar & Tier
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "♥ ${focusedNpc.affinity}",
                                color = if (focusedNpc.affinity < 0) Color(0xFFFCA5A5) else Color(0xFFFF9EBB),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = tierLabel,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x55FFFFFF)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(tier.progress.coerceIn(0.04f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            if (focusedNpc.affinity < 0) {
                                                listOf(Color(0xFFEF4444), Color(0xFFF97316))
                                            } else {
                                                listOf(Color(0xFFF472B6), stageAccent)
                                            },
                                        ),
                                    ),
                            )
                        }
                    }
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
private fun StageVariantSwipePill(
    index: Int,
    total: Int,
    accentColor: Color = Color(0xFFFDE68A),
    onSwitch: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
        color = Color(0xCC121726),
        border = BorderStroke(1.dp, Color(0x66CBD5E1)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            IconButton(
                onClick = { onSwitch((index - 1).coerceAtLeast(0)) },
                enabled = index > 0,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = if (index > 0) Color(0xFFF3F6FC) else Color(0x55CBD5E1),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "${index + 1}/$total",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            IconButton(
                onClick = { onSwitch((index + 1).coerceAtMost(total - 1)) },
                enabled = index < total - 1,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (index < total - 1) Color(0xFFF3F6FC) else Color(0x55CBD5E1),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun StageInlineActionChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false,
    onDarkSurface: Boolean = true,
    accentColor: Color = Color(0xFFF5C563),
) {
    val bg = when {
        !onDarkSurface && highlight && enabled -> Color(0x339C723C)
        !onDarkSurface -> Color(0x1F5C4326)
        highlight && enabled -> accentColor.copy(alpha = 0.22f)
        else -> Color(0x66222B42)
    }
    val fg = when {
        !enabled -> if (onDarkSurface) Color(0x6694A3B8) else Color(0x666B5946)
        !onDarkSurface && highlight -> Color(0xFF5A3714)
        !onDarkSurface -> Color(0xFF3E2E1E)
        highlight -> Color(0xFFFFF0C4)
        else -> Color(0xFFDCE4F5)
    }
    val border = when {
        !enabled -> if (onDarkSurface) Color(0x3364748B) else Color(0x338C7558)
        !onDarkSurface && highlight -> Color(0x889C723C)
        !onDarkSurface -> Color(0x448C7558)
        highlight -> accentColor.copy(alpha = 0.65f)
        else -> Color(0x44CBD5E1)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheaterOpeningCurtain(
    rp: RpConfig,
    hasModelSelected: Boolean,
    isZh: Boolean,
    stageAccent: Color,
    onOpenModelSheet: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val styleLabel = when (rp.playStyle) {
        RpPlayStyle.chengguang -> strings.rp.styleChengguang
        RpPlayStyle.tavern -> strings.rp.styleTavern
        RpPlayStyle.trpg -> strings.rp.styleTrpg
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasModelSelected) {
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xCC121726),
                border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.65f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = stageAccent,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xD9101522),
            border = BorderStroke(1.2.dp, stageAccent.copy(alpha = 0.68f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceMetaChip(text = "✦ $styleLabel", variant = GuBadgeVariant.HONEY, onDarkStage = true)
                    ChoiceMetaChip(
                        text = if (isZh) "第${rp.state.actNumber}幕" else "Act ${rp.state.actNumber}",
                        variant = GuBadgeVariant.BLUE,
                        onDarkStage = true,
                    )
                }

                Text(
                    text = rp.title,
                    color = Color(0xFFFFF8EA),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                if (rp.worldview.isNotBlank()) {
                    Text(
                        text = rp.worldview,
                        color = Color(0xFFCED6E8),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val npcs = rp.cast.filter { !it.isPlayer }
                if (npcs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        npcs.take(4).forEach { npc ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x991C2438))
                                    .border(1.dp, stageAccent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RpNpcPortraitAvatar(character = npc, fallbackName = npc.name, size = 24.dp)
                                Text(
                                    text = "${npc.name} ♥${npc.affinity}",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                GuButton(
                    onClick = {
                        onStarterPrompt(
                            if (isZh) "拉开第一幕帷幕，请以极具画面感的笔触描写初始场景、登场人物与互动选项。"
                            else "Raise the curtain on Act 1: vividly set the opening scene, introduce the characters, and offer my first choices.",
                        )
                    },
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.wizardStartBtn, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TheaterHistoryLogSheet(
    rp: RpConfig,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
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
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = "${rp.title} · ${strings.rp.theaterLogBtn}",
                subtitle = strings.chat.messageCount.fmt("count" to messages.size),
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    val (speaker, npc) = if (isUser) {
                        rp.playerName.ifBlank { "Player" } to null
                    } else {
                        resolveRpSpeaker(msg, rp, strings.rp.badge)
                    }
                    val cleanText = if (isUser) {
                        msg.content
                    } else {
                        extractMessageThinkAndBody(msg.reasoning, msg.content, ConversationMode.rp).second
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isUser) c.primarySoft.copy(alpha = 0.65f) else c.surface,
                        border = BorderStroke(1.dp, if (isUser) c.primary.copy(alpha = 0.3f) else c.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                if (!isUser) {
                                    RpNpcPortraitAvatar(character = npc, fallbackName = speaker, size = 24.dp)
                                }
                                Text(
                                    text = speaker,
                                    color = if (isUser) c.primarySoftInk else c.ink,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isUser && msg.rpInputKind != null) {
                                    val kindLabel = when (msg.rpInputKind) {
                                        RpInputKind.action -> strings.rp.inputAction
                                        RpInputKind.speech -> strings.rp.inputSay
                                        RpInputKind.thought -> strings.rp.inputThought
                                        RpInputKind.gm_command -> strings.rp.inputOoc
                                    }
                                    GuBadge(kindLabel, variant = GuBadgeVariant.BLUE)
                                }
                                if (msg.rpDiceRoll != null) {
                                    GuBadge(
                                        "🎲 ${msg.rpDiceRoll.expression}=${msg.rpDiceRoll.total}",
                                        variant = GuBadgeVariant.HONEY,
                                    )
                                }
                            }
                            if (cleanText.isNotBlank()) {
                                if (isUser) {
                                    Text(
                                        text = cleanText,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                    )
                                } else {
                                    RpRichMessageContent(
                                        rawMarkdown = cleanText,
                                        rp = rp,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                        useSerif = rp.visualTheme.useSerif,
                                        onCopyToast = {},
                                    )
                                }
                            }
                            if (!isUser) {
                                val deltas = msg.activeVariant()?.rpDeltas.orEmpty()
                                if (rp.visualTheme.showTurnDeltas && deltas.isNotEmpty()) {
                                    RpTurnDeltaStrip(deltas = deltas)
                                }
                                msg.activeVariant()?.rpEndingUnlocked?.let { ending ->
                                    RpEndingCard(ending = ending)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
