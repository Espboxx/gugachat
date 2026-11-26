package com.gugachat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.UserMemory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMemorySheet(
    userMemories: List<UserMemory>,
    ledgerItems: List<MemoryLedgerItem>,
    episodes: List<MemoryEpisode>,
    waterline: ContextWaterlineSummary,
    isExtracting: Boolean,
    initialTab: Int = 0,
    onExtractMemories: () -> Unit,
    onAddMemory: (MemoryCategory, String) -> Unit,
    onToggleMemoryPin: (String) -> Unit,
    onToggleMemoryEnabled: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onAddLedger: (String, String, String) -> Unit,
    onToggleLedgerPin: (String) -> Unit,
    onDeleteLedger: (String) -> Unit,
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__recall_header__") {
                            GuSectionHeader(
                                title = strings.chat.recallTitle,
                                trailing = {
                                    GuBadge(
                                        text = "${waterline.percentage}% · ${waterline.usedTokens / 1000}K",
                                        variant = if (waterline.exceedsModelWindow) GuBadgeVariant.DANGER else GuBadgeVariant.BLUE,
                                    )
                                },
                            )
                        }
                        if (waterline.recalledItems.isEmpty()) {
                            item(key = "__recall_empty__") {
                                GuEmptyState(
                                    title = strings.chat.recallEmpty,
                                    icon = Icons.Default.Search,
                                )
                            }
                        } else {
                            items(waterline.recalledItems, key = { "${it.source}:${it.id}" }) { item ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = item.title,
                                            color = c.ink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            GuBadge(
                                                text = localizeRecallSource(item.source, strings),
                                                variant = GuBadgeVariant.BLUE,
                                            )
                                            item.score?.let { sc ->
                                                GuBadge(strings.chat.recallScore.fmt("score" to sc), variant = GuBadgeVariant.HONEY)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(item.contentPreview, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.5.sp)
                                    if (item.matchedTerms.isNotEmpty()) {
                                        Spacer(Modifier.height(5.dp))
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
                }
            }
        }
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
