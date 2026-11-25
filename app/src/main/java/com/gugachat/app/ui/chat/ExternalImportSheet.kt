package com.gugachat.app.ui.chat

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.data.api.ExternalImportService
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.ExternalImportBundle
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
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
    var searchQuery by remember { mutableStateOf("") }
    var targetModeOverride by remember { mutableStateOf<ConversationMode?>(null) }

    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

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
                val bundle = withContext(Dispatchers.Default) {
                    ExternalImportCodec.parseBytes(bytes, fileName = displayName, hintSource = hint)
                }
                parsedBundle = bundle
                selectedIds = bundle.items.map { it.conversation.id }.toSet()
                searchQuery = ""
                targetModeOverride = null
            } catch (e: Throwable) {
                errorMsg = e.message?.takeIf { it.isNotBlank() && it != "HTML_RESPONSE" } ?: strings.chat.importFailed
            } finally {
                isBusy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
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
                    // Stage 1: Platform Switcher with full-bleed horizontal edge padding
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
                                border = BorderStroke(1.dp, if (selected) c.primary else c.border),
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
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ImportModeOptionCard(
                                    title = strings.chat.importDeepSeekModeZip,
                                    icon = Icons.Default.FolderZip,
                                    selected = deepSeekSubTab == 0,
                                    onClick = {
                                        deepSeekSubTab = 0
                                        errorMsg = null
                                        linkNeedsBrowser = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ImportModeOptionCard(
                                    title = strings.chat.importDeepSeekModeLink,
                                    icon = Icons.Default.Link,
                                    selected = deepSeekSubTab == 1,
                                    onClick = {
                                        deepSeekSubTab = 1
                                        errorMsg = null
                                        linkNeedsBrowser = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else if (activeTab == ImportPlatformTab.CHENGGUANG) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ImportModeOptionCard(
                                    title = strings.chat.importChengguangModeFile,
                                    icon = Icons.Default.Description,
                                    selected = chengguangSubTab == 0,
                                    onClick = {
                                        chengguangSubTab = 0
                                        errorMsg = null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ImportModeOptionCard(
                                    title = strings.chat.importChengguangModePaste,
                                    icon = Icons.Default.EditNote,
                                    selected = chengguangSubTab == 1,
                                    onClick = {
                                        chengguangSubTab = 1
                                        errorMsg = null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = c.surface,
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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
                                                lineHeight = 18.sp,
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
                                                lineHeight = 18.sp,
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
                                            lineHeight = 18.sp,
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
                                                lineHeight = 18.sp,
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
                                                lineHeight = 17.sp,
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
                                                            searchQuery = ""
                                                            targetModeOverride = null
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
                                            lineHeight = 18.sp,
                                        )
                                        GuButton(
                                            onClick = { filePicker.launch(arrayOf("application/json", "*/*")) },
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
                                            lineHeight = 18.sp,
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
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = c.primary)
                                        Text(busyLabel, color = c.muted, fontSize = 12.sp)
                                    }
                                }

                                errorMsg?.let { err ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = c.dangerBg,
                                        border = BorderStroke(1.dp, c.danger.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(err, color = c.danger, fontSize = 12.sp, lineHeight = 17.sp)
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
                // Stage 2: Universal Preview & Selective Import
                val totalMsgs = remember(bundle) { bundle.items.sumOf { it.messages.size } }
                val filteredItems = remember(bundle, searchQuery) {
                    FuzzyMatcher.filterAndRank(bundle.items, searchQuery) { item ->
                        listOf(item.conversation.title)
                    }
                }
                val allVisibleSelected = filteredItems.isNotEmpty() && filteredItems.all { it.conversation.id in selectedIds }

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
                        GuBadge(
                            text = strings.chat.importDeepSeekPreviewSummary.fmt(
                                "convs" to bundle.items.size,
                                "msgs" to totalMsgs,
                            ),
                            variant = GuBadgeVariant.BLUE,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = strings.chat.importDeepSeekReselect,
                            color = c.primarySoftInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(enabled = !isBusy) {
                                    parsedBundle = null
                                    selectedIds = emptySet()
                                    targetModeOverride = null
                                    errorMsg = null
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                    }

                    // Target Mode Override Pills stacked cleanly under label
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = strings.chat.importTargetModeLabel,
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuPill(strings.chat.importTargetAuto, targetModeOverride == null, { targetModeOverride = null })
                            GuPill(strings.novel.filterChat, targetModeOverride == ConversationMode.chat, { targetModeOverride = ConversationMode.chat })
                            GuPill(strings.rp.filterRp, targetModeOverride == ConversationMode.rp, { targetModeOverride = ConversationMode.rp })
                            GuPill(strings.novel.filterNovel, targetModeOverride == ConversationMode.novel, { targetModeOverride = ConversationMode.novel })
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
                                    .clickable {
                                        val visibleIds = filteredItems.map { it.conversation.id }.toSet()
                                        selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 280.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filteredItems, key = { it.conversation.id }) { item ->
                                val checked = item.conversation.id in selectedIds
                                val effectiveMode = targetModeOverride ?: item.conversation.mode
                                val modeTag = when (effectiveMode) {
                                    ConversationMode.rp -> strings.rp.filterRp
                                    ConversationMode.novel -> strings.novel.filterNovel
                                    ConversationMode.chat -> strings.novel.filterChat
                                }
                                val hasBadges = item.hasReasoning ||
                                    item.loreEntries.isNotEmpty() ||
                                    item.novelChapters.size > 1 ||
                                    (effectiveMode == ConversationMode.novel && item.novelChapters.isNotEmpty())

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (checked) c.primarySoft else c.surfaceSoft.copy(alpha = 0.65f))
                                        .border(
                                            width = 1.dp,
                                            color = if (checked) c.primary.copy(alpha = 0.45f) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            selectedIds = if (checked) selectedIds - item.conversation.id else selectedIds + item.conversation.id
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = null,
                                        tint = if (checked) c.primary else c.muted,
                                        modifier = Modifier.size(20.dp).padding(top = 1.dp),
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
                                                if (item.novelChapters.size > 1 || (effectiveMode == ConversationMode.novel && item.novelChapters.isNotEmpty())) {
                                                    GuBadge(strings.chat.importHasChapters.fmt("count" to item.novelChapters.size), variant = GuBadgeVariant.NEUTRAL)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.border.copy(alpha = 0.7f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    GuButton(
                        onClick = {
                            val toImport = bundle.items
                                .filter { it.conversation.id in selectedIds }
                                .map { ExternalImportCodec.applyTargetMode(it, targetModeOverride) }
                            if (toImport.isEmpty()) return@GuButton
                            scope.launch {
                                isBusy = true
                                try {
                                    val res = ExternalImportService.persistSelected(container.database, toImport)
                                    container.toastController.show(
                                        strings.chat.importExternalSuccess.fmt(
                                            "convs" to res.conversationsCount,
                                            "msgs" to res.messagesCount,
                                        ),
                                    )
                                    onImported(res.firstConversationId)
                                    onDismiss()
                                } catch (e: Throwable) {
                                    container.toastController.show(e.message ?: strings.chat.importFailed, isError = true)
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        enabled = !isBusy && selectedIds.isNotEmpty(),
                        loading = isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.chat.importDeepSeekConfirmBtn.fmt("count" to selectedIds.size),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun sourceDisplayName(source: ExternalImportSource, strings: AppStrings): String = when (source) {
    ExternalImportSource.DEEPSEEK -> strings.chat.importSourceDeepSeek
    ExternalImportSource.SILLYTAVERN -> strings.chat.importSourceTavern
    ExternalImportSource.CHENGGUANG -> strings.chat.importSourceChengguang
    ExternalImportSource.CHATBOX -> strings.chat.importSourceChatbox
    ExternalImportSource.CHATGPT -> "ChatGPT"
    ExternalImportSource.CLAUDE -> "Claude"
    ExternalImportSource.GENERIC -> strings.chat.importSourceOther
}

private fun readClipboardText(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    return cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
}

private fun queryDisplayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else {
            null
        }
    }
}.getOrNull() ?: uri.lastPathSegment

@Composable
private fun ImportModeOptionCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.primarySoft else c.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) c.primary else c.border,
        ),
        modifier = modifier.height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) c.primarySoftInk else c.muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                color = if (selected) c.primarySoftInk else c.ink,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
