package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionChooserSheet(
    onSelectChat: () -> Unit,
    onSelectRp: () -> Unit,
    onSelectNovel: () -> Unit,
    onDismiss: () -> Unit,
    recentCards: List<CharacterCardEntity> = emptyList(),
    onSelectCard: (String) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chatVm = LocalChatViewModel.current
    val vmCards = chatVm?.ui?.collectAsStateWithLifecycle()?.value?.characterCards
    val effectiveRecentCards = remember(recentCards, vmCards) {
        (if (recentCards.isNotEmpty()) recentCards else vmCards.orEmpty()).take(4)
    }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.newSessionTitle,
                subtitle = strings.chat.newSessionSubtitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SessionModeOptionCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    iconBg = c.primarySoft,
                    iconTint = c.primarySoftInk,
                    title = strings.chat.modeChatTitle,
                    description = strings.chat.modeChatDesc,
                    features = if (isZh) {
                        listOf("深度思考", "视觉识图", "长期记忆")
                    } else {
                        listOf("DeepThink", "Vision", "Long Memory")
                    },
                    onClick = onSelectChat,
                )

                SessionModeOptionCard(
                    icon = Icons.Default.Casino,
                    iconBg = c.successBg,
                    iconTint = c.success,
                    title = strings.chat.modeRpTitle,
                    description = strings.chat.modeRpDesc,
                    features = if (isZh) {
                        listOf("橙光剧场", "酒馆角色卡", "分支与骰子")
                    } else {
                        listOf("VN Theater", "Tavern Cards", "Branches & Dice")
                    },
                    onClick = onSelectRp,
                )

                if (effectiveRecentCards.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuMetaText(
                            text = if (isZh) "最近角色卡 · 一键开演" else "Recent Cards · 1-Tap Launch",
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(effectiveRecentCards, key = { it.id }) { card ->
                                val avatarModel = remember(card.avatarPath, card.spritePath) {
                                    LocalMediaPathResolver.resolveImageModel(
                                        ctx,
                                        card.avatarPath ?: card.spritePath,
                                    )
                                }
                                Surface(
                                    onClick = {
                                        if (chatVm != null) {
                                            chatVm.createRpSessionFromCharacterCard(card.id)
                                            onDismiss()
                                        } else {
                                            onSelectCard(card.id)
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = c.surface,
                                    shadowElevation = 0.8.dp,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .widthIn(max = 160.dp)
                                            .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (avatarModel != null) {
                                            Image(
                                                painter = rememberAsyncImagePainter(avatarModel),
                                                contentDescription = card.name,
                                                contentScale = ContentScale.Crop,
                                                alignment = Alignment.TopCenter,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(c.primary, c.accent),
                                                        ),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = card.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "✦",
                                                    color = Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Text(
                                            text = card.name.ifBlank { if (isZh) "角色卡" else "Card" },
                                            color = c.ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SessionModeOptionCard(
                    icon = Icons.Default.AutoStories,
                    iconBg = c.tag,
                    iconTint = c.tagInk,
                    title = strings.chat.modeNovelTitle,
                    description = strings.chat.modeNovelDesc,
                    features = if (isZh) {
                        listOf("分章大纲", "世界设定集", "纯净阅读")
                    } else {
                        listOf("Chapters", "Lorebook", "Pure Reader")
                    },
                    onClick = onSelectNovel,
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SessionModeOptionCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String,
    features: List<String>,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors

    GuCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuIconTile(
                icon = icon,
                tint = iconTint,
                backgroundColor = iconBg,
                size = 44.dp,
                iconSize = 22.dp,
                cornerRadius = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    color = c.muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
                if (features.isNotEmpty()) {
                    GuMetaText(
                        text = features.joinToString(" · "),
                        color = c.muted.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = c.muted.copy(alpha = 0.65f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
