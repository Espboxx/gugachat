package com.gugachat.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.SavedAccount
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSwitcherSheet(
    accounts: List<SavedAccount>,
    activeAccountKey: String,
    isDirectApiOnly: Boolean,
    enabledProvidersCount: Int,
    switchingAccountKey: String?,
    onSelectAccount: (String) -> Unit,
    onSelectDirectMode: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (SavedAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingRemove by remember { mutableStateOf<SavedAccount?>(null) }

    val orderedAccounts = remember(accounts, activeAccountKey, isDirectApiOnly) {
        if (isDirectApiOnly || activeAccountKey.isBlank()) {
            accounts
        } else {
            val active = accounts.firstOrNull { it.accountKey == activeAccountKey }
            if (active != null) {
                listOf(active) + accounts.filterNot { it.accountKey == activeAccountKey }
            } else {
                accounts
            }
        }
    }

    pendingRemove?.let { target ->
        GuConfirmDialog(
            title = strings.profile.removeAccountConfirmTitle,
            message = strings.profile.removeAccountConfirmMsg.fmt("name" to target.displayName()),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                pendingRemove = null
                onRemoveAccount(target)
            },
            onDismiss = { pendingRemove = null },
        )
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
                title = strings.profile.switchAccount,
                subtitle = strings.profile.switchAccountSubtitle,
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (orderedAccounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(title = strings.profile.savedAccountsTitle)
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            orderedAccounts.forEachIndexed { index, account ->
                                val isCurrent = !isDirectApiOnly && account.accountKey == activeAccountKey
                                val isSwitching = switchingAccountKey == account.accountKey
                                val rowBg = if (isCurrent) {
                                    c.primarySoft.copy(alpha = if (theme.isDark) 0.42f else 0.55f)
                                } else {
                                    Color.Transparent
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .clickable(enabled = switchingAccountKey == null) {
                                            if (isCurrent) {
                                                onDismiss()
                                            } else {
                                                onSelectAccount(account.accountKey)
                                            }
                                        }
                                        .padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) c.primary else c.primarySoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = account.displayName().take(1).uppercase(),
                                            color = if (isCurrent) Color.White else c.primarySoftInk,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = account.displayName(),
                                                color = c.ink,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            when {
                                                isCurrent -> GuBadge(
                                                    text = strings.profile.currentAccountBadge,
                                                    variant = GuBadgeVariant.EMERALD,
                                                )
                                                account.sessionExpired -> GuBadge(
                                                    text = strings.profile.accountExpiredBadge,
                                                    variant = GuBadgeVariant.AMBER,
                                                )
                                            }
                                        }
                                        val detailParts = buildList {
                                            account.user.email.trim()
                                                .takeIf { it.isNotBlank() && !it.equals(account.displayName(), ignoreCase = true) }
                                                ?.let { add(it) }
                                            account.serverHostLabel()
                                                .takeIf { it.isNotBlank() }
                                                ?.let { add(it) }
                                            if (!account.sessionExpired) {
                                                add(FormatUtils.formatBalance(account.user.balance))
                                            }
                                        }
                                        Text(
                                            text = if (account.sessionExpired) {
                                                strings.profile.accountExpiredTapHint
                                            } else {
                                                detailParts.joinToString(" · ")
                                            },
                                            color = if (account.sessionExpired) c.warning else c.muted,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    if (isSwitching) {
                                        CircularProgressIndicator(
                                            color = c.primary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .size(16.dp),
                                        )
                                    } else if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = c.primary,
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .size(18.dp),
                                        )
                                    }

                                    IconButton(
                                        onClick = { pendingRemove = account },
                                        enabled = switchingAccountKey == null,
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = strings.common.delete,
                                            tint = c.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                if (index < orderedAccounts.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 61.dp),
                                        thickness = 0.5.dp,
                                        color = c.border.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }
                }

                // Direct API (BYOK) switch row — preserves all saved Sub2API sessions
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    val directBg = if (isDirectApiOnly) {
                        c.tag.copy(alpha = if (theme.isDark) 0.40f else 0.55f)
                    } else {
                        Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(directBg)
                            .clickable(enabled = switchingAccountKey == null) {
                                if (isDirectApiOnly) {
                                    onDismiss()
                                } else {
                                    onSelectDirectMode()
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.Key,
                            tint = c.tagInk,
                            backgroundColor = c.tag,
                            size = 38.dp,
                            iconSize = 19.dp,
                            cornerRadius = 11.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = strings.profile.directModeRowTitle,
                                    color = c.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isDirectApiOnly) {
                                    GuBadge(
                                        text = strings.profile.currentAccountBadge,
                                        variant = GuBadgeVariant.HONEY,
                                    )
                                }
                            }
                            Text(
                                text = strings.profile.directModeRowSubtitle.fmt("count" to enabledProvidersCount),
                                color = c.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isDirectApiOnly) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                GuButton(
                    onClick = onAddAccount,
                    enabled = switchingAccountKey == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.profile.addAccountBtn,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
