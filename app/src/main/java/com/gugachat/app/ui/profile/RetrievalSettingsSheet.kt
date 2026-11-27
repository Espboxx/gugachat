package com.gugachat.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.DiscoveredModelKind
import com.gugachat.app.data.api.DiscoveredRetrievalModel
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalModelDownloadState
import com.gugachat.app.data.local.LocalModelSpec
import com.gugachat.app.domain.chat.REMOTE_EMBEDDING_PRESETS
import com.gugachat.app.domain.chat.REMOTE_RERANK_PRESETS
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.VectorIndexProgress
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RetrievalSettingsSheet(
    retrievalSettings: RetrievalSettings,
    apiProviders: List<ApiProviderConfig>,
    localModelSpecs: List<LocalModelSpec>,
    localModelStates: Map<String, LocalModelDownloadState>,
    vectorIndexProgress: VectorIndexProgress,
    indexedVectorCount: Int,
    isLocalEmbedLoaded: Boolean,
    embeddingTestStatus: String?,
    isTestingEmbedding: Boolean,
    discoveredRetrievalModels: List<DiscoveredRetrievalModel>,
    isFetchingRetrievalModels: Boolean,
    onSetRetrievalMode: (RetrievalMode) -> Unit,
    onSelectLocalModel: (String) -> Unit,
    onDownloadLocalModel: (String) -> Unit,
    onCancelLocalModelDownload: (String) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    onUnloadLocalModelNow: () -> Unit,
    onUpdateRemoteEmbeddingConfig: (RemoteEmbeddingConfig) -> Unit,
    onUpdateRemoteRerankConfig: (RemoteRerankConfig) -> Unit,
    onTestRemoteEmbedding: () -> Unit,
    onFetchRemoteRetrievalModels: (String, String, String, ApiProtocol) -> Unit,
    onClearVectorCache: () -> Unit,
    onDismiss: () -> Unit,
    memoryBudget: String = "4K",
    onSetMemoryBudget: ((String) -> Unit)? = null,
    sseAutoRetryCount: Int = 3,
    onSetSseAutoRetryCount: ((Int) -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val ctxUi = strings.contextUi
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val embedCfg = retrievalSettings.remoteEmbedding
    val rerankCfg = retrievalSettings.remoteRerank

    // Local text field drafts prevent IME cursor jitter during async DataStore / SecretVaultStore persistence
    var embedBaseUrlDraft by remember { mutableStateOf(embedCfg.customBaseUrl) }
    var lastDispatchedEmbedBaseUrl by remember { mutableStateOf(embedCfg.customBaseUrl) }
    var embedApiKeyDraft by remember { mutableStateOf(embedCfg.customApiKey) }
    var lastDispatchedEmbedApiKey by remember { mutableStateOf(embedCfg.customApiKey) }
    var embedModelIdDraft by remember { mutableStateOf(embedCfg.modelId) }
    var lastDispatchedEmbedModelId by remember { mutableStateOf(embedCfg.modelId) }
    var showEmbedApiKey by remember { mutableStateOf(false) }

    var rerankBaseUrlDraft by remember { mutableStateOf(rerankCfg.customBaseUrl) }
    var lastDispatchedRerankBaseUrl by remember { mutableStateOf(rerankCfg.customBaseUrl) }
    var rerankApiKeyDraft by remember { mutableStateOf(rerankCfg.customApiKey) }
    var lastDispatchedRerankApiKey by remember { mutableStateOf(rerankCfg.customApiKey) }
    var rerankModelIdDraft by remember { mutableStateOf(rerankCfg.modelId) }
    var lastDispatchedRerankModelId by remember { mutableStateOf(rerankCfg.modelId) }
    var showRerankApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(embedCfg.customBaseUrl) {
        if (embedCfg.customBaseUrl != lastDispatchedEmbedBaseUrl && embedCfg.customBaseUrl != embedBaseUrlDraft) {
            embedBaseUrlDraft = embedCfg.customBaseUrl
            lastDispatchedEmbedBaseUrl = embedCfg.customBaseUrl
        }
    }
    LaunchedEffect(embedCfg.customApiKey) {
        if (embedCfg.customApiKey != lastDispatchedEmbedApiKey.trim() && embedCfg.customApiKey != embedApiKeyDraft.trim()) {
            embedApiKeyDraft = embedCfg.customApiKey
            lastDispatchedEmbedApiKey = embedCfg.customApiKey
        }
    }
    LaunchedEffect(embedCfg.modelId) {
        if (embedCfg.modelId != lastDispatchedEmbedModelId && embedCfg.modelId != embedModelIdDraft) {
            embedModelIdDraft = embedCfg.modelId
            lastDispatchedEmbedModelId = embedCfg.modelId
        }
    }
    LaunchedEffect(rerankCfg.customBaseUrl) {
        if (rerankCfg.customBaseUrl != lastDispatchedRerankBaseUrl && rerankCfg.customBaseUrl != rerankBaseUrlDraft) {
            rerankBaseUrlDraft = rerankCfg.customBaseUrl
            lastDispatchedRerankBaseUrl = rerankCfg.customBaseUrl
        }
    }
    LaunchedEffect(rerankCfg.customApiKey) {
        if (rerankCfg.customApiKey != lastDispatchedRerankApiKey.trim() && rerankCfg.customApiKey != rerankApiKeyDraft.trim()) {
            rerankApiKeyDraft = rerankCfg.customApiKey
            lastDispatchedRerankApiKey = rerankCfg.customApiKey
        }
    }
    LaunchedEffect(rerankCfg.modelId) {
        if (rerankCfg.modelId != lastDispatchedRerankModelId && rerankCfg.modelId != rerankModelIdDraft) {
            rerankModelIdDraft = rerankCfg.modelId
            lastDispatchedRerankModelId = rerankCfg.modelId
        }
    }

    if (showClearCacheConfirm) {
        GuConfirmDialog(
            title = ctxUi.vectorCacheClearConfirmTitle,
            message = ctxUi.vectorCacheClearConfirmMsg,
            confirmText = ctxUi.vectorCacheClearBtn,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                showClearCacheConfirm = false
                onClearVectorCache()
            },
            onDismiss = { showClearCacheConfirm = false },
        )
    }

    val modes = remember {
        listOf(
            RetrievalMode.LEXICAL_ONLY,
            RetrievalMode.LOCAL_SMALL_MODEL,
            RetrievalMode.REMOTE_API,
        )
    }
    val selectedModeIndex = modes.indexOf(retrievalSettings.mode).coerceAtLeast(0)
    val modeLabels = remember(ctxUi) {
        listOf(ctxUi.modeLexicalTab, ctxUi.modeLocalTab, ctxUi.modeRemoteTab)
    }

    val embedCompatibleProviders = remember(apiProviders) {
        apiProviders.filter { it.protocol != ApiProtocol.ANTHROPIC }
    }
    val rerankCompatibleProviders = remember(apiProviders) {
        apiProviders.filter { it.protocol != ApiProtocol.ANTHROPIC && it.protocol != ApiProtocol.GEMINI }
    }

    val discoveredEmbedModels = remember(discoveredRetrievalModels) {
        val exact = discoveredRetrievalModels.filter { it.kind == DiscoveredModelKind.EMBEDDING }
        if (exact.isNotEmpty()) exact else discoveredRetrievalModels
    }
    val discoveredRerankModels = remember(discoveredRetrievalModels) {
        discoveredRetrievalModels.filter { it.kind == DiscoveredModelKind.RERANK }
    }

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
                .fillMaxHeight(0.90f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = ctxUi.retrievalSheetTitle,
                subtitle = ctxUi.retrievalSheetSubtitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            // 1. 3-Mode Segmented Control
            GuSegmentedControl(
                items = modeLabels,
                selectedIndex = selectedModeIndex,
                onSelect = { idx ->
                    modes.getOrNull(idx)?.let(onSetRetrievalMode)
                },
                compact = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (retrievalSettings.mode) {
                    RetrievalMode.LEXICAL_ONLY -> {
                        // 2. Lexical Mode Summary Card
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.Bolt,
                                    tint = c.success,
                                    backgroundColor = c.successBg,
                                    size = 36.dp,
                                    iconSize = 20.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = ctxUi.lexicalCardTitle,
                                        color = c.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = ctxUi.lexicalCardDesc,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                LexicalFeatureRow(ctxUi.lexicalBullet1)
                                LexicalFeatureRow(ctxUi.lexicalBullet2)
                                LexicalFeatureRow(ctxUi.lexicalBullet3)
                            }
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = c.successBg,
                                border = null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = ctxUi.lexicalStatsFooter,
                                    color = c.success,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    RetrievalMode.LOCAL_SMALL_MODEL -> {
                        // 3. Local Tiny Model Section (slimmer cards, unified runtime hint at bottom)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuSectionHeader(
                                title = ctxUi.localModelsSectionTitle,
                                subtitle = ctxUi.localModelsSectionSub,
                            )

                            localModelSpecs.forEach { spec ->
                                val isSelected = retrievalSettings.localModelId == spec.id
                                val dlState = localModelStates[spec.id] ?: LocalModelDownloadState.NotDownloaded
                                val isRecommended = spec.id == LocalEmbeddingModelManager.DEFAULT_MODEL_ID

                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    borderColor = if (isSelected) c.primary else null,
                                    onClick = { onSelectLocalModel(spec.id) },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) {
                                                Icons.Default.RadioButtonChecked
                                            } else {
                                                Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) c.primary else c.muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = spec.displayName,
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isRecommended) {
                                            GuBadge(text = ctxUi.localRecommendedBadge, variant = GuBadgeVariant.HONEY)
                                        }
                                        GuBadge(text = "~${spec.approxSizeMb} MB", variant = GuBadgeVariant.NEUTRAL)
                                    }

                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = if (isZh) spec.descriptionZh else spec.descriptionEn,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 16.5.sp,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        GuBadge(text = "${spec.dimension}d · ${spec.maxTokens}t", variant = GuBadgeVariant.BLUE)
                                        when (dlState) {
                                            is LocalModelDownloadState.Ready -> {
                                                GuBadge(
                                                    text = "${ctxUi.localReadyBadge} · ${formatSizeMb(dlState.sizeBytes)}",
                                                    variant = GuBadgeVariant.EMERALD,
                                                )
                                            }
                                            is LocalModelDownloadState.NotDownloaded -> {
                                                GuBadge(text = ctxUi.localNotDownloadedBadge, variant = GuBadgeVariant.NEUTRAL)
                                            }
                                            is LocalModelDownloadState.Error -> {
                                                GuBadge(text = ctxUi.localRetryBtn, variant = GuBadgeVariant.DANGER)
                                            }
                                            is LocalModelDownloadState.Downloading -> Unit
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    when (dlState) {
                                        is LocalModelDownloadState.Downloading -> {
                                            val pct = if (dlState.totalBytes > 0L) {
                                                ((dlState.bytesRead * 100L) / dlState.totalBytes).toInt().coerceIn(0, 100)
                                            } else {
                                                0
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (dlState.totalBytes > 0L) {
                                                    LinearProgressIndicator(
                                                        progress = { (dlState.bytesRead.toFloat() / dlState.totalBytes.toFloat()).coerceIn(0f, 1f) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(6.dp)
                                                            .clip(RoundedCornerShape(999.dp)),
                                                        color = c.primary,
                                                        trackColor = c.surfaceSoft,
                                                    )
                                                } else {
                                                    LinearProgressIndicator(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(6.dp)
                                                            .clip(RoundedCornerShape(999.dp)),
                                                        color = c.primary,
                                                        trackColor = c.surfaceSoft,
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(
                                                        text = ctxUi.localDownloadingLabel.fmt(
                                                            "pct" to pct,
                                                            "mirror" to dlState.mirrorLabel,
                                                        ),
                                                        color = c.muted,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    GuButton(
                                                        onClick = { onCancelLocalModelDownload(spec.id) },
                                                        compact = true,
                                                        variant = GuButtonVariant.SECONDARY,
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(ctxUi.localCancelBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }

                                        is LocalModelDownloadState.Ready -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                GuButton(
                                                    onClick = { onDeleteLocalModel(spec.id) },
                                                    compact = true,
                                                    variant = GuButtonVariant.SECONDARY,
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = c.danger,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = ctxUi.localDeleteBtn,
                                                        color = c.danger,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        }

                                        is LocalModelDownloadState.Error -> {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = dlState.message,
                                                    color = c.danger,
                                                    fontSize = 11.5.sp,
                                                    lineHeight = 16.sp,
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    GuButton(
                                                        onClick = { onDownloadLocalModel(spec.id) },
                                                        compact = true,
                                                        variant = GuButtonVariant.PRIMARY,
                                                    ) {
                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(5.dp))
                                                        Text(ctxUi.localRetryBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }

                                        LocalModelDownloadState.NotDownloaded -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                GuButton(
                                                    onClick = { onDownloadLocalModel(spec.id) },
                                                    compact = true,
                                                    variant = GuButtonVariant.PRIMARY,
                                                ) {
                                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(ctxUi.localDownloadBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Live RAM Status Card + unified runtime highlights line
                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    GuIconTile(
                                        icon = Icons.Default.Memory,
                                        tint = if (isLocalEmbedLoaded) c.success else c.muted,
                                        backgroundColor = if (isLocalEmbedLoaded) c.successBg else c.surfaceSoft,
                                        size = 34.dp,
                                        iconSize = 18.dp,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isLocalEmbedLoaded) c.success else c.muted.copy(alpha = 0.55f)),
                                            )
                                            Text(
                                                text = ctxUi.localRamTitle,
                                                color = c.ink,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Text(
                                            text = if (isLocalEmbedLoaded) ctxUi.localRamLoaded else ctxUi.localRamSleeping,
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                        )
                                    }
                                    if (isLocalEmbedLoaded) {
                                        GuButton(
                                            onClick = onUnloadLocalModelNow,
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                        ) {
                                            Text(ctxUi.localUnloadNowBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                GuMetaText(
                                    text = ctxUi.localHighlightsLine,
                                    color = c.primarySoftInk,
                                    maxLines = 2,
                                )
                            }
                        }
                    }

                    RetrievalMode.REMOTE_API -> {
                        // 4A. Remote / LAN Embedding Config Card
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuSectionHeader(
                                title = ctxUi.remoteEmbedSectionTitle,
                                subtitle = ctxUi.remoteEmbedSectionSub,
                            )

                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = ctxUi.remoteProviderLabel,
                                    color = c.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GuPill(
                                        label = ctxUi.remoteProviderCustom,
                                        selected = embedCfg.providerId.isBlank(),
                                        onClick = {
                                            onUpdateRemoteEmbeddingConfig(embedCfg.copy(providerId = ""))
                                        },
                                    )
                                    embedCompatibleProviders.forEach { prov ->
                                        GuPill(
                                            label = prov.name.ifBlank { prov.protocol.name },
                                            selected = embedCfg.providerId == prov.id,
                                            onClick = {
                                                onUpdateRemoteEmbeddingConfig(
                                                    embedCfg.copy(
                                                        providerId = prov.id,
                                                        protocol = prov.protocol,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }

                                if (embedCfg.providerId.isBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = ctxUi.remoteProtocolLabel,
                                        color = c.ink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GuPill(
                                            label = ctxUi.remoteProtocolOpenAi,
                                            selected = embedCfg.protocol != ApiProtocol.GEMINI,
                                            onClick = {
                                                onUpdateRemoteEmbeddingConfig(embedCfg.copy(protocol = ApiProtocol.OPENAI_CHAT))
                                            },
                                        )
                                        GuPill(
                                            label = ctxUi.remoteProtocolGemini,
                                            selected = embedCfg.protocol == ApiProtocol.GEMINI,
                                            onClick = {
                                                onUpdateRemoteEmbeddingConfig(embedCfg.copy(protocol = ApiProtocol.GEMINI))
                                            },
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    GuTextField(
                                        value = embedBaseUrlDraft,
                                        onValueChange = {
                                            embedBaseUrlDraft = it
                                            lastDispatchedEmbedBaseUrl = it
                                            onUpdateRemoteEmbeddingConfig(embedCfg.copy(customBaseUrl = it))
                                        },
                                        label = ctxUi.remoteBaseUrlLabel,
                                        placeholder = ctxUi.remoteBaseUrlPlaceholder,
                                    )

                                    Spacer(Modifier.height(10.dp))
                                    GuTextField(
                                        value = embedApiKeyDraft,
                                        onValueChange = {
                                            embedApiKeyDraft = it
                                            lastDispatchedEmbedApiKey = it
                                            onUpdateRemoteEmbeddingConfig(embedCfg.copy(customApiKey = it))
                                        },
                                        label = ctxUi.remoteApiKeyLabel,
                                        placeholder = ctxUi.remoteApiKeyPlaceholder,
                                        visualTransformation = if (showEmbedApiKey) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { showEmbedApiKey = !showEmbedApiKey }) {
                                                Icon(
                                                    imageVector = if (showEmbedApiKey) {
                                                        Icons.Default.VisibilityOff
                                                    } else {
                                                        Icons.Default.Visibility
                                                    },
                                                    contentDescription = null,
                                                    tint = c.muted,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GuTextField(
                                        value = embedModelIdDraft,
                                        onValueChange = {
                                            embedModelIdDraft = it
                                            lastDispatchedEmbedModelId = it
                                            onUpdateRemoteEmbeddingConfig(embedCfg.copy(modelId = it))
                                        },
                                        label = ctxUi.remoteModelIdLabel,
                                        placeholder = ctxUi.remoteModelIdPlaceholder,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuButton(
                                        onClick = {
                                            onFetchRemoteRetrievalModels(
                                                embedCfg.providerId,
                                                embedBaseUrlDraft,
                                                embedApiKeyDraft,
                                                embedCfg.protocol,
                                            )
                                        },
                                        loading = isFetchingRetrievalModels,
                                        compact = true,
                                        variant = GuButtonVariant.SECONDARY,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    ) {
                                        if (!isFetchingRetrievalModels) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = if (isFetchingRetrievalModels) {
                                                ctxUi.remoteFetchingModelsBtn
                                            } else {
                                                ctxUi.remoteFetchModelsBtn
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }

                                if (discoveredEmbedModels.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = ctxUi.remoteDiscoveredLabel,
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        discoveredEmbedModels.take(12).forEach { dm ->
                                            GuPill(
                                                label = dm.id,
                                                selected = embedModelIdDraft.equals(dm.id, ignoreCase = true),
                                                useHoneySelected = true,
                                                onClick = {
                                                    embedModelIdDraft = dm.id
                                                    onUpdateRemoteEmbeddingConfig(embedCfg.copy(modelId = dm.id))
                                                },
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = ctxUi.remotePresetsLabel,
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(REMOTE_EMBEDDING_PRESETS, key = { it.id }) { preset ->
                                        val isCurrent = embedModelIdDraft.equals(preset.modelId, ignoreCase = true) &&
                                            (embedCfg.providerId.isNotBlank() || embedBaseUrlDraft.trim().startsWith(preset.defaultBaseUrl.take(24)))
                                        GuPill(
                                            label = "${preset.providerName} · ${preset.modelId.substringAfterLast('/')}",
                                            selected = isCurrent,
                                            useHoneySelected = preset.isFreeTier,
                                            onClick = {
                                                embedModelIdDraft = preset.modelId
                                                val nextBase = if (embedCfg.providerId.isBlank()) {
                                                    embedBaseUrlDraft = preset.defaultBaseUrl
                                                    preset.defaultBaseUrl
                                                } else {
                                                    embedCfg.customBaseUrl
                                                }
                                                onUpdateRemoteEmbeddingConfig(
                                                    embedCfg.copy(
                                                        customBaseUrl = nextBase,
                                                        protocol = if (embedCfg.providerId.isBlank()) preset.protocol else embedCfg.protocol,
                                                        modelId = preset.modelId,
                                                        dimensions = if (preset.supportsMrl) embedCfg.dimensions else null,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = ctxUi.remoteDimensionsLabel,
                                    color = c.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                val dimOptions = remember { listOf(null, 512, 768, 1024) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    dimOptions.forEach { dim ->
                                        GuPill(
                                            label = dim?.toString() ?: ctxUi.remoteDimAuto,
                                            selected = embedCfg.dimensions == dim,
                                            onClick = {
                                                onUpdateRemoteEmbeddingConfig(embedCfg.copy(dimensions = dim))
                                            },
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                GuButton(
                                    onClick = onTestRemoteEmbedding,
                                    loading = isTestingEmbedding,
                                    variant = GuButtonVariant.PRIMARY,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = if (isTestingEmbedding) ctxUi.remoteTestingEmbedBtn else ctxUi.remoteTestEmbedBtn,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                if (!embeddingTestStatus.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    val isErr = embeddingTestStatus.startsWith("失败") ||
                                        embeddingTestStatus.startsWith("Failed", ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isErr) c.dangerBg else c.successBg,
                                        border = null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (isErr) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isErr) c.danger else c.success,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = embeddingTestStatus,
                                                color = if (isErr) c.danger else c.success,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 16.5.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4B. Optional Rerank Config Card
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuSectionHeader(
                                title = ctxUi.remoteRerankSectionTitle,
                                subtitle = ctxUi.remoteRerankSectionSub,
                            )

                            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                GuSwitchRow(
                                    label = ctxUi.remoteRerankEnableLabel,
                                    checked = rerankCfg.enabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateRemoteRerankConfig(rerankCfg.copy(enabled = enabled))
                                    },
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )

                                if (rerankCfg.enabled) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp,
                                        color = c.border.copy(alpha = 0.65f),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = ctxUi.remoteProviderLabel,
                                            color = c.ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            GuPill(
                                                label = ctxUi.remoteProviderCustom,
                                                selected = rerankCfg.providerId.isBlank(),
                                                onClick = {
                                                    onUpdateRemoteRerankConfig(rerankCfg.copy(providerId = ""))
                                                },
                                            )
                                            rerankCompatibleProviders.forEach { prov ->
                                                GuPill(
                                                    label = prov.name.ifBlank { prov.protocol.name },
                                                    selected = rerankCfg.providerId == prov.id,
                                                    onClick = {
                                                        onUpdateRemoteRerankConfig(rerankCfg.copy(providerId = prov.id))
                                                    },
                                                )
                                            }
                                        }

                                        if (rerankCfg.providerId.isBlank()) {
                                            GuTextField(
                                                value = rerankBaseUrlDraft,
                                                onValueChange = {
                                                    rerankBaseUrlDraft = it
                                                    lastDispatchedRerankBaseUrl = it
                                                    onUpdateRemoteRerankConfig(rerankCfg.copy(customBaseUrl = it))
                                                },
                                                label = ctxUi.remoteBaseUrlLabel,
                                                placeholder = ctxUi.remoteBaseUrlPlaceholder,
                                            )
                                            GuTextField(
                                                value = rerankApiKeyDraft,
                                                onValueChange = {
                                                    rerankApiKeyDraft = it
                                                    lastDispatchedRerankApiKey = it
                                                    onUpdateRemoteRerankConfig(rerankCfg.copy(customApiKey = it))
                                                },
                                                label = ctxUi.remoteApiKeyLabel,
                                                placeholder = ctxUi.remoteApiKeyPlaceholder,
                                                visualTransformation = if (showRerankApiKey) {
                                                    VisualTransformation.None
                                                } else {
                                                    PasswordVisualTransformation()
                                                },
                                                trailingIcon = {
                                                    IconButton(onClick = { showRerankApiKey = !showRerankApiKey }) {
                                                        Icon(
                                                            imageVector = if (showRerankApiKey) {
                                                                Icons.Default.VisibilityOff
                                                            } else {
                                                                Icons.Default.Visibility
                                                            },
                                                            contentDescription = null,
                                                            tint = c.muted,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                            )
                                        }

                                        GuTextField(
                                            value = rerankModelIdDraft,
                                            onValueChange = {
                                                rerankModelIdDraft = it
                                                lastDispatchedRerankModelId = it
                                                onUpdateRemoteRerankConfig(rerankCfg.copy(modelId = it))
                                            },
                                            label = ctxUi.remoteRerankModelLabel,
                                            placeholder = ctxUi.remoteRerankModelPlaceholder,
                                        )

                                        if (discoveredRerankModels.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                discoveredRerankModels.take(8).forEach { dm ->
                                                    GuPill(
                                                        label = dm.id,
                                                        selected = rerankModelIdDraft.equals(dm.id, ignoreCase = true),
                                                        useHoneySelected = true,
                                                        onClick = {
                                                            rerankModelIdDraft = dm.id
                                                            onUpdateRemoteRerankConfig(rerankCfg.copy(modelId = dm.id))
                                                        },
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = ctxUi.remotePresetsLabel,
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(REMOTE_RERANK_PRESETS, key = { it.id }) { preset ->
                                                GuPill(
                                                    label = "${preset.providerName} · ${preset.modelId.substringAfterLast('/')}",
                                                    selected = rerankModelIdDraft.equals(preset.modelId, ignoreCase = true),
                                                    useHoneySelected = preset.isFreeTier,
                                                    onClick = {
                                                        rerankModelIdDraft = preset.modelId
                                                        val nextBase = if (rerankCfg.providerId.isBlank()) {
                                                            rerankBaseUrlDraft = preset.defaultBaseUrl
                                                            preset.defaultBaseUrl
                                                        } else {
                                                            rerankCfg.customBaseUrl
                                                        }
                                                        onUpdateRemoteRerankConfig(
                                                            rerankCfg.copy(
                                                                customBaseUrl = nextBase,
                                                                modelId = preset.modelId,
                                                                useTeiSchema = preset.useTeiSchema,
                                                            ),
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = c.border.copy(alpha = 0.65f),
                                        )
                                        GuSwitchRow(
                                            label = ctxUi.remoteRerankTeiLabel,
                                            subtitle = ctxUi.remoteRerankTeiSub,
                                            checked = rerankCfg.useTeiSchema,
                                            onCheckedChange = { useTei ->
                                                onUpdateRemoteRerankConfig(rerankCfg.copy(useTeiSchema = useTei))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Memory Budget & Stream Auto-Retry Group (consolidated from ProfileScreen)
                if (onSetMemoryBudget != null || onSetSseAutoRetryCount != null) {
                    val memoryIndex = when (memoryBudget.trim().lowercase()) {
                        "2k", "low" -> 0
                        "8k", "high" -> 2
                        else -> 1
                    }
                    val memoryBadge = when (memoryIndex) {
                        0 -> "2K"
                        2 -> "8K"
                        else -> "4K"
                    }
                    val retryPresets = remember { listOf(0, 1, 3, 5) }
                    val retrySegIndex = retryPresets.indexOf(sseAutoRetryCount)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(
                            title = if (isZh) "记忆预算与流式容错" else "Memory Budget & Stream Resilience",
                        )
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            if (onSetMemoryBudget != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                ) {
                                    GuSettingsRow(
                                        title = strings.chat.memoryBudgetLabel,
                                        icon = Icons.Default.Psychology,
                                        iconTint = c.success,
                                        iconBg = c.successBg,
                                        showDivider = false,
                                        trailing = {
                                            GuBadge(
                                                text = memoryBadge,
                                                variant = GuBadgeVariant.EMERALD,
                                            )
                                        },
                                    )
                                    GuSegmentedControl(
                                        items = listOf(
                                            if (isZh) "低 2K" else "Low 2K",
                                            if (isZh) "标准 4K" else "Std 4K",
                                            if (isZh) "高 8K" else "High 8K",
                                        ),
                                        selectedIndex = memoryIndex,
                                        onSelect = { idx ->
                                            onSetMemoryBudget(
                                                when (idx) {
                                                    0 -> "2K"
                                                    2 -> "8K"
                                                    else -> "4K"
                                                },
                                            )
                                        },
                                        compact = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 56.dp, end = 14.dp),
                                    )
                                }
                            }

                            if (onSetMemoryBudget != null && onSetSseAutoRetryCount != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    thickness = 0.5.dp,
                                    color = c.border.copy(alpha = 0.65f),
                                )
                            }

                            if (onSetSseAutoRetryCount != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                ) {
                                    GuSettingsRow(
                                        title = strings.profile.sseRetryLabel,
                                        subtitle = strings.profile.sseRetrySubtitle,
                                        icon = Icons.Default.Autorenew,
                                        iconTint = c.accent,
                                        iconBg = c.accentSoft,
                                        showDivider = false,
                                        trailing = {
                                            GuInlineStepper(
                                                valueText = if (sseAutoRetryCount == 0) {
                                                    strings.profile.sseRetryOff
                                                } else {
                                                    strings.profile.sseRetryTimes.fmt("count" to sseAutoRetryCount)
                                                },
                                                onDecrement = {
                                                    onSetSseAutoRetryCount((sseAutoRetryCount - 1).coerceAtLeast(0))
                                                },
                                                onIncrement = {
                                                    onSetSseAutoRetryCount((sseAutoRetryCount + 1).coerceAtMost(10))
                                                },
                                                canDecrement = sseAutoRetryCount > 0,
                                                canIncrement = sseAutoRetryCount < 10,
                                            )
                                        },
                                    )
                                    GuSegmentedControl(
                                        items = listOf(
                                            strings.profile.sseRetryOff,
                                            strings.profile.sseRetryTimes.fmt("count" to 1),
                                            strings.profile.sseRetryTimes.fmt("count" to 3),
                                            strings.profile.sseRetryTimes.fmt("count" to 5),
                                        ),
                                        selectedIndex = retrySegIndex,
                                        onSelect = { idx ->
                                            retryPresets.getOrNull(idx)?.let(onSetSseAutoRetryCount)
                                        },
                                        compact = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 56.dp, end = 14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Shared Vector Cache Card (bottom)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(
                        title = ctxUi.vectorCacheTitle,
                        trailing = {
                            GuBadge(
                                text = "$indexedVectorCount",
                                variant = if (indexedVectorCount > 0) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL,
                            )
                        },
                    )
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GuIconTile(
                                icon = Icons.Default.Storage,
                                tint = c.primarySoftInk,
                                backgroundColor = c.primarySoft,
                                size = 34.dp,
                                iconSize = 18.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = ctxUi.vectorCacheCount.fmt("count" to indexedVectorCount),
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (vectorIndexProgress.running) {
                                    Text(
                                        text = ctxUi.vectorIndexRunning.fmt(
                                            "indexed" to vectorIndexProgress.indexed,
                                            "total" to vectorIndexProgress.total,
                                        ),
                                        color = c.primarySoftInk,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            GuButton(
                                onClick = { showClearCacheConfirm = true },
                                enabled = indexedVectorCount > 0 && !vectorIndexProgress.running,
                                compact = true,
                                variant = GuButtonVariant.SECONDARY,
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = if (indexedVectorCount > 0) c.danger else c.muted,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = ctxUi.vectorCacheClearBtn,
                                    color = if (indexedVectorCount > 0) c.danger else c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        if (vectorIndexProgress.running) {
                            Spacer(Modifier.height(10.dp))
                            if (vectorIndexProgress.total > 0) {
                                val ratio = (vectorIndexProgress.indexed.toFloat() / vectorIndexProgress.total.toFloat())
                                    .coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = c.primary,
                                    trackColor = c.surfaceSoft,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = c.primary,
                                    trackColor = c.surfaceSoft,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LexicalFeatureRow(text: String) {
    val c = LocalAppTheme.current.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = c.success,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
        )
        Text(
            text = text,
            color = c.ink,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatSizeMb(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 MB"
    val mb = sizeBytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
