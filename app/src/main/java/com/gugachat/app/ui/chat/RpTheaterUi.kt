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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalDragHandle
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

// ==================== Shared RP Visual Components ====================

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
    val effectiveBorder = borderColor ?: c.accent.copy(alpha = 0.45f)
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
                .border(0.8.dp, effectiveBorder, shape),
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
                .border(0.8.dp, effectiveBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = Color(0xFFFFF5E1),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
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
    onDarkStage: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val stageHighlight = accentColor ?: Color(0xFFE8B95B)
    var forceConfirmTarget by remember { mutableStateOf<Pair<RpChoice, String>?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (onDarkStage) 5.dp else 6.dp),
    ) {
        // Inline non-modal lock bypass confirm bar (no AlertDialog)
        forceConfirmTarget?.let { (choice, reason) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (onDarkStage) Color(0xE826161C) else c.dangerBg,
                border = BorderStroke(0.6.dp, if (onDarkStage) Color(0x99F87171) else c.danger.copy(alpha = 0.45f)),
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
                        color = if (onDarkStage) Color(0xFFFECACA) else c.danger,
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
                            color = if (onDarkStage) Color(0xFFCBD5E1) else c.muted,
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
                        color = if (onDarkStage) Color(0xFFDC2626) else c.danger,
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
                onDarkStage = onDarkStage,
                fallback = if (onDarkStage) stageHighlight else c.primary,
            )
            val cardBgBrush = when {
                onDarkStage && !eval.unlocked -> Brush.horizontalGradient(
                    listOf(Color(0xBD151926), Color(0xAD111420)),
                )
                onDarkStage -> Brush.horizontalGradient(
                    listOf(Color(0xE0192034), Color(0xD4121828)),
                )
                !eval.unlocked -> Brush.horizontalGradient(
                    listOf(c.surfaceSoft.copy(alpha = 0.72f), c.surfaceSoft.copy(alpha = 0.55f)),
                )
                else -> Brush.horizontalGradient(
                    listOf(c.surface, c.surfaceSoft.copy(alpha = 0.65f)),
                )
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
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = when {
                        !eval.unlocked -> if (onDarkStage) Color(0x4094A3B8) else c.outline.copy(alpha = 0.45f)
                        onDarkStage -> toneAccent.copy(alpha = 0.42f)
                        else -> toneAccent.copy(alpha = 0.28f)
                    },
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
                        .padding(
                            start = if (onDarkStage) 10.dp else 12.dp,
                            end = if (onDarkStage) 10.dp else 11.dp,
                            top = if (onDarkStage) 6.dp else 7.5.dp,
                            bottom = if (onDarkStage) 6.dp else 7.5.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (onDarkStage) 8.dp else 9.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (onDarkStage) 20.dp else 22.dp)
                            .clip(CircleShape)
                            .background(
                                if (!eval.unlocked) {
                                    if (onDarkStage) Color(0xFF363F56) else c.outline
                                } else {
                                    toneAccent
                                },
                            ),
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
                                fontSize = if (onDarkStage) 13.sp else 13.5.sp,
                                lineHeight = 17.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (onDarkStage) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (hasSkill) {
                                val dcSuffix = choice.difficulty?.let { " DC$it" }.orEmpty()
                                ChoiceMetaChip(
                                    text = "🎲${choice.skillCheck}$dcSuffix",
                                    variant = GuBadgeVariant.HONEY,
                                    onDarkStage = onDarkStage,
                                )
                            } else if (toneMeta != null) {
                                ChoiceMetaChip(
                                    text = toneMeta.first,
                                    variant = toneMeta.second,
                                    onDarkStage = onDarkStage,
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
            val ghostBg = if (onDarkStage) Color(0x99101522) else Color.Transparent
            val ghostBorder = if (onDarkStage) Color(0x40B8C7E0) else c.border
            val ghostFg = if (onDarkStage) Color(0xFFDCE4F5) else c.muted

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
                            color = if (onDarkStage) stageHighlight else c.primary,
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

private fun resolveChoiceToneAccent(
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

internal data class DialogSkinSpec(
    val boxBg: Color,
    val boxBorder: BorderStroke?,
    val boxShape: RoundedCornerShape,
    val topStripColor: Color?,
    val topStripHeight: Dp,
    val innerTopHighlight: Color?,
    val nameplateShape: RoundedCornerShape,
    val nameplateBg: Color,
    val nameplateBorder: Color,
    val nameplateText: Color,
    val nameplateMoodText: Color,
    val nameplateDotColor: Color,
    val nameplateDecorPrefix: String,
    val nameplateDecorSuffix: String,
    val nameplateFontFamily: FontFamily,
    val bodyText: Color,
    val mutedText: Color,
    val dividerColor: Color,
    val markdownPalette: MarkdownPalette,
    val isDarkSurface: Boolean,
)

internal fun resolveDialogSkin(
    theme: RpVisualTheme,
    stageAccent: Color,
    customAccent: Color?,
    appIsDark: Boolean,
): DialogSkinSpec {
    val opacity = theme.dialogOpacity.coerceIn(0.45f, 1.0f)
    val style = theme.dialogStyle.trim().lowercase()
    val hasTopLeftTab = theme.showNameplate
    val defaultTabShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

    fun darkMarkdownPalette(accent: Color, text: Color = Color(0xFFF2F5FC)): MarkdownPalette = MarkdownPalette(
        text = text,
        muted = Color(0xFF96A3BD),
        accent = accent,
        border = Color(0x44CBD5E1),
        quoteBg = Color(0x551F2940),
        inlineCodeBg = Color(0x6627334E),
        inlineCodeInk = Color(0xFFFDE68A),
        codeBg = Color(0xCC0A0D14),
        codeHeaderBg = Color(0xCC131824),
        codeInk = Color(0xFFE6EDF3),
        codeMeta = Color(0xFFA6B4CA),
        tableHeaderBg = Color(0x661C263B),
    )

    return when (style) {
        "classic_vn" -> {
            val gold = customAccent ?: Color(0xFFE0B954)
            DialogSkinSpec(
                boxBg = Color(0xFF18101C).copy(alpha = opacity),
                boxBorder = BorderStroke(1.dp, gold.copy(alpha = 0.80f)),
                boxShape = RoundedCornerShape(
                    topStart = if (hasTopLeftTab) 4.dp else 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                topStripColor = gold.copy(alpha = 0.72f),
                topStripHeight = 2.dp,
                innerTopHighlight = gold.copy(alpha = 0.16f),
                nameplateShape = RoundedCornerShape(topStart = 10.dp, topEnd = 14.dp, bottomStart = 2.dp, bottomEnd = 6.dp),
                nameplateBg = Color(0xFF2B1724).copy(alpha = (opacity + 0.10f).coerceAtMost(0.98f)),
                nameplateBorder = gold.copy(alpha = 0.85f),
                nameplateText = Color(0xFFFFF5DC),
                nameplateMoodText = Color(0xFFE5CCA0),
                nameplateDotColor = gold,
                nameplateDecorPrefix = "❖ ",
                nameplateDecorSuffix = "",
                nameplateFontFamily = FontFamily.Serif,
                bodyText = Color(0xFFFBF5E8),
                mutedText = Color(0xFFD3C5AF),
                dividerColor = gold.copy(alpha = 0.25f),
                markdownPalette = darkMarkdownPalette(accent = gold, text = Color(0xFFFBF5E8)),
                isDarkSurface = true,
            )
        }

        "minimal" -> {
            val minimalAccent = customAccent ?: Color(0xFFE2E8F0)
            DialogSkinSpec(
                boxBg = Color(0xFF090D16).copy(alpha = (opacity * 0.84f).coerceIn(0.38f, 0.90f)),
                boxBorder = null,
                boxShape = RoundedCornerShape(18.dp),
                topStripColor = Color.White.copy(alpha = 0.18f),
                topStripHeight = 1.dp,
                innerTopHighlight = null,
                nameplateShape = RoundedCornerShape(999.dp),
                nameplateBg = Color(0xFF101726).copy(alpha = (opacity * 0.95f).coerceIn(0.55f, 0.96f)),
                nameplateBorder = Color(0x36FFFFFF),
                nameplateText = Color(0xFFF8FAFC),
                nameplateMoodText = Color(0xFFCBD5E1),
                nameplateDotColor = minimalAccent,
                nameplateDecorPrefix = "",
                nameplateDecorSuffix = "",
                nameplateFontFamily = FontFamily.Default,
                bodyText = Color(0xFFF4F7FD),
                mutedText = Color(0xFFB5C2D9),
                dividerColor = Color(0x20FFFFFF),
                markdownPalette = darkMarkdownPalette(accent = stageAccent),
                isDarkSurface = true,
            )
        }

        "cyber" -> {
            val neon = customAccent ?: Color(0xFF22D3EE)
            DialogSkinSpec(
                boxBg = Color(0xFF07101B).copy(alpha = opacity),
                boxBorder = BorderStroke(1.dp, neon.copy(alpha = 0.80f)),
                boxShape = RoundedCornerShape(
                    topStart = 2.dp,
                    topEnd = 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 2.dp,
                ),
                topStripColor = neon,
                topStripHeight = 2.dp,
                innerTopHighlight = neon.copy(alpha = 0.16f),
                nameplateShape = RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 0.dp, bottomEnd = 2.dp),
                nameplateBg = Color(0xFF0A1D2E).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                nameplateBorder = neon.copy(alpha = 0.88f),
                nameplateText = Color(0xFFE4FBFF),
                nameplateMoodText = Color(0xFF7DD3FC),
                nameplateDotColor = neon,
                nameplateDecorPrefix = "▸ ",
                nameplateDecorSuffix = "",
                nameplateFontFamily = FontFamily.Monospace,
                bodyText = Color(0xFFE5F6FC),
                mutedText = Color(0xFF99C8DC),
                dividerColor = neon.copy(alpha = 0.28f),
                markdownPalette = darkMarkdownPalette(accent = neon, text = Color(0xFFE5F6FC)),
                isDarkSurface = true,
            )
        }

        "parchment" -> {
            if (appIsDark) {
                val sepiaAccent = customAccent ?: Color(0xFFCFA76E)
                DialogSkinSpec(
                    boxBg = Color(0xFF241B14).copy(alpha = opacity),
                    boxBorder = BorderStroke(0.8.dp, sepiaAccent.copy(alpha = 0.60f)),
                    boxShape = RoundedCornerShape(
                        topStart = if (hasTopLeftTab) 4.dp else 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                    topStripColor = sepiaAccent.copy(alpha = 0.42f),
                    topStripHeight = 1.5.dp,
                    innerTopHighlight = sepiaAccent.copy(alpha = 0.12f),
                    nameplateShape = defaultTabShape,
                    nameplateBg = Color(0xFF33261B).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                    nameplateBorder = sepiaAccent.copy(alpha = 0.72f),
                    nameplateText = Color(0xFFFCEBC9),
                    nameplateMoodText = Color(0xFFD6BE9A),
                    nameplateDotColor = sepiaAccent,
                    nameplateDecorPrefix = "❦ ",
                    nameplateDecorSuffix = "",
                    nameplateFontFamily = FontFamily.Serif,
                    bodyText = Color(0xFFF6ECDA),
                    mutedText = Color(0xFFCBBBA0),
                    dividerColor = sepiaAccent.copy(alpha = 0.24f),
                    markdownPalette = darkMarkdownPalette(accent = sepiaAccent, text = Color(0xFFF6ECDA)),
                    isDarkSurface = true,
                )
            } else {
                val sepiaAccent = customAccent ?: Color(0xFF9C723C)
                val paperInk = Color(0xFF2B2016)
                val paperMuted = Color(0xFF6B5946)
                DialogSkinSpec(
                    boxBg = Color(0xFFF7F0E2).copy(alpha = opacity),
                    boxBorder = BorderStroke(0.8.dp, sepiaAccent.copy(alpha = 0.56f)),
                    boxShape = RoundedCornerShape(
                        topStart = if (hasTopLeftTab) 4.dp else 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                    topStripColor = sepiaAccent.copy(alpha = 0.38f),
                    topStripHeight = 1.5.dp,
                    innerTopHighlight = Color.White.copy(alpha = 0.35f),
                    nameplateShape = defaultTabShape,
                    nameplateBg = Color(0xFFEDE0C8).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                    nameplateBorder = sepiaAccent.copy(alpha = 0.68f),
                    nameplateText = Color(0xFF3A2613),
                    nameplateMoodText = Color(0xFF7B6347),
                    nameplateDotColor = sepiaAccent,
                    nameplateDecorPrefix = "❦ ",
                    nameplateDecorSuffix = "",
                    nameplateFontFamily = FontFamily.Serif,
                    bodyText = paperInk,
                    mutedText = paperMuted,
                    dividerColor = sepiaAccent.copy(alpha = 0.24f),
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
                boxBg = Color(0xFF0E1320).copy(alpha = opacity),
                boxBorder = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.45f)),
                boxShape = RoundedCornerShape(
                    topStart = if (hasTopLeftTab) 4.dp else 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                topStripColor = null,
                topStripHeight = 1.dp,
                innerTopHighlight = Color.White.copy(alpha = 0.14f),
                nameplateShape = defaultTabShape,
                nameplateBg = Color(0xFF1A1D2F).copy(alpha = (opacity + 0.08f).coerceAtMost(0.98f)),
                nameplateBorder = stageAccent.copy(alpha = 0.62f),
                nameplateText = Color(0xFFFFF4DA),
                nameplateMoodText = Color(0xFFD6C29B),
                nameplateDotColor = stageAccent,
                nameplateDecorPrefix = "",
                nameplateDecorSuffix = "",
                nameplateFontFamily = FontFamily.Default,
                bodyText = Color(0xFFF2F5FC),
                mutedText = Color(0xFF96A3BD),
                dividerColor = Color(0x26CBD5E1),
                markdownPalette = darkMarkdownPalette(accent = stageAccent),
                isDarkSurface = true,
            )
        }
    }
}

// ==================== True Z-Axis Chengguang Interactive Visual Novel Stage View ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpTheaterStageView(
    rp: RpConfig,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    streamingStatusHint: String? = null,
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
    onFrontendFillInput: (String) -> Unit = onFrontendAction,
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    onToast: (String) -> Unit,
    drawOwnBackdrop: Boolean = true,
    showInternalTopBar: Boolean = true,
    externalHideUi: Boolean? = null,
    onExternalHideUiChange: ((Boolean) -> Unit)? = null,
    externalShowHistory: Boolean? = null,
    onExternalShowHistoryChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isComposerExpanded: Boolean = false,
    onToggleComposer: (() -> Unit)? = null,
    onOpenQuickControl: (() -> Unit)? = null,
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

    var internalHideUi by remember { mutableStateOf(false) }
    val hideUi = externalHideUi ?: internalHideUi
    val setHideUi: (Boolean) -> Unit = { next ->
        internalHideUi = next
        onExternalHideUiChange?.invoke(next)
    }

    var internalShowHistory by remember { mutableStateOf(false) }
    val showHistorySheet = externalShowHistory ?: internalShowHistory
    val setShowHistorySheet: (Boolean) -> Unit = { next ->
        internalShowHistory = next
        onExternalShowHistoryChange?.invoke(next)
    }

    var isEditingReply by remember(latestAssistant?.id) { mutableStateOf(false) }
    var editBuffer by remember(latestAssistant?.id) { mutableStateOf("") }
    var choicesPeekHidden by remember(latestAssistant?.id) { mutableStateOf(false) }

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
    val activeHighlightId = focusedNpcId
        ?: resolvedSpeakerNpc?.id
        ?: stageCharacters.firstOrNull()?.second?.id
    val activeSpeakerSlot = remember(stageCharacters, activeHighlightId) {
        stageCharacters.firstOrNull { it.second.id == activeHighlightId }?.first
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

    if (showHistorySheet) {
        TheaterHistoryLogSheet(
            rp = rp,
            messages = activeMessages,
            onDismiss = { setShowHistorySheet(false) },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = hideUi && showInternalTopBar) { setHideUi(false) },
    ) {
        // Layer 0: Fullscreen Scene CG + Atmospheric Depth Vignette
        if (drawOwnBackdrop) {
            TheaterStageBackdrop(
                rp = rp,
                stageAccent = stageAccent,
                activeSlot = activeSpeakerSlot,
                backdropDim = theme.backdropDim,
                dimForReading = !hideUi,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Pure CG Appreciation Show-UI Floating Pill (when standalone)
        if (showInternalTopBar) {
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
                    onClick = { setHideUi(false) },
                )
            }
        }

        if (!hideUi) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val stageMaxH = maxHeight
                    val dialogueHeight = (stageMaxH * 0.32f).coerceIn(194.dp, 268.dp)

                    if (activeMessages.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (showInternalTopBar) {
                                TheaterTopStageBar(
                                    rp = rp,
                                    isZh = isZh,
                                    stageAccent = stageAccent,
                                    hideSecondaryEnvRow = true,
                                    onExitTheater = onExitTheater,
                                    onOpenHistory = { setShowHistorySheet(true) },
                                    onOpenFrontendEditor = onOpenFrontendEditor,
                                    onHideUi = { setHideUi(true) },
                                    onOpenStudio = onOpenStudio,
                                )
                            }
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
                        }
                    } else {
                        val spriteLayout = theme.spriteLayout.trim().lowercase()
                        val bodyFontSizeSp = theme.fontSizeSp.coerceIn(12f, 22f)
                        val stageChoices = remember(latestAssistant?.rpChoices, rp.pendingChoices) {
                            latestAssistant?.rpChoices?.takeIf { it.isNotEmpty() } ?: rp.pendingChoices
                        }
                        val hasChoicesOverlay = !isStreaming &&
                            !isEditingReply &&
                            !isComposerExpanded &&
                            (stageChoices.isNotEmpty() || isRefreshingChoices || (rp.enableChoices && latestAssistant != null))

                        // Layer 1: Z-Axis Half-Body Sprite Stage (Zero 178dp rounded-box confinement!)
                        if (spriteLayout != "hidden") {
                            when (spriteLayout) {
                                "portrait_card" -> {
                                    if (displayedNpc != null) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .fillMaxWidth()
                                                .padding(
                                                    top = if (showInternalTopBar) 54.dp else 12.dp,
                                                    start = 14.dp,
                                                    end = 14.dp,
                                                ),
                                        ) {
                                            TheaterCastPortraitShowcase(
                                                focusedNpc = displayedNpc,
                                                allNpcs = nonPlayerCast,
                                                stageAccent = stageAccent,
                                                onSelectNpc = { npc -> focusedNpcId = npc.id },
                                                onOpenStudio = onOpenStudio,
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    val spritesWithFiles = remember(stageCharacters) {
                                        stageCharacters.filter { (_, ch) ->
                                            val path = RpEngine.resolveCharacterSpritePath(ch)
                                            !path.isNullOrBlank() && File(path).isFile
                                        }
                                    }
                                    if (spritesWithFiles.isNotEmpty()) {
                                        // Full-height proportion (~0.62f by default, feather-faded behind Layer 2!)
                                        val spriteHeightFraction = (theme.spriteHeightRatio + 0.18f).coerceIn(0.52f, 0.72f)
                                        val stageActors = remember(spritesWithFiles) {
                                            if (spritesWithFiles.size <= 2) spritesWithFiles
                                            else {
                                                // Prioritize active speaker + 1 companion for clean 1~2 half-body composition
                                                val speaker = spritesWithFiles.firstOrNull { it.second.id == activeHighlightId }
                                                val other = spritesWithFiles.firstOrNull { it.second.id != activeHighlightId }
                                                listOfNotNull(speaker, other).distinctBy { it.second.id }
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(bottom = dialogueHeight * 0.42f)
                                                .fillMaxHeight(spriteHeightFraction),
                                        ) {
                                            stageActors.forEachIndexed { idx, (slot, char) ->
                                                val isActive = char.id == activeHighlightId
                                                val actorAlign = when {
                                                    stageActors.size == 1 -> Alignment.BottomCenter
                                                    slot == "left" || idx == 0 -> Alignment.BottomStart
                                                    else -> Alignment.BottomEnd
                                                }
                                                val widthFraction = if (stageActors.size == 1) 0.78f else 0.58f
                                                val zOrder = if (isActive) 2f else 1f
                                                val targetScale = (if (isActive) 1.03f else 0.94f) * char.spriteScale.coerceIn(0.7f, 1.4f)
                                                val animatedScale by animateFloatAsState(
                                                    targetValue = targetScale,
                                                    animationSpec = tween(durationMillis = 260),
                                                    label = "zSpriteScale_${char.id}",
                                                )
                                                val animatedAlpha by animateFloatAsState(
                                                    targetValue = if (isActive) 1.0f else 0.72f,
                                                    animationSpec = tween(durationMillis = 260),
                                                    label = "zSpriteAlpha_${char.id}",
                                                )

                                                FeatherHalfBodySprite(
                                                    character = char,
                                                    onTap = { focusedNpcId = char.id },
                                                    modifier = Modifier
                                                        .align(actorAlign)
                                                        .zIndex(zOrder)
                                                        .fillMaxWidth(widthFraction)
                                                        .fillMaxHeight()
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
                                    } else {
                                        // Refined character nameplate badge only when no sprite image is bound
                                        val activeChar = stageCharacters.firstOrNull { it.second.id == activeHighlightId }?.second
                                            ?: displayedNpc
                                        if (activeChar != null) {
                                            TheaterCastBadgeCluster(
                                                focusedNpc = activeChar,
                                                allNpcs = nonPlayerCast,
                                                stageAccent = stageAccent,
                                                isZh = isZh,
                                                onSelectNpc = { focusedNpcId = it.id },
                                                onOpenStudio = onOpenStudio,
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .padding(
                                                        top = if (showInternalTopBar) 56.dp else 12.dp,
                                                        start = 14.dp,
                                                        end = 14.dp,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top-bar (when standalone) + Floating Cast Switcher + Turn Deltas + Endings
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (showInternalTopBar) {
                                TheaterTopStageBar(
                                    rp = rp,
                                    isZh = isZh,
                                    stageAccent = stageAccent,
                                    hideSecondaryEnvRow = true,
                                    onExitTheater = onExitTheater,
                                    onOpenHistory = { setShowHistorySheet(true) },
                                    onOpenFrontendEditor = onOpenFrontendEditor,
                                    onHideUi = { setHideUi(true) },
                                    onOpenStudio = onOpenStudio,
                                )
                            }

                            if (isGeneratingArt) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color(0xD9101522),
                                        border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.6f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 1.6.dp,
                                                color = stageAccent,
                                            )
                                            Text(
                                                text = "✨ ${artStatusLabel.ifBlank { strings.rp.aiGeneratingArt }}",
                                                color = Color(0xFFFFF4DC),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }

                            val hasSprites = stageCharacters.any { (_, ch) ->
                                val path = RpEngine.resolveCharacterSpritePath(ch)
                                !path.isNullOrBlank() && File(path).isFile
                            }
                            if (hasSprites && nonPlayerCast.size > 1 && spriteLayout == "stage") {
                                StageCastMiniStrip(
                                    allNpcs = nonPlayerCast,
                                    focusedNpcId = displayedNpc?.id,
                                    stageAccent = stageAccent,
                                    onSelectNpc = { focusedNpcId = it.id },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }

                            val deltas = latestVariant?.rpDeltas.orEmpty()
                            if (theme.showTurnDeltas && deltas.isNotEmpty() && !isStreaming) {
                                RpTurnDeltaStrip(
                                    deltas = deltas,
                                    onDarkStage = true,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                            latestVariant?.rpEndingUnlocked?.let { ending ->
                                RpEndingCard(
                                    ending = ending,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }

                        // Layer 2: Bottom Frosted Glass Dialogue Box (~30%-34% screen height)
                        TheaterDialogueStageFrame(
                            rp = rp,
                            skin = skin,
                            stageAccent = stageAccent,
                            speakerName = resolvedSpeakerName,
                            speakerNpc = resolvedSpeakerNpc,
                            latestAssistant = latestAssistant,
                            parsedReasoning = parsedReasoning,
                            cleanBody = cleanBody,
                            bodyFontSizeSp = bodyFontSizeSp,
                            isStreaming = isStreaming,
                            streamingStatusHint = streamingStatusHint,
                            fallbackUserText = activeMessages.lastOrNull { it.role == "user" }?.content,
                            dialogueScrollState = dialogueScrollState,
                            isEditingReply = isEditingReply,
                            editBuffer = editBuffer,
                            onEditBufferChange = { editBuffer = it },
                            onStartEdit = {
                                if (latestAssistant != null) {
                                    editBuffer = cleanBody.ifBlank { latestAssistant.content }
                                    isEditingReply = true
                                }
                            },
                            onCancelEdit = { isEditingReply = false },
                            onSaveEdit = {
                                if (latestAssistant != null && editBuffer.isNotBlank()) {
                                    onEditAssistantMessage(latestAssistant.id, editBuffer)
                                    isEditingReply = false
                                }
                            },
                            onSwitchVariant = { idx -> latestAssistant?.let { onSwitchVariant(it.id, idx) } },
                            onRegenerate = { latestAssistant?.let { onRegenerate(it.id) } },
                            onQuickSave = onQuickSave,
                            onContinue = onContinue,
                            onOpenHistory = { setShowHistorySheet(true) },
                            onCopyBody = {
                                if (cleanBody.isNotBlank()) {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("reply", cleanBody))
                                    onToast(strings.common.copied)
                                }
                            },
                            onToast = onToast,
                            onFrontendAction = onFrontendAction,
                            onFrontendFillInput = onFrontendFillInput,
                            onFrontendSetFlag = onFrontendSetFlag,
                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            isComposerExpanded = isComposerExpanded,
                            onToggleComposer = onToggleComposer,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(3f)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .height(dialogueHeight),
                        )

                        // Layer 3: Center-Bottom Floating Action Choice Deck (Zero 128dp nested scroll box!)
                        if (hasChoicesOverlay) {
                            TheaterFloatingChoiceDeck(
                                choices = stageChoices,
                                rp = rp,
                                isRefreshing = isRefreshingChoices,
                                peekHidden = choicesPeekHidden,
                                stageAccent = stageAccent,
                                isZh = isZh,
                                onTogglePeek = { choicesPeekHidden = !choicesPeekHidden },
                                onSelectChoice = onSelectChoice,
                                onRefreshChoices = onRefreshChoices,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .zIndex(4f)
                                    .fillMaxWidth()
                                    .padding(
                                        start = 14.dp,
                                        end = 14.dp,
                                        bottom = dialogueHeight + 18.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatherHalfBodySprite(
    character: RpCharacter,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    } ?: return

    Box(
        modifier = modifier.clickable(onClick = onTap),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = rememberAsyncImagePainter(spriteFile),
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.14f to Color.Black,
                            0.86f to Color.Black,
                            1.0f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.64f to Color.Black,
                            0.98f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

@Composable
private fun TheaterCastBadgeCluster(
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
    val roleLabel = formatRpRoleLabel(focusedNpc.role, isZh)
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
private fun TheaterDialogueStageFrame(
    rp: RpConfig,
    skin: DialogSkinSpec,
    stageAccent: Color,
    speakerName: String,
    speakerNpc: RpCharacter?,
    latestAssistant: ChatMessage?,
    parsedReasoning: String,
    cleanBody: String,
    bodyFontSizeSp: Float,
    isStreaming: Boolean,
    streamingStatusHint: String?,
    fallbackUserText: String?,
    dialogueScrollState: androidx.compose.foundation.ScrollState,
    isEditingReply: Boolean,
    editBuffer: String,
    onEditBufferChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onSwitchVariant: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onQuickSave: () -> Unit,
    onContinue: () -> Unit,
    onOpenHistory: () -> Unit,
    onCopyBody: () -> Unit,
    onToast: (String) -> Unit,
    onFrontendAction: (String) -> Unit,
    onFrontendFillInput: (String) -> Unit,
    onFrontendSetFlag: (String, String) -> Unit,
    onFrontendAdjustAffinity: (String, Int) -> Unit,
    isComposerExpanded: Boolean,
    onToggleComposer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val theme = rp.visualTheme

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Top Nameplate (left) + Minimal Top-Right Controls ([‹ 1/2 ›] [↻] [✎] [💾])
        val variantAssistant = latestAssistant?.takeIf { it.variants.size > 1 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (theme.showNameplate) {
                Surface(
                    shape = skin.nameplateShape,
                    color = skin.nameplateBg,
                    border = BorderStroke(0.6.dp, skin.nameplateBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (skin.nameplateDecorPrefix.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(skin.nameplateDotColor),
                            )
                        }
                        Text(
                            text = "${skin.nameplateDecorPrefix}$speakerName${skin.nameplateDecorSuffix}",
                            color = skin.nameplateText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = skin.nameplateFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (speakerNpc != null) {
                            val exprKey = RpEngine.resolveExpressionKey(speakerNpc.mood)
                            val moodText = speakerNpc.mood.trim().takeIf { it.isNotEmpty() }
                            val metaLabel = buildString {
                                if (moodText != null) {
                                    append(expressionEmoji(exprKey))
                                    append(' ')
                                    append(moodText)
                                    append(" · ")
                                }
                                append("♥")
                                append(speakerNpc.affinity)
                            }
                            GuMetaText(
                                text = metaLabel,
                                color = skin.nameplateMoodText,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                if (variantAssistant != null) {
                    StageVariantSwipePill(
                        index = variantAssistant.activeVariantIndex,
                        total = variantAssistant.variants.size,
                        accentColor = stageAccent,
                        onSwitch = onSwitchVariant,
                    )
                }
                StageBoxMiniIconBtn(
                    icon = Icons.Default.Refresh,
                    contentDescription = strings.rp.reroll,
                    enabled = !isStreaming && latestAssistant != null,
                    onClick = onRegenerate,
                )
                StageBoxMiniIconBtn(
                    icon = Icons.Default.Edit,
                    contentDescription = strings.common.edit,
                    enabled = !isStreaming && latestAssistant != null,
                    active = isEditingReply,
                    accentColor = stageAccent,
                    onClick = { if (isEditingReply) onCancelEdit() else onStartEdit() },
                )
                StageBoxMiniIconBtn(
                    icon = Icons.Default.Save,
                    contentDescription = strings.rp.quickSaveBtn,
                    enabled = true,
                    onClick = onQuickSave,
                )
            }
        }

        // Main Dialogue Box Surface
        Surface(
            shape = skin.boxShape,
            color = skin.boxBg,
            border = skin.boxBorder,
            shadowElevation = if (skin.boxBorder != null) 4.dp else 0.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val topAccent = skin.topStripColor ?: skin.innerTopHighlight
                val topAccentHeight = if (skin.topStripColor != null) skin.topStripHeight else 0.8.dp
                topAccent?.let { stripCol ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topAccentHeight)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        stripCol.copy(alpha = 0.14f),
                                        stripCol,
                                        stripCol.copy(alpha = 0.14f),
                                    ),
                                ),
                            ),
                    )
                }

                if (isEditingReply) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuTextField(
                            value = editBuffer,
                            onValueChange = onEditBufferChange,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(onClick = onCancelEdit) {
                                Text(strings.common.cancel, color = skin.mutedText, fontSize = 12.sp)
                            }
                            GuButton(
                                onClick = onSaveEdit,
                                enabled = editBuffer.isNotBlank(),
                                compact = true,
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(dialogueScrollState)
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                onFrontendFillInput = onFrontendFillInput,
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
                                    color = if (!streamingStatusHint.isNullOrBlank()) c.warning else stageAccent,
                                )
                                Text(
                                    text = streamingStatusHint ?: strings.chat.streamingWriting,
                                    color = if (!streamingStatusHint.isNullOrBlank()) c.warning else skin.mutedText,
                                    fontSize = 13.sp,
                                    fontWeight = if (!streamingStatusHint.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        } else {
                            Text(
                                text = fallbackUserText?.takeIf { it.isNotBlank() } ?: strings.chat.streamingWriting,
                                color = skin.mutedText,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                            )
                        }

                        if ((isStreaming || latestAssistant?.status == "streaming") &&
                            cleanBody.isNotBlank() &&
                            !streamingStatusHint.isNullOrBlank()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = c.warning,
                                )
                                Text(
                                    text = streamingStatusHint,
                                    color = c.warning,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        val errText = latestAssistant?.error
                        if (latestAssistant != null && !errText.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (skin.isDarkSurface) Color(0xCC3C161D) else c.dangerBg,
                                border = BorderStroke(0.6.dp, Color(0x88F87171)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                                        TextButton(onClick = onRegenerate) {
                                            Text(
                                                strings.common.retry,
                                                color = if (skin.isDarkSurface) Color(0xFFFCA5A5) else c.danger,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Micro-Action Footer + Layer 4 On-Demand Composer Capsule
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
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
                                label = strings.rp.theaterLogBtn,
                                icon = Icons.Default.History,
                                enabled = true,
                                onDarkSurface = skin.isDarkSurface,
                                accentColor = stageAccent,
                                onClick = onOpenHistory,
                            )
                            if (cleanBody.isNotBlank()) {
                                StageBoxMiniIconBtn(
                                    icon = Icons.Default.ContentCopy,
                                    contentDescription = strings.common.copy,
                                    enabled = true,
                                    onClick = onCopyBody,
                                )
                            }
                        }

                        if (onToggleComposer != null) {
                            Surface(
                                onClick = onToggleComposer,
                                shape = RoundedCornerShape(999.dp),
                                color = if (isComposerExpanded) stageAccent.copy(alpha = 0.24f) else Color(0xD91A1510),
                                border = BorderStroke(
                                    0.7.dp,
                                    stageAccent.copy(alpha = if (isComposerExpanded) 0.90f else 0.68f),
                                ),
                            ) {
                                Text(
                                    text = if (isComposerExpanded) {
                                        if (isZh) "▾ 收起输入" else "▾ Collapse"
                                    } else {
                                        if (isZh) "🎲 自由扮演 ✍️" else "🎲 Free Play ✍️"
                                    },
                                    color = Color(0xFFFFF4DC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp),
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
private fun StageBoxMiniIconBtn(
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
private fun TheaterFloatingChoiceDeck(
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
private fun StageCastMiniStrip(
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
internal fun TheaterStageBackdrop(
    rp: RpConfig,
    stageAccent: Color,
    activeSlot: String?,
    backdropDim: Float,
    dimForReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val sceneFile = remember(rp.state.sceneImagePath) {
        rp.state.sceneImagePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val effectiveDim = backdropDim.coerceIn(0f, 0.78f)
    val spotlightXRatio = when (activeSlot) {
        "left" -> 0.25f
        "right" -> 0.75f
        else -> 0.50f
    }

    Box(modifier = modifier) {
        if (sceneFile != null) {
            Image(
                painter = rememberAsyncImagePainter(sceneFile),
                contentDescription = rp.state.scene,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
                                    colors = listOf(glowCol.copy(alpha = 0.28f), Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                                    radius = glowRadius,
                                ),
                            )
                        }
                    },
            )
        }

        if (dimForReading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val spotRadius = size.maxDimension * 0.52f
                        if (spotRadius > 0f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        stageAccent.copy(alpha = if (sceneFile != null) 0.16f else 0.22f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width * spotlightXRatio, size.height * 0.34f),
                                    radius = spotRadius,
                                ),
                            )
                        }
                    },
            )
        }

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
                            0.0f to Color(if (sceneFile != null) 0x5C060912 else 0x3305070E),
                            0.24f to Color.Transparent,
                            0.62f to Color(if (sceneFile != null) 0x58060912 else 0x2605070E),
                            1.0f to Color(if (sceneFile != null) 0xC4060912 else 0x8C05070E),
                        ),
                    ),
            )
        } else if (sceneFile != null) {
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

internal fun formatRpActHeader(actNumber: Int, chapterTitle: String, isZh: Boolean): String {
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xC4131928),
                    border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.56f)),
                    modifier = Modifier.heightIn(min = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = stageAccent,
                            modifier = Modifier.size(12.5.dp),
                        )
                        Text(
                            text = actLabel,
                            color = Color(0xFFFFF4DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

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

        if (!hideSecondaryEnvRow && envText.isNotBlank()) {
            Surface(
                onClick = onOpenStudio,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xA60D121F),
                border = BorderStroke(0.5.dp, Color(0x40CBD5E1)),
            ) {
                Text(
                    text = "📍 $envText",
                    color = Color(0xFFDDE5F5),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun StageGlassPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Boolean = false,
    accentColor: Color = Color(0xFFF5C563),
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (accent) Color(0xD42A1F12) else Color(0xBA0D1220),
        border = BorderStroke(0.6.dp, if (accent) accentColor.copy(alpha = 0.65f) else Color(0x44CBD5E1)),
        modifier = Modifier.heightIn(min = 28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun StageGlassIconPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    accentColor: Color = Color(0xFFF5C563),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (accent) Color(0xD42A1F12) else Color(0xBA0D1220),
        border = BorderStroke(0.6.dp, if (accent) accentColor.copy(alpha = 0.65f) else Color(0x44CBD5E1)),
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (accent) accentColor else Color(0xFFE2E8F0),
                modifier = Modifier.size(14.dp),
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
            border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.42f)),
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
                    size = 70.dp,
                    cornerRadius = 14.dp,
                    borderColor = stageAccent.copy(alpha = 0.65f),
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (focusedNpc.mood.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0x66273452),
                            ) {
                                Text(
                                    text = "${expressionEmoji(exprKey)} ${focusedNpc.mood}",
                                    color = Color(0xFFDCE6FA),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
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
                        GuMetaText(
                            text = subtitle,
                            color = Color(0xFFB5C0D6),
                        )
                    }

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
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = tierLabel,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x44FFFFFF)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(tier.progress.coerceIn(0.04f, 1f))
                                    .height(5.dp)
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
        shape = RoundedCornerShape(999.dp),
        color = Color(0xD4101522),
        border = BorderStroke(0.5.dp, Color(0x48E2E8F0)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            IconButton(
                onClick = { onSwitch((index - 1).coerceAtLeast(0)) },
                enabled = index > 0,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = if (index > 0) Color(0xFFF3F6FC) else Color(0x48CBD5E1),
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = "${index + 1}/$total",
                color = accentColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            IconButton(
                onClick = { onSwitch((index + 1).coerceAtMost(total - 1)) },
                enabled = index < total - 1,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (index < total - 1) Color(0xFFF3F6FC) else Color(0x48CBD5E1),
                    modifier = Modifier.size(15.dp),
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
                border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.55f)),
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
                        fontWeight = FontWeight.SemiBold,
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
            border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.52f)),
            shadowElevation = 6.dp,
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
                    fontWeight = FontWeight.Bold,
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
                                    .border(0.6.dp, stageAccent.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
                                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RpNpcPortraitAvatar(character = npc, fallbackName = npc.name, size = 24.dp)
                                Text(
                                    text = "${npc.name} ♥${npc.affinity}",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                    Text(strings.rp.wizardStartBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    val appTheme = LocalAppTheme.current
    val c = appTheme.colors
    val sheetBg = if (c.page.alpha < 0.95f) Color(0xFF0E1320) else c.page
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color(0x8C060912),
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !appTheme.isDark
        }

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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                                    fontWeight = FontWeight.SemiBold,
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
                                    RpTurnDeltaStrip(
                                        deltas = deltas,
                                        onDarkStage = appTheme.isDark,
                                    )
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
