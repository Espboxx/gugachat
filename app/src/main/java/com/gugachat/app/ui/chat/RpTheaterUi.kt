package com.gugachat.app.ui.chat

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
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSheetHeader
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
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.GuVariantSwipePill
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

internal val PORTRAIT_GRADIENTS = listOf(
    Color(0xFF8B3A62) to Color(0xFF34162E),
    Color(0xFF3A5BA0) to Color(0xFF1A254B),
    Color(0xFF9C662B) to Color(0xFF3E2610),
    Color(0xFF2E7D6F) to Color(0xFF143530),
    Color(0xFF6B4E9B) to Color(0xFF271B44),
    Color(0xFFA04747) to Color(0xFF3D171E),
)

internal fun expressionEmoji(exprKey: String): String = when (exprKey.lowercase()) {
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
    val ctx = LocalContext.current
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
    val avatarModel = remember(resolvedPath) {
        LocalMediaPathResolver.resolveImageModel(ctx, resolvedPath)
    }

    if (avatarModel != null) {
        Image(
            painter = rememberAsyncImagePainter(avatarModel),
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
    onOpenStudio: () -> Unit,
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
    externalHideUi: Boolean? = null,
    onExternalHideUiChange: ((Boolean) -> Unit)? = null,
    externalShowHistory: Boolean? = null,
    onExternalShowHistoryChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isComposerExpanded: Boolean = false,
    onToggleComposer: (() -> Unit)? = null,
    onOpenQuickControl: (() -> Unit)? = null,
    activeGreetingVariants: List<String> = emptyList(),
    greetingActions: RpGreetingActions? = null,
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
    val activeUserTurnCount = remember(activeMessages) { activeMessages.count { it.role == "user" } }
    val seq1GreetingMsg = remember(activeMessages) {
        activeMessages.firstOrNull { it.role == "assistant" && it.seq == 1 }
    }
    val greetingPreviews = remember(seq1GreetingMsg?.variants, activeGreetingVariants) {
        val fromMsg = seq1GreetingMsg?.variants.orEmpty().map { it.content }.filter { it.isNotBlank() }
        if (fromMsg.size > 1) fromMsg else activeGreetingVariants
    }
    val activeGreetingIdx = (seq1GreetingMsg?.activeVariantIndex ?: 0)
        .coerceIn(0, (greetingPreviews.size - 1).coerceAtLeast(0))
    val showGreetingSwitcher = activeUserTurnCount == 0 && greetingPreviews.size > 1 && !isStreaming
    var showGreetingPickerSheet by remember { mutableStateOf(false) }

    if (showGreetingPickerSheet && greetingPreviews.size > 1) {
        RpGreetingPickerSheet(
            greetings = greetingPreviews,
            activeIndex = activeGreetingIdx,
            onSelect = { idx ->
                showGreetingPickerSheet = false
                if (greetingActions != null) {
                    greetingActions.onSelectGreetingVariant(idx)
                } else if (seq1GreetingMsg != null) {
                    onSwitchVariant(seq1GreetingMsg.id, idx)
                }
            },
            onDismiss = { showGreetingPickerSheet = false },
        )
    }

    var internalHideUi by remember { mutableStateOf(false) }
    val hideUi = externalHideUi ?: internalHideUi

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

    Box(modifier = modifier.fillMaxSize()) {
        if (!hideUi) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val stageMaxH = maxHeight
                    val dialogueHeight = (stageMaxH * 0.32f).coerceIn(194.dp, 268.dp)

                    if (activeMessages.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                                    top = 12.dp,
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
                                            LocalMediaPathResolver.resolveImageModel(ctx, path) != null
                                        }
                                    }
                                    if (spritesWithFiles.isNotEmpty()) {
                                        // Anchor full half-body stage below top HUD so character faces stay clear above choice buttons
                                        val spriteTopClearance = 44.dp
                                        val spriteHeightFraction = (theme.spriteHeightRatio + 0.50f).coerceIn(0.84f, 1.0f)
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
                                                .padding(top = spriteTopClearance, bottom = dialogueHeight * 0.34f)
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
                                                        top = 12.dp,
                                                        start = 14.dp,
                                                        end = 14.dp,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Cast Switcher + Turn Deltas + Endings
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {

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
                                LocalMediaPathResolver.resolveImageModel(ctx, path) != null
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
                                    com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, "reply", cleanBody)
                                    onToast(strings.common.copied)
                                }
                            },
                            onToast = onToast,
                            onFrontendAction = onFrontendAction,
                            onFrontendFillInput = onFrontendFillInput,
                            onFrontendSetFlag = onFrontendSetFlag,
                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            showGreetingSwitcher = showGreetingSwitcher,
                            greetingIndex = activeGreetingIdx,
                            greetingsCount = greetingPreviews.size,
                            onOpenGreetingPicker = { showGreetingPickerSheet = true },
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
internal fun FeatherHalfBodySprite(
    character: RpCharacter,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val resolvedPath = remember(
        character.mood,
        character.expressionSprites,
        character.spritePath,
        character.avatarPath,
    ) {
        RpEngine.resolveCharacterSpritePath(character)
    }
    val spriteModel = remember(resolvedPath) {
        LocalMediaPathResolver.resolveImageModel(ctx, resolvedPath)
    } ?: return

    Box(
        modifier = modifier.clickable(onClick = onTap),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = rememberAsyncImagePainter(spriteModel),
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
internal fun TheaterDialogueStageFrame(
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
    showGreetingSwitcher: Boolean = false,
    greetingIndex: Int = 0,
    greetingsCount: Int = 0,
    onOpenGreetingPicker: () -> Unit = {},
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
                if (showGreetingSwitcher) {
                    Surface(
                        onClick = onOpenGreetingPicker,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xD9161D2E),
                        border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.65f)),
                    ) {
                        Text(
                            text = if (isZh) {
                                "🎭 换开场 ${greetingIndex + 1}/$greetingsCount ▾"
                            } else {
                                "🎭 Greeting ${greetingIndex + 1}/$greetingsCount ▾"
                            },
                            color = Color(0xFFFFF4DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                } else if (variantAssistant != null) {
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
internal fun TheaterStageBackdrop(
    rp: RpConfig,
    stageAccent: Color,
    activeSlot: String?,
    backdropDim: Float,
    dimForReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val sceneModel = remember(rp.state.sceneImagePath) {
        LocalMediaPathResolver.resolveImageModel(ctx, rp.state.sceneImagePath)
    }
    val effectiveDim = backdropDim.coerceIn(0f, 0.78f)
    val spotlightXRatio = when (activeSlot) {
        "left" -> 0.25f
        "right" -> 0.75f
        else -> 0.50f
    }

    Box(modifier = modifier) {
        if (sceneModel != null) {
            Image(
                painter = rememberAsyncImagePainter(sceneModel),
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
                                        stageAccent.copy(alpha = if (sceneModel != null) 0.16f else 0.22f),
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
                            0.0f to Color(if (sceneModel != null) 0x5C060912 else 0x3305070E),
                            0.24f to Color.Transparent,
                            0.62f to Color(if (sceneModel != null) 0x58060912 else 0x2605070E),
                            1.0f to Color(if (sceneModel != null) 0xC4060912 else 0x8C05070E),
                        ),
                    ),
            )
        } else if (sceneModel != null) {
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

internal data class StageAtmosphere(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glow: Color,
)

internal fun resolveStageAtmosphere(rp: RpConfig): StageAtmosphere {
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

internal val ACT_PREFIX_RE = Regex("^(第.{1,3}幕|Act\\s+\\d+)", RegexOption.IGNORE_CASE)

internal fun formatRpActHeader(actNumber: Int, chapterTitle: String, isZh: Boolean): String {
    val cleanTitle = chapterTitle.trim()
    if (cleanTitle.isNotEmpty() && ACT_PREFIX_RE.containsMatchIn(cleanTitle)) {
        return cleanTitle
    }
    val prefix = if (isZh) "第${actNumber}幕" else "Act $actNumber"
    return if (cleanTitle.isNotEmpty()) "$prefix · $cleanTitle" else prefix
}

internal fun formatRpRoleLabel(role: String, isZh: Boolean): String {
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
internal fun TheaterCastPortraitShowcase(
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
