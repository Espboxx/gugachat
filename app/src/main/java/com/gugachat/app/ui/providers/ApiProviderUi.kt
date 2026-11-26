package com.gugachat.app.ui.providers

import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.gugachat.app.core.storage.ApiProviderImportBundle
import com.gugachat.app.core.storage.ApiProviderImportCodec
import com.gugachat.app.core.theme.AppThemeSpec
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private data class ProtocolVisual(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val border: Color,
)

private fun resolveProtocolVisual(protocol: ApiProtocol, theme: AppThemeSpec): ProtocolVisual {
    val c = theme.colors
    return when (protocol) {
        ApiProtocol.OPENAI_CHAT -> ProtocolVisual(
            icon = Icons.Default.Forum,
            tint = c.success,
            bg = c.successBg,
            border = c.success.copy(alpha = 0.36f),
        )
        ApiProtocol.OPENAI_RESPONSES -> ProtocolVisual(
            icon = Icons.Default.AccountTree,
            tint = c.primarySoftInk,
            bg = c.primarySoft,
            border = c.primary.copy(alpha = 0.34f),
        )
        ApiProtocol.ANTHROPIC -> ProtocolVisual(
            icon = Icons.Default.Psychology,
            tint = c.tagInk,
            bg = c.tag,
            border = c.accent.copy(alpha = 0.42f),
        )
        ApiProtocol.GEMINI -> {
            val indigoTint = if (theme.isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
            val indigoBg = if (theme.isDark) Color(0xFF1E1F42) else Color(0xFFEEF2FF)
            ProtocolVisual(
                icon = Icons.Default.AutoAwesome,
                tint = indigoTint,
                bg = indigoBg,
                border = indigoTint.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun ProtocolBadge(
    protocol: ApiProtocol,
    label: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val visual = resolveProtocolVisual(protocol, theme)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(theme.pillRadius),
        color = visual.bg,
        border = BorderStroke(theme.borderWidth, visual.border),
    ) {
        Text(
            text = label,
            color = visual.tint,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 2.5.dp),
        )
    }
}

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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var keyVisible by remember { mutableStateOf(false) }
    val canTest = draft.baseUrl.isNotBlank() && draft.apiKey.isNotBlank() && !isTesting

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Protocol Selector (clean 2x2 pill grid with semantic protocol dot)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = strings.providers.protocolLabel,
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            val rows = ApiProtocol.entries.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { proto ->
                            val selected = draft.protocol == proto
                            val visual = resolveProtocolVisual(proto, theme)
                            val label = strings.providers.protocolShortNames[proto.wireValue] ?: proto.displayName
                            val pillShape = RoundedCornerShape(12.dp)
                            Surface(
                                onClick = { onProtocolChange(proto) },
                                shape = pillShape,
                                color = if (selected) visual.bg else c.surfaceSoft.copy(alpha = if (theme.isDark) 0.72f else 0.55f),
                                border = BorderStroke(
                                    width = if (selected) 1.2.dp else theme.borderWidth,
                                    color = if (selected) visual.tint.copy(alpha = 0.65f) else c.border,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) visual.tint else visual.tint.copy(alpha = 0.55f)),
                                    )
                                    Text(
                                        text = label,
                                        color = if (selected) visual.tint else c.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
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
            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )

        GuTextField(
            value = draft.apiKey,
            onValueChange = onApiKeyChange,
            label = strings.providers.apiKeyLabel,
            placeholder = strings.providers.apiKeyPlaceholder,
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(19.dp),
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

        // Sleek Inline Connection Test Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.72f else 0.75f),
            border = BorderStroke(theme.borderWidth, c.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            text = if (isTesting) strings.providers.testingBtn else strings.providers.testBtn,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (testSuccessCount != null) {
                        GuBadge(
                            text = strings.providers.testOkFormat.fmt("count" to testSuccessCount),
                            variant = GuBadgeVariant.EMERALD,
                        )
                    } else if (testError.isNullOrBlank()) {
                        Text(
                            text = if (isZh) "校验连通并拉取模型列表" else "Verify endpoint & fetch models",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (!testError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.dangerBg,
                        border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = c.danger,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(15.dp),
                            )
                            Text(
                                text = testError,
                                color = c.danger,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val protoLabel = strings.providers.protocolShortNames[initialDraft.protocol.wireValue]
        ?: initialDraft.protocol.displayName

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
                .imePadding(),
        ) {
            GuSheetHeader(
                title = if (initialDraft.editingId == null) {
                    strings.providers.newProviderTitle
                } else {
                    strings.providers.editProviderTitle
                },
                subtitle = protoLabel,
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

            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.85f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
    showHeader: Boolean = true,
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showHeader) {
            GuSectionHeader(
                title = strings.providers.managerTitle,
                subtitle = strings.providers.managerSubtitle,
                trailing = {
                    GuButton(onClick = onAddClick, compact = true, variant = GuButtonVariant.SUBTLE) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.providers.addProviderBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }

        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
            if (providers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.Key,
                        tint = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                        size = 42.dp,
                        iconSize = 20.dp,
                        cornerRadius = 12.dp,
                    )
                    Text(
                        text = strings.providers.emptyProviders,
                        color = c.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    GuButton(
                        onClick = onAddClick,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.providers.addProviderBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                providers.forEachIndexed { index, p ->
                    val visual = resolveProtocolVisual(p.protocol, theme)
                    val protoLabel = strings.providers.protocolShortNames[p.protocol.wireValue]
                        ?: p.protocol.displayName
                    val cleanUrl = p.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                    val metaParts = buildList {
                        if (cleanUrl.isNotBlank()) add(cleanUrl)
                        val masked = p.maskedApiKey()
                        if (masked.isNotBlank()) add(masked)
                        if (p.customModels.isNotEmpty()) {
                            add(
                                if (isZh) "${p.customModels.size} 个模型" else "${p.customModels.size} models",
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditClick(p) }
                            .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 6.dp),
                    ) {
                        // Row 1: Protocol IconTile + Title + Protocol Badge + Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GuIconTile(
                                icon = visual.icon,
                                tint = visual.tint,
                                backgroundColor = visual.bg,
                                size = 34.dp,
                                iconSize = 18.dp,
                                cornerRadius = 10.dp,
                                modifier = Modifier.alpha(if (p.enabled) 1f else 0.5f),
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (p.enabled) 1f else 0.6f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = p.displayTitle(),
                                    color = c.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                ProtocolBadge(
                                    protocol = p.protocol,
                                    label = protoLabel,
                                )
                            }
                            Switch(
                                checked = p.enabled,
                                onCheckedChange = { onToggleEnabled(p.id, it) },
                                modifier = Modifier.padding(end = 6.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = c.primary,
                                    checkedBorderColor = c.primary,
                                    uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                                    uncheckedTrackColor = c.surfaceSoft,
                                    uncheckedBorderColor = c.outline,
                                ),
                            )
                        }

                        // Row 2: Compact Endpoint / Key / Model Count meta + 44dp Edit & Delete buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (p.enabled) 1f else 0.6f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = metaParts.joinToString(" · "),
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p.customModels.isNotEmpty()) {
                                    Text(
                                        text = p.customModels.joinToString(", "),
                                        color = visual.tint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
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
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = strings.common.edit,
                                    tint = c.muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { pendingDelete = p },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.common.delete,
                                    tint = c.danger,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    if (index < providers.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProviderImportSheet(
    onImportSelected: (List<ApiProviderConfig>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var subTab by remember { mutableIntStateOf(0) } // 0 = file, 1 = paste
    var pasteInput by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var parsedBundle by remember { mutableStateOf<ApiProviderImportBundle?>(null) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            errorMsg = null
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val data = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read selected file")
                    val disp = runCatching {
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                            if (cur.moveToFirst()) {
                                val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) cur.getString(idx) else null
                            } else {
                                null
                            }
                        }
                    }.getOrNull() ?: uri.lastPathSegment
                    data to disp
                }
                val bundle = withContext(Dispatchers.Default) {
                    ApiProviderImportCodec.parseBytes(bytes, fileName = name)
                }
                parsedBundle = bundle
                selectedKeys = bundle.providers.map(ApiProviderImportCodec::dedupeKey).toSet()
            } catch (e: Throwable) {
                errorMsg = e.message ?: strings.chat.importFailed
            } finally {
                isBusy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.providers.importProviderTitle,
                subtitle = strings.providers.importProviderSubtitle,
                closeContentDescription = strings.common.close,
                onClose = { if (!isBusy) onDismiss() },
            )

            val bundle = parsedBundle
            if (bundle == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GuSegmentedControl(
                        items = listOf(
                            strings.providers.importProviderModeFile,
                            strings.providers.importProviderModePaste,
                        ),
                        selectedIndex = subTab,
                        onSelect = { idx ->
                            subTab = idx
                            errorMsg = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (subTab == 0) {
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = if (isZh) {
                                        "支持 RikkaHub 备份 (.zip / settings.json)、Cherry Studio 备份 (.zip / data.json)、Chatbox Backup v2 (.zip / settings.json) 以及通用供应商 JSON 文件。"
                                    } else {
                                        "Supports RikkaHub backups (.zip / settings.json), Cherry Studio (.zip / data.json), Chatbox Backup v2 (.zip / settings.json), and generic provider JSON."
                                    },
                                    color = c.muted,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                )
                                GuButton(
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                    enabled = !isBusy,
                                    loading = isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.providers.importProviderPickBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GuTextField(
                                    value = pasteInput,
                                    onValueChange = {
                                        pasteInput = it
                                        errorMsg = null
                                    },
                                    placeholder = strings.providers.importProviderPasteHint,
                                    singleLine = false,
                                    minLines = 3,
                                    maxLines = 6,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                                                if (clip.isNotBlank()) {
                                                    pasteInput = clip
                                                    errorMsg = null
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Default.ContentPaste,
                                                contentDescription = strings.chat.importDeepSeekPasteBtn,
                                                tint = c.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                )
                                GuButton(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            errorMsg = null
                                            try {
                                                val parsed = withContext(Dispatchers.Default) {
                                                    ApiProviderImportCodec.parseText(pasteInput)
                                                }
                                                parsedBundle = parsed
                                                selectedKeys = parsed.providers.map(ApiProviderImportCodec::dedupeKey).toSet()
                                            } catch (e: Throwable) {
                                                errorMsg = e.message ?: strings.chat.importFailed
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    },
                                    enabled = !isBusy && pasteInput.isNotBlank(),
                                    loading = isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.providers.importProviderParseBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    errorMsg?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.dangerBg,
                            border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.42f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = err,
                                color = c.danger,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp,
                                modifier = Modifier.padding(11.dp),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuBadge(text = bundle.sourceLabel, variant = GuBadgeVariant.HONEY)
                        GuBadge(
                            text = if (isZh) "发现 ${bundle.providers.size} 个供应商" else "${bundle.providers.size} providers",
                            variant = GuBadgeVariant.BLUE,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = strings.chat.importDeepSeekReselect,
                            color = c.primarySoftInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable {
                                    parsedBundle = null
                                    selectedKeys = emptySet()
                                    errorMsg = null
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }

                    GuGroupedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 300.dp),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(bundle.providers, key = { _, p -> ApiProviderImportCodec.dedupeKey(p) }) { idx, p ->
                                val key = ApiProviderImportCodec.dedupeKey(p)
                                val checked = key in selectedKeys
                                val protoLabel = strings.providers.protocolShortNames[p.protocol.wireValue] ?: p.protocol.displayName
                                val cleanUrl = p.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (checked) c.primarySoft.copy(alpha = 0.5f) else Color.Transparent)
                                            .clickable {
                                                selectedKeys = if (checked) selectedKeys - key else selectedKeys + key
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = if (checked) c.primary else c.muted,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(top = 1.dp),
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Row(
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
                                                ProtocolBadge(protocol = p.protocol, label = protoLabel)
                                            }
                                            Text(
                                                text = listOfNotNull(
                                                    cleanUrl.takeIf { it.isNotBlank() },
                                                    p.maskedApiKey().takeIf { it.isNotBlank() },
                                                    p.customModels.takeIf { it.isNotEmpty() }?.let {
                                                        if (isZh) "${it.size} 个模型" else "${it.size} models"
                                                    },
                                                ).joinToString(" · "),
                                                color = c.muted,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (idx < bundle.providers.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 42.dp),
                                            thickness = theme.borderWidth,
                                            color = c.border.copy(alpha = 0.78f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.8f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val selectedList = bundle.providers.filter { ApiProviderImportCodec.dedupeKey(it) in selectedKeys }
                    GuButton(
                        onClick = {
                            if (selectedList.isNotEmpty()) {
                                onImportSelected(selectedList)
                                onDismiss()
                            }
                        },
                        enabled = selectedList.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.providers.importProviderConfirmBtn.fmt("count" to selectedList.size),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
