package com.gugachat.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.gugachat.app.core.theme.GuSheetHeader
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.activeVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TheaterOpeningCurtain(
    rp: RpConfig,
    hasModelSelected: Boolean,
    isZh: Boolean,
    stageAccent: Color,
    onOpenModelSheet: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val styleLabel = when (rp.playStyle) {
        RpPlayStyle.chengguang -> strings.rp.styleChengguang
        RpPlayStyle.tavern -> strings.rp.styleTavern
        RpPlayStyle.trpg -> strings.rp.styleTrpg
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hasModelSelected) {
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xCC121726),
                border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = stageAccent,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xD9101522),
            border = BorderStroke(0.8.dp, stageAccent.copy(alpha = 0.52f)),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceMetaChip(text = "✦ $styleLabel", variant = GuBadgeVariant.HONEY, onDarkStage = true)
                    ChoiceMetaChip(
                        text = if (isZh) "第${rp.state.actNumber}幕" else "Act ${rp.state.actNumber}",
                        variant = GuBadgeVariant.BLUE,
                        onDarkStage = true,
                    )
                }

                Text(
                    text = rp.title,
                    color = Color(0xFFFFF8EA),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (rp.worldview.isNotBlank()) {
                    Text(
                        text = rp.worldview,
                        color = Color(0xFFCED6E8),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val npcs = rp.cast.filter { !it.isPlayer }
                if (npcs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        npcs.take(4).forEach { npc ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x991C2438))
                                    .border(0.6.dp, stageAccent.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
                                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RpNpcPortraitAvatar(character = npc, fallbackName = npc.name, size = 24.dp)
                                Text(
                                    text = "${npc.name} ♥${npc.affinity}",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                GuButton(
                    onClick = {
                        onStarterPrompt(
                            if (isZh) "拉开第一幕帷幕，请以极具画面感的笔触描写初始场景、登场人物与互动选项。"
                            else "Raise the curtain on Act 1: vividly set the opening scene, introduce the characters, and offer my first choices.",
                        )
                    },
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.wizardStartBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TheaterHistoryLogSheet(
    rp: RpConfig,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val appTheme = LocalAppTheme.current
    val c = appTheme.colors
    val sheetBg = if (c.page.alpha < 0.95f) Color(0xFF0E1320) else c.page
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color(0x8C060912),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !appTheme.isDark
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = "${rp.title} · ${strings.rp.theaterLogBtn}",
                subtitle = strings.chat.messageCount.fmt("count" to messages.size),
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    val (speaker, npc) = if (isUser) {
                        rp.playerName.ifBlank { "Player" } to null
                    } else {
                        resolveRpSpeaker(msg, rp, strings.rp.badge)
                    }
                    val cleanText = if (isUser) {
                        msg.content
                    } else {
                        extractMessageThinkAndBody(msg.reasoning, msg.content, ConversationMode.rp).second
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isUser) c.primarySoft.copy(alpha = 0.65f) else c.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                if (!isUser) {
                                    RpNpcPortraitAvatar(character = npc, fallbackName = speaker, size = 24.dp)
                                }
                                Text(
                                    text = speaker,
                                    color = if (isUser) c.primarySoftInk else c.ink,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isUser && msg.rpInputKind != null) {
                                    val kindLabel = when (msg.rpInputKind) {
                                        RpInputKind.action -> strings.rp.inputAction
                                        RpInputKind.speech -> strings.rp.inputSay
                                        RpInputKind.thought -> strings.rp.inputThought
                                        RpInputKind.gm_command -> strings.rp.inputOoc
                                    }
                                    GuBadge(kindLabel, variant = GuBadgeVariant.BLUE)
                                }
                                if (msg.rpDiceRoll != null) {
                                    GuBadge(
                                        "🎲 ${msg.rpDiceRoll.expression}=${msg.rpDiceRoll.total}",
                                        variant = GuBadgeVariant.HONEY,
                                    )
                                }
                            }
                            if (cleanText.isNotBlank()) {
                                if (isUser) {
                                    Text(
                                        text = cleanText,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                    )
                                } else {
                                    RpRichMessageContent(
                                        rawMarkdown = cleanText,
                                        rp = rp,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                        useSerif = rp.visualTheme.useSerif,
                                        onCopyToast = {},
                                    )
                                }
                            }
                            if (!isUser) {
                                val deltas = msg.activeVariant()?.rpDeltas.orEmpty()
                                if (rp.visualTheme.showTurnDeltas && deltas.isNotEmpty()) {
                                    RpTurnDeltaStrip(
                                        deltas = deltas,
                                        onDarkStage = appTheme.isDark,
                                    )
                                }
                                msg.activeVariant()?.rpEndingUnlocked?.let { ending ->
                                    RpEndingCard(ending = ending)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GREETING_WHITESPACE_RE = Regex("\\s+")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RpGreetingPickerSheet(
    greetings: List<String>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            GuSheetHeader(
                title = if (isZh) "选择开场白" else "Choose Opening Greeting",
                subtitle = if (isZh) "共 ${greetings.size} 个开场分支，发送首条消息前可随时切换" else "${greetings.size} opening greetings available before turn 1",
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(greetings.size, key = { it }) { idx ->
                    val raw = greetings[idx]
                    val preview = raw.trim().replace(GREETING_WHITESPACE_RE, " ")
                    val selected = idx == activeIndex
                    Surface(
                        onClick = { onSelect(idx) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) c.primarySoft else c.surface,
                        shadowElevation = if (selected) 0.dp else 0.8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = when {
                                        idx == 0 && isZh -> "主开场 #1"
                                        idx == 0 -> "Main Greeting #1"
                                        isZh -> "备用开场 #${idx + 1}"
                                        else -> "Alternate Greeting #${idx + 1}"
                                    },
                                    color = if (selected) c.primarySoftInk else c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = preview,
                                    color = c.muted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.5.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = c.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
