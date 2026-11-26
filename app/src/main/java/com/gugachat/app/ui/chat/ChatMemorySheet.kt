package com.gugachat.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.VectorIndexProgress

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMemorySheet(
    userMemories: List<UserMemory>,
    ledgerItems: List<MemoryLedgerItem>,
    episodes: List<MemoryEpisode>,
    waterline: ContextWaterlineSummary,
    isExtracting: Boolean,
    initialTab: Int = 0,
    mutedRecallKeys: Set<String> = emptySet(),
    vectorIndexProgress: VectorIndexProgress = VectorIndexProgress(),
    activeRetrievalMode: RetrievalMode = RetrievalMode.LEXICAL_ONLY,
    isRerankActive: Boolean = false,
    onExtractMemories: () -> Unit,
    onAddMemory: (MemoryCategory, String) -> Unit,
    onToggleMemoryPin: (String) -> Unit,
    onToggleMemoryEnabled: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onAddLedger: (String, String, String) -> Unit,
    onToggleLedgerPin: (String) -> Unit,
    onDeleteLedger: (String) -> Unit,
    onToggleMuteRecall: ((String) -> Unit)? = null,
    onTogglePinRecall: ((String, String) -> Unit)? = null,
    onDisableRecall: ((String, String) -> Unit)? = null,
    onReindexVectors: (() -> Unit)? = null,
    onOpenContextSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    var newMemCat by remember { mutableStateOf(MemoryCategory.preference) }
    var newMemText by remember { mutableStateOf("") }

    var newLedgerKind by remember { mutableStateOf("fact") }
    var newLedgerTitle by remember { mutableStateOf("") }
    var newLedgerContent by remember { mutableStateOf("") }

    val tabLabels = remember(isZh) {
        if (isZh) {
            listOf("已保存", "账本", "编年史", "召回")
        } else {
            listOf("Saved", "Ledger", "Arcs", "Recall")
        }
    }
    val tabBadges = remember(userMemories.size, waterline.recalledItems.size) {
        buildMap {
            if (userMemories.isNotEmpty()) put(0, userMemories.size)
            if (waterline.recalledItems.isNotEmpty()) put(3, waterline.recalledItems.size)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.memoryTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            GuSegmentedControl(
                items = tabLabels,
                selectedIndex = tab,
                onSelect = { tab = it },
                badgeCounts = tabBadges,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__mem_controls__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuSectionHeader(
                                    title = strings.chat.savedMemories.fmt("count" to userMemories.size),
                                    subtitle = strings.chat.memoryCapacityCount.fmt("count" to userMemories.size, "max" to 500),
                                    trailing = {
                                        GuButton(
                                            onClick = onExtractMemories,
                                            loading = isExtracting,
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(strings.chat.extractMemory, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                )

                                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(
                                                listOf(
                                                    MemoryCategory.preference,
                                                    MemoryCategory.profile,
                                                    MemoryCategory.project,
                                                    MemoryCategory.rule,
                                                    MemoryCategory.other,
                                                ),
                                                key = { it.name },
                                            ) { cat ->
                                                GuPill(
                                                    label = localizeMemoryCategory(cat, strings),
                                                    selected = newMemCat == cat,
                                                    onClick = { newMemCat = cat },
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            GuTextField(
                                                value = newMemText,
                                                onValueChange = { newMemText = it },
                                                placeholder = strings.chat.memoryContentPlaceholder,
                                                modifier = Modifier.weight(1f),
                                            )
                                            GuButton(
                                                onClick = {
                                                    if (newMemText.isNotBlank()) {
                                                        onAddMemory(newMemCat, newMemText)
                                                        newMemText = ""
                                                    }
                                                },
                                                enabled = newMemText.isNotBlank(),
                                                compact = true,
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = strings.chat.addMemory, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (userMemories.isEmpty()) {
                            item(key = "__mem_empty__") {
                                GuEmptyState(
                                    title = strings.chat.emptyMemories,
                                    icon = Icons.Default.Psychology,
                                )
                            }
                        } else {
                            items(userMemories, key = { it.id }) { mem ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuBadge(localizeMemoryCategory(mem.category, strings), variant = GuBadgeVariant.BLUE)
                                        Text(
                                            text = "~${mem.estimatedTokens}t",
                                            color = c.muted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Switch(
                                            checked = mem.enabled,
                                            onCheckedChange = { onToggleMemoryEnabled(mem.id) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = c.primary,
                                                checkedBorderColor = c.primary,
                                                uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                                                uncheckedTrackColor = c.surfaceSoft,
                                                uncheckedBorderColor = c.outline,
                                            ),
                                        )
                                        IconButton(
                                            onClick = { onToggleMemoryPin(mem.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = if (mem.pinned) strings.chat.ledgerUnpin else strings.chat.ledgerPin,
                                                tint = if (mem.pinned) c.accent else c.muted,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteMemory(mem.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = strings.common.delete,
                                                tint = c.danger,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(mem.content, color = c.ink, fontSize = 13.5.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__ledger_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuSectionHeader(
                                    title = strings.chat.ledgerTabTitle,
                                    trailing = {
                                        GuBadge("${ledgerItems.size}", variant = GuBadgeVariant.NEUTRAL)
                                    },
                                )
                                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(listOf("fact", "relation", "promise", "event", "quest"), key = { it }) { k ->
                                                GuPill(strings.chat.ledgerKinds[k] ?: k, newLedgerKind == k, { newLedgerKind = k })
                                            }
                                        }
                                        GuTextField(
                                            value = newLedgerTitle,
                                            onValueChange = { newLedgerTitle = it },
                                            placeholder = strings.chat.ledgerTitlePlaceholder,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            GuTextField(
                                                value = newLedgerContent,
                                                onValueChange = { newLedgerContent = it },
                                                placeholder = strings.chat.ledgerContentPlaceholder,
                                                modifier = Modifier.weight(1f),
                                            )
                                            val canAddLedger = newLedgerTitle.isNotBlank() && newLedgerContent.isNotBlank()
                                            GuButton(
                                                onClick = {
                                                    if (canAddLedger) {
                                                        onAddLedger(newLedgerKind, newLedgerTitle, newLedgerContent)
                                                        newLedgerTitle = ""
                                                        newLedgerContent = ""
                                                    }
                                                },
                                                enabled = canAddLedger,
                                                compact = true,
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = strings.chat.ledgerAddBtn, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (ledgerItems.isEmpty()) {
                            item(key = "__ledger_empty__") {
                                GuEmptyState(
                                    title = strings.chat.ledgerEmpty,
                                    icon = Icons.Default.Bookmarks,
                                )
                            }
                        } else {
                            items(ledgerItems, key = { it.id }) { item ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuBadge(strings.chat.ledgerKinds[item.kind] ?: item.kind, variant = GuBadgeVariant.HONEY)
                                        Text(
                                            text = item.title,
                                            color = c.ink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.stale) {
                                            GuBadge(if (isZh) "待更新" else "Stale", variant = GuBadgeVariant.DANGER)
                                        }
                                        IconButton(
                                            onClick = { onToggleLedgerPin(item.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = if (item.pinned) strings.chat.ledgerUnpin else strings.chat.ledgerPin,
                                                tint = if (item.pinned) c.accent else c.muted,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteLedger(item.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = strings.common.delete,
                                                tint = c.danger,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.content, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.5.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__ep_header__") {
                            GuSectionHeader(
                                title = strings.chat.episodesTitle,
                                trailing = {
                                    GuBadge("${episodes.size}", variant = GuBadgeVariant.NEUTRAL)
                                },
                            )
                        }
                        if (episodes.isEmpty()) {
                            item(key = "__ep_empty__") {
                                GuEmptyState(
                                    title = strings.chat.episodesEmpty,
                                    icon = Icons.Default.AutoStories,
                                )
                            }
                        } else {
                            items(episodes, key = { it.id }) { ep ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val lvlName = when (ep.level) {
                                            3 -> if (isZh) "总编年 L3" else "Chronicle L3"
                                            2 -> if (isZh) "分卷 L2" else "Arc L2"
                                            else -> if (isZh) "幕次 L1" else "Episode L1"
                                        }
                                        GuBadge(lvlName, variant = GuBadgeVariant.BLUE)
                                        GuBadge("Seq ${ep.fromSeq}~${ep.toSeq}", variant = GuBadgeVariant.NEUTRAL)
                                        if (ep.stale) {
                                            GuBadge(if (isZh) "待更新" else "Stale", variant = GuBadgeVariant.DANGER)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(ep.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(ep.summary, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.5.sp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    val ctxUi = strings.contextUi
                    val recallBudget = waterline.recallBudgetTokens.coerceAtLeast(0)
                    val recallUsed = waterline.recalledTotalTokens.coerceAtLeast(0)
                    val recallRatio = if (recallBudget > 0) (recallUsed.toFloat() / recallBudget.toFloat()).coerceIn(0f, 1f) else 0f
                    val recallOverCap = recallBudget > 0 && recallUsed >= recallBudget
                    val modePillLabel = when (activeRetrievalMode) {
                        RetrievalMode.LEXICAL_ONLY -> ctxUi.modePillLexical
                        RetrievalMode.LOCAL_SMALL_MODEL -> ctxUi.modePillLocalVector
                        RetrievalMode.REMOTE_API -> if (isRerankActive) ctxUi.modePillCloudRerank else ctxUi.modePillCloudVector
                    }
                    val modePillVariant = when (activeRetrievalMode) {
                        RetrievalMode.LEXICAL_ONLY -> GuBadgeVariant.NEUTRAL
                        RetrievalMode.LOCAL_SMALL_MODEL -> GuBadgeVariant.EMERALD
                        RetrievalMode.REMOTE_API -> if (isRerankActive) GuBadgeVariant.BLUE else GuBadgeVariant.EMERALD
                    }
                    val recallBudgetText = ctxUi.recallBudgetBadge.fmt(
                        "used" to FileContextBudgeter.formatShortTokens(recallUsed),
                        "budget" to FileContextBudgeter.formatShortTokens(recallBudget),
                    )
                    val showCalmStrip = waterline.isSliding || waterline.hardOverflow || waterline.trimmedMessageCount > 0

                    val groupedRecall = remember(waterline.recalledItems) {
                        val pinnedGroup = waterline.recalledItems.filter {
                            it.triggerKind == "constant" || it.triggerKind == "pinned_ledger"
                        }
                        val keywordGroup = waterline.recalledItems.filter {
                            it.triggerKind == "keyword" || it.triggerKind == "recursion_1hop"
                        }
                        val semanticGroup = waterline.recalledItems.filter {
                            it.triggerKind != "constant" &&
                                it.triggerKind != "pinned_ledger" &&
                                it.triggerKind != "keyword" &&
                                it.triggerKind != "recursion_1hop"
                        }
                        buildList {
                            if (pinnedGroup.isNotEmpty()) add(Triple("pinned", ctxUi.groupPinnedHeader, pinnedGroup))
                            if (keywordGroup.isNotEmpty()) add(Triple("keyword", ctxUi.groupKeywordHeader, keywordGroup))
                            if (semanticGroup.isNotEmpty()) add(Triple("semantic", ctxUi.groupSemanticHeader, semanticGroup))
                        }
                    }

                    val mutedTitlesByKey = remember(mutedRecallKeys, userMemories, ledgerItems, episodes) {
                        mutedRecallKeys.associateWith { key ->
                            val sep = key.indexOf(':')
                            val src = if (sep > 0) key.substring(0, sep) else ""
                            val id = if (sep > 0) key.substring(sep + 1) else key
                            when (src) {
                                "user_memory_archival" -> userMemories.firstOrNull { it.id == id }?.content?.take(18)
                                "rp_ledger" -> ledgerItems.firstOrNull { it.id == id }?.title
                                "episode_archived" -> episodes.firstOrNull { it.id == id }?.title
                                else -> null
                            }?.takeIf { it.isNotBlank() } ?: "${localizeRecallSource(src, strings)} #${id.take(6)}"
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__recall_header__") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuSectionHeader(
                                    title = strings.chat.recallTitle,
                                    trailing = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GuBadge(text = modePillLabel, variant = modePillVariant)
                                            GuBadge(
                                                text = recallBudgetText,
                                                variant = if (recallOverCap) GuBadgeVariant.AMBER else GuBadgeVariant.BLUE,
                                            )
                                        }
                                    },
                                )
                                if (recallBudget > 0) {
                                    LinearProgressIndicator(
                                        progress = { recallRatio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = if (recallOverCap) c.warning else c.primary,
                                        trackColor = c.surfaceSoft,
                                    )
                                }
                            }
                        }

                        if (showCalmStrip) {
                            item(key = "__recall_context_strip__") {
                                val isDanger = waterline.hardOverflow
                                val stripBg = if (isDanger) c.dangerBg else c.warningBg
                                val stripFg = if (isDanger) c.danger else c.warning
                                val stripBorder = stripFg.copy(alpha = 0.42f)
                                val sentShort = FileContextBudgeter.formatShortTokens(waterline.sentTokens)
                                val limitShort = FileContextBudgeter.formatShortTokens(waterline.effectiveLimitTokens)
                                val windowShort = FileContextBudgeter.formatShortTokens(waterline.modelContextWindow)
                                val stripText = if (isDanger) {
                                    ctxUi.calmOverflowStrip.fmt("sent" to sentShort, "window" to windowShort)
                                } else {
                                    ctxUi.calmSlidingStrip.fmt(
                                        "sent" to sentShort,
                                        "limit" to limitShort,
                                        "trimmed" to waterline.trimmedMessageCount,
                                    )
                                }
                                Surface(
                                    onClick = { onOpenContextSettings?.invoke() },
                                    enabled = onOpenContextSettings != null,
                                    shape = RoundedCornerShape(12.dp),
                                    color = stripBg,
                                    border = BorderStroke(theme.borderWidth, stripBorder),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 11.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isDanger) Icons.Default.WarningAmber else Icons.Default.Info,
                                            contentDescription = null,
                                            tint = stripFg,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stripText,
                                            color = stripFg,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (onOpenContextSettings != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                            ) {
                                                Text(
                                                    text = ctxUi.manageBudgetAction,
                                                    color = stripFg,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = stripFg,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (activeRetrievalMode != RetrievalMode.LEXICAL_ONLY) {
                            item(key = "__recall_vector_strip__") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surface,
                                    border = BorderStroke(theme.borderWidth, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 11.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                tint = if (vectorIndexProgress.lastError != null) c.danger else c.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            val statusText = when {
                                                vectorIndexProgress.running -> ctxUi.vectorIndexRunning.fmt(
                                                    "indexed" to vectorIndexProgress.indexed,
                                                    "total" to vectorIndexProgress.total,
                                                )
                                                !vectorIndexProgress.lastError.isNullOrBlank() -> vectorIndexProgress.lastError
                                                vectorIndexProgress.total > 0 -> ctxUi.vectorIndexDone.fmt(
                                                    "indexed" to vectorIndexProgress.indexed,
                                                    "total" to vectorIndexProgress.total,
                                                )
                                                else -> ctxUi.vectorIndexIdle
                                            }
                                            Text(
                                                text = statusText,
                                                color = if (vectorIndexProgress.lastError != null) c.danger else c.muted,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (onReindexVectors != null) {
                                                Surface(
                                                    onClick = onReindexVectors,
                                                    enabled = !vectorIndexProgress.running,
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = c.primarySoft,
                                                    border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.32f)),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = ctxUi.vectorReindexBtn,
                                                            tint = c.primarySoftInk,
                                                            modifier = Modifier.size(13.dp),
                                                        )
                                                        Text(
                                                            text = ctxUi.vectorReindexBtn,
                                                            color = c.primarySoftInk,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (vectorIndexProgress.running) {
                                            if (vectorIndexProgress.total > 0) {
                                                val ratio = (vectorIndexProgress.indexed.toFloat() / vectorIndexProgress.total.toFloat())
                                                    .coerceIn(0f, 1f)
                                                LinearProgressIndicator(
                                                    progress = { ratio },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(999.dp)),
                                                    color = c.primary,
                                                    trackColor = c.surfaceSoft,
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(999.dp)),
                                                    color = c.primary,
                                                    trackColor = c.surfaceSoft,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (mutedRecallKeys.isNotEmpty() && onToggleMuteRecall != null) {
                            item(key = "__recall_muted_strip__") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(theme.borderWidth, c.warning.copy(alpha = 0.38f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 11.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = ctxUi.mutedSectionTitle.fmt("count" to mutedRecallKeys.size),
                                            color = c.warning,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            mutedRecallKeys.forEach { key ->
                                                val label = mutedTitlesByKey[key] ?: key
                                                Surface(
                                                    onClick = { onToggleMuteRecall(key) },
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = c.warningBg,
                                                    border = BorderStroke(theme.borderWidth, c.warning.copy(alpha = 0.42f)),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                                            contentDescription = null,
                                                            tint = c.warning,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Text(
                                                            text = label,
                                                            color = c.warning,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = ctxUi.unmuteThisTurn,
                                                            tint = c.warning,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (waterline.recalledItems.isEmpty()) {
                            item(key = "__recall_empty__") {
                                GuEmptyState(
                                    title = strings.chat.recallEmpty,
                                    icon = Icons.Default.Search,
                                )
                            }
                        } else {
                            groupedRecall.forEach { (groupKey, groupTitle, groupItems) ->
                                item(key = "__recall_grp_$groupKey") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, start = 2.dp, end = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = groupTitle,
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        GuBadge(text = "${groupItems.size}", variant = GuBadgeVariant.NEUTRAL)
                                    }
                                }
                                items(groupItems, key = { "${it.source}:${it.id}" }) { item ->
                                    val recallKey = "${item.source}:${item.id}"
                                    val isMuted = recallKey in mutedRecallKeys
                                    val supportsPinOrDisable = item.source in setOf(
                                        "user_memory_archival",
                                        "rp_ledger",
                                        "novel_lore",
                                    )
                                    val isPinnedOrConstant = item.triggerKind == "constant" || item.triggerKind == "pinned_ledger"
                                    val (triggerLabel, triggerVariant) = resolveTriggerBadge(item.triggerKind, strings)

                                    GuCard(modifier = Modifier.fillMaxWidth()) {
                                        // Header row: Trigger badge + Title + Monospace ~Nt badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GuBadge(text = triggerLabel, variant = triggerVariant)
                                            Text(
                                                text = item.title,
                                                color = c.ink,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(c.surfaceSoft)
                                                    .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "~${item.estimatedTokens}t",
                                                    color = c.ink,
                                                    fontSize = 10.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        // Secondary meta + quick controls row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GuBadge(
                                                text = localizeRecallSource(item.source, strings),
                                                variant = GuBadgeVariant.NEUTRAL,
                                            )
                                            item.score?.let { sc ->
                                                GuBadge(
                                                    text = strings.chat.recallScore.fmt("score" to sc),
                                                    variant = GuBadgeVariant.HONEY,
                                                )
                                            }
                                            if (isMuted && onToggleMuteRecall != null) {
                                                Surface(
                                                    onClick = { onToggleMuteRecall(recallKey) },
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = c.warningBg,
                                                    border = BorderStroke(theme.borderWidth, c.warning.copy(alpha = 0.42f)),
                                                ) {
                                                    Text(
                                                        text = ctxUi.unmuteThisTurn,
                                                        color = c.warning,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.weight(1f))
                                            if (onToggleMuteRecall != null) {
                                                IconButton(
                                                    onClick = { onToggleMuteRecall(recallKey) },
                                                    modifier = Modifier.size(30.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                                        contentDescription = if (isMuted) ctxUi.unmuteThisTurn else ctxUi.muteThisTurn,
                                                        tint = if (isMuted) c.warning else c.muted,
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                }
                                            }
                                            if (supportsPinOrDisable && onTogglePinRecall != null) {
                                                IconButton(
                                                    onClick = { onTogglePinRecall(item.source, item.id) },
                                                    modifier = Modifier.size(30.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = ctxUi.pinAction,
                                                        tint = if (isPinnedOrConstant) c.accent else c.muted,
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                }
                                            }
                                            if (supportsPinOrDisable && onDisableRecall != null) {
                                                IconButton(
                                                    onClick = { onDisableRecall(item.source, item.id) },
                                                    modifier = Modifier.size(30.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Block,
                                                        contentDescription = ctxUi.disableAction,
                                                        tint = c.danger.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = item.contentPreview,
                                            color = c.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.5.sp,
                                        )
                                        if (item.matchedTerms.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = strings.chat.recallMatched.fmt("terms" to item.matchedTerms.joinToString(", ")),
                                                color = c.primarySoftInk,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (waterline.droppedRecallCount > 0) {
                            item(key = "__recall_dropped_footer__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.surfaceSoft)
                                        .border(theme.borderWidth, c.border, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = ctxUi.droppedRecallFooter.fmt("count" to waterline.droppedRecallCount),
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveTriggerBadge(triggerKind: String, strings: AppStrings): Pair<String, GuBadgeVariant> {
    val ctxUi = strings.contextUi
    return when (triggerKind.lowercase()) {
        "constant" -> ctxUi.triggerConstant to GuBadgeVariant.BLUE
        "pinned_ledger" -> ctxUi.triggerPinnedLedger to GuBadgeVariant.BLUE
        "keyword" -> ctxUi.triggerKeyword to GuBadgeVariant.EMERALD
        "semantic" -> ctxUi.triggerSemantic to GuBadgeVariant.HONEY
        "recursion_1hop" -> ctxUi.triggerRecursion to GuBadgeVariant.AMBER
        else -> ctxUi.triggerLexical to GuBadgeVariant.NEUTRAL
    }
}

private fun localizeMemoryCategory(cat: MemoryCategory, strings: AppStrings): String {
    val isZh = strings.language == AppLanguage.ZH
    return when (cat) {
        MemoryCategory.preference -> strings.chat.catPreference
        MemoryCategory.profile -> strings.chat.catProfile
        MemoryCategory.project -> strings.chat.catProject
        MemoryCategory.rule -> if (isZh) "规则" else "Rule"
        MemoryCategory.other -> strings.chat.catOther
    }
}

private fun localizeRecallSource(source: String, strings: AppStrings): String {
    val isZh = strings.language == AppLanguage.ZH
    return when (source.lowercase()) {
        "rp_ledger" -> if (isZh) "文游账本" else "RP Ledger"
        "ledger", "recalled" -> strings.chat.recallSourceRecalled
        "episode_archived", "episode" -> strings.chat.recallSourceEpisode
        "user_memory_archival", "user_memory" -> strings.chat.recallSourceUserMemory
        "novel_lore" -> strings.chat.recallSourceNovelLore
        "pinned" -> strings.chat.recallSourcePinned
        "open_promise" -> strings.chat.recallSourceOpenPromise
        else -> source
    }
}
