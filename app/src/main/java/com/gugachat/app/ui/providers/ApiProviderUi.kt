package com.gugachat.app.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
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
                    useHoneySelected = proto == ApiProtocol.ANTHROPIC || proto == ApiProtocol.GEMINI,
                )
            }
        }

        GuTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = strings.providers.nameLabel,
            placeholder = strings.providers.namePlaceholder,
        )

        GuTextField(
            value = draft.baseUrl,
            onValueChange = onBaseUrlChange,
            label = strings.providers.baseUrlLabel,
            placeholder = draft.protocol.defaultBaseUrl,
            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = c.muted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        GuTextField(
            value = draft.customModelsText,
            onValueChange = onCustomModelsChange,
            label = strings.providers.customModelsLabel,
            placeholder = strings.providers.customModelsPlaceholder,
            singleLine = false,
            minLines = 1,
            maxLines = 3,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuButton(
                onClick = onTestConnection,
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initialDraft.editingId == null) strings.providers.newProviderTitle else strings.providers.editProviderTitle,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )

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

            GuButton(
                onClick = { onSave(initialDraft) },
                enabled = initialDraft.baseUrl.isNotBlank() && initialDraft.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.common.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
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
    val c = LocalAppTheme.current.colors

    GuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.providers.managerTitle, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(strings.providers.managerSubtitle, color = c.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            GuButton(onClick = onAddClick, compact = true, variant = GuButtonVariant.HONEY) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.providers.addProviderBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (providers.isEmpty()) {
            GuEmptyState(title = strings.providers.emptyProviders)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceSoft)
                            .border(1.dp, if (p.enabled) c.primary else c.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = p.displayTitle(),
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                GuBadge(
                                    text = strings.providers.protocolShortNames[p.protocol.wireValue] ?: p.protocol.displayName,
                                    variant = when (p.protocol) {
                                        ApiProtocol.ANTHROPIC, ApiProtocol.GEMINI -> GuBadgeVariant.HONEY
                                        else -> GuBadgeVariant.BLUE
                                    },
                                )
                            }
                            Text(
                                text = "${p.baseUrl} · ${p.maskedApiKey()}",
                                color = c.muted,
                                fontSize = 11.sp,
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
                        Switch(
                            checked = p.enabled,
                            onCheckedChange = { onToggleEnabled(p.id, it) },
                        )
                        IconButton(onClick = { onEditClick(p) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = strings.common.edit, tint = c.ink, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteClick(p.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
