package com.gugachat.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
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
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showModelSheet by remember { mutableStateOf(false) }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var memorySheetInitialTab by remember { mutableIntStateOf(0) }
    var showNovelWizard by remember { mutableStateOf(false) }
    var showNovelStudio by remember { mutableStateOf(false) }
    var showRpWizard by remember { mutableStateOf(false) }
    var showRpStudio by remember { mutableStateOf(false) }
    var showDiceDialog by remember { mutableStateOf(false) }

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
            onCreateFromPreset = viewModel::createRpSessionFromPreset,
            onImportCardUri = { uri -> viewModel.importSillyTavernCardUri(uri, ctx.contentResolver) },
        )
    }

    if (showDiceDialog) {
        RpDiceDialog(
            onDismiss = { showDiceDialog = false },
            onRoll = viewModel::rollDiceForDraft,
        )
    }

    if (showRpStudio && activeConv?.rp != null) {
        RpStudioSheet(
            rp = activeConv.rp,
            saveSlots = state.rpSaveSlots,
            onAdjustAffinity = viewModel::updateRpCharacterAffinity,
            onAddNpc = viewModel::addRpNpc,
            onCreateSaveSlot = viewModel::createRpSaveSlot,
            onLoadSaveSlot = viewModel::loadRpSaveSlotRewind,
            onUpdateConfig = viewModel::updateRpConfig,
            onExportCardJson = { uri -> viewModel.exportRpCardJsonToUri(uri, ctx.contentResolver) },
            onDismiss = { showRpStudio = false },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatSessionsDrawerContent(
                conversations = state.conversations,
                activeConversationId = activeConv?.id,
                onSelectConversation = viewModel::selectConversation,
                onNewChat = { viewModel.createNewChatSession() },
                onOpenNovelWizard = { showNovelWizard = true },
                onOpenRpWizard = { showRpWizard = true },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = strings.chat.sessions, tint = c.ink)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showModelSheet = true },
                    ) {
                        Text(
                            text = activeConv?.title ?: strings.chat.title,
                            color = c.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = state.selectedModelId ?: strings.chat.selectModel,
                                color = c.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
                            if (activeModel?.free_quota != null) {
                                GuBadge("限免", variant = GuBadgeVariant.EMERALD)
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.createNewChatSession() }) {
                        Icon(Icons.Default.Add, contentDescription = strings.chat.newChat, tint = c.ink)
                    }
                    IconButton(onClick = { memorySheetInitialTab = 0; showMemorySheet = true }) {
                        Icon(Icons.Default.Psychology, contentDescription = strings.chat.memoryTitle, tint = c.ink)
                    }
                    IconButton(onClick = { showParamsSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = strings.chat.paramsTitle, tint = c.ink)
                    }
                }
            }

            if (mode == ConversationMode.novel && activeConv?.novel != null) {
                NovelStatusHudBar(
                    novel = activeConv.novel,
                    chapters = state.novelChapters,
                    onOpenStudio = { showNovelStudio = true },
                    onEnterPureReading = { viewModel.setPureReadingMode(true) },
                )
            } else if (mode == ConversationMode.rp && activeConv?.rp != null) {
                RpStatusHudBar(
                    rp = activeConv.rp,
                    onQuickSave = { viewModel.createRpSaveSlot("快速存档") },
                    onOpenStudio = { showRpStudio = true },
                )
            }

            val activeGroupName = state.groups.firstOrNull { it.id == state.selectedGroupId }?.name.orEmpty()
            ChatMessageList(
                mode = mode,
                messages = state.messages,
                waterline = state.waterline,
                hasModelSelected = !state.selectedModelId.isNullOrBlank(),
                selectedModelLabel = state.selectedModelId.orEmpty(),
                selectedGroupLabel = activeGroupName,
                memoryEnabled = activeConv?.params?.enableAutoMemory ?: true,
                isStreaming = state.isStreaming,
                attachmentPathResolver = { id -> File(ctx.filesDir, "chat_attachments/$id.jpg") },
                onStarterPrompt = viewModel::applyStarterPrompt,
                onSelectNovelChoice = { viewModel.sendMessage(overrideText = it) },
                onSelectRpChoice = viewModel::triggerRpChoice,
                onRegenerate = viewModel::regenerateFromAssistant,
                onEditUserMessage = viewModel::editAndResendUserMessage,
                onBranchFromMessage = viewModel::branchFromMessage,
                onDeleteMessage = viewModel::deleteMessage,
                onSwitchVariant = viewModel::switchMessageVariant,
                onToast = { container.toastController.show(it) },
                modifier = Modifier.weight(1f),
            )

            ChatComposerBar(
                mode = mode,
                draftText = state.draftText,
                draftAttachments = state.draftAttachments,
                draftNovelKind = state.draftNovelKind,
                draftRpKind = state.draftRpKind,
                draftDiceRoll = state.draftDiceRoll,
                waterline = state.waterline,
                isStreaming = state.isStreaming,
                supportsVision = activeModel?.supports_vision ?: true,
                onDraftChange = viewModel::updateDraftText,
                onSelectNovelKind = viewModel::setDraftNovelKind,
                onSelectRpKind = viewModel::setDraftRpKind,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveAttachment = viewModel::removeDraftAttachment,
                onOpenDiceDialog = { showDiceDialog = true },
                onClearDiceRoll = viewModel::clearDraftDiceRoll,
                onOpenRecallInspector = { memorySheetInitialTab = 3; showMemorySheet = true },
                onSend = { viewModel.sendMessage() },
                onStop = viewModel::stopStreaming,
            )
        }
    }
}
