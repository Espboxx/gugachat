package com.gugachat.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppShellController
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.local.AttachmentFileStore
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.TimelineReconciler
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatWorkspaceScreen(
    container: AppContainer,
    viewModel: ChatViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val appSettings by container.settingsState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val outerTheme = LocalAppTheme.current
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val shell = LocalAppShellController.current

    LaunchedEffect(drawerState.isOpen) {
        shell.setChatDrawerOpen(drawerState.isOpen)
    }
    DisposableEffect(Unit) {
        onDispose {
            shell.setChatDrawerOpen(false)
            shell.setScrollHideBottomBar(false)
        }
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var memorySheetInitialTab by remember { mutableIntStateOf(0) }
    var showNovelWizard by remember { mutableStateOf(false) }
    var showNovelStudio by remember { mutableStateOf(false) }
    var novelStudioInitialTab by remember { mutableIntStateOf(0) }
    var showRpWizard by remember { mutableStateOf(false) }
    var showRpStudio by remember { mutableStateOf(false) }
    var rpStudioInitialTab by remember { mutableIntStateOf(0) }
    var showDiceDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeepSeekImport by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImageUris(uris)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFileUris(uris)
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            viewModel.addImageUris(listOf(uri))
        }
    }

    val launchCameraCapture: () -> Unit = {
        runCatching {
            val (_, uri) = container.attachmentStore.createCameraTempFile()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure { err ->
            pendingCameraUri = null
            container.toastController.show(err.message ?: strings.common.unknownError, isError = true)
        }
        Unit
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            container.toastController.show(strings.attachments.cameraPermDenied, isError = true)
        }
    }

    val attachmentPathResolver = remember(ctx) {
        { id: String -> File(ctx.filesDir, "chat_attachments/$id.jpg") }
    }

    val activeModel = state.models.firstOrNull { it.id == state.selectedModelId }
    val activeConv = state.activeConversation
    val effectiveModelId = activeConv?.model?.takeIf { it.isNotBlank() } ?: state.selectedModelId
    val nativeModelWindow = state.models.firstOrNull { it.id == effectiveModelId }?.context_window
        ?: effectiveModelId?.let { DirectProtocolCodec.inferChatModelMeta(it).context_window }
        ?: state.waterline.modelContextWindow
    val isRerankActive = container.retrievalCoordinator.isRerankEnabled(appSettings.retrievalSettings)
    val mode = activeConv?.mode ?: ConversationMode.chat
    val activeGroupName = state.groups.firstOrNull { it.id == state.selectedGroupId }?.name.orEmpty()
    val novelConfig = activeConv?.novel
    val rpConfig = activeConv?.rp
    val isFreeQuota = activeModel?.free_quota != null
    val enableReasoning = activeConv?.params?.enableReasoning ?: true
    val enableAutoMemory = activeConv?.params?.enableAutoMemory ?: true
    val supportsVision = activeModel?.supports_vision ?: true

    LaunchedEffect(activeConv?.id, mode) {
        shell.setScrollHideBottomBar(false)
    }

    if (state.pureReadingMode && novelConfig != null) {
        PureReadingOverlay(
            novel = novelConfig,
            chapters = state.novelChapters,
            messages = state.messages,
            paper = state.readerPaper,
            fontSizeSp = state.readerFontSizeSp,
            lineHeightMultiplier = state.readerLineHeight,
            useSerif = state.readerSerif,
            useIndent = state.readerIndent,
            onPaperChange = { viewModel.updateReaderPrefs(paper = it) },
            onFontSizeChange = { viewModel.updateReaderPrefs(fontSize = it) },
            onLineHeightChange = { viewModel.updateReaderPrefs(lineHeight = it) },
            onSerifToggle = { viewModel.updateReaderPrefs(serif = it) },
            onIndentToggle = { viewModel.updateReaderPrefs(indent = it) },
            onContinueWriting = { viewModel.continueLastAssistantMessage() },
            onExit = { viewModel.setPureReadingMode(false) },
        )
    }

    if (showModelSheet) {
        ModelGroupSheet(
            groups = state.groups,
            models = state.models,
            selectedGroupId = state.selectedGroupId,
            selectedModelId = state.selectedModelId,
            params = state.effectiveChatParams,
            waterline = state.waterline,
            onSelectGroup = viewModel::selectGroup,
            onSelectModel = viewModel::selectModel,
            onSelectCustomModel = viewModel::selectCustomModel,
            onUpdateContextSettings = viewModel::updateContextSettings,
            onDismiss = { showModelSheet = false },
        )
    }

    if (showNewSessionSheet) {
        NewSessionChooserSheet(
            onSelectChat = {
                showNewSessionSheet = false
                viewModel.createNewChatSession()
            },
            onSelectRp = {
                showNewSessionSheet = false
                showRpWizard = true
            },
            onSelectNovel = {
                showNewSessionSheet = false
                showNovelWizard = true
            },
            onDismiss = { showNewSessionSheet = false },
        )
    }

    if (showParamsSheet) {
        ChatParamsSheet(
            systemPrompt = activeConv?.systemPrompt.orEmpty(),
            params = state.effectiveChatParams,
            waterline = state.waterline,
            isCompacting = state.isCompacting,
            compactingProgressText = state.compactingProgressText,
            nativeModelWindow = nativeModelWindow,
            onSave = viewModel::updateConversationParams,
            onCompactNow = viewModel::triggerManualCompaction,
            onDismiss = { showParamsSheet = false },
        )
    }

    if (showMemorySheet) {
        ChatMemorySheet(
            userMemories = state.userMemories,
            ledgerItems = state.memoryLedger,
            episodes = state.memoryEpisodes,
            waterline = state.waterline,
            isExtracting = state.isExtractingMemory,
            initialTab = memorySheetInitialTab,
            mutedRecallKeys = state.mutedRecallKeys,
            vectorIndexProgress = state.vectorIndexProgress,
            activeRetrievalMode = state.activeRetrievalMode,
            isRerankActive = isRerankActive,
            onExtractMemories = viewModel::extractUserMemoriesNow,
            onAddMemory = viewModel::addUserMemory,
            onToggleMemoryPin = viewModel::toggleUserMemoryPin,
            onToggleMemoryEnabled = viewModel::toggleUserMemoryEnabled,
            onDeleteMemory = viewModel::deleteUserMemory,
            onAddLedger = viewModel::addLedgerItem,
            onToggleLedgerPin = viewModel::toggleLedgerPin,
            onDeleteLedger = viewModel::deleteLedgerItem,
            onToggleMuteRecall = viewModel::toggleMuteRecallKey,
            onTogglePinRecall = viewModel::togglePinRecallItem,
            onDisableRecall = viewModel::disableRecallItem,
            onReindexVectors = viewModel::reindexActiveConversationVectors,
            onOpenContextSettings = {
                showMemorySheet = false
                showParamsSheet = true
            },
            onDismiss = { showMemorySheet = false },
        )
    }

    if (showNovelWizard) {
        NovelWizardDialog(
            hasModelSelected = !state.selectedModelId.isNullOrBlank(),
            isCreatingWithAi = state.isCreatingNovelWithAi,
            onDismiss = { showNovelWizard = false },
            onCreate = { presetId, title, idea, pov, targetWords, playStyle, antiAiRules ->
                viewModel.createNovelSession(
                    presetId = presetId,
                    title = title,
                    customIdea = idea,
                    pov = pov,
                    targetWords = targetWords,
                    playStyle = playStyle,
                    antiAiRules = antiAiRules,
                )
            },
            onCreateWithAi = { presetId, title, idea, pov, targetWords, playStyle, antiAiRules, onDone ->
                viewModel.createNovelSessionWithAi(
                    presetId = presetId,
                    title = title,
                    customIdea = idea,
                    pov = pov,
                    targetWords = targetWords,
                    playStyle = playStyle,
                    antiAiRules = antiAiRules,
                    onCompleted = onDone,
                )
            },
        )
    }

    if (showNovelStudio && novelConfig != null) {
        NovelStudioSheet(
            novel = novelConfig,
            chapters = state.novelChapters,
            lore = state.novelLore,
            messages = state.messages,
            triggeredLoreIds = state.waterline.triggeredLoreIds.toSet(),
            initialTab = novelStudioInitialTab,
            draftOptions = state.threePathDrafts,
            outlineReply = state.outlineChatReply,
            polishResult = state.polishResult,
            isBusy = state.isGeneratingDrafts,
            isExtractingLore = state.isExtractingNovelLore,
            isGeneratingRecap = state.isGeneratingRecap,
            isSyncingChapters = state.isSyncingOutlineChapters,
            onSelectChapter = viewModel::selectActiveChapter,
            onAddChapter = { title, outline -> viewModel.addOrSelectNovelChapter(title, outline) },
            onUpdateChapter = viewModel::updateNovelChapter,
            onDeleteChapter = viewModel::deleteNovelChapter,
            onFinishChapterAndAdvance = viewModel::finishActiveChapterAndAdvance,
            onGenerateChapterRecap = viewModel::generateChapterRecap,
            onAddLore = viewModel::upsertNovelLore,
            onUpdateLore = viewModel::updateNovelLore,
            onToggleLoreEnabled = viewModel::toggleNovelLoreEnabled,
            onToggleLoreAlwaysActive = viewModel::toggleNovelLoreAlwaysActive,
            onDeleteLore = viewModel::deleteNovelLore,
            onImportLorebook = { uri -> viewModel.importLorebookOrPresetUri(uri, ctx.contentResolver) },
            onExtractLoreNow = viewModel::extractNovelLoreNow,
            onSaveForeshadowToLore = viewModel::saveForeshadowToLore,
            onUpdateConfig = viewModel::updateNovelConfig,
            onGenerate3Paths = viewModel::generateThreePathDrafts,
            onAdoptDraft = viewModel::adoptThreePathDraft,
            onOutlineChat = viewModel::runOutlineChat,
            onApplyOutlineToBook = viewModel::applyOutlineToBook,
            onSyncChaptersFromOutline = viewModel::syncChaptersFromOutline,
            onPolishLast = { inst, presetMode ->
                val lastProse = TimelineReconciler.activeMessages(state.messages).lastOrNull { it.role == "assistant" }?.content.orEmpty()
                viewModel.runPolish(lastProse, inst, presetMode)
            },
            onApplyPolishAsVariant = viewModel::applyPolishAsVariant,
            onFillComposerDraft = viewModel::updateDraftText,
            onExportTxt = { uri, includeLore -> viewModel.exportNovelToUri(uri, ctx.contentResolver, markdown = false, includeLore = includeLore) },
            onExportMd = { uri, includeLore -> viewModel.exportNovelToUri(uri, ctx.contentResolver, markdown = true, includeLore = includeLore) },
            onToast = { container.toastController.show(it) },
            onDismiss = { showNovelStudio = false },
        )
    }

    if (showRpWizard) {
        RpWizardDialog(
            onDismiss = { showRpWizard = false },
            onCreateFromPreset = { presetId, title, playStyle, playerName, playerPersona ->
                viewModel.createRpSessionFromPreset(
                    presetId = presetId,
                    customTitle = title,
                    playStyle = playStyle,
                    playerName = playerName,
                    playerPersona = playerPersona,
                )
            },
            onImportCardUri = { uri -> viewModel.importSillyTavernCardUri(uri, ctx.contentResolver) },
        )
    }

    if (showDiceDialog) {
        RpDiceDialog(
            rp = rpConfig,
            onDismiss = { showDiceDialog = false },
            onRoll = viewModel::rollDiceForDraft,
        )
    }

    if (showRpStudio && rpConfig != null) {
        RpStudioSheet(
            rp = rpConfig,
            lore = state.novelLore,
            triggeredLoreIds = state.waterline.triggeredLoreIds.toSet(),
            saveSlots = state.rpSaveSlots,
            initialTab = rpStudioInitialTab,
            isGeneratingArt = state.isGeneratingRpArt,
            artStatusLabel = state.rpArtStatusLabel,
            onAdjustAffinity = viewModel::updateRpCharacterAffinity,
            onUpsertNpc = viewModel::upsertRpNpc,
            onDeleteNpc = viewModel::deleteRpNpc,
            onBindNpcAvatar = { charId, uri -> viewModel.bindRpCharacterAvatar(charId, uri, ctx.contentResolver) },
            onBindNpcSprite = { charId, exprKey, uri ->
                viewModel.bindRpCharacterSprite(charId, exprKey, uri, ctx.contentResolver)
            },
            onClearNpcSprite = viewModel::clearRpCharacterSprite,
            onGenerateNpcSprite = { charId, exprKey, hint ->
                viewModel.generateRpCharacterSprite(charId, exprKey, hint)
            },
            onUpdateState = viewModel::updateRpState,
            onBindSceneImage = { uri -> viewModel.bindRpSceneImage(uri, ctx.contentResolver) },
            onClearSceneImage = viewModel::clearRpSceneImage,
            onGenerateSceneCg = { hint, saveToGallery ->
                viewModel.generateRpSceneCg(hint, saveToGallery)
            },
            onAddCgFromUri = { title, caption, uri ->
                viewModel.addRpCgFromUri(title, caption, uri, ctx.contentResolver)
            },
            onSaveCurrentSceneAsCg = viewModel::saveCurrentSceneAsCg,
            onApplyCgAsScene = viewModel::applyCgAsScene,
            onDeleteCg = viewModel::deleteRpCg,
            onUpdateVisualTheme = viewModel::updateRpVisualTheme,
            onResetVisualTheme = viewModel::resetRpVisualTheme,
            onUpsertRegexScript = viewModel::upsertRpRegexScript,
            onToggleRegexScript = viewModel::toggleRpRegexScript,
            onDeleteRegexScript = viewModel::deleteRpRegexScript,
            onApplyRegexPreset = viewModel::applyRpRegexPreset,
            onApplyStatusWidgetTemplate = viewModel::applyRpStatusWidgetTemplate,
            onAddLore = viewModel::upsertNovelLore,
            onUpdateLore = viewModel::updateNovelLore,
            onToggleLoreEnabled = viewModel::toggleNovelLoreEnabled,
            onToggleLoreAlwaysActive = viewModel::toggleNovelLoreAlwaysActive,
            onDeleteLore = viewModel::deleteNovelLore,
            onImportLorebookOrPreset = { uri -> viewModel.importLorebookOrPresetUri(uri, ctx.contentResolver) },
            onImportRegexJson = { uri -> viewModel.importRpRegexJsonUri(uri, ctx.contentResolver) },
            onCreateSaveSlot = { name, note -> viewModel.createRpSaveSlot(name, note) },
            onOverwriteSaveSlot = viewModel::overwriteRpSaveSlot,
            onDeleteSaveSlot = viewModel::deleteRpSaveSlot,
            onLoadSaveSlot = viewModel::loadRpSaveSlotRewind,
            onBranchSaveSlot = viewModel::branchFromRpSaveSlot,
            onUpdateConfig = viewModel::updateRpConfig,
            onSetPlayStyle = viewModel::setRpPlayStyle,
            onExportCardJson = { uri -> viewModel.exportRpCardJsonToUri(uri, ctx.contentResolver) },
            onExportCardPng = { uri -> viewModel.exportRpCardPngToUri(uri, ctx.contentResolver) },
            onDismiss = { showRpStudio = false },
        )
    }

    if (showDeepSeekImport) {
        ExternalImportSheet(
            container = container,
            onImported = { firstConvId ->
                if (!firstConvId.isNullOrBlank()) {
                    viewModel.selectConversation(firstConvId)
                }
            },
            onDismiss = { showDeepSeekImport = false },
            selectedGroupId = state.selectedGroupId,
            selectedModelId = state.selectedModelId,
        )
    }

    val moreMenu: @Composable () -> Unit = {
        CompositionLocalProvider(LocalAppTheme provides outerTheme) {
            val mc = outerTheme.colors
            GuPopupMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                if (mode == ConversationMode.rp && rpConfig != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = mc.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.chat.newChat,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            showNewSessionSheet = true
                        },
                    )
                    HorizontalDivider(
                        thickness = outerTheme.borderWidth,
                        color = mc.border.copy(alpha = 0.75f),
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = mc.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            strings.chat.memoryTitle,
                            color = mc.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    onClick = {
                        showMoreMenu = false
                        memorySheetInitialTab = 0
                        showMemorySheet = true
                    },
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = mc.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            strings.chat.paramsTitle,
                            color = mc.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    onClick = {
                        showMoreMenu = false
                        showParamsSheet = true
                    },
                )
                val overflowStatusColor = when {
                    state.waterline.hardOverflow -> mc.danger
                    state.waterline.isSliding -> mc.warning
                    else -> mc.muted
                }
                val overflowSentShort = FileContextBudgeter.formatShortTokens(state.waterline.sentTokens)
                val overflowLimitShort = FileContextBudgeter.formatShortTokens(state.waterline.effectiveLimitTokens)
                val overflowRecallSuffix = if (state.waterline.retrievedMemoryCount > 0) {
                    strings.contextUi.overflowSubtitleRecallSuffix.fmt("count" to state.waterline.retrievedMemoryCount)
                } else {
                    ""
                }
                val overflowSubtitle = strings.contextUi.overflowSubtitleSent.fmt(
                    "pct" to state.waterline.sentPercentage,
                    "sent" to overflowSentShort,
                    "limit" to overflowLimitShort,
                ) + overflowRecallSuffix

                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = overflowStatusColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                strings.chat.recallTitle,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = overflowSubtitle,
                                color = overflowStatusColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    onClick = {
                        showMoreMenu = false
                        memorySheetInitialTab = 3
                        showMemorySheet = true
                    },
                )
                if (mode == ConversationMode.novel && novelConfig != null) {
                    HorizontalDivider(
                        thickness = outerTheme.borderWidth,
                        color = mc.border.copy(alpha = 0.75f),
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                tint = mc.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.novel.studioTitle,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            novelStudioInitialTab = 0
                            showNovelStudio = true
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = mc.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.novel.pureReading,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            viewModel.setPureReadingMode(true)
                        },
                    )
                } else if (mode == ConversationMode.rp && rpConfig != null) {
                    HorizontalDivider(
                        thickness = outerTheme.borderWidth,
                        color = mc.border.copy(alpha = 0.75f),
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = if (rpConfig.theaterMode) Icons.Default.Forum else Icons.Default.Theaters,
                                contentDescription = null,
                                tint = mc.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                text = if (rpConfig.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            viewModel.toggleRpTheaterMode()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Default.Casino,
                                contentDescription = null,
                                tint = mc.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.rp.studioBtn,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            rpStudioInitialTab = 0
                            showRpStudio = true
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = mc.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.rp.frontendTab,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            rpStudioInitialTab = 3
                            showRpStudio = true
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                tint = mc.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                strings.rp.quickSaveBtn,
                                color = mc.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            viewModel.createRpSaveSlot(strings.rp.quickSaveBtn)
                        },
                    )
                }
            }
        }
    }

    state.pendingScannedPdf?.let { scanned ->
        val renderPages = minOf(scanned.pageCount.coerceAtLeast(1), AttachmentFileStore.MAX_SCANNED_PDF_PAGES)
        GuConfirmDialog(
            title = strings.attachments.scannedPdfTitle,
            message = strings.attachments.scannedPdfMsg.fmt(
                "name" to scanned.fileName,
                "pages" to scanned.pageCount,
                "renderPages" to renderPages,
            ),
            confirmText = strings.attachments.scannedPdfConvertBtn,
            cancelText = strings.common.cancel,
            isDestructive = false,
            onConfirm = { viewModel.convertPendingScannedPdfToImages(renderPages) },
            onDismiss = viewModel::dismissPendingScannedPdf,
        )
    }

    val onPickImage = {
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onTakePhoto = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val onPickFile = {
        filePicker.launch(arrayOf("*/*"))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatSessionsDrawerContent(
                conversations = state.conversations,
                activeConversationId = activeConv?.id,
                onSelectConversation = viewModel::selectConversation,
                onNewSession = { showNewSessionSheet = true },
                onImportDeepSeek = { showDeepSeekImport = true },
                onTogglePin = viewModel::togglePinConversation,
                onRenameConversation = viewModel::renameConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onSwitchMainTab = shell.selectMainTab,
            )
        },
    ) {
        when {
            mode == ConversationMode.novel && novelConfig != null -> {
                NovelWorkspaceScreen(
                    novel = novelConfig,
                    chapters = state.novelChapters,
                    messages = state.messages,
                    waterline = state.waterline,
                    readerPaper = state.readerPaper,
                    selectedModelId = state.selectedModelId,
                    activeModelFreeQuota = isFreeQuota,
                    selectedGroupLabel = activeGroupName,
                    memoryEnabled = enableAutoMemory,
                    enableReasoning = enableReasoning,
                    supportsVision = supportsVision,
                    draftText = state.draftText,
                    draftAttachments = state.draftAttachments,
                    attachmentsById = state.attachmentsById,
                    isImportingAttachment = state.isImportingAttachment,
                    draftNovelKind = state.draftNovelKind,
                    isStreaming = state.isStreaming,
                    streamingStatusHint = state.streamingStatusHint,
                    isRefreshingChoices = state.isRefreshingChoices,
                    attachmentPathResolver = attachmentPathResolver,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSheet = { showModelSheet = true },
                    onNewSession = { showNewSessionSheet = true },
                    onOpenMoreMenu = { showMoreMenu = true },
                    moreMenuContent = moreMenu,
                    onSelectPaper = { viewModel.updateReaderPrefs(paper = it) },
                    onOpenNovelStudio = { tab ->
                        novelStudioInitialTab = tab
                        showNovelStudio = true
                    },
                    onEnterPureReading = { viewModel.setPureReadingMode(true) },
                    onFinishChapterAndAdvance = viewModel::finishActiveChapterAndAdvance,
                    onUpdateAuthorNote = { note -> viewModel.updateNovelConfig { it.copy(authorNote = note) } },
                    onStarterPrompt = viewModel::applyStarterPrompt,
                    onSelectNovelChoice = { viewModel.sendMessage(overrideText = it) },
                    onRefreshNovelChoices = viewModel::refreshNovelChoices,
                    onContinueNovel = { viewModel.continueLastAssistantMessage() },
                    onRegenerate = viewModel::regenerateFromAssistant,
                    onEditUserMessage = viewModel::editAndResendUserMessage,
                    onEditAssistantMessage = viewModel::editAssistantMessage,
                    onBranchFromMessage = viewModel::branchFromMessage,
                    onDeleteMessage = viewModel::deleteMessage,
                    onSwitchVariant = viewModel::switchMessageVariant,
                    onDraftChange = viewModel::updateDraftText,
                    onSelectNovelKind = viewModel::setDraftNovelKind,
                    onToggleReasoning = viewModel::toggleActiveReasoning,
                    onToggleMemory = viewModel::toggleActiveMemory,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickFile = onPickFile,
                    onRemoveAttachment = viewModel::removeDraftAttachment,
                    onUpdateAttachmentInjectMode = viewModel::updateDraftAttachmentInjectMode,
                    onReadAttachmentText = viewModel::readAttachmentText,
                    onOpenRecallInspector = {
                        memorySheetInitialTab = 3
                        showMemorySheet = true
                    },
                    onOpenContextSettings = { showParamsSheet = true },
                    onSend = { viewModel.sendMessage() },
                    onStop = viewModel::stopStreaming,
                    onToast = { container.toastController.show(it) },
                )
            }

            mode == ConversationMode.rp && rpConfig != null -> {
                RpWorkspaceScreen(
                    rp = rpConfig,
                    messages = state.messages,
                    waterline = state.waterline,
                    selectedModelId = state.selectedModelId,
                    activeModelFreeQuota = isFreeQuota,
                    selectedGroupLabel = activeGroupName,
                    memoryEnabled = enableAutoMemory,
                    enableReasoning = enableReasoning,
                    supportsVision = supportsVision,
                    draftText = state.draftText,
                    draftAttachments = state.draftAttachments,
                    attachmentsById = state.attachmentsById,
                    isImportingAttachment = state.isImportingAttachment,
                    draftRpKind = state.draftRpKind,
                    draftDiceRoll = state.draftDiceRoll,
                    isStreaming = state.isStreaming,
                    streamingStatusHint = state.streamingStatusHint,
                    isRefreshingChoices = state.isRefreshingChoices,
                    isImpersonating = state.isImpersonating,
                    isGeneratingArt = state.isGeneratingRpArt,
                    artStatusLabel = state.rpArtStatusLabel,
                    attachmentPathResolver = attachmentPathResolver,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSheet = { showModelSheet = true },
                    onOpenMoreMenu = { showMoreMenu = true },
                    moreMenuContent = moreMenu,
                    onToggleTheaterMode = viewModel::toggleRpTheaterMode,
                    onSetTheaterMode = viewModel::setRpTheaterMode,
                    onOpenStudio = {
                        rpStudioInitialTab = 0
                        showRpStudio = true
                    },
                    onOpenFrontendEditor = {
                        rpStudioInitialTab = 3
                        showRpStudio = true
                    },
                    onQuickSave = { viewModel.createRpSaveSlot(strings.rp.quickSaveBtn) },
                    onUpdateAuthorNote = { note -> viewModel.updateRpConfig { it.copy(authorNote = note) } },
                    onStarterPrompt = viewModel::applyStarterPrompt,
                    onSelectChoice = { choice, bypassLock -> viewModel.triggerRpChoice(choice, bypassLock) },
                    onRefreshChoices = viewModel::refreshRpChoices,
                    onContinue = { viewModel.continueLastAssistantMessage() },
                    onRegenerate = viewModel::regenerateFromAssistant,
                    onEditUserMessage = viewModel::editAndResendUserMessage,
                    onEditAssistantMessage = viewModel::editAssistantMessage,
                    onBranchFromMessage = viewModel::branchFromMessage,
                    onDeleteMessage = viewModel::deleteMessage,
                    onSwitchVariant = viewModel::switchMessageVariant,
                    onFrontendAction = viewModel::handleRpFrontendAction,
                    onFrontendFillInput = viewModel::handleRpFrontendFillInput,
                    onFrontendSetFlag = viewModel::handleRpFrontendSetFlag,
                    onFrontendAdjustAffinity = viewModel::handleRpFrontendAdjustAffinity,
                    onDraftChange = viewModel::updateDraftText,
                    onSelectRpKind = viewModel::setDraftRpKind,
                    onToggleReasoning = viewModel::toggleActiveReasoning,
                    onToggleMemory = viewModel::toggleActiveMemory,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickFile = onPickFile,
                    onRemoveAttachment = viewModel::removeDraftAttachment,
                    onUpdateAttachmentInjectMode = viewModel::updateDraftAttachmentInjectMode,
                    onReadAttachmentText = viewModel::readAttachmentText,
                    onOpenDiceDialog = { showDiceDialog = true },
                    onClearDiceRoll = viewModel::clearDraftDiceRoll,
                    onOpenRecallInspector = {
                        memorySheetInitialTab = 3
                        showMemorySheet = true
                    },
                    onOpenContextSettings = { showParamsSheet = true },
                    onImpersonate = viewModel::impersonatePlayerDraft,
                    onSend = { viewModel.sendMessage() },
                    onStop = viewModel::stopStreaming,
                    onToast = { container.toastController.show(it) },
                )
            }

            else -> {
                StandardChatWorkspace(
                    messages = state.messages,
                    waterline = state.waterline,
                    selectedModelId = state.selectedModelId,
                    activeModelFreeQuota = isFreeQuota,
                    selectedGroupLabel = activeGroupName,
                    memoryEnabled = enableAutoMemory,
                    enableReasoning = enableReasoning,
                    supportsVision = supportsVision,
                    draftText = state.draftText,
                    draftAttachments = state.draftAttachments,
                    attachmentsById = state.attachmentsById,
                    isImportingAttachment = state.isImportingAttachment,
                    isStreaming = state.isStreaming,
                    streamingStatusHint = state.streamingStatusHint,
                    attachmentPathResolver = attachmentPathResolver,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSheet = { showModelSheet = true },
                    onNewSession = { showNewSessionSheet = true },
                    onOpenMoreMenu = { showMoreMenu = true },
                    moreMenuContent = moreMenu,
                    onStarterPrompt = viewModel::applyStarterPrompt,
                    onRegenerate = viewModel::regenerateFromAssistant,
                    onEditUserMessage = viewModel::editAndResendUserMessage,
                    onEditAssistantMessage = viewModel::editAssistantMessage,
                    onBranchFromMessage = viewModel::branchFromMessage,
                    onDeleteMessage = viewModel::deleteMessage,
                    onSwitchVariant = viewModel::switchMessageVariant,
                    onDraftChange = viewModel::updateDraftText,
                    onToggleReasoning = viewModel::toggleActiveReasoning,
                    onToggleMemory = viewModel::toggleActiveMemory,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickFile = onPickFile,
                    onRemoveAttachment = viewModel::removeDraftAttachment,
                    onUpdateAttachmentInjectMode = viewModel::updateDraftAttachmentInjectMode,
                    onReadAttachmentText = viewModel::readAttachmentText,
                    onOpenRecallInspector = {
                        memorySheetInitialTab = 3
                        showMemorySheet = true
                    },
                    onOpenContextSettings = { showParamsSheet = true },
                    onSend = { viewModel.sendMessage() },
                    onStop = viewModel::stopStreaming,
                    onToast = { container.toastController.show(it) },
                    conversationTitle = activeConv?.title.orEmpty(),
                    onScrollHideBottomBarChanged = shell.setScrollHideBottomBar,
                    onOpenRpWizard = { showRpWizard = true },
                    onOpenNovelWizard = { showNovelWizard = true },
                )
            }
        }
    }
}
