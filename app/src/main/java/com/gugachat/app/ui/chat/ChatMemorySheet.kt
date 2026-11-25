package com.gugachat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.UserMemory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val c = LocalAppTheme.current.colors
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    var newMemCat by remember { mutableStateOf(MemoryCategory.preference) }
    var newMemText by remember { mutableStateOf("") }

    var newLedgerKind by remember { mutableStateOf("fact") }
    var newLedgerTitle by remember { mutableStateOf("") }
    var newLedgerContent by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings.chat.memoryTitle,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuPill(strings.chat.savedMemories.fmt("count" to userMemories.size), tab == 0, { tab = 0 })
                GuPill(strings.chat.ledgerTabTitle, tab == 1, { tab = 1 })
                GuPill(strings.chat.episodesTitle, tab == 2, { tab = 2 })
                GuPill(strings.chat.recallTitle, tab == 3, { tab = 3 }, useHoneySelected = true)
            }

            when (tab) {
                0 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.chat.memoryCapacityCount.fmt("count" to userMemories.size, "max" to 200),
                            color = c.muted,
                            fontSize = 12.sp,
                        )
                        GuButton(
                            onClick = onExtractMemories,
                            loading = isExtracting,
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.chat.extractMemory, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            MemoryCategory.preference to strings.chat.catPreference,
                            MemoryCategory.profile to strings.chat.catProfile,
                            MemoryCategory.project to strings.chat.catProject,
                            MemoryCategory.rule to "规则",
                            MemoryCategory.other to strings.chat.catOther,
                        ).forEach { (cat, label) ->
                            GuPill(label, newMemCat == cat, { newMemCat = cat })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(
                            value = newMemText,
                            onValueChange = { newMemText = it },
                            placeholder = strings.chat.memoryContentPlaceholder,
                            modifier = Modifier.weight(1f),
                        )
                        GuButton(
                            onClick = {
                                onAddMemory(newMemCat, newMemText)
                                newMemText = ""
                            },
                            compact = true,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.chat.addMemory, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (userMemories.isEmpty()) {
                        GuEmptyState(title = strings.chat.emptyMemories)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(userMemories, key = { it.id }) { mem ->
                                GuCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GuBadge(mem.category.name, variant = GuBadgeVariant.BLUE)
                                        Spacer(Modifier.size(6.dp))
                                        Text("~${mem.estimatedTokens}t", color = c.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Switch(checked = mem.enabled, onCheckedChange = { onToggleMemoryEnabled(mem.id) })
                                        IconButton(onClick = { onToggleMemoryPin(mem.id) }) {
                                            Icon(Icons.Default.PushPin, contentDescription = null, tint = if (mem.pinned) c.accent else c.muted)
                                        }
                                        IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger)
                                        }
                                    }
                                    Text(mem.content, color = c.ink, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("fact", "relation", "promise", "event", "quest").forEach { k ->
                            GuPill(strings.chat.ledgerKinds[k] ?: k, newLedgerKind == k, { newLedgerKind = k })
                        }
                    }
                    GuTextField(
                        value = newLedgerTitle,
                        onValueChange = { newLedgerTitle = it },
                        placeholder = strings.chat.ledgerTitlePlaceholder,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(
                            value = newLedgerContent,
                            onValueChange = { newLedgerContent = it },
                            placeholder = strings.chat.ledgerContentPlaceholder,
                            modifier = Modifier.weight(1f),
                        )
                        GuButton(
                            onClick = {
                                onAddLedger(newLedgerKind, newLedgerTitle, newLedgerContent)
                                newLedgerTitle = ""
                                newLedgerContent = ""
                            },
                            compact = true,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (ledgerItems.isEmpty()) {
                        GuEmptyState(title = strings.chat.ledgerEmpty)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(ledgerItems, key = { it.id }) { item ->
                                GuCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GuBadge(strings.chat.ledgerKinds[item.kind] ?: item.kind, variant = GuBadgeVariant.HONEY)
                                        Spacer(Modifier.size(6.dp))
                                        Text(item.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (item.stale) GuBadge("stale", variant = GuBadgeVariant.DANGER)
                                        IconButton(onClick = { onToggleLedgerPin(item.id) }) {
                                            Icon(Icons.Default.PushPin, contentDescription = null, tint = if (item.pinned) c.accent else c.muted)
                                        }
                                        IconButton(onClick = { onDeleteLedger(item.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger)
                                        }
                                    }
                                    Text(item.content, color = c.muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (episodes.isEmpty()) {
                        GuEmptyState(title = strings.chat.episodesEmpty)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(episodes, key = { it.id }) { ep ->
                                GuCard {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val lvlName = when (ep.level) {
                                            3 -> "Chronicle L3"
                                            2 -> "Arc L2"
                                            else -> "Episode L1"
                                        }
                                        GuBadge(lvlName, variant = GuBadgeVariant.BLUE)
                                        GuBadge("Seq ${ep.fromSeq}~${ep.toSeq}", variant = GuBadgeVariant.NEUTRAL)
                                        if (ep.stale) GuBadge("stale", variant = GuBadgeVariant.DANGER)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(ep.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(ep.summary, color = c.muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (waterline.recalledItems.isEmpty()) {
                        GuEmptyState(title = strings.chat.recallEmpty)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(waterline.recalledItems, key = { "${it.source}:${it.id}" }) { item ->
                                GuCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(item.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            GuBadge(item.source, variant = GuBadgeVariant.BLUE)
                                            item.score?.let { sc ->
                                                GuBadge(strings.chat.recallScore.fmt("score" to sc), variant = GuBadgeVariant.HONEY)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.contentPreview, color = c.muted, fontSize = 12.sp)
                                    if (item.matchedTerms.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = strings.chat.recallMatched.fmt("terms" to item.matchedTerms.joinToString(", ")),
                                            color = c.primary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
