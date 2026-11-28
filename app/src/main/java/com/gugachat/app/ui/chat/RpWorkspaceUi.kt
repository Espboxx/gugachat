package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModelAvatar
import com.gugachat.app.core.theme.LocalAppShellController
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
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.TimelineReconciler
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
    activeGreetingVariants: List<String> = emptyList(),
    greetingActions: RpGreetingActions? = null,
    onOpenStudio: (Int) -> Unit,
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
    onOpenContextSettings: () -> Unit = onOpenRecallInspector,
    onImpersonate: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToast: (String) -> Unit,
    onUpdateSentAttachmentInjectMode: ((String, FileInjectMode) -> Unit)? = null,
    minOutputChars: Int? = null,
    autoContinueShortReply: Boolean = false,
) {
    val strings = LocalAppStrings.current
    val outerTheme = LocalAppTheme.current
    val shellController = LocalAppShellController.current
    val isZh = strings.language == AppLanguage.ZH

    val stageAccent = remember(rp.visualTheme.accentHex) {
        resolveRpStageAccent(rp.visualTheme.accentHex)
    }
    val rpTheme = remember(outerTheme, stageAccent) {
        deriveRpStageAppTheme(outerTheme, stageAccent)
    }

    var hideUi by remember(rp.theaterMode) { mutableStateOf(false) }
    var showHistory by remember(rp.theaterMode) { mutableStateOf(false) }
    var showQuickPopover by remember { mutableStateOf(false) }
    var showCustomStatusPopover by remember { mutableStateOf(false) }
    var showTheaterComposer by remember(rp.theaterMode) { mutableStateOf(false) }
    var showGreetingPicker by remember { mutableStateOf(false) }
    var streamScrollHide by remember(rp.title) { mutableStateOf(false) }

    val activeMsgs = remember(messages) { TimelineReconciler.activeMessages(messages) }
    val activeUserTurnCount = remember(activeMsgs) { activeMsgs.count { it.role == "user" } }
    val seq1GreetingMsg = remember(activeMsgs) {
        activeMsgs.firstOrNull { it.role == "assistant" && it.seq == 1 }
    }
    val greetingPreviews = remember(seq1GreetingMsg?.variants, activeGreetingVariants) {
        if (seq1GreetingMsg == null) {
            emptyList()
        } else {
            val fromMsg = seq1GreetingMsg.variants.map { it.content }.filter { it.isNotBlank() }
            if (fromMsg.size > 1) fromMsg else activeGreetingVariants
        }
    }
    val activeGreetingIdx = (seq1GreetingMsg?.activeVariantIndex ?: 0)
        .coerceIn(0, (greetingPreviews.size - 1).coerceAtLeast(0))
    val showStreamGreetingPill = !rp.theaterMode &&
        seq1GreetingMsg != null &&
        activeUserTurnCount == 0 &&
        greetingPreviews.size > 1 &&
        !isStreaming

    if (showGreetingPicker && greetingPreviews.size > 1) {
        RpGreetingPickerSheet(
            greetings = greetingPreviews,
            activeIndex = activeGreetingIdx,
            onSelect = { idx ->
                showGreetingPicker = false
                if (greetingActions != null) {
                    greetingActions.onSelectGreetingVariant(idx)
                } else if (seq1GreetingMsg != null) {
                    onSwitchVariant(seq1GreetingMsg.id, idx)
                }
            },
            onDismiss = { showGreetingPicker = false },
        )
    }

    val isImmersiveHidden = hideUi && rp.theaterMode
    val hasCustomStatusWidget = rp.visualTheme.showCustomStatusWidget && rp.visualTheme.customStatusHtml.isNotBlank()

    // Keep status & navigation bars crisp light-on-dark across the RP dark stage
    WorkspaceSystemBarsEffect(isDarkBackdrop = true)

    // Sync global bottom bar visibility with reading scroll / theater / overlay states
    LaunchedEffect(
        rp.theaterMode,
        isImmersiveHidden,
        showQuickPopover,
        showCustomStatusPopover,
        showTheaterComposer,
        streamScrollHide,
    ) {
        shellController.setScrollHideBottomBar(
            rp.theaterMode ||
                isImmersiveHidden ||
                showQuickPopover ||
                showCustomStatusPopover ||
                showTheaterComposer ||
                streamScrollHide,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            shellController.setScrollHideBottomBar(false)
        }
    }

    val streamNestedScroll = rememberScrollHideBottomBarConnection(
        enabled = messages.isNotEmpty(),
        isHidden = streamScrollHide,
        onHiddenChange = { streamScrollHide = it },
        hideThresholdPx = 36f,
        revealThresholdPx = 36f,
        topRevealAvailablePx = 16f,
        usePreScroll = true,
    )

    BackHandler(enabled = showQuickPopover || showCustomStatusPopover || showTheaterComposer || isImmersiveHidden) {
        when {
            showQuickPopover -> showQuickPopover = false
            showCustomStatusPopover -> showCustomStatusPopover = false
            showTheaterComposer -> showTheaterComposer = false
            isImmersiveHidden -> hideUi = false
        }
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
        // Layer 0: Full-bleed Game Canvas Backdrop
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

        // Layer 1+: Unified Game Chrome & Workspace Surface
        CompositionLocalProvider(
            LocalAppTheme provides rpTheme,
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            // Pure CG Appreciation Floating Pill
            AnimatedVisibility(
                visible = isImmersiveHidden,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .zIndex(10f),
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
                    // Single-row ~42dp Frosted Glass HUD (replaces dual-row HUD + redundant RpStatusHudBar)
                    RpSingleRowGameTopHud(
                        rp = rp,
                        isZh = isZh,
                        stageAccent = stageAccent,
                        isQuickPopoverOpen = showQuickPopover,
                        onOpenDrawer = onOpenDrawer,
                        onToggleQuickPopover = {
                            showCustomStatusPopover = false
                            showQuickPopover = !showQuickPopover
                        },
                        onToggleTheaterMode = {
                            showQuickPopover = false
                            onToggleTheaterMode()
                        },
                        onOpenStudio = {
                            showQuickPopover = false
                            onOpenStudio(0)
                        },
                        onOpenMoreMenu = {
                            showQuickPopover = false
                            onOpenMoreMenu()
                        },
                        moreMenuContent = moreMenuContent,
                    )

                    // Middle Stage / Story Stream Area (Wrapped in Box for zero-push floating overlays)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
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
                                onOpenStudio = { onOpenStudio(0) },
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
                                externalHideUi = hideUi,
                                externalShowHistory = showHistory,
                                onExternalShowHistoryChange = { showHistory = it },
                                isComposerExpanded = showTheaterComposer,
                                onToggleComposer = { showTheaterComposer = !showTheaterComposer },
                                activeGreetingVariants = activeGreetingVariants,
                                greetingActions = greetingActions,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(streamNestedScroll),
                            ) {
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
                                    onUpdateSentAttachmentInjectMode = onUpdateSentAttachmentInjectMode,
                                    minOutputChars = minOutputChars,
                                    autoContinueShortReply = autoContinueShortReply,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        // Floating Art Progress Chip (Stream mode; Theater mode shows it cleanly inside stage)
                        if (!rp.theaterMode && isGeneratingArt) {
                            Surface(
                                color = Color(0xD8101624),
                                border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.5.dp),
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
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (showStreamGreetingPill) {
                            Surface(
                                onClick = { showGreetingPicker = true },
                                color = Color(0xD8101624),
                                border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 12.dp),
                            ) {
                                Text(
                                    text = if (isZh) {
                                        "🎭 换开场 ${activeGreetingIdx + 1}/${greetingPreviews.size} ▾"
                                    } else {
                                        "🎭 Greeting ${activeGreetingIdx + 1}/${greetingPreviews.size} ▾"
                                    },
                                    color = Color(0xFFFFF4DC),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                )
                            }
                        }

                        // Tamed Custom HTML Status Widget: Collapsed top-right pill -> Overlay Popover (zero permanently pinned WebView)
                        if (hasCustomStatusWidget) {
                            if (showCustomStatusPopover) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x5204070E))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { showCustomStatusPopover = false },
                                )
                            }

                            Surface(
                                onClick = {
                                    showQuickPopover = false
                                    showCustomStatusPopover = !showCustomStatusPopover
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (showCustomStatusPopover) Color(0xEB261E14) else Color(0xCC0D1322),
                                border = BorderStroke(
                                    0.6.dp,
                                    if (showCustomStatusPopover) stageAccent.copy(alpha = 0.85f) else Color(0x48CBD5E1),
                                ),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 10.dp)
                                    .zIndex(7f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = stageAccent,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (isZh) "状态面板" else "Status",
                                        color = Color(0xFFFFF4DC),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(
                                        imageVector = if (showCustomStatusPopover) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showCustomStatusPopover,
                                enter = fadeIn() + slideInVertically { -it / 4 },
                                exit = fadeOut() + slideOutVertically { -it / 4 },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 38.dp, start = 12.dp, end = 12.dp)
                                    .zIndex(8f),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFA0E1422),
                                    border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.45f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                            .verticalScroll(rememberScrollState())
                                            .padding(8.dp),
                                    ) {
                                        RpHtmlCardSandbox(
                                            htmlFragment = rp.visualTheme.customStatusHtml,
                                            rp = rp,
                                            customCss = rp.visualTheme.customCss,
                                            minHeightDp = 52,
                                            maxHeightDp = 240,
                                            onFrontendAction = { act ->
                                                showCustomStatusPopover = false
                                                onFrontendAction(act)
                                            },
                                            onFrontendFillInput = { txt ->
                                                showCustomStatusPopover = false
                                                onFrontendFillInput(txt)
                                            },
                                            onFrontendSetFlag = onFrontendSetFlag,
                                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // On-Demand Theater Mode Composer (shifts stage upward cleanly with zero overlap)
                    AnimatedVisibility(
                        visible = rp.theaterMode && showTheaterComposer,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top) + slideInVertically { it / 3 },
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically { it / 3 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFA090D16))
                                .padding(top = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Surface(
                                    onClick = { showTheaterComposer = false },
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xCC131928),
                                    border = BorderStroke(0.5.dp, Color(0x44CBD5E1)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFFDCE4F5),
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = if (isZh) "收起" else "Hide",
                                            color = Color(0xFFDCE4F5),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
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
                                onOpenContextSettings = onOpenContextSettings,
                                onSend = {
                                    showTheaterComposer = false
                                    onSend()
                                },
                                onStop = onStop,
                                isImpersonating = isImpersonating,
                                onImpersonate = onImpersonate,
                                onContinueRp = {
                                    showTheaterComposer = false
                                    onContinue()
                                },
                            )
                        }
                    }

                    // Stream Mode Composer (hidden cleanly in Theater Mode until user taps [🎲 自由扮演 ✍️])
                    if (!rp.theaterMode) {
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
                            onOpenContextSettings = onOpenContextSettings,
                            onSend = onSend,
                            onStop = onStop,
                            isImpersonating = isImpersonating,
                            onImpersonate = onImpersonate,
                            onContinueRp = onContinue,
                        )
                    }
                }

                // Quick-Control Overlay Popover (Box Overlay above content, NEVER pushes content down!)
                if (showQuickPopover) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x8A04070E))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showQuickPopover = false }
                            .zIndex(12f),
                    )
                }

                AnimatedVisibility(
                    visible = showQuickPopover,
                    enter = fadeIn() + slideInVertically { -it / 5 },
                    exit = fadeOut() + slideOutVertically { -it / 5 },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 48.dp, start = 10.dp, end = 10.dp)
                        .zIndex(13f),
                ) {
                    RpQuickControlOverlayPopover(
                        rp = rp,
                        isZh = isZh,
                        stageAccent = stageAccent,
                        selectedModelId = selectedModelId,
                        activeModelFreeQuota = activeModelFreeQuota,
                        triggeredLoreCount = waterline.triggeredLoreIds.size,
                        onAdjustAffinity = onFrontendAdjustAffinity,
                        onJumpToStateTab = {
                            showQuickPopover = false
                            onOpenStudio(1)
                            onToast(
                                if (isZh) "已在手账「状态」中打开数值微调" else "Opened State panel to adjust vitals",
                            )
                        },
                        onOpenModelSheet = {
                            showQuickPopover = false
                            onOpenModelSheet()
                        },
                        onUpdateAuthorNote = { note ->
                            onUpdateAuthorNote(note)
                            onToast(if (isZh) "已更新导演备注" else "Director note saved")
                        },
                        onQuickSave = {
                            showQuickPopover = false
                            onQuickSave()
                        },
                        onOpenHistory = {
                            showQuickPopover = false
                            showHistory = true
                        },
                        onHideUi = {
                            showQuickPopover = false
                            hideUi = true
                        },
                        onOpenFullStudio = {
                            showQuickPopover = false
                            onOpenStudio(0)
                        },
                        onDismiss = { showQuickPopover = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RpSingleRowGameTopHud(
    rp: RpConfig,
    isZh: Boolean,
    stageAccent: Color,
    isQuickPopoverOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleQuickPopover: () -> Unit,
    onToggleTheaterMode: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    moreMenuContent: @Composable () -> Unit,
) {
    val strings = LocalAppStrings.current
    val centerCapsuleLabel = remember(
        rp.state.actNumber,
        rp.state.chapterTitle,
        rp.state.scene,
        rp.state.hp,
        isZh,
    ) {
        val actPart = if (isZh) "第${rp.state.actNumber}幕" else "Act ${rp.state.actNumber}"
        val scenePart = rp.state.scene.trim().ifBlank { rp.state.chapterTitle.trim() }
        if (scenePart.isNotEmpty()) {
            "$actPart · $scenePart · ♥${rp.state.hp}"
        } else {
            "$actPart · ♥${rp.state.hp}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xDE070B14),
                        Color(0x9E070B14),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Left: [☰]
            StageGlassIconPill(
                icon = Icons.Default.Menu,
                contentDescription = strings.chat.sessions,
                accentColor = stageAccent,
                onClick = onOpenDrawer,
            )

            // Center: Clickable Capsule `第X幕 · 场景名 · ♥HP ▾`
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    onClick = onToggleQuickPopover,
                    shape = RoundedCornerShape(999.dp),
                    color = if (isQuickPopoverOpen) Color(0xE0261D12) else Color(0xBD101624),
                    border = BorderStroke(
                        width = 0.6.dp,
                        color = stageAccent.copy(alpha = if (isQuickPopoverOpen) 0.85f else 0.45f),
                    ),
                    modifier = Modifier.heightIn(min = 30.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = stageAccent,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = centerCapsuleLabel,
                            color = Color(0xFFFFF4DC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = if (isQuickPopoverOpen) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = stageAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Right: ONLY 3 clear buttons — [🎭 剧场/对话], [🎛 工坊], [⋮]
            StageGlassPill(
                label = if (rp.theaterMode) {
                    if (isZh) "对话" else "Chat"
                } else {
                    if (isZh) "剧场" else "Stage"
                },
                icon = if (rp.theaterMode) Icons.Default.Forum else Icons.Default.Theaters,
                accent = rp.theaterMode,
                accentColor = stageAccent,
                onClick = onToggleTheaterMode,
            )
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpQuickControlOverlayPopover(
    rp: RpConfig,
    isZh: Boolean,
    stageAccent: Color,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    triggeredLoreCount: Int,
    onAdjustAffinity: (npcName: String, delta: Int) -> Unit,
    onJumpToStateTab: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
    onQuickSave: () -> Unit,
    onOpenHistory: () -> Unit,
    onHideUi: () -> Unit,
    onOpenFullStudio: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var noteDraft by remember(rp.authorNote) { mutableStateOf(rp.authorNote) }
    val castOrdered = remember(rp.cast) {
        val npcs = rp.cast.filter { !it.isPlayer }
        npcs.sortedByDescending { it.inScene }
    }
    val envSummary = remember(rp.state.scene, rp.state.time, rp.state.weather, rp.state.mood) {
        listOf(rp.state.scene, rp.state.time, rp.state.weather, rp.state.mood)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFC101624),
        border = BorderStroke(0.6.dp, stageAccent.copy(alpha = 0.42f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
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
                        text = formatRpActChapter(rp.state.actNumber, rp.state.chapterTitle, isZh),
                        color = Color(0xFFFFF4DC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (envSummary.isNotEmpty()) {
                        GuMetaText(
                            text = "📍 $envSummary",
                            color = Color(0xFFA7B3CB),
                        )
                    }
                }
                if (triggeredLoreCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF132E24),
                    ) {
                        Text(
                            text = "📖 $triggeredLoreCount",
                            color = Color(0xFF86EFAC),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x551F2940))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.common.close,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Horizontal Scroll Cast Strip (±5 Affinity + Mood)
            if (castOrdered.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuMetaText(
                        text = if (isZh) "在场角色 · 好感速调" else "Cast · Quick Affinity",
                        color = Color(0xFFA7B3CB),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        castOrdered.forEach { npc ->
                            val tier = RpEngine.affinityTier(npc.affinity)
                            val tierLabel = strings.rp.affinityTiers[tier.code]
                                ?: if (isZh) tier.zhLabel else tier.enLabel
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xB8182034),
                                modifier = Modifier.widthIn(min = 166.dp, max = 196.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        RpNpcPortraitAvatar(
                                            character = npc,
                                            fallbackName = npc.name,
                                            size = 28.dp,
                                            borderColor = stageAccent.copy(alpha = 0.6f),
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(1.dp),
                                        ) {
                                            Text(
                                                text = npc.name,
                                                color = Color(0xFFF3F6FC),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            GuMetaText(
                                                text = listOfNotNull(
                                                    npc.mood.takeIf { it.isNotBlank() },
                                                    tierLabel,
                                                ).joinToString(" · "),
                                                color = Color(0xFFA7B3CB),
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "♥ ${npc.affinity}",
                                            color = if (npc.affinity < 0) Color(0xFFFCA5A5) else Color(0xFFFFB4CE),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            QuickMiniStepChip(
                                                label = "-5",
                                                onClick = { onAdjustAffinity(npc.name, -5) },
                                            )
                                            QuickMiniStepChip(
                                                label = "+5",
                                                accent = stageAccent,
                                                onClick = { onAdjustAffinity(npc.name, 5) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // HP / MP / Gold GuInlineStepper Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuMetaText(
                    text = if (isZh) "角色状态 · 点击步进跳手账调节" else "Vitals · Tap to adjust in State tab",
                    color = Color(0xFFA7B3CB),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xB8182034))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    QuickVitalStepperRow(
                        label = "HP",
                        valueText = "${rp.state.hp}/${rp.state.maxHp}",
                        accentColor = Color(0xFFF87171),
                        onStep = onJumpToStateTab,
                    )
                    QuickVitalStepperRow(
                        label = "MP",
                        valueText = "${rp.state.mp}/${rp.state.maxMp}",
                        accentColor = Color(0xFF60A5FA),
                        onStep = onJumpToStateTab,
                    )
                    QuickVitalStepperRow(
                        label = if (isZh) "🪙 金币" else "🪙 Gold",
                        valueText = "${rp.state.gold}",
                        accentColor = stageAccent,
                        onStep = onJumpToStateTab,
                    )
                }
            }

            // Model Switcher Row
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(13.dp),
                color = Color(0xB8182034),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuModelAvatar(modelId = selectedModelId, size = 20.dp)
                    Text(
                        text = selectedModelId ?: strings.chat.selectModel,
                        color = Color(0xFFF3F6FC),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeModelFreeQuota) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80)),
                        )
                    }
                    GuMetaText(
                        text = if (isZh) "切换模型 ▸" else "Switch ▸",
                        color = stageAccent,
                    )
                }
            }

            // Single-line OOC Director Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xB8182034))
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = stageAccent,
                    modifier = Modifier.size(16.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (noteDraft.isEmpty()) {
                        Text(
                            text = if (isZh) "单行 OOC 导演备注（随时微调剧情走向）" else "Single-line OOC director note...",
                            color = Color(0xFF7D8AA4),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFFF3F6FC),
                            fontSize = 12.5.sp,
                        ),
                        cursorBrush = SolidColor(stageAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (noteDraft != rp.authorNote) {
                    Surface(
                        onClick = { onUpdateAuthorNote(noteDraft.trim()) },
                        shape = RoundedCornerShape(999.dp),
                        color = stageAccent.copy(alpha = 0.22f),
                    ) {
                        Text(
                            text = strings.common.save,
                            color = stageAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = Color(0x28CBD5E1))

            // Quick Actions Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StageGlassPill(
                    label = strings.rp.quickSaveBtn,
                    icon = Icons.Default.Save,
                    accent = false,
                    accentColor = stageAccent,
                    onClick = onQuickSave,
                )
                if (rp.theaterMode) {
                    StageGlassPill(
                        label = strings.rp.theaterLogBtn,
                        icon = Icons.Default.History,
                        accent = false,
                        accentColor = stageAccent,
                        onClick = onOpenHistory,
                    )
                    StageGlassPill(
                        label = strings.rp.theaterHideUi,
                        icon = Icons.Default.VisibilityOff,
                        accent = false,
                        accentColor = stageAccent,
                        onClick = onHideUi,
                    )
                }
                StageGlassPill(
                    label = if (isZh) "打开全部工坊 ↗" else "Full Studio ↗",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    accent = true,
                    accentColor = stageAccent,
                    onClick = onOpenFullStudio,
                )
            }
        }
    }
}

@Composable
private fun QuickMiniStepChip(
    label: String,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (accent != null) accent.copy(alpha = 0.18f) else Color(0x6627334E),
    ) {
        Text(
            text = label,
            color = accent ?: Color(0xFFDCE4F5),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun QuickVitalStepperRow(
    label: String,
    valueText: String,
    accentColor: Color,
    onStep: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        GuInlineStepper(
            valueText = valueText,
            onDecrement = onStep,
            onIncrement = onStep,
        )
    }
}
