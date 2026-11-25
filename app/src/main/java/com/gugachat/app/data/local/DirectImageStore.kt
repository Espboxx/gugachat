package com.gugachat.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.OnlineImageAssetDto
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.data.api.OnlineImageHistoryDto
import com.gugachat.app.data.api.OnlineImagePresetDto
import com.gugachat.app.data.api.OnlineImagePresetInputDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

class DirectImageStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "direct_images").apply { mkdirs() }
    private val indexFile = File(dir, "generations.json")
    private val presetsFile = File(dir, "presets.json")
    private val mutex = Mutex()

    suspend fun saveGeneration(
        gen: OnlineImageGenerationDto,
        images: List<Pair<ByteArray, String>>,
    ): OnlineImageGenerationDto = mutex.withLock {
        withContext(Dispatchers.IO) {
            val assets = images.mapIndexed { idx, (bytes, mime) ->
                val file = File(dir, "${gen.id}_$idx.png")
                file.writeBytes(bytes)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                OnlineImageAssetDto(
                    index = idx,
                    content_type = mime,
                    bytes = bytes.size.toLong(),
                    thumb_bytes = bytes.size.toLong(),
                    has_thumb = true,
                    width = opts.outWidth.takeIf { it > 0 },
                    height = opts.outHeight.takeIf { it > 0 },
                )
            }
            val saved = gen.copy(
                images = assets,
                actual_count = assets.size,
                status = "completed",
                updated_at = System.currentTimeMillis(),
            )
            val list = readGenerationsLocked().toMutableList()
            list.removeAll { it.id == saved.id }
            list.add(0, saved)
            writeGenerationsLocked(list)
            saved
        }
    }

    suspend fun list(search: String? = null, filter: String? = null): OnlineImageHistoryDto = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readGenerationsLocked()
            val q = search?.trim()?.lowercase().orEmpty()
            val f = filter?.trim()?.lowercase().orEmpty()
            val filtered = all.filter { item ->
                val matchesSearch = q.isEmpty() ||
                    item.prompt.lowercase().contains(q) ||
                    item.model.lowercase().contains(q)
                val matchesFilter = when (f) {
                    "", "all" -> true
                    "processing" -> item.status in setOf("pending", "queued", "processing", "running")
                    "edited" -> !item.action.isNullOrBlank() && item.action != "generate"
                    else -> item.status.equals(f, ignoreCase = true)
                }
                matchesSearch && matchesFilter
            }
            OnlineImageHistoryDto(
                items = filtered,
                total = filtered.size,
                all_total = all.size,
                has_more = false,
            )
        }
    }

    suspend fun get(id: String): OnlineImageGenerationDto? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readGenerationsLocked().firstOrNull { it.id == id }
        }
    }

    suspend fun versions(id: String): List<OnlineImageGenerationDto> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readGenerationsLocked()
            val target = all.firstOrNull { it.id == id } ?: return@withContext emptyList()
            val rootId = target.root_generation_id ?: target.source_generation_id ?: target.id
            all.filter {
                it.id == rootId ||
                    it.root_generation_id == rootId ||
                    it.source_generation_id == rootId ||
                    it.id == target.id
            }.sortedBy { it.created_at }
        }
    }

    suspend fun remove(id: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val list = readGenerationsLocked()
            val target = list.firstOrNull { it.id == id } ?: return@withContext false
            target.images.forEach { asset ->
                File(dir, "${id}_${asset.index}.png").delete()
            }
            writeGenerationsLocked(list.filterNot { it.id == id })
            true
        }
    }

    fun getImageFile(id: String, index: Int): File? {
        val f = File(dir, "${id}_$index.png")
        return f.takeIf { it.exists() }
    }

    suspend fun readImageBytes(id: String, index: Int): ByteArray? = withContext(Dispatchers.IO) {
        val f = File(dir, "${id}_$index.png")
        if (f.exists()) f.readBytes() else null
    }

    suspend fun listPresets(): List<OnlineImagePresetDto> = mutex.withLock {
        withContext(Dispatchers.IO) { readPresetsLocked() }
    }

    suspend fun createPreset(input: OnlineImagePresetInputDto): OnlineImagePresetDto = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val created = OnlineImagePresetDto(
                id = "local_${UUID.randomUUID()}",
                name = input.name,
                group_id = input.group_id,
                api_key_id = input.api_key_id,
                provider = input.provider,
                model = input.model,
                aspect_ratio = input.aspect_ratio,
                resolution = input.resolution,
                requested_count = input.requested_count,
                include_prompt = input.include_prompt,
                prompt = input.prompt,
                novelai = input.novelai,
                created_at = now,
                updated_at = now,
            )
            val next = listOf(created) + readPresetsLocked()
            writePresetsLocked(next)
            created
        }
    }

    suspend fun renamePreset(id: String, name: String): OnlineImagePresetDto? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val list = readPresetsLocked()
            val target = list.firstOrNull { it.id == id } ?: return@withContext null
            val updated = target.copy(name = name, updated_at = System.currentTimeMillis())
            writePresetsLocked(list.map { if (it.id == id) updated else it })
            updated
        }
    }

    suspend fun deletePreset(id: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val list = readPresetsLocked()
            if (list.none { it.id == id }) return@withContext false
            writePresetsLocked(list.filterNot { it.id == id })
            true
        }
    }

    private fun readGenerationsLocked(): List<OnlineImageGenerationDto> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(
                ListSerializer(OnlineImageGenerationDto.serializer()),
                indexFile.readText(Charsets.UTF_8),
            )
        }.getOrDefault(emptyList())
    }

    private fun writeGenerationsLocked(items: List<OnlineImageGenerationDto>) {
        indexFile.writeText(
            AppJson.encodeToString(ListSerializer(OnlineImageGenerationDto.serializer()), items),
            Charsets.UTF_8,
        )
    }

    private fun readPresetsLocked(): List<OnlineImagePresetDto> {
        if (!presetsFile.exists()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(
                ListSerializer(OnlineImagePresetDto.serializer()),
                presetsFile.readText(Charsets.UTF_8),
            )
        }.getOrDefault(emptyList())
    }

    private fun writePresetsLocked(items: List<OnlineImagePresetDto>) {
        presetsFile.writeText(
            AppJson.encodeToString(ListSerializer(OnlineImagePresetDto.serializer()), items),
            Charsets.UTF_8,
        )
    }
}
