package com.gugachat.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.data.chat.AssetImportResult
import com.gugachat.app.data.api.ExternalImportService
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.ExternalImportBundle
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.TavernAssetCodec
import com.gugachat.app.domain.chat.UuSaveCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ImportPlatformTab {
    DEEPSEEK,
    TAVERN,
    CHENGGUANG,
    CHATBOX,
    OTHER,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExternalImportSheet(
    container: AppContainer,
    onImported: (firstConversationId: String?) -> Unit,
    onDismiss: () -> Unit,
    selectedGroupId: Long? = null,
    selectedModelId: String? = null,
    onImportTavernAssetBytes: (suspend (ByteArray, String?) -> Boolean)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activeTab by remember { mutableStateOf(ImportPlatformTab.DEEPSEEK) }
    var deepSeekSubTab by remember { mutableIntStateOf(0) } // 0 = Import ZIP, 1 = Download Link
    var chengguangSubTab by remember { mutableIntStateOf(0) } // 0 = File, 1 = Paste Script
    var linkInput by remember { mutableStateOf("") }
    var pastedScript by remember { mutableStateOf("") }

    var isBusy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var linkNeedsBrowser by remember { mutableStateOf(false) }

    var parsedBundle by remember { mutableStateOf<ExternalImportBundle?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importSyncedProviders by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var targetModeOverride by remember { mutableStateOf<ConversationMode?>(null) }

    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val targetModes = remember { listOf(null, ConversationMode.chat, ConversationMode.rp, ConversationMode.novel) }
    val targetModeLabels = remember(strings) {
        listOf(
            strings.chat.importTargetAuto,
            strings.novel.filterChat,
            strings.rp.filterRp,
            strings.novel.filterNovel,
        )
    }

    fun hintForTab(tab: ImportPlatformTab): ExternalImportSource = when (tab) {
        ImportPlatformTab.DEEPSEEK -> ExternalImportSource.DEEPSEEK
        ImportPlatformTab.TAVERN -> ExternalImportSource.SILLYTAVERN
        ImportPlatformTab.CHENGGUANG -> ExternalImportSource.CHENGGUANG
        ImportPlatformTab.CHATBOX -> ExternalImportSource.CHATBOX
        ImportPlatformTab.OTHER -> ExternalImportSource.GENERIC
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val hint = hintForTab(activeTab)
        scope.launch {
            isBusy = true
            busyLabel = strings.chat.importDeepSeekParsing
            errorMsg = null
            linkNeedsBrowser = false
            try {
                val (bytes, displayName) = withContext(Dispatchers.IO) {
                    val data = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read selected file")
                    data to queryDisplayName(ctx, uri)
                }
                val (isBinaryTavernCard, isStandaloneTavernAsset) = withContext(Dispatchers.Default) {
                    val isBin = RpCardCodec.isPngBytes(bytes) || RpCardCodec.extractCardFromZipBytes(bytes) != null
                    if (isBin) {
                        true to false
                    } else {
                        val rawText = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                        val isTavernTab = activeTab == ImportPlatformTab.TAVERN || activeTab == ImportPlatformTab.OTHER
                        val isAsset = isTavernTab && (
                            TavernAssetCodec.parseJsonText(rawText, conversationId = "preview", fileName = displayName) != null ||
                                looksLikeCharacterCardJson(rawText)
                            )
                        false to isAsset
                    }
                }

                if (isBinaryTavernCard || isStandaloneTavernAsset) {
                    if (onImportTavernAssetBytes != null) {
                        if (onImportTavernAssetBytes(bytes, displayName)) {
                            onDismiss()
                            return@launch
                        }
                    } else {
                        val isZh = strings.language == AppLanguage.ZH
                        val handled = when (
                            val imported = container.assetImportCoordinator.importBytes(
                                bytes = bytes,
                                fileName = displayName,
                            )
                        ) {
                            is AssetImportResult.CharacterCardImported -> {
                                container.toastController.show(
                                    if (isZh) "已导入角色卡「${imported.card.name}」到「创作 → 角色」"
                                    else "Imported character card \"${imported.card.name}\" to Creative Studio",
                                )
                                true
                            }
                            is AssetImportResult.WorldbookImported -> {
                                container.toastController.show(
                                    if (isZh) "已导入世界书「${imported.worldbook.name}」（${imported.entries.size} 条）到「创作 → 设定」"
                                    else "Imported worldbook \"${imported.worldbook.name}\" (${imported.entries.size} entries)",
                                )
                                true
                            }
                            is AssetImportResult.PromptPresetImported -> {
                                container.toastController.show(
                                    if (isZh) "已导入预设「${imported.preset.name}」到「创作 → 设定」"
                                    else "Imported preset \"${imported.preset.name}\" to library",
                                )
                                true
                            }
                            else -> false
                        }
                        if (handled) {
                            onDismiss()
                            return@launch
                        }
                    }
                }
                val bundle = withContext(Dispatchers.Default) {
                    ExternalImportCodec.parseBytes(bytes, fileName = displayName, hintSource = hint)
                }
                parsedBundle = bundle
                selectedIds = bundle.items.map { it.conversation.id }.toSet()
                importSyncedProviders = bundle.importedProviders.isNotEmpty()
                searchQuery = ""
                targetModeOverride = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errorMsg = e.message?.takeIf { it.isNotBlank() && it != "HTML_RESPONSE" } ?: strings.chat.importFailed
            } finally {
                isBusy = false
            }
        }
    }

    GuModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.importExternalTitle,
                onClose = { if (!isBusy) onDismiss() },
                closeContentDescription = strings.common.close,
            )

            val bundle = parsedBundle
            if (bundle == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val platformTabs = remember(strings) {
                        listOf(
                            Triple(ImportPlatformTab.DEEPSEEK, strings.chat.importSourceDeepSeek, Icons.Default.AutoAwesome),
                            Triple(ImportPlatformTab.TAVERN, strings.chat.importSourceTavern, Icons.Default.Casino),
                            Triple(ImportPlatformTab.CHENGGUANG, strings.chat.importSourceChengguang, Icons.Default.AutoStories),
                            Triple(ImportPlatformTab.CHATBOX, strings.chat.importSourceChatbox, Icons.AutoMirrored.Filled.Chat),
                            Triple(ImportPlatformTab.OTHER, strings.chat.importSourceOther, Icons.Default.Public),
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(platformTabs, key = { it.first.name }) { (tab, label, icon) ->
                            val selected = activeTab == tab
                            Surface(
                                onClick = {
                                    if (!isBusy) {
                                        activeTab = tab
                                        errorMsg = null
                                        linkNeedsBrowser = false
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) c.primary else c.surface,
                                border = if (theme.isDark) {
                                    BorderStroke(theme.borderWidth, if (selected) c.primary else c.border)
                                } else {
                                    null
                                },
                                shadowElevation = if (!theme.isDark && !selected) 0.8.dp else 0.dp,
                                modifier = Modifier.heightIn(min = 36.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else c.muted,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else c.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (activeTab == ImportPlatformTab.DEEPSEEK) {
                            GuSegmentedControl(
                                items = listOf(strings.chat.importDeepSeekModeZip, strings.chat.importDeepSeekModeLink),
                                selectedIndex = deepSeekSubTab,
                                onSelect = { idx ->
                                    deepSeekSubTab = idx
                                    errorMsg = null
                                    linkNeedsBrowser = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (activeTab == ImportPlatformTab.CHENGGUANG) {
                            GuSegmentedControl(
                                items = listOf(strings.chat.importChengguangModeFile, strings.chat.importChengguangModePaste),
                                selectedIndex = chengguangSubTab,
                                onSelect = { idx ->
                                    chengguangSubTab = idx
                                    errorMsg = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        val (activeIcon, activeTint, activeBg, activeTitle) = when (activeTab) {
                            ImportPlatformTab.DEEPSEEK -> Quad(Icons.Default.AutoAwesome, c.primarySoftInk, c.primarySoft, strings.chat.importSourceDeepSeek)
                            ImportPlatformTab.TAVERN -> Quad(Icons.Default.Casino, c.success, c.successBg, strings.chat.importSourceTavern)
                            ImportPlatformTab.CHENGGUANG -> Quad(Icons.Default.AutoStories, c.tagInk, c.tag, strings.chat.importSourceChengguang)
                            ImportPlatformTab.CHATBOX -> Quad(Icons.AutoMirrored.Filled.Chat, c.primarySoftInk, c.primarySoft, strings.chat.importSourceChatbox)
                            ImportPlatformTab.OTHER -> Quad(Icons.Default.Public, c.accent, c.accentSoft, strings.chat.importSourceOther)
                        }

                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = activeIcon,
                                    tint = activeTint,
                                    backgroundColor = activeBg,
                                    size = 38.dp,
                                    iconSize = 19.dp,
                                    cornerRadius = 11.dp,
                                )
                                Text(
                                    text = activeTitle,
                                    color = c.ink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            HorizontalDivider(
                                thickness = theme.borderWidth,
                                color = c.border.copy(alpha = 0.78f),
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (activeTab) {
                                    ImportPlatformTab.DEEPSEEK -> {
                                        if (deepSeekSubTab == 0) {
                                            Text(
                                                text = strings.chat.importDeepSeekZipHint,
                                                color = c.muted,
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.5.sp,
                                            )
                                            GuButton(
                                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                                enabled = !isBusy,
                                                loading = isBusy,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.chat.importDeepSeekPickZipBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text(
                                                text = strings.chat.importDeepSeekLinkHint,
                                                color = c.muted,
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.5.sp,
                                            )
                                            GuTextField(
                                                value = linkInput,
                                                onValueChange = {
                                                    linkInput = it
                                                    errorMsg = null
                                                    linkNeedsBrowser = false
                                                },
                                                placeholder = strings.chat.importDeepSeekLinkPlaceholder,
                                                singleLine = false,
                                                minLines = 2,
                                                maxLines = 4,
                                                trailingIcon = {
                                                    IconButton(
                                                        onClick = {
                                                            val clip = readClipboardText(ctx)
                                                            if (clip.isNotBlank()) {
                                                                linkInput = clip
                                                                errorMsg = null
                                                                linkNeedsBrowser = false
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ContentPaste,
                                                            contentDescription = strings.chat.importDeepSeekPasteBtn,
                                                            tint = c.primary,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                            )
                                            GuButton(
                                                onClick = {
                                                    scope.launch {
                                                        isBusy = true
                                                        busyLabel = strings.chat.importDeepSeekDownloading
                                                        errorMsg = null
                                                        linkNeedsBrowser = false
                                                        try {
                                                            val fetched = ExternalImportService.downloadAndParseLink(
                                                                httpClient = container.apiClient.streamingClient,
                                                                rawLinkInput = linkInput,
                                                                hintSource = ExternalImportSource.DEEPSEEK,
                                                            )
                                                            parsedBundle = fetched
                                                            selectedIds = fetched.items.map { it.conversation.id }.toSet()
                                                            importSyncedProviders = fetched.importedProviders.isNotEmpty()
                                                            searchQuery = ""
                                                            targetModeOverride = null
                                                        } catch (_: DeepSeekLinkAuthRequiredException) {
                                                            linkNeedsBrowser = true
                                                            errorMsg = strings.chat.importDeepSeekLinkNeedBrowser
                                                        } catch (e: IllegalArgumentException) {
                                                            errorMsg = if (e.message == "INVALID_LINK") {
                                                                strings.chat.importDeepSeekInvalidLink
                                                            } else {
                                                                e.message ?: strings.chat.importFailed
                                                            }
                                                        } catch (e: CancellationException) {
                                                            throw e
                                                        } catch (e: Throwable) {
                                                            errorMsg = e.message ?: strings.chat.importFailed
                                                        } finally {
                                                            isBusy = false
                                                        }
                                                    }
                                                },
                                                enabled = !isBusy && linkInput.isNotBlank(),
                                                loading = isBusy,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.chat.importDeepSeekFetchBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    ImportPlatformTab.TAVERN -> {
                                        Text(
                                            text = strings.chat.importTavernHint,
                                            color = c.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.5.sp,
                                        )
                                        GuButton(
                                            onClick = { filePicker.launch(arrayOf("*/*")) },
                                            enabled = !isBusy,
                                            loading = isBusy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.chat.importTavernPickBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    ImportPlatformTab.CHENGGUANG -> {
                                        if (chengguangSubTab == 0) {
                                            Text(
                                                text = strings.chat.importChengguangHint,
                                                color = c.muted,
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.5.sp,
                                            )
                                            GuButton(
                                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                                enabled = !isBusy,
                                                loading = isBusy,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.chat.importChengguangPickBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text(
                                                text = strings.chat.importChengguangHint,
                                                color = c.muted,
                                                fontSize = 12.sp,
                                                lineHeight = 17.5.sp,
                                            )
                                            GuTextField(
                                                value = pastedScript,
                                                onValueChange = {
                                                    pastedScript = it
                                                    errorMsg = null
                                                },
                                                placeholder = strings.chat.importChengguangPastePlaceholder,
                                                singleLine = false,
                                                minLines = 4,
                                                maxLines = 7,
                                                trailingIcon = {
                                                    IconButton(
                                                        onClick = {
                                                            val clip = readClipboardText(ctx)
                                                            if (clip.isNotBlank()) {
                                                                pastedScript = clip
                                                                errorMsg = null
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ContentPaste,
                                                            contentDescription = strings.chat.importDeepSeekPasteBtn,
                                                            tint = c.primary,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                            )
                                            GuButton(
                                                onClick = {
                                                    scope.launch {
                                                        isBusy = true
                                                        busyLabel = strings.chat.importDeepSeekParsing
                                                        errorMsg = null
                                                        try {
                                                            val parsed = withContext(Dispatchers.Default) {
                                                                ExternalImportCodec.parsePastedScript(pastedScript)
                                                            }
                                                            parsedBundle = parsed
                                                            selectedIds = parsed.items.map { it.conversation.id }.toSet()
                                                            importSyncedProviders = parsed.importedProviders.isNotEmpty()
                                                            searchQuery = ""
                                                            targetModeOverride = null
                                                        } catch (e: CancellationException) {
                                                            throw e
                                                        } catch (e: Throwable) {
                                                            errorMsg = e.message ?: strings.chat.importFailed
                                                        } finally {
                                                            isBusy = false
                                                        }
                                                    }
                                                },
                                                enabled = !isBusy && pastedScript.isNotBlank(),
                                                loading = isBusy,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.chat.importChengguangParseBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    ImportPlatformTab.CHATBOX -> {
                                        Text(
                                            text = strings.chat.importChatboxHint,
                                            color = c.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.5.sp,
                                        )
                                        GuButton(
                                            onClick = { filePicker.launch(arrayOf("*/*")) },
                                            enabled = !isBusy,
                                            loading = isBusy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.chat.importChatboxPickBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    ImportPlatformTab.OTHER -> {
                                        Text(
                                            text = strings.chat.importOtherHint,
                                            color = c.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.5.sp,
                                        )
                                        GuButton(
                                            onClick = { filePicker.launch(arrayOf("*/*")) },
                                            enabled = !isBusy,
                                            loading = isBusy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.chat.importOtherPickBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (isBusy && busyLabel.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = c.primary,
                                        )
                                        Text(busyLabel, color = c.muted, fontSize = 12.sp)
                                    }
                                }

                                errorMsg?.let { err ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = c.dangerBg,
                                        border = if (theme.isDark) BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.42f)) else null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(11.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(err, color = c.danger, fontSize = 12.sp, lineHeight = 17.5.sp)
                                            if (linkNeedsBrowser) {
                                                GuButton(
                                                    onClick = {
                                                        deepSeekSubTab = 0
                                                        errorMsg = null
                                                        linkNeedsBrowser = false
                                                    },
                                                    compact = true,
                                                    variant = GuButtonVariant.SUBTLE,
                                                ) {
                                                    Text(strings.chat.importDeepSeekSwitchToZip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Stage 2: Preview & Selective Import
                val totalMsgs = remember(bundle) { bundle.items.sumOf { it.messages.size } }
                val filteredItems = remember(bundle, searchQuery) {
                    FuzzyMatcher.filterAndRank(bundle.items, searchQuery) { item ->
                        listOf(item.conversation.title)
                    }
                }
                val allVisibleSelected = filteredItems.isNotEmpty() && filteredItems.all { it.conversation.id in selectedIds }
                val selectedModeIndex = targetModes.indexOf(targetModeOverride).coerceAtLeast(0)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuBadge(
                            text = sourceDisplayName(bundle.source, strings),
                            variant = GuBadgeVariant.HONEY,
                        )
                        if (bundle.items.isNotEmpty()) {
                            GuBadge(
                                text = strings.chat.importDeepSeekPreviewSummary.fmt(
                                    "convs" to bundle.items.size,
                                    "msgs" to totalMsgs,
                                ),
                                variant = GuBadgeVariant.BLUE,
                            )
                        } else if (bundle.importedProviders.isNotEmpty()) {
                            GuBadge(
                                text = "${bundle.importedProviders.size} API",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = strings.chat.importDeepSeekReselect,
                            color = c.primarySoftInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable(enabled = !isBusy) {
                                    parsedBundle = null
                                    selectedIds = emptySet()
                                    importSyncedProviders = true
                                    targetModeOverride = null
                                    errorMsg = null
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }

                    if (bundle.importedProviders.isNotEmpty()) {
                        Surface(
                            onClick = { importSyncedProviders = !importSyncedProviders },
                            shape = RoundedCornerShape(12.dp),
                            color = if (importSyncedProviders) c.successBg else c.surfaceSoft,
                            border = if (theme.isDark) {
                                BorderStroke(
                                    theme.borderWidth,
                                    if (importSyncedProviders) c.success.copy(alpha = 0.45f) else c.border,
                                )
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (importSyncedProviders) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (importSyncedProviders) c.success else c.muted,
                                    modifier = Modifier.size(19.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.providers.importSyncProvidersToggle.fmt("count" to bundle.importedProviders.size),
                                        color = c.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = bundle.importedProviders.take(4).joinToString(" · ") { it.displayTitle() },
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (bundle.items.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuSectionHeader(title = strings.chat.importTargetModeLabel)
                            GuSegmentedControl(
                                items = targetModeLabels,
                                selectedIndex = selectedModeIndex,
                                onSelect = { idx -> targetModeOverride = targetModes.getOrNull(idx) },
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (bundle.items.size > 1) {
                        GuCompactSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = strings.chat.importDeepSeekSearchPlaceholder,
                            clearContentDescription = strings.common.clear,
                        )
                    }

                    if (bundle.items.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.chat.importDeepSeekSelectedCount.fmt(
                                    "selected" to selectedIds.size,
                                    "total" to bundle.items.size,
                                ),
                                color = c.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (bundle.items.size > 1) {
                                Text(
                                    text = if (allVisibleSelected) strings.chat.importDeepSeekDeselectAll else strings.chat.importDeepSeekSelectAll,
                                    color = c.primarySoftInk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .clickable {
                                            val visibleIds = filteredItems.map { it.conversation.id }.toSet()
                                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                        }

                        GuGroupedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 300.dp),
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(filteredItems, key = { _, it -> it.conversation.id }) { idx, item ->
                                    val checked = item.conversation.id in selectedIds
                                    val effectiveMode = targetModeOverride ?: item.conversation.mode
                                    val modeTag = when (effectiveMode) {
                                        ConversationMode.rp -> strings.rp.filterRp
                                        ConversationMode.novel -> strings.novel.filterNovel
                                        ConversationMode.chat -> strings.novel.filterChat
                                    }
                                    val regexCount = item.conversation.rp?.regexScripts?.size ?: 0
                                    val memoryCount = item.memoryLedger.size + item.memoryEpisodes.size
                                    val hasBadges = item.hasReasoning ||
                                        item.loreEntries.isNotEmpty() ||
                                        memoryCount > 0 ||
                                        regexCount > 0 ||
                                        item.novelChapters.size > 1 ||
                                        (effectiveMode == ConversationMode.novel && item.novelChapters.isNotEmpty())

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (checked) c.primarySoft.copy(alpha = 0.55f) else Color.Transparent)
                                                .clickable {
                                                    selectedIds = if (checked) selectedIds - item.conversation.id else selectedIds + item.conversation.id
                                                }
                                                .padding(horizontal = 12.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                tint = if (checked) c.primary else c.muted,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .padding(top = 1.dp),
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = item.conversation.title,
                                                    color = c.ink,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = modeTag,
                                                        color = c.primarySoftInk,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        text = strings.chat.messageCount.fmt("count" to item.messages.size),
                                                        color = c.muted,
                                                        fontSize = 11.sp,
                                                    )
                                                    Text(
                                                        text = dateFmt.format(Date(item.conversation.updatedAt)),
                                                        color = c.muted,
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                                if (hasBadges) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        if (item.hasReasoning) {
                                                            GuBadge(strings.chat.importDeepSeekHasThinking, variant = GuBadgeVariant.HONEY)
                                                        }
                                                        if (item.loreEntries.isNotEmpty()) {
                                                            GuBadge(strings.chat.importHasLore.fmt("count" to item.loreEntries.size), variant = GuBadgeVariant.BLUE)
                                                        }
                                                        if (memoryCount > 0) {
                                                            val memLabel = if (strings.language == AppLanguage.ZH) {
                                                                "记忆 $memoryCount"
                                                            } else {
                                                                "$memoryCount mem"
                                                            }
                                                            GuBadge(memLabel, variant = GuBadgeVariant.HONEY)
                                                        }
                                                        if (regexCount > 0) {
                                                            GuBadge(strings.providers.importHasRegex.fmt("count" to regexCount), variant = GuBadgeVariant.EMERALD)
                                                        }
                                                        if (item.novelChapters.size > 1 || (effectiveMode == ConversationMode.novel && item.novelChapters.isNotEmpty())) {
                                                            GuBadge(strings.chat.importHasChapters.fmt("count" to item.novelChapters.size), variant = GuBadgeVariant.NEUTRAL)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (idx < filteredItems.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 42.dp),
                                                thickness = theme.borderWidth,
                                                color = c.border.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.8f),
                )
                val willImportProviders = importSyncedProviders && bundle.importedProviders.isNotEmpty()
                val canConfirm = !isBusy && (selectedIds.isNotEmpty() || willImportProviders)
                val confirmLabel = when {
                    selectedIds.isNotEmpty() -> strings.chat.importDeepSeekConfirmBtn.fmt("count" to selectedIds.size)
                    willImportProviders -> strings.providers.importProvidersOnlyConfirm.fmt("count" to bundle.importedProviders.size)
                    else -> strings.chat.importDeepSeekConfirmBtn.fmt("count" to 0)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    GuButton(
                        onClick = {
                            val toImport = bundle.items
                                .filter { it.conversation.id in selectedIds }
                                .map { ExternalImportCodec.applyTargetMode(it, targetModeOverride) }
                            val providersToImport = if (willImportProviders) bundle.importedProviders else emptyList()
                            if (toImport.isEmpty() && providersToImport.isEmpty()) return@GuButton
                            scope.launch {
                                isBusy = true
                                try {
                                    val addedProviders = if (providersToImport.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            container.apiProviderStore.upsertAllDeduped(providersToImport)
                                        }
                                    } else {
                                        0
                                    }
                                    if (toImport.isNotEmpty()) {
                                        val settings = container.settingsStore.currentSettings()
                                        val fallbackGroupId = selectedGroupId
                                            ?: settings.lastChatGroupId.takeIf { it != 0L }
                                            ?: container.apiProviderStore.enabledProviders().firstOrNull()?.syntheticGroupId
                                        val fallbackModelId = selectedModelId?.takeIf { it.isNotBlank() }
                                            ?: settings.lastChatModel.takeIf { it.isNotBlank() }
                                        val prepared = toImport.map { item ->
                                            val conv = item.conversation
                                            val mergedParams = conv.params.copy(
                                                maxTokens = conv.params.maxTokens ?: settings.defaultMaxOutputTokens,
                                                contextWindowOverride = conv.params.contextWindowOverride ?: settings.defaultContextWindowOverride,
                                                maxHistoryMessages = conv.params.maxHistoryMessages ?: settings.defaultMaxHistoryMessages,
                                                activeWindowStartSeq = conv.params.activeWindowStartSeq,
                                            )
                                            item.copy(
                                                conversation = conv.copy(
                                                    groupId = conv.groupId ?: fallbackGroupId,
                                                    model = conv.model?.takeIf { it.isNotBlank() } ?: fallbackModelId,
                                                    params = mergedParams,
                                                ),
                                            )
                                        }
                                        val res = ExternalImportService.persistSelected(container.database, prepared)
                                        val rpConvIds = prepared
                                            .filter { it.conversation.rp != null }
                                            .map { it.conversation.id }
                                        if (rpConvIds.isNotEmpty()) {
                                            container.scope.launch {
                                                ExternalImportService.cacheRemoteRpAvatars(
                                                    appContext = container.appContext,
                                                    httpClient = container.apiClient.standardClient,
                                                    db = container.database,
                                                    conversationIds = rpConvIds,
                                                )
                                            }
                                        }
                                        val baseToast = strings.chat.importExternalSuccess.fmt(
                                            "convs" to res.conversationsCount,
                                            "msgs" to res.messagesCount,
                                        )
                                        val fullToast = if (addedProviders > 0) {
                                            "$baseToast · ${strings.providers.importProvidersSuccess.fmt("added" to addedProviders)}"
                                        } else {
                                            baseToast
                                        }
                                        container.toastController.show(fullToast)
                                        onImported(res.firstConversationId)
                                    } else {
                                        val toast = if (addedProviders > 0) {
                                            strings.providers.importProviderSuccess.fmt("added" to addedProviders)
                                        } else {
                                            strings.providers.importProviderAllDup
                                        }
                                        container.toastController.show(toast)
                                        onImported(null)
                                    }
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    container.toastController.show(e.message ?: strings.chat.importFailed, isError = true)
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        enabled = canConfirm,
                        loading = isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = confirmLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun sourceDisplayName(source: ExternalImportSource, strings: AppStrings): String = when (source) {
    ExternalImportSource.DEEPSEEK -> strings.chat.importSourceDeepSeek
    ExternalImportSource.SILLYTAVERN -> strings.chat.importSourceTavern
    ExternalImportSource.CHENGGUANG -> strings.chat.importSourceChengguang
    ExternalImportSource.CHATBOX -> strings.chat.importSourceChatbox
    ExternalImportSource.CHATGPT -> "ChatGPT"
    ExternalImportSource.CLAUDE -> "Claude"
    ExternalImportSource.RIKKAHUB -> "RikkaHub"
    ExternalImportSource.CHERRY_STUDIO -> "Cherry Studio"
    ExternalImportSource.UU_GAME -> if (strings.language == AppLanguage.ZH) "UU 存档" else "UU Save"
    ExternalImportSource.GENERIC -> strings.chat.importSourceOther
}

private fun readClipboardText(ctx: Context): String =
    com.gugachat.app.core.util.ClipboardUtils.readText(ctx)

private fun queryDisplayName(ctx: Context, uri: Uri): String? =
    com.gugachat.app.core.util.UriUtils.resolveDisplayName(ctx.contentResolver, uri) ?: uri.lastPathSegment

private fun looksLikeCharacterCardJson(rawText: String): Boolean {
    if (!rawText.startsWith("{")) return false
    val root = runCatching { AppJson.parseToJsonElement(rawText) as? JsonObject }.getOrNull() ?: return false
    if (UuSaveCodec.isUuGameSave(root)) return false
    val spec = (root["spec"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (spec.equals("chara_card_v2", ignoreCase = true) ||
        spec.equals("chara_card_v3", ignoreCase = true) ||
        spec.equals("chara_card_v1", ignoreCase = true) ||
        spec.equals("chara_card", ignoreCase = true)
    ) {
        return true
    }
    val data = root["data"] as? JsonObject
    if (data != null && (
            data.containsKey("first_mes") ||
                data.containsKey("mes_example") ||
                data.containsKey("character_book") ||
                (!(data["name"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() &&
                    (data.containsKey("description") || data.containsKey("personality") || data.containsKey("scenario")))
            )
    ) {
        return true
    }
    return root.containsKey("first_mes") ||
        root.containsKey("char_greeting") ||
        (root.containsKey("char_name") && root.containsKey("char_persona"))
}
