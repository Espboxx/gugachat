package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuCard
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionModeOptionCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    iconBg = c.primarySoft,
                    iconTint = c.primarySoftInk,
                    title = strings.chat.modeChatTitle,
                    description = strings.chat.modeChatDesc,
                    onClick = onSelectChat,
                )

                SessionModeOptionCard(
                    icon = Icons.Default.Casino,
                    iconBg = c.successBg,
                    iconTint = c.success,
                    title = strings.chat.modeRpTitle,
                    description = strings.chat.modeRpDesc,
                    onClick = onSelectRp,
                )

                SessionModeOptionCard(
                    icon = Icons.Default.AutoStories,
                    iconBg = c.tag,
                    iconTint = c.tagInk,
                    title = strings.chat.modeNovelTitle,
                    description = strings.chat.modeNovelDesc,
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
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors

    GuCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = c.border,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = description,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
