package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPlayStyle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpWorkspaceScreen(
    rp: RpConfig,
    messages: List<ChatMessage>,
    waterline: ContextWaterlineSummary,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    selectedGroupLabel: String,
    memoryEnabled: Boolean,
    enableReasoning: Boolean,
    supportsVision: Boolean,
    draftText: String,
    draftAttachments: List<ChatAttachmentMeta>,
    attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    isImportingAttachment: Boolean = false,
    draftRpKind: RpInputKind,
    draftDiceRoll: RpDiceRoll?,
    isStreaming: Boolean,
    streamingStatusHint: String?,
    isRefreshingChoices: Boolean,
    isImpersonating: Boolean,
    isGeneratingArt: Boolean,
    artStatusLabel: String,
    attachmentPathResolver: (String) -> File,
    onOpenDrawer: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    moreMenuContent: @Composable () -> Unit,
    onToggleTheaterMode: () -> Unit,
    onSetTheaterMode: (Boolean) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenFrontendEditor: () -> Unit,
    onQuickSave: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
    onStarterPrompt: (String) -> Unit,
    onSelectChoice: (RpChoice, Boolean) -> Unit,
    onRefreshChoices: () -> Unit,
    onContinue: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onEditAssistantMessage: (String, String) -> Unit,
    onBranchFromMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onFrontendAction: (String) -> Unit,
    onFrontendFillInput: (String) -> Unit,
    onFrontendSetFlag: (String, String) -> Unit,
    onFrontendAdjustAffinity: (String, Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onSelectRpKind: (RpInputKind) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleMemory: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit,
    onUpdateAttachmentInjectMode: (String, FileInjectMode) -> Unit = { _, _ -> },
    onReadAttachmentText: (suspend (String) -> String?)? = null,
    onOpenDiceDialog: () -> Unit,
    onClearDiceRoll: () -> Unit,
    onOpenRecallInspector: () -> Unit,
    onImpersonate: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val outerTheme = LocalAppTheme.current
    val isZh = strings.language == AppLanguage.ZH

    val stageAccent = remember(rp.visualTheme.accentHex) {
        resolveRpStageAccent(rp.visualTheme.accentHex)
    }
    val rpTheme = remember(outerTheme, stageAccent) {
        deriveRpStageAppTheme(outerTheme, stageAccent)
    }

    var hideUi by remember(rp.theaterMode) { mutableStateOf(false) }
    var showHistory by remember(rp.theaterMode) { mutableStateOf(false) }
    val isImmersiveHidden = hideUi && rp.theaterMode

    WorkspaceSystemBarsEffect(isDarkBackdrop = true)

    BackHandler(enabled = isImmersiveHidden) {
        hideUi = false
    }

    val restoreInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = restoreInteraction,
                indication = null,
                enabled = isImmersiveHidden,
            ) { hideUi = false },
    ) {
        // Layer 1: Full-bleed Game Canvas Backdrop (Status Bar -> Game Top HUD -> Stage/Stream -> Dark Frosted Composer)
        TheaterStageBackdrop(
            rp = rp,
            stageAccent = stageAccent,
            activeSlot = null,
            backdropDim = if (rp.theaterMode) {
                rp.visualTheme.backdropDim
            } else {
                rp.visualTheme.backdropDim.coerceAtLeast(0.45f)
            },
            dimForReading = !isImmersiveHidden,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 2: Unified Game Chrome & Workspace Surface
        CompositionLocalProvider(
            LocalAppTheme provides rpTheme,
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            // Immersive CG Appreciation Mode: Floating Show-UI Pill Only
            AnimatedVisibility(
                visible = isImmersiveHidden,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
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

            if (!isImmersiveHidden) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Unified Translucent Game Top HUD (Zero white app bar)
                    RpUnifiedGameTopHud(
                        rp = rp,
                        isZh = isZh,
                        stageAccent = stageAccent,
                        selectedModelId = selectedModelId,
                        activeModelFreeQuota = activeModelFreeQuota,
                        showVitalsPillInRow2 = rp.theaterMode || !rp.enableStateHud,
                        onOpenDrawer = onOpenDrawer,
                        onOpenStudio = onOpenStudio,
                        onToggleTheaterMode = onToggleTheaterMode,
                        onOpenHistory = { showHistory = true },
                        onOpenFrontendEditor = onOpenFrontendEditor,
                        onHideUi = { hideUi = true },
                        onOpenMoreMenu = onOpenMoreMenu,
                        moreMenuContent = moreMenuContent,
                        onOpenModelSheet = onOpenModelSheet,
                    )

                    // Stream Sub-Mode Extras (Status HUD Ribbon, AI Art Banner, Custom HTML Status Widget)
                    if (!rp.theaterMode) {
                        if (rp.enableStateHud) {
                            RpStatusHudBar(
                                rp = rp,
                                triggeredLoreCount = waterline.triggeredLoreIds.size,
                                onToggleTheaterMode = onToggleTheaterMode,
                                onQuickSave = onQuickSave,
                                onOpenStudio = onOpenStudio,
                                onUpdateAuthorNote = onUpdateAuthorNote,
                                showHeaderActions = false,
                            )
                        }
                        if (isGeneratingArt) {
                            Surface(
                                color = Color(0xD4101624),
                                border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = stageAccent,
                                    )
                                    Text(
                                        text = "✨ ${artStatusLabel.ifBlank { strings.rp.aiGeneratingArt }}",
                                        color = Color(0xFFFFF4DC),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        val rpVisualTheme = rp.visualTheme
                        if (rpVisualTheme.showCustomStatusWidget && rpVisualTheme.customStatusHtml.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                RpHtmlCardSandbox(
                                    htmlFragment = rpVisualTheme.customStatusHtml,
                                    rp = rp,
                                    customCss = rpVisualTheme.customCss,
                                    minHeightDp = 52,
                                    maxHeightDp = 220,
                                    onFrontendAction = onFrontendAction,
                                    onFrontendFillInput = onFrontendFillInput,
                                    onFrontendSetFlag = onFrontendSetFlag,
                                    onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                                )
                            }
                        }
                    }

                    // Middle Stage / Story Stream Area
                    if (rp.theaterMode) {
                        RpTheaterStageView(
                            rp = rp,
                            messages = messages,
                            isStreaming = isStreaming,
                            streamingStatusHint = streamingStatusHint,
                            isRefreshingChoices = isRefreshingChoices,
                            hasModelSelected = !selectedModelId.isNullOrBlank(),
                            isGeneratingArt = isGeneratingArt,
                            artStatusLabel = artStatusLabel,
                            onOpenModelSheet = onOpenModelSheet,
                            onExitTheater = { onSetTheaterMode(false) },
                            onOpenStudio = onOpenStudio,
                            onOpenFrontendEditor = onOpenFrontendEditor,
                            onQuickSave = onQuickSave,
                            onStarterPrompt = onStarterPrompt,
                            onSelectChoice = onSelectChoice,
                            onRefreshChoices = onRefreshChoices,
                            onContinue = onContinue,
                            onRegenerate = onRegenerate,
                            onEditAssistantMessage = onEditAssistantMessage,
                            onSwitchVariant = onSwitchVariant,
                            onFrontendAction = onFrontendAction,
                            onFrontendFillInput = onFrontendFillInput,
                            onFrontendSetFlag = onFrontendSetFlag,
                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            onToast = onToast,
                            drawOwnBackdrop = false,
                            showInternalTopBar = false,
                            externalHideUi = hideUi,
                            onExternalHideUiChange = { hideUi = it },
                            externalShowHistory = showHistory,
                            onExternalShowHistoryChange = { showHistory = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    } else {
                        ChatMessageList(
                            mode = ConversationMode.rp,
                            messages = messages,
                            waterline = waterline,
                            hasModelSelected = !selectedModelId.isNullOrBlank(),
                            selectedModelLabel = selectedModelId.orEmpty(),
                            selectedGroupLabel = selectedGroupLabel,
                            memoryEnabled = memoryEnabled,
                            rpConfig = rp,
                            novelConfig = null,
                            novelChapters = emptyList(),
                            isStreaming = isStreaming,
                            streamingStatusHint = streamingStatusHint,
                            isRefreshingChoices = isRefreshingChoices,
                            attachmentPathResolver = attachmentPathResolver,
                            attachmentsById = attachmentsById,
                            onReadAttachmentText = onReadAttachmentText,
                            onOpenModelSheet = onOpenModelSheet,
                            onStarterPrompt = onStarterPrompt,
                            onSelectNovelChoice = {},
                            onRefreshNovelChoices = {},
                            onContinueNovel = {},
                            onOpenNovelStudio = {},
                            onSelectRpChoice = onSelectChoice,
                            onRefreshRpChoices = onRefreshChoices,
                            onContinueRp = onContinue,
                            onRegenerate = onRegenerate,
                            onEditUserMessage = onEditUserMessage,
                            onEditAssistantMessage = onEditAssistantMessage,
                            onBranchFromMessage = onBranchFromMessage,
                            onDeleteMessage = onDeleteMessage,
                            onSwitchVariant = onSwitchVariant,
                            onToast = onToast,
                            onFrontendAction = onFrontendAction,
                            onFrontendFillInput = onFrontendFillInput,
                            onFrontendSetFlag = onFrontendSetFlag,
                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }

                    // Bottom: Frosted Dark Glass Single-Row Composer
                    ChatComposerBar(
                        mode = ConversationMode.rp,
                        draftText = draftText,
                        draftAttachments = draftAttachments,
                        draftNovelKind = NovelInputKind.director,
                        draftRpKind = draftRpKind,
                        draftDiceRoll = draftDiceRoll,
                        waterline = waterline,
                        enableReasoning = enableReasoning,
                        enableAutoMemory = memoryEnabled,
                        isStreaming = isStreaming,
                        supportsVision = supportsVision,
                        isImportingAttachment = isImportingAttachment,
                        onDraftChange = onDraftChange,
                        onSelectNovelKind = {},
                        onSelectRpKind = onSelectRpKind,
                        onToggleReasoning = onToggleReasoning,
                        onToggleMemory = onToggleMemory,
                        onPickImage = onPickImage,
                        onTakePhoto = onTakePhoto,
                        onPickFile = onPickFile,
                        onRemoveAttachment = onRemoveAttachment,
                        onUpdateAttachmentInjectMode = onUpdateAttachmentInjectMode,
                        onReadAttachmentText = onReadAttachmentText,
                        onOpenModelSheet = onOpenModelSheet,
                        onToast = onToast,
                        onOpenDiceDialog = onOpenDiceDialog,
                        onClearDiceRoll = onClearDiceRoll,
                        onOpenRecallInspector = onOpenRecallInspector,
                        onSend = onSend,
                        onStop = onStop,
                        isImpersonating = isImpersonating,
                        onImpersonate = onImpersonate,
                        onContinueRp = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun RpUnifiedGameTopHud(
    rp: RpConfig,
    isZh: Boolean,
    stageAccent: Color,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    showVitalsPillInRow2: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenStudio: () -> Unit,
    onToggleTheaterMode: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFrontendEditor: () -> Unit,
    onHideUi: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    moreMenuContent: @Composable () -> Unit,
    onOpenModelSheet: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val actLabel = remember(rp.state.actNumber, rp.state.chapterTitle, isZh) {
        formatRpActHeader(rp.state.actNumber, rp.state.chapterTitle, isZh)
    }
    val envText = remember(rp.state.scene, rp.state.time, rp.state.weather) {
        listOf(rp.state.scene, rp.state.time, rp.state.weather)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
    }
    val hasCustomStatusWidget = rp.visualTheme.showCustomStatusWidget && rp.visualTheme.customStatusHtml.isNotBlank()
    val showVitals = showVitalsPillInRow2 && !hasCustomStatusWidget &&
        (rp.playStyle == RpPlayStyle.trpg || rp.state.gold > 0 || rp.state.hp < rp.state.maxHp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x99060912),
                        Color(0x3D060912),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Row 1 (34dp): Drawer + Act Pill + Mode Toggle + Stage Tools + More Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StageGlassIconPill(
                icon = Icons.Default.Menu,
                contentDescription = strings.chat.sessions,
                accentColor = stageAccent,
                onClick = onOpenDrawer,
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xC4131928),
                    border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.66f)),
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
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            StageGlassPill(
                label = if (rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                icon = if (rp.theaterMode) Icons.Default.Forum else Icons.Default.Theaters,
                accent = !rp.theaterMode,
                accentColor = stageAccent,
                onClick = onToggleTheaterMode,
            )
            if (rp.theaterMode) {
                StageGlassIconPill(
                    icon = Icons.Default.History,
                    contentDescription = strings.rp.theaterLogBtn,
                    accentColor = stageAccent,
                    onClick = onOpenHistory,
                )
            }
            StageGlassIconPill(
                icon = Icons.Default.Palette,
                contentDescription = strings.rp.frontendTab,
                accent = true,
                accentColor = stageAccent,
                onClick = onOpenFrontendEditor,
            )
            if (rp.theaterMode) {
                StageGlassIconPill(
                    icon = Icons.Default.VisibilityOff,
                    contentDescription = strings.rp.theaterHideUi,
                    accentColor = stageAccent,
                    onClick = onHideUi,
                )
            }
            StageGlassIconPill(
                icon = Icons.Default.Tune,
                contentDescription = strings.rp.studioBtn,
                accent = true,
                accentColor = stageAccent,
                onClick = onOpenStudio,
            )
            Box {
                StageGlassIconPill(
                    icon = Icons.Default.MoreVert,
                    contentDescription = strings.chat.moreActions,
                    accentColor = stageAccent,
                    onClick = onOpenMoreMenu,
                )
                moreMenuContent()
            }
        }

        // Row 2 (22dp): Scene/Time/Weather + Optional Vitals (left) & Compact Translucent Model Pill (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val sceneLabel = if (envText.isNotBlank()) "📍 $envText" else "✦ ${rp.title}"
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xA60D121F),
                    border = BorderStroke(0.8.dp, Color(0x47CBD5E1)),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = sceneLabel,
                        color = Color(0xFFDDE5F5),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                if (showVitals) {
                    Surface(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xA60D121F),
                        border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.48f)),
                    ) {
                        Text(
                            text = "♥${rp.state.hp}/${rp.state.maxHp} · 🪙${rp.state.gold}",
                            color = Color(0xFFFDE68A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            val hasModel = !selectedModelId.isNullOrBlank()
            val dotColor = when {
                activeModelFreeQuota -> Color(0xFF4ADE80)
                hasModel -> stageAccent
                else -> Color(0xFF94A3B8)
            }
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xB80E1422),
                border = BorderStroke(
                    width = 0.8.dp,
                    color = if (hasModel) stageAccent.copy(alpha = 0.48f) else Color(0x52CBD5E1),
                ),
                modifier = Modifier.widthIn(max = 168.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = selectedModelId ?: strings.chat.selectModel,
                        color = if (hasModel) Color(0xFFEAF0FC) else Color(0xFFA7B3CB),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFA7B3CB),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
