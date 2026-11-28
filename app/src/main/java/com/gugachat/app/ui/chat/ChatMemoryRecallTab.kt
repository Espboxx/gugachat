package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.VectorIndexProgress
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.ChatMemoryRecallInspectorTab(
    userMemories: List<UserMemory>,
    ledgerItems: List<MemoryLedgerItem>,
    episodes: List<MemoryEpisode>,
    waterline: ContextWaterlineSummary,
    mutedRecallKeys: Set<String>,
    vectorIndexProgress: VectorIndexProgress,
    activeRetrievalMode: RetrievalMode,
    isRerankActive: Boolean,
    onToggleMuteRecall: ((String) -> Unit)?,
    onTogglePinRecall: ((String, String) -> Unit)?,
    onDisableRecall: ((String, String) -> Unit)?,
    onReindexVectors: (() -> Unit)?,
    onOpenContextSettings: (() -> Unit)?,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var expandedRecallKey by remember { mutableStateOf<String?>(null) }

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
    val recallBudgetText = "${FileContextBudgeter.formatShortTokens(recallUsed)} / ${FileContextBudgeter.formatShortTokens(recallBudget)}"
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Compact single-line header + slim progress bar
        item(key = "__recall_header__") {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.chat.recallTitle,
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    GuBadge(text = modePillLabel, variant = modePillVariant)
                    GuMetaText(
                        text = recallBudgetText,
                        color = if (recallOverCap) c.warning else c.muted,
                    )
                }
                if (recallBudget > 0) {
                    GuLinearProgressBar(
                        progress = recallRatio,
                        height = 3.dp,
                        color = if (recallOverCap) c.warning else c.primary,
                        trackColor = c.surfaceSoft,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Single-line narrow calm amber/danger sliding strip
        if (showCalmStrip) {
            item(key = "__recall_context_strip__") {
                val isDanger = waterline.hardOverflow
                val stripBg = if (isDanger) c.dangerBg else c.warningBg
                val stripFg = if (isDanger) c.danger else c.warning
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(stripBg)
                        .then(
                            if (onOpenContextSettings != null) {
                                Modifier.clickable(onClick = onOpenContextSettings)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isDanger) Icons.Default.WarningAmber else Icons.Default.Info,
                        contentDescription = null,
                        tint = stripFg,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stripText,
                        color = stripFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                                fontWeight = FontWeight.Bold,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = stripFg,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (activeRetrievalMode != RetrievalMode.LEXICAL_ONLY) {
            item(key = "__recall_vector_strip__") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = if (vectorIndexProgress.lastError != null) c.danger else c.primary,
                        modifier = Modifier.size(14.dp),
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
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onReindexVectors != null) {
                        Surface(
                            onClick = onReindexVectors,
                            enabled = !vectorIndexProgress.running,
                            shape = RoundedCornerShape(999.dp),
                            color = c.primarySoft,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = ctxUi.vectorReindexBtn,
                                    tint = c.primarySoftInk,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = ctxUi.vectorReindexBtn,
                                    color = c.primarySoftInk,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mutedRecallKeys.isNotEmpty() && onToggleMuteRecall != null) {
            item(key = "__recall_muted_strip__") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.warningBg.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = ctxUi.mutedSectionTitle.fmt("count" to mutedRecallKeys.size),
                        color = c.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        mutedRecallKeys.forEach { key ->
                            val label = mutedTitlesByKey[key] ?: key
                            Surface(
                                onClick = { onToggleMuteRecall(key) },
                                shape = RoundedCornerShape(999.dp),
                                color = c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = null,
                                        tint = c.warning,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = label,
                                        color = c.warning,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = ctxUi.unmuteThisTurn,
                                        tint = c.warning,
                                        modifier = Modifier.size(11.dp),
                                    )
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
                        GuMetaText(
                            text = "$groupTitle (${groupItems.size})",
                            color = c.muted,
                        )
                    }
                }
                items(groupItems, key = { "${it.source}:${it.id}" }) { item ->
                    val recallKey = "${item.source}:${item.id}"
                    val isExpanded = expandedRecallKey == recallKey
                    val isMuted = recallKey in mutedRecallKeys
                    val supportsPinOrDisable = item.source in setOf(
                        "user_memory_archival",
                        "rp_ledger",
                        "novel_lore",
                    )
                    val isPinnedOrConstant = item.triggerKind == "constant" || item.triggerKind == "pinned_ledger"
                    val (triggerLabel, triggerVariant) = resolveTriggerBadge(item.triggerKind, strings)
                    val badgeText = item.score?.let { sc ->
                        "$triggerLabel · ${String.format(Locale.US, "%.2f", sc)}"
                    } ?: triggerLabel

                    val fullText = remember(item, isExpanded, userMemories, ledgerItems, episodes) {
                        if (!isExpanded) {
                            item.contentPreview
                        } else {
                            when (item.source) {
                                "user_memory_archival" -> userMemories.firstOrNull { it.id == item.id }?.content
                                "rp_ledger" -> ledgerItems.firstOrNull { it.id == item.id }?.content
                                "episode_archived" -> episodes.firstOrNull { it.id == item.id }?.summary
                                else -> null
                            }?.takeIf { it.isNotBlank() } ?: item.contentPreview
                        }
                    }

                    val highlightedPreview = remember(fullText, item.matchedTerms, c.primarySoft, c.primarySoftInk) {
                        buildHighlightedRecallPreview(
                            text = fullText,
                            matchedTerms = item.matchedTerms,
                            highlightBg = c.primarySoft,
                            highlightFg = c.primarySoftInk,
                        )
                    }

                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                        onClick = {
                            expandedRecallKey = if (isExpanded) null else recallKey
                        },
                    ) {
                        // Row 1: [6dp badge: trigger · score] + title + ~Nt + [🔇 | 📌]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GuBadge(text = badgeText, variant = triggerVariant)
                            Text(
                                text = item.title,
                                color = c.ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GuMetaText("~${item.estimatedTokens}t")
                            if (onToggleMuteRecall != null) {
                                IconButton(
                                    onClick = { onToggleMuteRecall(recallKey) },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = if (isMuted) ctxUi.unmuteThisTurn else ctxUi.muteThisTurn,
                                        tint = if (isMuted) c.warning else c.muted.copy(alpha = 0.72f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (supportsPinOrDisable && onTogglePinRecall != null) {
                                IconButton(
                                    onClick = { onTogglePinRecall(item.source, item.id) },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = ctxUi.pinAction,
                                        tint = if (isPinnedOrConstant) c.accent else c.muted.copy(alpha = 0.72f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(5.dp))

                        // Row 2: 2-line preview with inline highlighted matched terms
                        Text(
                            text = highlightedPreview,
                            color = c.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Tap-to-expand details + secondary Disable button
                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    GuMetaText(
                                        text = localizeRecallSource(item.source, strings),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (supportsPinOrDisable && onDisableRecall != null) {
                                        Surface(
                                            onClick = { onDisableRecall(item.source, item.id) },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.dangerBg,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = c.danger,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                                Text(
                                                    text = if (isZh) "停用该条目" else ctxUi.disableAction,
                                                    color = c.danger,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
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

        if (waterline.droppedRecallCount > 0) {
            item(key = "__recall_dropped_footer__") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(13.dp),
                    )
                    GuMetaText(
                        text = ctxUi.droppedRecallFooter.fmt("count" to waterline.droppedRecallCount),
                    )
                }
            }
        }
    }
}

internal fun buildHighlightedRecallPreview(
    text: String,
    matchedTerms: List<String>,
    highlightBg: Color,
    highlightFg: Color,
): AnnotatedString {
    val cleanTerms = matchedTerms.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedByDescending { it.length }
        .toList()
    if (cleanTerms.isEmpty() || text.isEmpty()) return AnnotatedString(text)

    val spans = ArrayList<Pair<Int, Int>>()
    for (term in cleanTerms) {
        var startIndex = 0
        while (startIndex < text.length) {
            val found = text.indexOf(term, startIndex = startIndex, ignoreCase = true)
            if (found < 0) break
            val end = found + term.length
            val overlaps = spans.any { (s, e) -> found < e && end > s }
            if (!overlaps) {
                spans.add(found to end)
            }
            startIndex = end.coerceAtLeast(startIndex + 1)
        }
    }
    if (spans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        val style = SpanStyle(
            background = highlightBg,
            color = highlightFg,
            fontWeight = FontWeight.SemiBold,
        )
        for ((start, end) in spans) {
            if (start in 0 until end && end <= text.length) {
                addStyle(style, start, end)
            }
        }
    }
}

internal fun resolveTriggerBadge(triggerKind: String, strings: AppStrings): Pair<String, GuBadgeVariant> {
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

internal fun localizeMemoryCategory(cat: MemoryCategory, strings: AppStrings): String {
    val isZh = strings.language == AppLanguage.ZH
    return when (cat) {
        MemoryCategory.preference -> strings.chat.catPreference
        MemoryCategory.profile -> strings.chat.catProfile
        MemoryCategory.project -> strings.chat.catProject
        MemoryCategory.rule -> if (isZh) "规则" else "Rule"
        MemoryCategory.other -> strings.chat.catOther
    }
}

internal fun localizeRecallSource(source: String, strings: AppStrings): String {
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
