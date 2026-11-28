package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.RpConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatEmptyWelcomeCanvas(
    mode: ConversationMode,
    hasModelSelected: Boolean,
    selectedModelLabel: String,
    selectedGroupLabel: String,
    memoryEnabled: Boolean,
    rpConfig: RpConfig?,
    novelConfig: NovelConfig?,
    novelChapters: List<NovelChapter> = emptyList(),
    onOpenModelSheet: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    onOpenNovelStudio: (Int) -> Unit = {},
    onOpenRpWizard: () -> Unit = {},
    onOpenNovelWizard: () -> Unit = {},
    onStartNormalChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    if ((mode == ConversationMode.rp && rpConfig != null) || (mode == ConversationMode.novel && novelConfig != null)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!hasModelSelected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.surfaceSoft)
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = c.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            if (mode == ConversationMode.rp && rpConfig != null) {
                GuGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.primarySoft.copy(alpha = 0.85f),
                                        c.tag.copy(alpha = 0.65f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuIconTile(
                                icon = Icons.Default.Casino,
                                tint = c.primarySoftInk,
                                backgroundColor = c.surfaceSoft,
                                size = 42.dp,
                                iconSize = 21.dp,
                                cornerRadius = 12.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = rpConfig.title,
                                    color = c.ink,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (rpConfig.playerName.isNotBlank()) {
                                    Text(
                                        text = "🎭 ${rpConfig.playerName}",
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            GuBadge(strings.rp.badge, variant = GuBadgeVariant.BLUE)
                        }
                    }

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (rpConfig.worldview.isNotBlank()) {
                            Text(
                                text = rpConfig.worldview,
                                color = c.muted,
                                fontSize = 13.5.sp,
                                lineHeight = 20.5.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val npcs = rpConfig.cast.filter { !it.isPlayer }
                        if (npcs.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                npcs.forEach { npc ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.surfaceSoft)
                                            .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        RpNpcPortraitAvatar(
                                            character = npc,
                                            fallbackName = npc.name,
                                            size = 20.dp,
                                        )
                                        Text(
                                            text = "${npc.name} ♥${npc.affinity}",
                                            color = c.ink,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        GuButton(
                            onClick = {
                                onStarterPrompt(
                                    if (isZh) "开始第一幕，请描写我身处的初始场景并给出行动选择。"
                                    else "Start Scene 1: describe the opening setting and suggest my first choices.",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.rp.wizardStartBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (mode == ConversationMode.novel && novelConfig != null) {
                GuGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.surfaceSoft,
                                        c.tag.copy(alpha = if (theme.isDark) 0.55f else 0.65f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuIconTile(
                                icon = Icons.Default.AutoStories,
                                tint = c.tagInk,
                                backgroundColor = c.surface,
                                size = 42.dp,
                                iconSize = 21.dp,
                                cornerRadius = 12.dp,
                            )
                            Text(
                                text = novelConfig.title,
                                color = c.ink,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (novelConfig.genre.isNotBlank()) {
                                GuBadge(novelConfig.genre, variant = GuBadgeVariant.HONEY)
                            }
                        }
                    }

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val povLabel = when (novelConfig.pov) {
                            "first" -> strings.novel.povFirst
                            "third_omniscient" -> strings.novel.povThirdOmniscient
                            else -> strings.novel.povThirdLimited
                        }
                        val firstCh = novelChapters.firstOrNull { it.id == novelConfig.activeChapterId } ?: novelChapters.firstOrNull()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(povLabel, variant = GuBadgeVariant.BLUE)
                            GuBadge(
                                text = if (isZh) "目标 ${novelConfig.targetWordsPerChapter} 字/章" else "~${novelConfig.targetWordsPerChapter}w/ch",
                                variant = GuBadgeVariant.NEUTRAL,
                            )
                            if (novelConfig.antiAiRules) {
                                GuBadge(if (isZh) "反AI味" else "Anti-AI", variant = GuBadgeVariant.EMERALD)
                            }
                        }
                        if (novelConfig.synopsis.isNotBlank()) {
                            Text(
                                text = novelConfig.synopsis,
                                color = c.muted,
                                fontSize = 13.5.sp,
                                lineHeight = 20.5.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (firstCh != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceSoft)
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = firstCh.title,
                                    color = c.ink,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (firstCh.outline.isNotBlank()) {
                                    Text(
                                        text = firstCh.outline,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuButton(
                                onClick = { onOpenNovelStudio(3) },
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.weight(0.85f),
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.novel.studioBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = {
                                    val prompt = if (!firstCh?.outline.isNullOrBlank()) {
                                        if (isZh) "请正式开启《${firstCh!!.title}》，围绕本章细纲「${firstCh.outline}」，以有画面感和人物动作细节的场景切入故事。"
                                        else "Begin ${firstCh!!.title} with a vivid opening scene grounded in: ${firstCh.outline}"
                                    } else {
                                        if (isZh) "请正式开启第一章，以有画面感的场景切入故事。"
                                        else "Begin Chapter 1 with a vivid opening scene."
                                    }
                                    onStarterPrompt(prompt)
                                },
                                variant = GuButtonVariant.HONEY,
                                modifier = Modifier.weight(1.35f),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.novel.wizardEmptyBtn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )

            val heading = when (mode) {
                ConversationMode.rp -> rpConfig?.title?.ifBlank { strings.chat.modeRpTitle } ?: strings.chat.modeRpTitle
                ConversationMode.novel -> novelConfig?.title?.ifBlank { strings.chat.modeNovelTitle } ?: strings.chat.modeNovelTitle
                else -> strings.chat.welcomeTitle
            }
            Text(
                text = heading,
                color = c.ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            if (!hasModelSelected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(c.muted.copy(alpha = 0.5f)),
                    )
                    Text(
                        text = strings.chat.selectModel,
                        color = c.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else if (selectedModelLabel.isNotBlank()) {
                val memLabel = if (memoryEnabled) strings.chat.memoryOn else strings.chat.memoryOff
                val metaLine = buildString {
                    if (selectedGroupLabel.isNotBlank()) append("$selectedGroupLabel · ")
                    append("${strings.chat.memoryShort}$memLabel")
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(c.primary),
                    )
                    GuMetaText(
                        text = metaLine,
                        color = c.muted,
                    )
                }
            }
        }

        // 3 light quick-mode pills: Normal Chat / Immersive RP / Long-form Novel
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WelcomeModeQuickPill(
                label = if (isZh) "💬 普通对话" else "💬 Normal Chat",
                active = mode == ConversationMode.chat,
                onClick = onStartNormalChat,
            )
            WelcomeModeQuickPill(
                label = if (isZh) "🎭 沉浸文游" else "🎭 Story RP",
                active = mode == ConversationMode.rp,
                onClick = onOpenRpWizard,
            )
            WelcomeModeQuickPill(
                label = if (isZh) "📖 长篇创作" else "📖 Novel",
                active = mode == ConversationMode.novel,
                onClick = onOpenNovelWizard,
            )
        }

        if (mode == ConversationMode.chat) {
            val starters = listOf(
                StarterTileSpec(
                    icon = Icons.Default.Code,
                    tint = c.primarySoftInk,
                    bg = c.primarySoft,
                    title = strings.chat.starterCodeTitle,
                    prompt = strings.chat.starterCodePrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.ImageSearch,
                    tint = c.success,
                    bg = c.successBg,
                    title = strings.chat.starterVisionTitle,
                    prompt = strings.chat.starterVisionPrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.Description,
                    tint = c.accent,
                    bg = c.accentSoft,
                    title = strings.chat.starterDocTitle,
                    prompt = strings.chat.starterDocPrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.Psychology,
                    tint = c.primarySoftInk,
                    bg = c.primarySoft,
                    title = strings.chat.starterMemoryTitle,
                    prompt = strings.chat.starterMemoryPrompt,
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                starters.forEachIndexed { idx, spec ->
                    if (idx > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = c.border.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStarterPrompt(spec.prompt) }
                            .padding(horizontal = 6.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = spec.tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = spec.title,
                                color = c.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = spec.prompt,
                                color = c.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = c.muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WelcomeModeQuickPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) c.primarySoft else c.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (active) c.primarySoftInk else c.ink,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

internal data class StarterTileSpec(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val title: String,
    val prompt: String,
)

