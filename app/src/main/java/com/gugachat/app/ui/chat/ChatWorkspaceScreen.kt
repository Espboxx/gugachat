package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.TimelineReconciler
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatWorkspaceScreen(
    container: AppContainer,
    viewModel: ChatViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showModelSheet by remember { mutableStateOf(false) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var memorySheetInitialTab by remember { mutableIntStateOf(0) }
    var showNovelWizard by remember { mutableStateOf(false) }
    var showNovelStudio by remember { mutableStateOf(false) }
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
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.addAttachmentUri(uri)
    }

    val activeModel = state.models.firstOrNull { it.id == state.selectedModelId }
    val activeConv = state.activeConversation
    val mode = activeConv?.mode ?: ConversationMode.chat

    if (state.pureReadingMode && activeConv?.novel != null) {
        PureReadingOverlay(
            novel = activeConv.novel,
            chapters = state.novelChapters,
            messages = state.messages,
            paper = state.readerPaper,
            fontSizeSp = state.readerFontSizeSp,
            useSerif = state.readerSerif,
            onPaperChange = { viewModel.updateReaderPrefs(paper = it) },
            onFontSizeChange = { viewModel.updateReaderPrefs(fontSize = it) },
            onSerifToggle = { viewModel.updateReaderPrefs(serif = it) },
            onExit = { viewModel.setPureReadingMode(false) },
        )
    }

    if (showModelSheet) {
        ModelGroupSheet(
            groups = state.groups,
            models = state.models,
            selectedGroupId = state.selectedGroupId,
            selectedModelId = state.selectedModelId,
            onSelectGroup = viewModel::selectGroup,
            onSelectModel = viewModel::selectModel,
            onSelectCustomModel = viewModel::selectCustomModel,
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
            params = activeConv?.params ?: ChatParams(),
            waterline = state.waterline,
            isCompacting = state.isCompacting,
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
            onExtractMemories = viewModel::extractUserMemoriesNow,
            onAddMemory = viewModel::addUserMemory,
            onToggleMemoryPin = viewModel::toggleUserMemoryPin,
            onToggleMemoryEnabled = viewModel::toggleUserMemoryEnabled,
            onDeleteMemory = viewModel::deleteUserMemory,
            onAddLedger = viewModel::addLedgerItem,
            onToggleLedgerPin = viewModel::toggleLedgerPin,
            onDeleteLedger = viewModel::deleteLedgerItem,
            onDismiss = { showMemorySheet = false },
        )
    }

    if (showNovelWizard) {
        NovelWizardDialog(
            onDismiss = { showNovelWizard = false },
            onCreate = viewModel::createNovelSession,
        )
    }

    if (showNovelStudio && activeConv?.novel != null) {
        NovelStudioSheet(
            novel = activeConv.novel,
            chapters = state.novelChapters,
            lore = state.novelLore,
            messages = state.messages,
            draftOptions = state.threePathDrafts,
            outlineReply = state.outlineChatReply,
            polishResult = state.polishResult,
            isBusy = state.isGeneratingDrafts,
            onSelectChapter = viewModel::selectActiveChapter,
            onAddChapter = viewModel::addOrSelectNovelChapter,
            onAddLore = viewModel::upsertNovelLore,
            onDeleteLore = viewModel::deleteNovelLore,
            onUpdateConfig = viewModel::updateNovelConfig,
            onGenerate3Paths = viewModel::generateThreePathDrafts,
            onAdoptDraft = { text -> viewModel.updateDraftText(text) },
            onOutlineChat = viewModel::runOutlineChat,
            onPolishLast = { inst ->
                val lastProse = TimelineReconciler.activeMessages(state.messages).lastOrNull { it.role == "assistant" }?.content.orEmpty()
                viewModel.runPolish(lastProse, inst)
            },
            onExportTxt = { uri -> viewModel.exportNovelToUri(uri, ctx.contentResolver, markdown = false) },
            onExportMd = { uri -> viewModel.exportNovelToUri(uri, ctx.contentResolver, markdown = true) },
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
            rp = activeConv?.rp,
            onDismiss = { showDiceDialog = false },
            onRoll = viewModel::rollDiceForDraft,
        )
    }

    if (showRpStudio && activeConv?.rp != null) {
        RpStudioSheet(
            rp = activeConv.rp,
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
        )
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
                onDeleteConversation = viewModel::deleteConversation,
                onCloseDrawer = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.page),
        ) {
            GuAwningHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 6.dp),
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = strings.chat.sessions,
                            tint = c.ink,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val hasModel = !state.selectedModelId.isNullOrBlank()
                        val dotColor = when {
                            activeModel?.free_quota != null -> c.success
                            hasModel -> c.primary
                            else -> c.muted.copy(alpha = 0.55f)
                        }
                        Surface(
                            onClick = { showModelSheet = true },
                            shape = RoundedCornerShape(999.dp),
                            color = if (hasModel) c.surfaceSoft else c.surface,
                            border = BorderStroke(
                                width = theme.borderWidth,
                                color = if (hasModel) c.border else c.outline.copy(alpha = 0.8f),
                            ),
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )
                                Text(
                                    text = state.selectedModelId ?: strings.chat.selectModel,
                                    color = if (hasModel) c.ink else c.muted,
                                    fontSize = 13.sp,
                                    fontWeight = if (hasModel) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = c.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { showNewSessionSheet = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = strings.chat.newChat,
                                tint = c.ink,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = strings.chat.moreActions,
                                    tint = c.ink,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            GuPopupMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Psychology,
                                            contentDescription = null,
                                            tint = c.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    text = {
                                        Text(
                                            strings.chat.memoryTitle,
                                            color = c.ink,
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
                                            tint = c.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    text = {
                                        Text(
                                            strings.chat.paramsTitle,
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showParamsSheet = true
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Bookmarks,
                                            contentDescription = null,
                                            tint = if (state.waterline.exceedsModelWindow) c.danger else c.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                strings.chat.recallTitle,
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = "${state.waterline.percentage}% · ${state.waterline.usedTokens / 1000}K / ${state.waterline.effectiveLimitTokens / 1000}K",
                                                color = if (state.waterline.exceedsModelWindow) c.danger else c.muted,
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
                                HorizontalDivider(
                                    thickness = theme.borderWidth,
                                    color = c.border.copy(alpha = 0.75f),
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FolderZip,
                                            contentDescription = null,
                                            tint = c.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    text = {
                                        Text(
                                            strings.chat.importExternalTitle,
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeepSeekImport = true
                                    },
                                )
                                if (mode == ConversationMode.novel && activeConv?.novel != null) {
                                    HorizontalDivider(
                                        thickness = theme.borderWidth,
                                        color = c.border.copy(alpha = 0.75f),
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Book,
                                                contentDescription = null,
                                                tint = c.accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                strings.novel.studioTitle,
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showNovelStudio = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = c.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                strings.novel.pureReading,
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setPureReadingMode(true)
                                        },
                                    )
                                } else if (mode == ConversationMode.rp && activeConv?.rp != null) {
                                    HorizontalDivider(
                                        thickness = theme.borderWidth,
                                        color = c.border.copy(alpha = 0.75f),
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (activeConv.rp.theaterMode) Icons.Default.Forum else Icons.Default.Theaters,
                                                contentDescription = null,
                                                tint = c.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = if (activeConv.rp.theaterMode) strings.rp.chatModeBtn else strings.rp.theaterModeBtn,
                                                color = c.ink,
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
                                                tint = c.accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                strings.rp.studioBtn,
                                                color = c.ink,
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
                                                tint = c.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                strings.rp.frontendTab,
                                                color = c.ink,
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
                                                tint = c.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        text = {
                                            Text(
                                                strings.rp.quickSaveBtn,
                                                color = c.ink,
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
                }
            }

            if (mode == ConversationMode.novel && activeConv?.novel != null && activeConv.novel.enableStatusHud) {
                NovelStatusHudBar(
                    novel = activeConv.novel,
                    chapters = state.novelChapters,
                    onOpenStudio = { showNovelStudio = true },
                    onEnterPureReading = { viewModel.setPureReadingMode(true) },
                )
            } else if (mode == ConversationMode.rp && activeConv?.rp != null && !activeConv.rp.theaterMode) {
                if (activeConv.rp.enableStateHud) {
                    RpStatusHudBar(
                        rp = activeConv.rp,
                        triggeredLoreCount = state.waterline.triggeredLoreIds.size,
                        onToggleTheaterMode = viewModel::toggleRpTheaterMode,
                        onQuickSave = { viewModel.createRpSaveSlot(strings.rp.quickSaveBtn) },
                        onOpenStudio = {
                            rpStudioInitialTab = 0
                            showRpStudio = true
                        },
                        onUpdateAuthorNote = { note -> viewModel.updateRpConfig { it.copy(authorNote = note) } },
                    )
                }
                if (state.isGeneratingRpArt) {
                    Surface(
                        color = c.primarySoft,
                        border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.3f)),
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
                                color = c.primary,
                            )
                            Text(
                                text = "✨ ${state.rpArtStatusLabel.ifBlank { strings.rp.aiGeneratingArt }}",
                                color = c.primarySoftInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val rpTheme = activeConv.rp.visualTheme
                if (rpTheme.showCustomStatusWidget && rpTheme.customStatusHtml.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RpHtmlCardSandbox(
                            htmlFragment = rpTheme.customStatusHtml,
                            rp = activeConv.rp,
                            customCss = rpTheme.customCss,
                            minHeightDp = 52,
                            maxHeightDp = 220,
                            onFrontendAction = viewModel::handleRpFrontendAction,
                            onFrontendFillInput = viewModel::handleRpFrontendFillInput,
                            onFrontendSetFlag = viewModel::handleRpFrontendSetFlag,
                            onFrontendAdjustAffinity = viewModel::handleRpFrontendAdjustAffinity,
                        )
                    }
                }
            }

            val activeGroupName = state.groups.firstOrNull { it.id == state.selectedGroupId }?.name.orEmpty()
            if (mode == ConversationMode.rp && activeConv?.rp != null && activeConv.rp.theaterMode) {
                RpTheaterStageView(
                    rp = activeConv.rp,
                    messages = state.messages,
                    isStreaming = state.isStreaming,
                    streamingStatusHint = state.streamingStatusHint,
                    isRefreshingChoices = state.isRefreshingChoices,
                    hasModelSelected = !state.selectedModelId.isNullOrBlank(),
                    isGeneratingArt = state.isGeneratingRpArt,
                    artStatusLabel = state.rpArtStatusLabel,
                    onOpenModelSheet = { showModelSheet = true },
                    onExitTheater = { viewModel.setRpTheaterMode(false) },
                    onOpenStudio = {
                        rpStudioInitialTab = 0
                        showRpStudio = true
                    },
                    onOpenFrontendEditor = {
                        rpStudioInitialTab = 3
                        showRpStudio = true
                    },
                    onQuickSave = { viewModel.createRpSaveSlot(strings.rp.quickSaveBtn) },
                    onStarterPrompt = viewModel::applyStarterPrompt,
                    onSelectChoice = { choice, bypassLock -> viewModel.triggerRpChoice(choice, bypassLock) },
                    onRefreshChoices = viewModel::refreshRpChoices,
                    onContinue = { viewModel.continueLastAssistantMessage() },
                    onRegenerate = viewModel::regenerateFromAssistant,
                    onEditAssistantMessage = viewModel::editAssistantMessage,
                    onSwitchVariant = viewModel::switchMessageVariant,
                    onFrontendAction = viewModel::handleRpFrontendAction,
                    onFrontendFillInput = viewModel::handleRpFrontendFillInput,
                    onFrontendSetFlag = viewModel::handleRpFrontendSetFlag,
                    onFrontendAdjustAffinity = viewModel::handleRpFrontendAdjustAffinity,
                    onToast = { container.toastController.show(it) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                ChatMessageList(
                    mode = mode,
                    messages = state.messages,
                    waterline = state.waterline,
                    hasModelSelected = !state.selectedModelId.isNullOrBlank(),
                    selectedModelLabel = state.selectedModelId.orEmpty(),
                    selectedGroupLabel = activeGroupName,
                    memoryEnabled = activeConv?.params?.enableAutoMemory ?: true,
                    rpConfig = activeConv?.rp,
                    novelConfig = activeConv?.novel,
                    isStreaming = state.isStreaming,
                    streamingStatusHint = state.streamingStatusHint,
                    isRefreshingChoices = state.isRefreshingChoices,
                    attachmentPathResolver = { id -> File(ctx.filesDir, "chat_attachments/$id.jpg") },
                    onOpenModelSheet = { showModelSheet = true },
                    onStarterPrompt = viewModel::applyStarterPrompt,
                    onSelectNovelChoice = { viewModel.sendMessage(overrideText = it) },
                    onSelectRpChoice = { choice, bypassLock -> viewModel.triggerRpChoice(choice, bypassLock) },
                    onRefreshRpChoices = viewModel::refreshRpChoices,
                    onContinueRp = { viewModel.continueLastAssistantMessage() },
                    onRegenerate = viewModel::regenerateFromAssistant,
                    onEditUserMessage = viewModel::editAndResendUserMessage,
                    onEditAssistantMessage = viewModel::editAssistantMessage,
                    onBranchFromMessage = viewModel::branchFromMessage,
                    onDeleteMessage = viewModel::deleteMessage,
                    onSwitchVariant = viewModel::switchMessageVariant,
                    onToast = { container.toastController.show(it) },
                    onFrontendAction = viewModel::handleRpFrontendAction,
                    onFrontendFillInput = viewModel::handleRpFrontendFillInput,
                    onFrontendSetFlag = viewModel::handleRpFrontendSetFlag,
                    onFrontendAdjustAffinity = viewModel::handleRpFrontendAdjustAffinity,
                    modifier = Modifier.weight(1f),
                )
            }

            ChatComposerBar(
                mode = mode,
                draftText = state.draftText,
                draftAttachments = state.draftAttachments,
                draftNovelKind = state.draftNovelKind,
                draftRpKind = state.draftRpKind,
                draftDiceRoll = state.draftDiceRoll,
                waterline = state.waterline,
                enableReasoning = activeConv?.params?.enableReasoning ?: true,
                enableAutoMemory = activeConv?.params?.enableAutoMemory ?: true,
                isStreaming = state.isStreaming,
                supportsVision = activeModel?.supports_vision ?: true,
                onDraftChange = viewModel::updateDraftText,
                onSelectNovelKind = viewModel::setDraftNovelKind,
                onSelectRpKind = viewModel::setDraftRpKind,
                onToggleReasoning = viewModel::toggleActiveReasoning,
                onToggleMemory = viewModel::toggleActiveMemory,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveAttachment = viewModel::removeDraftAttachment,
                onOpenDiceDialog = { showDiceDialog = true },
                onClearDiceRoll = viewModel::clearDraftDiceRoll,
                onOpenRecallInspector = { memorySheetInitialTab = 3; showMemorySheet = true },
                onSend = { viewModel.sendMessage() },
                onStop = viewModel::stopStreaming,
                isImpersonating = state.isImpersonating,
                onImpersonate = viewModel::impersonatePlayerDraft,
                onContinueRp = { viewModel.continueLastAssistantMessage() },
            )
        }
    }
}
