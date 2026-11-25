package com.gugachat.app.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import java.util.UUID

data class ApiProviderDraft(
    val editingId: String? = null,
    val numericId: Long = 0L,
    val protocol: ApiProtocol = ApiProtocol.OPENAI_CHAT,
    val name: String = "",
    val baseUrl: String = ApiProtocol.OPENAI_CHAT.defaultBaseUrl,
    val apiKey: String = "",
    val customModelsText: String = "",
) {
    fun toConfig(): ApiProviderConfig = ApiProviderConfig(
        id = editingId ?: UUID.randomUUID().toString(),
        numericId = numericId,
        name = name.trim(),
        protocol = protocol,
        baseUrl = baseUrl.trim().ifBlank { protocol.defaultBaseUrl },
        apiKey = apiKey.trim(),
        customModels = customModelsText.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )

    companion object {
        fun fromConfig(config: ApiProviderConfig): ApiProviderDraft = ApiProviderDraft(
            editingId = config.id,
            numericId = config.numericId,
            protocol = config.protocol,
            name = config.name,
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            customModelsText = config.customModels.joinToString(", "),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiProviderFormContent(
    draft: ApiProviderDraft,
    isTesting: Boolean,
    testSuccessCount: Int?,
    testError: String?,
    onProtocolChange: (ApiProtocol) -> Unit,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCustomModelsChange: (String) -> Unit,
    onTestConnection: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var keyVisible by remember { mutableStateOf(false) }
    val canTest = draft.baseUrl.isNotBlank() && draft.apiKey.isNotBlank() && !isTesting

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.providers.protocolLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ApiProtocol.entries.forEach { proto ->
                GuPill(
                    label = strings.providers.protocolShortNames[proto.wireValue] ?: proto.displayName,
                    selected = draft.protocol == proto,
                    onClick = { onProtocolChange(proto) },
                )
            }
        }

        GuTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = strings.providers.nameLabel,
            placeholder = strings.providers.namePlaceholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        GuTextField(
            value = draft.baseUrl,
            onValueChange = onBaseUrlChange,
            label = strings.providers.baseUrlLabel,
            placeholder = draft.protocol.defaultBaseUrl,
            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = c.muted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )

        GuTextField(
            value = draft.apiKey,
            onValueChange = onApiKeyChange,
            label = strings.providers.apiKeyLabel,
            placeholder = strings.providers.apiKeyPlaceholder,
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = c.muted) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = c.muted,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )

        GuTextField(
            value = draft.customModelsText,
            onValueChange = onCustomModelsChange,
            label = strings.providers.customModelsLabel,
            placeholder = strings.providers.customModelsPlaceholder,
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuButton(
                onClick = onTestConnection,
                enabled = canTest,
                loading = isTesting,
                compact = true,
                variant = GuButtonVariant.SECONDARY,
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isTesting) strings.providers.testingBtn else strings.providers.testBtn,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (testSuccessCount != null) {
                GuBadge(
                    text = strings.providers.testOkFormat.fmt("count" to testSuccessCount),
                    variant = GuBadgeVariant.EMERALD,
                )
            }
        }

        if (!testError.isNullOrBlank()) {
            Text(testError, color = c.danger, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProviderEditorSheet(
    initialDraft: ApiProviderDraft,
    isTesting: Boolean,
    testSuccessCount: Int?,
    testError: String?,
    onDraftChange: (ApiProviderDraft) -> Unit,
    onTestConnection: (ApiProviderDraft) -> Unit,
    onSave: (ApiProviderDraft) -> Unit,
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
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (initialDraft.editingId == null) strings.providers.newProviderTitle else strings.providers.editProviderTitle,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.common.close,
                        tint = c.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(color = c.border)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ApiProviderFormContent(
                    draft = initialDraft,
                    isTesting = isTesting,
                    testSuccessCount = testSuccessCount,
                    testError = testError,
                    onProtocolChange = { nextProto ->
                        val shouldReplaceBase = initialDraft.baseUrl.isBlank() ||
                            ApiProtocol.entries.any { it.defaultBaseUrl == initialDraft.baseUrl.trim() }
                        onDraftChange(
                            initialDraft.copy(
                                protocol = nextProto,
                                baseUrl = if (shouldReplaceBase) nextProto.defaultBaseUrl else initialDraft.baseUrl,
                            ),
                        )
                    },
                    onNameChange = { onDraftChange(initialDraft.copy(name = it)) },
                    onBaseUrlChange = { onDraftChange(initialDraft.copy(baseUrl = it)) },
                    onApiKeyChange = { onDraftChange(initialDraft.copy(apiKey = it)) },
                    onCustomModelsChange = { onDraftChange(initialDraft.copy(customModelsText = it)) },
                    onTestConnection = { onTestConnection(initialDraft) },
                )
            }

            HorizontalDivider(color = c.border)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                GuButton(
                    onClick = { onSave(initialDraft) },
                    enabled = initialDraft.baseUrl.isNotBlank() && initialDraft.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.common.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ApiProviderManagerCard(
    providers: List<ApiProviderConfig>,
    onAddClick: () -> Unit,
    onEditClick: (ApiProviderConfig) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var pendingDelete by remember { mutableStateOf<ApiProviderConfig?>(null) }

    pendingDelete?.let { cfg ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = if (isZh) {
                "确定删除自定义接口「${cfg.displayTitle()}」及其保存的 API Key 吗？"
            } else {
                "Delete custom endpoint \"${cfg.displayTitle()}\" and its stored API key?"
            },
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                val id = cfg.id
                pendingDelete = null
                onDeleteClick(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    GuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.providers.managerTitle, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(strings.providers.managerSubtitle, color = c.muted, fontSize = 11.5.sp)
            }
            Spacer(Modifier.width(8.dp))
            GuButton(onClick = onAddClick, compact = true, variant = GuButtonVariant.SUBTLE) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.providers.addProviderBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (providers.isEmpty()) {
            // Borderless inset block avoids double-border over-boxing inside GuCard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceSoft)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.providers.emptyProviders,
                    color = c.muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceSoft)
                            .border(1.dp, if (p.enabled) c.primary.copy(alpha = 0.55f) else c.border, RoundedCornerShape(12.dp))
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Row 1: Title + Protocol Badge + Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = p.displayTitle(),
                                    color = c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                GuBadge(
                                    text = strings.providers.protocolShortNames[p.protocol.wireValue] ?: p.protocol.displayName,
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                            Switch(
                                checked = p.enabled,
                                onCheckedChange = { onToggleEnabled(p.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = c.primary,
                                    checkedBorderColor = c.primary,
                                    uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                                    uncheckedTrackColor = c.surface,
                                    uncheckedBorderColor = if (theme.isDark) Color(0xFF526280) else c.outline,
                                ),
                            )
                        }

                        // Row 2: URL/Key/Models metadata + 44dp Edit & Delete actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${p.baseUrl} · ${p.maskedApiKey()}",
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p.customModels.isNotEmpty()) {
                                    Text(
                                        text = p.customModels.joinToString(", "),
                                        color = c.primary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onEditClick(p) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = strings.common.edit,
                                    tint = c.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { pendingDelete = p },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = strings.common.delete,
                                    tint = c.danger,
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
