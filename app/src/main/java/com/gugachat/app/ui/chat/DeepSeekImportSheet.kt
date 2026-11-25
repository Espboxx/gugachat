package com.gugachat.app.ui.chat

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.DeepSeekImportService
import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.domain.chat.DeepSeekImportBundle
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekImportSheet(
    container: AppContainer,
    onImported: (firstConversationId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var modeTab by remember { mutableIntStateOf(0) } // 0 = Import ZIP, 1 = Download Link
    var linkInput by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var linkNeedsBrowser by remember { mutableStateOf(false) }

    var parsedBundle by remember { mutableStateOf<DeepSeekImportBundle?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            busyLabel = strings.chat.importDeepSeekParsing
            errorMsg = null
            linkNeedsBrowser = false
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read selected file")
                }
                val bundle = withContext(Dispatchers.Default) {
                    DeepSeekImportCodec.parseBytes(bytes)
                }
                parsedBundle = bundle
                selectedIds = bundle.items.map { it.conversation.id }.toSet()
                searchQuery = ""
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.chat.importDeepSeekTitle,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (!isBusy) onDismiss() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }

            val bundle = parsedBundle
            if (bundle == null) {
                // Two-option selector: 1. Import ZIP  |  2. Download Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ImportModeOptionCard(
                        title = strings.chat.importDeepSeekModeZip,
                        icon = Icons.Default.FolderZip,
                        selected = modeTab == 0,
                        onClick = {
                            modeTab = 0
                            errorMsg = null
                            linkNeedsBrowser = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ImportModeOptionCard(
                        title = strings.chat.importDeepSeekModeLink,
                        icon = Icons.Default.Link,
                        selected = modeTab == 1,
                        onClick = {
                            modeTab = 1
                            errorMsg = null
                            linkNeedsBrowser = false
                        },
                        modifier = Modifier.weight(1f),
                    )
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
                        if (modeTab == 0) {
                            Text(
                                text = strings.chat.importDeepSeekZipHint,
                                color = c.muted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                            GuButton(
                                onClick = {
                                    zipPicker.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                            "application/json",
                                            "*/*",
                                        ),
                                    )
                                },
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
                                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
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
                                            val fetched = DeepSeekImportService.downloadAndParseLink(
                                                httpClient = container.apiClient.streamingClient,
                                                rawLinkInput = linkInput,
                                            )
                                            parsedBundle = fetched
                                            selectedIds = fetched.items.map { it.conversation.id }.toSet()
                                            searchQuery = ""
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
                                border = BorderStroke(1.dp, c.danger.copy(alpha = 0.4f)),
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
                                                modeTab = 0
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
            } else {
                // Stage 2: Preview & Selective Import
                val totalMsgs = remember(bundle) { bundle.items.sumOf { it.messages.size } }
                val filteredItems = remember(bundle, searchQuery) {
                    FuzzyMatcher.filterAndRank(bundle.items, searchQuery) { item ->
                        listOf(item.conversation.title)
                    }
                }
                val allVisibleSelected = filteredItems.isNotEmpty() && filteredItems.all { it.conversation.id in selectedIds }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GuBadge(
                        text = strings.chat.importDeepSeekPreviewSummary.fmt(
                            "convs" to bundle.items.size,
                            "msgs" to totalMsgs,
                        ),
                        variant = GuBadgeVariant.BLUE,
                    )
                    Text(
                        text = strings.chat.importDeepSeekReselect,
                        color = c.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = !isBusy) {
                                parsedBundle = null
                                selectedIds = emptySet()
                                errorMsg = null
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                GuTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = strings.chat.importDeepSeekSearchPlaceholder,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                )

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
                    Text(
                        text = if (allVisibleSelected) strings.chat.importDeepSeekDeselectAll else strings.chat.importDeepSeekSelectAll,
                        color = c.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val visibleIds = filteredItems.map { it.conversation.id }.toSet()
                                selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 300.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredItems, key = { it.conversation.id }) { item ->
                            val checked = item.conversation.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (checked) c.primarySoft else c.surfaceSoft.copy(alpha = 0.6f))
                                    .border(
                                        width = 1.dp,
                                        color = if (checked) c.primary.copy(alpha = 0.45f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        selectedIds = if (checked) selectedIds - item.conversation.id else selectedIds + item.conversation.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (checked) c.primary else c.muted,
                                    modifier = Modifier.size(19.dp),
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.conversation.title,
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
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
                                }
                                if (item.hasReasoning) {
                                    GuBadge(strings.chat.importDeepSeekHasThinking, variant = GuBadgeVariant.HONEY)
                                }
                            }
                        }
                    }
                }

                GuButton(
                    onClick = {
                        val toImport = bundle.items.filter { it.conversation.id in selectedIds }
                        if (toImport.isEmpty()) return@GuButton
                        scope.launch {
                            isBusy = true
                            try {
                                val res = DeepSeekImportService.persistSelected(container.database, toImport)
                                container.toastController.show(
                                    strings.chat.importDeepSeekSuccess.fmt(
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
        modifier = modifier.height(62.dp),
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
                tint = if (selected) c.primary else c.muted,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                color = if (selected) c.primary else c.ink,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
