package com.gugachat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
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
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCard
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.chat.newSessionTitle,
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = strings.chat.newSessionSubtitle,
                    color = c.muted,
                    fontSize = 12.sp,
                )
            }

            SessionModeOptionCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                accentColor = c.primary,
                borderColor = c.primary,
                title = strings.chat.modeChatTitle,
                badgeText = strings.novel.filterChat,
                badgeVariant = GuBadgeVariant.BLUE,
                description = strings.chat.modeChatDesc,
                onClick = onSelectChat,
            )

            SessionModeOptionCard(
                icon = Icons.Default.Casino,
                accentColor = c.primary,
                borderColor = c.border,
                title = strings.chat.modeRpTitle,
                badgeText = strings.rp.badge,
                badgeVariant = GuBadgeVariant.BLUE,
                description = strings.chat.modeRpDesc,
                onClick = onSelectRp,
            )

            SessionModeOptionCard(
                icon = Icons.Default.AutoStories,
                accentColor = c.accent,
                borderColor = c.accent,
                title = strings.chat.modeNovelTitle,
                badgeText = strings.novel.badge,
                badgeVariant = GuBadgeVariant.HONEY,
                description = strings.chat.modeNovelDesc,
                onClick = onSelectNovel,
            )

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SessionModeOptionCard(
    icon: ImageVector,
    accentColor: Color,
    borderColor: Color,
    title: String,
    badgeText: String,
    badgeVariant: GuBadgeVariant,
    description: String,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors

    GuCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderColor,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(30.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        color = c.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    GuBadge(text = badgeText, variant = badgeVariant)
                }
                Text(
                    text = description,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
