package com.gugachat.app.ui.images

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.ImageBinaryUpload
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageListParams
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.data.api.OnlineImagePresetDto
import com.gugachat.app.data.api.OnlineImagePresetInputDto
import com.gugachat.app.data.api.OnlineImageReferenceIdDto
import com.gugachat.app.data.api.OnlineImageSubmitInput
import com.gugachat.app.core.util.newIdempotencyKey
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.images.ImageEditMode
import com.gugachat.app.domain.images.MaskPngEncoder
import com.gugachat.app.domain.images.MaskStroke
import com.gugachat.app.domain.images.NovelAIParams
import com.gugachat.app.domain.images.OnlineImageCanvasMath
import com.gugachat.app.domain.images.OnlineImageNovelAI
import com.gugachat.app.domain.images.OnlineImageStudioUtils
import com.gugachat.app.domain.images.OutpaintAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

data class LocalRefImage(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val previewUri: Uri?,
)

data class EditorTarget(
    val generation: OnlineImageGenerationDto,
    val imageIndex: Int,
    val mode: ImageEditMode = ImageEditMode.EDIT,
)

data class ImageUiState(
    val enabled: Boolean = true,
    val unavailableReason: String? = null,
    val isLoadingCapabilities: Boolean = false,
    val keys: List<OnlineImageKeyDto> = emptyList(),
    val models: List<OnlineImageModelDto> = emptyList(),
    val selectedKeyId: Long? = null,
    val selectedModelId: String? = null,
    val prompt: String = "",
    val count: Int = 1,
    val aspectRatio: String = "auto",
    val resolution: String = "auto",
    val referenceUploads: List<LocalRefImage> = emptyList(),
    val referenceAssets: List<OnlineImageReferenceIdDto> = emptyList(),
    val novelaiParams: NovelAIParams = NovelAIParams(),
    val presets: List<OnlineImagePresetDto> = emptyList(),
    val generations: List<OnlineImageGenerationDto> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: String = "all",
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val totalWorks: Int = 0,
    val isLoadingGallery: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSubmitting: Boolean = false,
    val selectedDetail: OnlineImageGenerationDto? = null,
    val detailImageIndex: Int = 0,
    val detailVersions: List<OnlineImageGenerationDto> = emptyList(),
    val lightboxTarget: Pair<OnlineImageGenerationDto, Int>? = null,
    val editorTarget: EditorTarget? = null,
    val batchMode: Boolean = false,
    val selectedBatchIds: Set<String> = emptySet(),
    val isBatchBusy: Boolean = false,
    val showCreateSheet: Boolean = false,
    val showPresetSheet: Boolean = false,
)

class ImageViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ImageUiState())
    val ui: StateFlow<ImageUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private var pendingPortraitCallback: (suspend (ByteArray) -> Unit)? = null
    private var pendingPortraitGenId: String? = null

    init {
        viewModelScope.launch {
            container.apiProviderStore.providers.drop(1).collect {
                loadAll()
            }
        }
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingCapabilities = true) }
            try {
                val isDirectOnly = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
                val customKeys = container.apiProviderStore.enabledProviders()
                    .filter {
                        DirectProtocolCodec.supportsImageGeneration(
                            protocol = it.protocol,
                            baseUrl = it.baseUrl,
                            customModels = it.customModels,
                        )
                    }
                    .map { p ->
                        OnlineImageKeyDto(
                            id = p.syntheticGroupId,
                            name = "${p.displayTitle()} · ${p.protocol.displayName}",
                            models = container.directApiClient.listImageModels(p),
                        )
                    }
                val cap = if (isDirectOnly) null else runCatching { container.onlineImagesApi.capabilities() }.getOrElse { err ->
                    if (customKeys.isEmpty()) throw err else null
                }
                val serverKeys = cap?.keys?.let { FreeQuotaUtils.sortImageKeysByFreeQuota(it) }.orEmpty()
                val sortedKeys = serverKeys + customKeys
                val saved = container.settingsStore.currentSettings()
                val preferredKey = sortedKeys.firstOrNull { it.id == _ui.value.selectedKeyId }
                    ?: sortedKeys.firstOrNull { it.id == saved.lastImageKeyId && saved.lastImageKeyId != 0L }
                    ?: sortedKeys.firstOrNull()

                _ui.update {
                    it.copy(
                        enabled = (cap?.enabled ?: false) || sortedKeys.isNotEmpty(),
                        unavailableReason = cap?.reason,
                        keys = sortedKeys,
                        selectedKeyId = preferredKey?.id,
                        isLoadingCapabilities = false,
                    )
                }

                if (preferredKey != null) {
                    loadModelsForKey(preferredKey)
                } else {
                    _ui.update { it.copy(models = emptyList(), selectedModelId = null) }
                }
                loadPresets()
                refreshGallery()
            } catch (e: Throwable) {
                _ui.update { it.copy(isLoadingCapabilities = false) }
                container.toastController.show(e.message ?: "Failed to load image capabilities", isError = true)
            }
        }
    }

    fun selectKey(keyId: Long) {
        if (keyId == 0L) return
        val keyObj = _ui.value.keys.firstOrNull { it.id == keyId } ?: return
        _ui.update { it.copy(selectedKeyId = keyId) }
        viewModelScope.launch { loadModelsForKey(keyObj) }
    }

    private suspend fun loadModelsForKey(key: OnlineImageKeyDto) {
        val fetched = runCatching { container.onlineImagesApi.models(key.id) }
            .getOrElse { key.models }
        val sortedModels = FreeQuotaUtils.sortImageModelsByFreeQuota(fetched.ifEmpty { key.models })
        val saved = container.settingsStore.currentSettings()
        val chosen = sortedModels.firstOrNull { it.id == _ui.value.selectedModelId }
            ?: sortedModels.firstOrNull { it.id == saved.lastImageModel }
            ?: sortedModels.firstOrNull()
        _ui.update { state ->
            val prevModel = state.models.firstOrNull { it.id == state.selectedModelId }
            val nextNai = if (OnlineImageStudioUtils.isNovelAIModel(chosen)) {
                val targetVersion = OnlineImageNovelAI.detectVersion(
                    chosen!!.id,
                    chosen.parameter_schema?.upstream_model,
                )
                if (OnlineImageStudioUtils.isNovelAIModel(prevModel)) {
                    OnlineImageNovelAI.adaptParamsForVersion(state.novelaiParams, targetVersion)
                } else {
                    OnlineImageNovelAI.defaultParamsForVersion(targetVersion)
                }
            } else {
                state.novelaiParams
            }
            state.copy(
                models = sortedModels,
                selectedModelId = chosen?.id,
                novelaiParams = nextNai,
            )
        }
        if (chosen != null) {
            container.settingsStore.setLastImageSelection(key.id, chosen.id)
        }
    }

    fun selectModel(modelId: String) {
        val keyId = _ui.value.selectedKeyId
        val modelObj = _ui.value.models.firstOrNull { it.id == modelId }
        _ui.update { state ->
            val prevModel = state.models.firstOrNull { it.id == state.selectedModelId }
            val nextNai = if (OnlineImageStudioUtils.isNovelAIModel(modelObj)) {
                val targetVersion = OnlineImageNovelAI.detectVersion(
                    modelId,
                    modelObj?.parameter_schema?.upstream_model,
                )
                if (OnlineImageStudioUtils.isNovelAIModel(prevModel)) {
                    OnlineImageNovelAI.adaptParamsForVersion(state.novelaiParams, targetVersion)
                } else {
                    OnlineImageNovelAI.defaultParamsForVersion(targetVersion)
                }
            } else {
                state.novelaiParams
            }
            val clampedCount = state.count.coerceIn(1, modelObj?.max_count?.coerceAtLeast(1) ?: 4)
            state.copy(selectedModelId = modelId, novelaiParams = nextNai, count = clampedCount)
        }
        if (keyId != null && keyId != 0L) {
            viewModelScope.launch { container.settingsStore.setLastImageSelection(keyId, modelId) }
        }
    }

    fun setPrompt(text: String) { _ui.update { it.copy(prompt = text) } }
    fun setCount(count: Int) { _ui.update { it.copy(count = count.coerceIn(1, 8)) } }
    fun setAspectRatio(ratio: String) { _ui.update { it.copy(aspectRatio = ratio) } }
    fun setResolution(res: String) { _ui.update { it.copy(resolution = res) } }
    fun updateNovelAIParams(transform: (NovelAIParams) -> NovelAIParams) {
        _ui.update { it.copy(novelaiParams = transform(it.novelaiParams)) }
    }

    fun setShowCreateSheet(show: Boolean) {
        if (!show && pendingPortraitGenId == null) {
            pendingPortraitCallback = null
        }
        _ui.update { it.copy(showCreateSheet = show) }
    }
    fun setShowPresetSheet(show: Boolean) { _ui.update { it.copy(showPresetSheet = show) } }

    fun addReferenceImageUri(uri: Uri, contentResolver: ContentResolver) {
        val maxRefs = (_ui.value.models.firstOrNull { it.id == _ui.value.selectedModelId }?.max_reference_images ?: 5).coerceIn(1, 5)
        if (_ui.value.referenceUploads.size + _ui.value.referenceAssets.size >= maxRefs) {
            container.toastController.show("Max $maxRefs reference images", isError = true)
            return
        }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                compressRefBytes(raw)
            }
            if (bytes == null) {
                container.toastController.show("Invalid image", isError = true)
                return@launch
            }
            val item = LocalRefImage(
                id = UUID.randomUUID().toString(),
                fileName = "ref_${_ui.value.referenceUploads.size + 1}.jpg",
                mimeType = "image/jpeg",
                bytes = bytes,
                previewUri = uri,
            )
            _ui.update { it.copy(referenceUploads = it.referenceUploads + item) }
        }
    }

    fun removeReferenceUpload(id: String) {
        _ui.update { it.copy(referenceUploads = it.referenceUploads.filterNot { r -> r.id == id }) }
    }

    fun removeReferenceAsset(ref: OnlineImageReferenceIdDto) {
        _ui.update { it.copy(referenceAssets = it.referenceAssets.filterNot { r -> r == ref }) }
    }

    fun useGenerationAsReference(gen: OnlineImageGenerationDto, imageIndex: Int = 0) {
        _ui.update { state ->
            val ref = OnlineImageReferenceIdDto(gen.id, imageIndex)
            val next = if (ref in state.referenceAssets) state.referenceAssets else (state.referenceAssets + ref).take(5)
            state.copy(
                referenceAssets = next,
                selectedDetail = null,
                showCreateSheet = true,
            )
        }
    }

    fun openCreateWithPrompt(
        promptText: String,
        styleAppend: Boolean = false,
        aspectRatio: String? = null,
    ) {
        pendingPortraitCallback = null
        pendingPortraitGenId = null
        _ui.update { state ->
            val nextPrompt = if (styleAppend && state.prompt.isNotBlank()) {
                "${state.prompt.trim()}, $promptText"
            } else {
                promptText
            }
            val nextRatio = aspectRatio?.takeIf { it.isNotBlank() && !styleAppend } ?: state.aspectRatio
            state.copy(prompt = nextPrompt, aspectRatio = nextRatio, showCreateSheet = true)
        }
    }

    /**
     * Opens the image generator sheet prefilled for a character card portrait and invokes
     * [onCompleted] with the finished image bytes once generation finishes.
     */
    fun openCreateForCardPortrait(
        promptText: String,
        aspectRatio: String? = "2:3",
        onCompleted: suspend (ByteArray) -> Unit,
    ) {
        pendingPortraitCallback = onCompleted
        pendingPortraitGenId = null
        _ui.update { state ->
            state.copy(
                prompt = promptText,
                aspectRatio = aspectRatio?.takeIf { it.isNotBlank() } ?: state.aspectRatio,
                count = 1,
                showCreateSheet = true,
            )
        }
    }

    fun openNovelAICreate() {
        val naiKey = _ui.value.keys.firstOrNull { k -> k.models.any { OnlineImageStudioUtils.isNovelAIModel(it) } }
        if (naiKey != null && naiKey.id != _ui.value.selectedKeyId) {
            selectKey(naiKey.id)
        }
        val naiModel = _ui.value.models.firstOrNull { OnlineImageStudioUtils.isNovelAIModel(it) }
            ?: naiKey?.models?.firstOrNull { OnlineImageStudioUtils.isNovelAIModel(it) }
        if (naiModel != null) {
            selectModel(naiModel.id)
        }
        _ui.update { it.copy(showCreateSheet = true) }
    }

    fun submitGeneration() {
        val state = _ui.value
        val keyId = state.selectedKeyId
        val modelObj = state.models.firstOrNull { it.id == state.selectedModelId }
        if (keyId == null || keyId == 0L || modelObj == null) {
            container.toastController.show("Select channel and model first", isError = true)
            return
        }
        if (state.prompt.isBlank()) {
            container.toastController.show("Please enter prompt", isError = true)
            return
        }
        if (state.isSubmitting) return

        viewModelScope.launch {
            _ui.update { it.copy(isSubmitting = true) }
            try {
                val isNai = OnlineImageStudioUtils.isNovelAIModel(modelObj)
                val naiJson = if (isNai) {
                    OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
                        prompt = state.prompt,
                        params = state.novelaiParams,
                        modelId = modelObj.id,
                        hasReferenceImages = state.referenceUploads.isNotEmpty() || state.referenceAssets.isNotEmpty(),
                        isImg2Img = false,
                        upstreamModel = modelObj.parameter_schema?.upstream_model,
                    )
                } else {
                    null
                }

                val created = container.onlineImagesApi.submit(
                    input = OnlineImageSubmitInput(
                        apiKeyId = keyId,
                        provider = modelObj.provider,
                        transport = modelObj.transport,
                        model = modelObj.id,
                        prompt = state.prompt.trim(),
                        count = state.count,
                        aspectRatio = state.aspectRatio,
                        resolution = state.resolution,
                        referenceImages = state.referenceUploads.map {
                            ImageBinaryUpload(it.fileName, it.mimeType, it.bytes)
                        },
                        referenceAssets = state.referenceAssets,
                        novelai = naiJson,
                    ),
                    idempotencyKey = newIdempotencyKey(),
                )
                _ui.update { cur ->
                    cur.copy(
                        isSubmitting = false,
                        showCreateSheet = false,
                        generations = listOf(created) + cur.generations.filterNot { it.id == created.id },
                        totalWorks = cur.totalWorks + 1,
                    )
                }
                container.updateImageWorksTotal(_ui.value.totalWorks)
                if (pendingPortraitCallback != null) {
                    if (isActiveStatus(created.status)) {
                        pendingPortraitGenId = created.id
                    } else {
                        deliverPendingPortraitIfSucceeded(created)
                    }
                }
                ensurePollingActive()
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false) }
                container.toastController.show(e.message ?: "Submit failed", isError = true)
            }
        }
    }

    fun submitEditorAction(
        target: EditorTarget,
        instruction: String,
        strokes: List<MaskStroke>,
        sourceWidth: Int,
        sourceHeight: Int,
        outpaintAspect: String,
        outpaintResolution: String,
        outpaintAnchor: OutpaintAnchor,
        outpaintScale: Float,
        variationStrength: Double,
    ) {
        val state = _ui.value
        val keyId = state.selectedKeyId ?: target.generation.api_key_id.takeIf { it != 0L }
        val modelObj = state.models.firstOrNull { it.id == state.selectedModelId }
            ?: state.keys.flatMap { it.models }.firstOrNull { it.id == target.generation.model }
        if (keyId == null || keyId == 0L || modelObj == null) {
            container.toastController.show("Model unavailable for editing", isError = true)
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(isSubmitting = true) }
            try {
                var maskUpload: ImageBinaryUpload? = null
                var canvasDto: com.gugachat.app.data.api.OnlineImageCanvasDto? = null
                var promptText = instruction.trim()

                when (target.mode) {
                    ImageEditMode.INPAINT -> {
                        val pngBytes = withContext(Dispatchers.Default) {
                            MaskPngEncoder.encodeMaskPng(sourceWidth, sourceHeight, strokes)
                        } ?: throw IllegalStateException("Please brush editable mask region first")
                        maskUpload = ImageBinaryUpload("mask.png", "image/png", pngBytes)
                        if (promptText.isEmpty()) promptText = target.generation.prompt
                    }
                    ImageEditMode.OUTPAINT -> {
                        val (tW, tH) = OnlineImageStudioUtils.computeCanvasDimensions(outpaintAspect, outpaintResolution, modelObj)
                        canvasDto = OnlineImageCanvasMath.computeOutpaintCanvas(
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            targetWidth = tW,
                            targetHeight = tH,
                            anchor = outpaintAnchor,
                            imageScale = outpaintScale,
                        )
                        if (promptText.isEmpty()) promptText = "Seamlessly extend the scene outward with matching lighting and details"
                    }
                    ImageEditMode.VARIATION -> {
                        if (promptText.isEmpty()) {
                            promptText = "Create a high-quality creative variation of ${target.generation.prompt}"
                        }
                    }
                    ImageEditMode.EDIT -> {
                        if (promptText.isEmpty()) {
                            throw IllegalStateException("Please enter edit instructions")
                        }
                    }
                }

                val isNai = OnlineImageStudioUtils.isNovelAIModel(modelObj)
                val naiJson = if (isNai) {
                    OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
                        prompt = promptText,
                        params = state.novelaiParams.copy(strength = variationStrength),
                        modelId = modelObj.id,
                        hasReferenceImages = false,
                        isImg2Img = target.mode == ImageEditMode.VARIATION,
                        upstreamModel = modelObj.parameter_schema?.upstream_model,
                    )
                } else {
                    null
                }

                val created = container.onlineImagesApi.submit(
                    input = OnlineImageSubmitInput(
                        apiKeyId = keyId,
                        provider = modelObj.provider,
                        transport = modelObj.transport,
                        model = modelObj.id,
                        prompt = promptText,
                        count = 1,
                        aspectRatio = if (target.mode == ImageEditMode.OUTPAINT) outpaintAspect else target.generation.aspect_ratio,
                        resolution = if (target.mode == ImageEditMode.OUTPAINT) outpaintResolution else target.generation.resolution,
                        action = target.mode.wireAction,
                        sourceGenerationId = target.generation.id,
                        sourceImageIndex = target.imageIndex,
                        mask = maskUpload,
                        canvas = canvasDto,
                        novelai = naiJson,
                    ),
                    idempotencyKey = newIdempotencyKey(),
                )
                _ui.update { cur ->
                    cur.copy(
                        isSubmitting = false,
                        editorTarget = null,
                        selectedDetail = null,
                        generations = listOf(created) + cur.generations.filterNot { it.id == created.id },
                    )
                }
                ensurePollingActive()
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false) }
                container.toastController.show(e.message ?: "Edit submit failed", isError = true)
            }
        }
    }

    fun setSearchQuery(q: String) {
        _ui.update { it.copy(searchQuery = q) }
    }

    fun setFilter(filter: String) {
        _ui.update { it.copy(activeFilter = filter) }
        refreshGallery()
    }

    fun refreshGallery() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingGallery = true) }
            try {
                val isDirectOnly = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
                val search = _ui.value.searchQuery.trim().ifBlank { null }
                val filter = _ui.value.activeFilter
                val localHist = container.directImageStore.list(search = search, filter = filter)

                val isFiltered = search != null || filter != "all"
                if (isDirectOnly) {
                    val total = localHist.all_total ?: localHist.total
                    if (!isFiltered || localHist.all_total != null) {
                        container.updateImageWorksTotal(total)
                    }
                    _ui.update {
                        it.copy(
                            generations = localHist.items,
                            hasMore = false,
                            nextCursor = null,
                            totalWorks = total,
                            isLoadingGallery = false,
                        )
                    }
                    return@launch
                }

                val res = container.onlineImagesApi.list(
                    OnlineImageListParams(
                        search = search,
                        filter = filter,
                        pageSize = 24,
                    ),
                )
                val merged = (localHist.items + res.items).distinctBy { it.id }
                val total = (res.all_total ?: res.total) + (localHist.all_total ?: localHist.total)
                if (!isFiltered || res.all_total != null) {
                    container.updateImageWorksTotal(total)
                }
                _ui.update {
                    it.copy(
                        generations = merged,
                        hasMore = res.has_more ?: !res.next_cursor.isNullOrBlank(),
                        nextCursor = res.next_cursor,
                        totalWorks = total,
                        isLoadingGallery = false,
                    )
                }
                if (merged.any { isActiveStatus(it.status) }) {
                    ensurePollingActive()
                }
            } catch (e: Throwable) {
                val search = _ui.value.searchQuery.trim().ifBlank { null }
                val filter = _ui.value.activeFilter
                val fallbackLocal = runCatching {
                    container.directImageStore.list(search = search, filter = filter)
                }.getOrNull()
                if (fallbackLocal != null && fallbackLocal.items.isNotEmpty()) {
                    val localTotal = fallbackLocal.all_total ?: fallbackLocal.total
                    container.updateImageWorksTotal(localTotal)
                    _ui.update {
                        it.copy(
                            generations = fallbackLocal.items,
                            hasMore = false,
                            nextCursor = null,
                            totalWorks = localTotal,
                            isLoadingGallery = false,
                        )
                    }
                } else {
                    _ui.update { it.copy(isLoadingGallery = false) }
                    container.toastController.show(e.message ?: "Failed to load gallery", isError = true)
                }
            }
        }
    }

    fun loadMoreGallery() {
        val cursor = _ui.value.nextCursor ?: return
        if (_ui.value.isLoadingMore || !_ui.value.hasMore) return
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingMore = true) }
            try {
                val res = container.onlineImagesApi.list(
                    OnlineImageListParams(
                        search = _ui.value.searchQuery.trim().ifBlank { null },
                        filter = _ui.value.activeFilter,
                        pageSize = 24,
                        cursor = cursor,
                    ),
                )
                _ui.update { cur ->
                    val merged = (cur.generations + res.items).distinctBy { it.id }
                    cur.copy(
                        generations = merged,
                        hasMore = res.has_more ?: !res.next_cursor.isNullOrBlank(),
                        nextCursor = res.next_cursor,
                        isLoadingMore = false,
                    )
                }
            } catch (_: Throwable) {
                _ui.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun openDetail(gen: OnlineImageGenerationDto, index: Int = 0) {
        _ui.update { it.copy(selectedDetail = gen, detailImageIndex = index, detailVersions = listOf(gen)) }
        viewModelScope.launch {
            runCatching {
                val vers = container.onlineImagesApi.versions(gen.id)
                if (vers.isNotEmpty()) {
                    _ui.update { cur ->
                        if (cur.selectedDetail?.id == gen.id) cur.copy(detailVersions = vers) else cur
                    }
                }
            }
        }
    }

    fun closeDetail() { _ui.update { it.copy(selectedDetail = null) } }
    fun setDetailImageIndex(idx: Int) { _ui.update { it.copy(detailImageIndex = idx) } }
    fun openLightbox(gen: OnlineImageGenerationDto, index: Int) { _ui.update { it.copy(lightboxTarget = gen to index) } }
    fun closeLightbox() { _ui.update { it.copy(lightboxTarget = null) } }
    fun openEditor(gen: OnlineImageGenerationDto, index: Int = 0, mode: ImageEditMode = ImageEditMode.EDIT) {
        _ui.update { it.copy(editorTarget = EditorTarget(gen, index, mode), selectedDetail = null) }
    }
    fun closeEditor() { _ui.update { it.copy(editorTarget = null) } }

    fun regenerate(genId: String) {
        viewModelScope.launch {
            try {
                val created = container.onlineImagesApi.regenerate(genId)
                _ui.update { cur ->
                    cur.copy(
                        selectedDetail = null,
                        generations = listOf(created) + cur.generations.filterNot { it.id == created.id },
                    )
                }
                ensurePollingActive()
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Regenerate failed", isError = true)
            }
        }
    }

    fun reuseParamsToCreate(gen: OnlineImageGenerationDto) {
        if (gen.api_key_id != 0L && gen.api_key_id != _ui.value.selectedKeyId) {
            selectKey(gen.api_key_id)
        }
        val parsedNai = gen.novelai?.let { raw ->
            runCatching { OnlineImageNovelAI.parseNovelAIParams(raw, modelId = gen.model) }.getOrNull()
        }
        _ui.update { state ->
            state.copy(
                selectedModelId = gen.model.ifBlank { state.selectedModelId },
                prompt = gen.prompt,
                count = gen.requested_count.coerceIn(1, 8),
                aspectRatio = gen.aspect_ratio.ifBlank { "auto" },
                resolution = gen.resolution.ifBlank { "auto" },
                novelaiParams = parsedNai ?: state.novelaiParams,
                selectedDetail = null,
                showCreateSheet = true,
            )
        }
    }

    fun deleteGeneration(id: String) {
        viewModelScope.launch {
            try {
                container.onlineImagesApi.remove(id)
                _ui.update { cur ->
                    val rem = cur.generations.filterNot { it.id == id }
                    val nextTotal = (cur.totalWorks - 1).coerceAtLeast(rem.size)
                    container.updateImageWorksTotal(nextTotal)
                    cur.copy(
                        generations = rem,
                        totalWorks = nextTotal,
                        selectedDetail = if (cur.selectedDetail?.id == id) null else cur.selectedDetail,
                        selectedBatchIds = cur.selectedBatchIds - id,
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Delete failed", isError = true)
            }
        }
    }

    fun toggleBatchMode() {
        _ui.update { cur -> cur.copy(batchMode = !cur.batchMode, selectedBatchIds = emptySet()) }
    }

    fun toggleBatchSelection(id: String) {
        _ui.update { cur ->
            val next = if (id in cur.selectedBatchIds) cur.selectedBatchIds - id else cur.selectedBatchIds + id
            cur.copy(selectedBatchIds = next)
        }
    }

    fun selectAllBatch() {
        _ui.update { cur -> cur.copy(selectedBatchIds = cur.generations.map { it.id }.toSet()) }
    }

    fun clearBatchSelection() {
        _ui.update { it.copy(selectedBatchIds = emptySet()) }
    }

    fun batchDeleteSelected() {
        val ids = _ui.value.selectedBatchIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(isBatchBusy = true) }
            try {
                ids.forEach { id -> runCatching { container.onlineImagesApi.remove(id) } }
                refreshGallery()
                _ui.update { it.copy(batchMode = false, selectedBatchIds = emptySet(), isBatchBusy = false) }
            } catch (e: Throwable) {
                _ui.update { it.copy(isBatchBusy = false) }
                container.toastController.show(e.message ?: "Batch delete failed", isError = true)
            }
        }
    }

    fun batchDownloadZip(uri: Uri, contentResolver: ContentResolver, successToast: String) {
        val ids = _ui.value.selectedBatchIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(isBatchBusy = true) }
            try {
                val bytes = container.onlineImagesApi.batchDownloadZip(ids)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot write ZIP")
                }
                container.toastController.show(successToast)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "ZIP download failed", isError = true)
            } finally {
                _ui.update { it.copy(isBatchBusy = false) }
            }
        }
    }

    fun saveImageToGallery(context: Context, genId: String, imageIndex: Int, successToast: String) {
        viewModelScope.launch {
            try {
                val bytes = container.onlineImagesApi.contentBytes(genId, imageIndex, variant = "original")
                withContext(Dispatchers.IO) {
                    val fileName = "GuGuChat_${genId.take(8)}_$imageIndex.png"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GuGuChat")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert failed")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                }
                container.toastController.show(successToast)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Save failed", isError = true)
            }
        }
    }

    fun shareImage(context: Context, genId: String, imageIndex: Int) {
        viewModelScope.launch {
            try {
                val bytes = container.onlineImagesApi.contentBytes(genId, imageIndex, variant = "original")
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share_images").apply { mkdirs() }
                    val file = File(dir, "share_${genId.take(8)}_$imageIndex.png")
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Image"))
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Share failed", isError = true)
            }
        }
    }

    // ==================== Presets CRUD ====================

    fun loadPresets() {
        viewModelScope.launch {
            runCatching {
                val res = container.onlineImagesApi.listPresets()
                _ui.update { it.copy(presets = res.items) }
            }
        }
    }

    fun createPreset(name: String, includePrompt: Boolean, successToast: String) {
        val state = _ui.value
        val modelObj = state.models.firstOrNull { it.id == state.selectedModelId } ?: return
        val keyId = state.selectedKeyId
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val isNai = OnlineImageStudioUtils.isNovelAIModel(modelObj)
                val input = OnlineImagePresetInputDto(
                    name = name.trim(),
                    api_key_id = keyId,
                    provider = modelObj.provider,
                    model = modelObj.id,
                    aspect_ratio = state.aspectRatio,
                    resolution = state.resolution,
                    requested_count = state.count,
                    include_prompt = includePrompt,
                    prompt = if (includePrompt) state.prompt else null,
                    novelai = if (isNai) {
                        OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
                            prompt = state.prompt,
                            params = state.novelaiParams,
                            modelId = modelObj.id,
                            upstreamModel = modelObj.parameter_schema?.upstream_model,
                        )
                    } else {
                        null
                    },
                )
                val created = container.onlineImagesApi.createPreset(input)
                _ui.update { cur -> cur.copy(presets = listOf(created) + cur.presets) }
                container.toastController.show(successToast)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Save preset failed", isError = true)
            }
        }
    }

    fun applyPreset(preset: OnlineImagePresetDto, toastText: String) {
        val targetKey = preset.api_key_id?.takeIf { it != 0L }
        if (targetKey != null && targetKey != _ui.value.selectedKeyId) {
            selectKey(targetKey)
        }
        val parsedNai = preset.novelai?.let { raw ->
            runCatching { OnlineImageNovelAI.parseNovelAIParams(raw, modelId = preset.model) }.getOrNull()
        }
        _ui.update { state ->
            state.copy(
                selectedModelId = preset.model.ifBlank { state.selectedModelId },
                aspectRatio = preset.aspect_ratio.ifBlank { "auto" },
                resolution = preset.resolution.ifBlank { "auto" },
                count = preset.requested_count.coerceIn(1, 8),
                prompt = if (preset.include_prompt && !preset.prompt.isNullOrBlank()) preset.prompt else state.prompt,
                novelaiParams = parsedNai ?: state.novelaiParams,
                showPresetSheet = false,
            )
        }
        container.toastController.show(toastText)
    }

    fun renamePreset(id: String, name: String, toastText: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val upd = container.onlineImagesApi.renamePreset(id, name.trim())
                _ui.update { cur -> cur.copy(presets = cur.presets.map { if (it.id == id) upd else it }) }
                container.toastController.show(toastText)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Rename failed", isError = true)
            }
        }
    }

    fun deletePreset(id: String, toastText: String) {
        viewModelScope.launch {
            try {
                container.onlineImagesApi.deletePreset(id)
                _ui.update { cur -> cur.copy(presets = cur.presets.filterNot { it.id == id }) }
                container.toastController.show(toastText)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Delete preset failed", isError = true)
            }
        }
    }

    private fun ensurePollingActive() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                val activeIds = _ui.value.generations.filter { isActiveStatus(it.status) }.map { it.id }
                if (activeIds.isEmpty()) break
                delay(2400L)
                for (id in activeIds) {
                    runCatching {
                        val fresh = container.onlineImagesApi.get(id)
                        _ui.update { cur ->
                            cur.copy(
                                generations = cur.generations.map { if (it.id == id) fresh else it },
                                selectedDetail = if (cur.selectedDetail?.id == id) fresh else cur.selectedDetail,
                            )
                        }
                        if (id == pendingPortraitGenId && !isActiveStatus(fresh.status)) {
                            deliverPendingPortraitIfSucceeded(fresh)
                        }
                    }
                }
            }
        }
    }

    private suspend fun deliverPendingPortraitIfSucceeded(generation: OnlineImageGenerationDto) {
        val callback = pendingPortraitCallback
        pendingPortraitCallback = null
        pendingPortraitGenId = null
        if (callback == null) return
        val status = generation.status.lowercase()
        if (status != "completed" && status != "succeeded") return
        val bytes = runCatching {
            container.onlineImagesApi.contentBytes(generation.id, 0, variant = "original")
        }.getOrNull() ?: return
        callback(bytes)
    }

    private fun isActiveStatus(status: String): Boolean {
        val s = status.lowercase()
        return s == "pending" || s == "queued" || s == "processing" || s == "running"
    }

    private fun compressRefBytes(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (max(w, h) > 2048) {
            sample *= 2; w /= 2; h /= 2
        }
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
        bmp.recycle()
        return baos.toByteArray()
    }
}
