package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.domain.chat.PromptPresetBlock
import kotlinx.serialization.builtins.ListSerializer

@Composable
internal fun PresetBlocksSubpage(
    presets: List<PromptPresetEntity>,
    boundPresetId: String?,
    onSelectPreset: (String?) -> Unit,
    onToggleBlock: (presetId: String, identifier: String, index: Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val activePreset = presets.firstOrNull { it.id == boundPresetId }
    val indexedBlocks = remember(activePreset?.id, activePreset?.blocksJson) {
        if (activePreset == null) {
            emptyList()
        } else {
            runCatching {
                AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), activePreset.blocksJson)
                    .mapIndexed { idx, block -> idx to block }
            }.getOrDefault(emptyList())
        }
    }
    var expandedIdx by remember(activePreset?.id) { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.common.back,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isZh) "返回" else "Back",
                    color = c.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (isZh) "提示词预设槽位" else "Prompt Preset Blocks",
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (activePreset != null) {
                TextButton(onClick = { onSelectPreset(null) }) {
                    Text(if (isZh) "停用" else "Clear", fontSize = 12.sp, color = c.danger)
                }
            }
        }
        SheetHairlineDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "__none__") {
                    GuPill(
                        label = if (isZh) "不使用预设" else "Default",
                        selected = activePreset == null,
                        onClick = { onSelectPreset(null) },
                    )
                }
                items(presets, key = { it.id }) { preset ->
                    GuPill(
                        label = preset.name.ifBlank { if (isZh) "预设" else "Preset" },
                        selected = preset.id == activePreset?.id,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }

            if (activePreset == null) {
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isZh) {
                            "当前未绑定 SillyTavern 提示词预设。上方选择预设后可查看并开关每个提示词槽位。"
                        } else {
                            "No prompt preset bound. Select a preset above to inspect and toggle prompt blocks."
                        },
                        color = c.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                val staticBlocks = indexedBlocks.filter { it.second.injectionPosition != 1 }
                val dynamicBlocks = indexedBlocks.filter { it.second.injectionPosition == 1 }

                if (staticBlocks.isNotEmpty()) {
                    PresetZoneDividerPill(
                        text = if (isZh) "⚡ 静态前缀缓存区" else "⚡ Static Prefix Cache Zone",
                        accentColor = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                    )
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        staticBlocks.forEachIndexed { pos, (origIdx, block) ->
                            if (pos > 0) SheetHairlineDivider()
                            PresetBlockRow(
                                block = block,
                                expanded = expandedIdx == origIdx,
                                isZh = isZh,
                                onToggleExpand = { expandedIdx = if (expandedIdx == origIdx) null else origIdx },
                                onToggleEnabled = { onToggleBlock(activePreset.id, block.identifier, origIdx) },
                            )
                        }
                    }
                }

                if (dynamicBlocks.isNotEmpty()) {
                    PresetZoneDividerPill(
                        text = if (isZh) "🌊 动态注入区" else "🌊 Dynamic Injection Zone",
                        accentColor = c.accent,
                        backgroundColor = c.accentSoft,
                    )
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        dynamicBlocks.forEachIndexed { pos, (origIdx, block) ->
                            if (pos > 0) SheetHairlineDivider()
                            PresetBlockRow(
                                block = block,
                                expanded = expandedIdx == origIdx,
                                isZh = isZh,
                                onToggleExpand = { expandedIdx = if (expandedIdx == origIdx) null else origIdx },
                                onToggleEnabled = { onToggleBlock(activePreset.id, block.identifier, origIdx) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PresetZoneDividerPill(
    text: String,
    accentColor: Color,
    backgroundColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun PresetBlockRow(
    block: PromptPresetBlock,
    expanded: Boolean,
    isZh: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuBadge(
                text = block.role.uppercase().take(4),
                variant = if (block.role.equals("assistant", ignoreCase = true)) GuBadgeVariant.AMBER else GuBadgeVariant.BLUE,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = block.name.ifBlank { block.identifier },
                    color = if (block.enabled) c.ink else c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    if (block.marker) add(if (isZh) "占位符" else "Marker")
                    if (block.injectionPosition == 1) add("@D${block.injectionDepth}")
                    add(block.identifier)
                }.joinToString(" · ")
                GuMetaText(text = meta, color = c.muted)
            }
            Switch(
                checked = block.enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = c.primary,
                    uncheckedThumbColor = c.muted,
                    uncheckedTrackColor = c.surfaceSoft,
                ),
                modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceSoft)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = block.content.ifBlank {
                        if (isZh) "(系统占位符槽位，发送时自动替换)" else "(Dynamic system marker slot)"
                    },
                    color = c.ink.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                )
            }
        }
    }
}
