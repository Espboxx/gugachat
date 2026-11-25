package com.gugachat.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.util.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri, ctx.contentResolver, strings.chat.exportDone)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(
                uri = uri,
                contentResolver = ctx.contentResolver,
                replaceExisting = false,
                successMessage = strings.chat.importDone,
                failedMessage = strings.chat.importFailed,
            )
        }
    }

    if (showLogoutConfirm) {
        GuConfirmDialog(
            title = strings.profile.logoutConfirmTitle,
            message = strings.profile.logoutConfirmMsg,
            confirmText = strings.profile.logout,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout(strings.profile.loggedOut)
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        GuAwningHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.profile.title,
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                IconButton(onClick = viewModel::refreshProfile, enabled = !state.isRefreshing) {
                    Icon(Icons.Default.Refresh, contentDescription = strings.profile.refreshProfile, tint = c.ink)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val user = state.user
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_guguga_badge),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = user?.username?.ifBlank { user.email } ?: "GuGuUser",
                                color = c.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val isAdmin = user?.role.equals("admin", ignoreCase = true)
                            GuBadge(
                                text = if (isAdmin) strings.profile.roleAdmin else strings.profile.roleUser,
                                variant = if (isAdmin) GuBadgeVariant.HONEY else GuBadgeVariant.BLUE,
                            )
                        }
                        if (!user?.email.isNullOrBlank()) {
                            Text(
                                text = user!!.email,
                                color = c.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${strings.profile.idPrefix} ${user?.id ?: 0L}",
                            color = c.muted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(
                        label = strings.profile.statKeys,
                        value = "${user?.concurrency ?: 1}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = strings.profile.statBalance,
                        value = FormatUtils.formatBalance(user?.balance ?: 0.0),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = strings.profile.statWorks,
                        value = "${state.worksCount}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.profile.serverLabel,
                    color = c.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.settings.serverUrl.ifBlank { "-" },
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Appearance & Language Card
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(strings.profile.themeStyleLabel, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = strings.profile.themeGuguga,
                        selected = state.settings.themeStyle == ThemeStyle.GUGUGA,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.GUGUGA) },
                        useHoneySelected = true,
                    )
                    GuPill(
                        label = strings.profile.themeClassic,
                        selected = state.settings.themeStyle == ThemeStyle.CLASSIC,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.CLASSIC) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.profile.colorModeLabel, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = strings.profile.colorSystem,
                        selected = state.settings.colorMode == ColorMode.SYSTEM,
                        onClick = { viewModel.setColorMode(ColorMode.SYSTEM) },
                    )
                    GuPill(
                        label = strings.profile.colorLight,
                        selected = state.settings.colorMode == ColorMode.LIGHT,
                        onClick = { viewModel.setColorMode(ColorMode.LIGHT) },
                    )
                    GuPill(
                        label = strings.profile.colorDark,
                        selected = state.settings.colorMode == ColorMode.DARK,
                        onClick = { viewModel.setColorMode(ColorMode.DARK) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.profile.languageLabel, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = AppLanguage.ZH.displayName,
                        selected = state.settings.language == AppLanguage.ZH,
                        onClick = { viewModel.setLanguage(AppLanguage.ZH) },
                    )
                    GuPill(
                        label = AppLanguage.EN.displayName,
                        selected = state.settings.language == AppLanguage.EN,
                        onClick = { viewModel.setLanguage(AppLanguage.EN) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.chat.memoryBudgetLabel, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "standard", "high").forEach { preset ->
                        val label = when (preset) {
                            "low" -> "Low (2K)"
                            "high" -> "High (8K)"
                            else -> "Standard (4K)"
                        }
                        GuPill(
                            label = label,
                            selected = state.settings.memoryBudget == preset || (preset == "standard" && state.settings.memoryBudget == "4K"),
                            onClick = { viewModel.setMemoryBudget(preset) },
                        )
                    }
                }
            }

            // Backup & Restore Card
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(strings.profile.backupSectionTitle, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuButton(
                        onClick = { exportLauncher.launch("gugachat-backup-${System.currentTimeMillis()}.json") },
                        enabled = !state.isBackupBusy,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.profile.exportBackup, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        enabled = !state.isBackupBusy,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.profile.importBackup, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            GuButton(
                onClick = { showLogoutConfirm = true },
                variant = GuButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.profile.logout, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(c.surfaceSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(label, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}
