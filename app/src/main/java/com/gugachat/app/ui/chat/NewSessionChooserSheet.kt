package com.gugachat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionChooserSheet(
    onSelectChat: () -> Unit,
    onSelectRp: () -> Unit,
    onSelectNovel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
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
                    borderTint = c.primary.copy(alpha = 0.28f),
                    title = strings.chat.modeChatTitle,
                    description = strings.chat.modeChatDesc,
                    features = if (isZh) {
                        listOf("深度思考", "视觉识图", "长期记忆")
                    } else {
                        listOf("DeepThink", "Vision", "Long Memory")
                    },
                    chipVariant = GuBadgeVariant.BLUE,
                    onClick = onSelectChat,
                )

                SessionModeOptionCard(
                    icon = Icons.Default.Casino,
                    iconBg = c.successBg,
                    iconTint = c.success,
                    borderTint = c.success.copy(alpha = 0.32f),
                    title = strings.chat.modeRpTitle,
                    description = strings.chat.modeRpDesc,
                    features = if (isZh) {
                        listOf("橙光剧场", "酒馆角色卡", "分支与骰子")
                    } else {
                        listOf("VN Theater", "Tavern Cards", "Branches & Dice")
                    },
                    chipVariant = GuBadgeVariant.EMERALD,
                    onClick = onSelectRp,
                )

                SessionModeOptionCard(
                    icon = Icons.Default.AutoStories,
                    iconBg = c.tag,
                    iconTint = c.tagInk,
                    borderTint = c.accent.copy(alpha = 0.35f),
                    title = strings.chat.modeNovelTitle,
                    description = strings.chat.modeNovelDesc,
                    features = if (isZh) {
                        listOf("分章大纲", "世界设定集", "纯净阅读")
                    } else {
                        listOf("Chapters", "Lorebook", "Pure Reader")
                    },
                    chipVariant = GuBadgeVariant.HONEY,
                    onClick = onSelectNovel,
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionModeOptionCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    borderTint: Color,
    title: String,
    description: String,
    features: List<String>,
    chipVariant: GuBadgeVariant,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors

    GuCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderTint,
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
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = description,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                )
                if (features.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        features.forEach { feat ->
                            GuBadge(text = feat, variant = chipVariant)
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = c.muted.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
