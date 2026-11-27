package com.gugachat.app.data.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.util.UriUtils
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.ParsedQuickRepliesAsset
import com.gugachat.app.domain.chat.ParsedTavernAsset
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.TavernAssetCodec
import com.gugachat.app.domain.chat.UuSaveCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

sealed interface AssetImportResult {
    data class CharacterCardImported(
        val card: CharacterCardEntity,
        val rpConfig: RpConfig,
        val worldbook: WorldbookEntity?,
        val entryCount: Int,
        val regexCount: Int,
        val deduplicatedCard: Boolean = false,
    ) : AssetImportResult

    data class WorldbookImported(
        val worldbook: WorldbookEntity,
        val entries: List<WorldbookEntryEntity>,
        val deduplicated: Boolean = false,
    ) : AssetImportResult

    data class PromptPresetImported(
        val preset: PromptPresetEntity,
        val regexScripts: List<RpRegexScript> = emptyList(),
        val loreEntries: List<NovelLoreEntry> = emptyList(),
    ) : AssetImportResult

    data class QuickRepliesImported(
        val quickReplies: ParsedQuickRepliesAsset,
    ) : AssetImportResult

    data class RegexScriptsImported(
        val scripts: List<RpRegexScript>,
    ) : AssetImportResult

    data class SettingsImported(
        val regexScripts: List<RpRegexScript>,
        val authorNote: String,
    ) : AssetImportResult

    data class UuGameSaveDetected(
        val rawJson: String,
        val displayName: String?,
    ) : AssetImportResult
}

/**
 * Coordinates importing SillyTavern V2/V3 PNG cards, `.charx`/`.byaf` archives, standalone
 * worldbooks, prompt presets, regex scripts, and personas into v0.4 Room tables (`AssetDao`),
 * storing extracted local media under `filesDir/rp_media/` as portable relative paths and
 * indexing worldbook entries into [RetrievalCoordinator].
 */
class AssetImportCoordinator(
    private val appContext: Context,
    private val database: AppDatabase,
    private val retrievalCoordinator: RetrievalCoordinator,
    private val scope: CoroutineScope,
) {
    private val assetDao = database.assetDao()
    private val filesDir: File
        get() = appContext.filesDir

    suspend fun importUri(
        uri: Uri,
        contentResolver: ContentResolver,
        persistRegexAsPreset: Boolean = false,
    ): AssetImportResult {
        val (bytes, displayName) = readUriBytesWithName(uri, contentResolver)
        return importBytes(bytes = bytes, fileName = displayName, persistRegexAsPreset = persistRegexAsPreset)
    }

    suspend fun importBytes(
        bytes: ByteArray,
        fileName: String? = null,
        persistRegexAsPreset: Boolean = false,
    ): AssetImportResult {
        val isPng = RpCardCodec.isPngBytes(bytes)
        val zipCard = if (!isPng) RpCardCodec.extractCardFromZipBytes(bytes) else null

        if (isPng || zipCard != null) {
            return importBinaryCharacterCard(bytes = bytes, isPng = isPng, zipCardJson = zipCard)
        }

        val jsonText = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        val rootElem = runCatching { AppJson.parseToJsonElement(jsonText) }.getOrNull()
        if (rootElem is JsonObject && UuSaveCodec.isUuGameSave(rootElem)) {
            return AssetImportResult.UuGameSaveDetected(rawJson = jsonText, displayName = fileName)
        }

        when (val asset = TavernAssetCodec.parseJsonText(jsonText, conversationId = "preview", fileName = fileName)) {
            is ParsedTavernAsset.Lorebook -> {
                val parsed = TavernAssetCodec.parseWorldbookV4(jsonText, fileName = fileName)
                val saved = persistWorldbookDeduped(parsed.worldbook, parsed.entries)
                return AssetImportResult.WorldbookImported(
                    worldbook = saved.first,
                    entries = parsed.entries,
                    deduplicated = saved.second,
                )
            }

            is ParsedTavernAsset.Preset -> {
                val preset = TavernAssetCodec.parsePromptPresetV4(jsonText, fileName = fileName)
                assetDao.upsertPromptPreset(preset)
                return AssetImportResult.PromptPresetImported(
                    preset = preset,
                    regexScripts = asset.asset.regexScripts,
                    loreEntries = asset.asset.loreEntries,
                )
            }

            is ParsedTavernAsset.QuickReplies -> {
                return AssetImportResult.QuickRepliesImported(asset.asset)
            }

            is ParsedTavernAsset.RegexScripts -> {
                if (persistRegexAsPreset && asset.scripts.isNotEmpty()) {
                    val name = fileNameBase(fileName)
                        ?: asset.scripts.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                        ?: "Regex Scripts"
                    val preset = savePromptPresetFromRegex(name = name, scripts = asset.scripts)
                    return AssetImportResult.PromptPresetImported(
                        preset = preset,
                        regexScripts = asset.scripts,
                    )
                }
                return AssetImportResult.RegexScriptsImported(asset.scripts)
            }

            is ParsedTavernAsset.Settings -> {
                if (persistRegexAsPreset && (asset.asset.regexScripts.isNotEmpty() || asset.asset.authorNote.isNotBlank())) {
                    val name = fileNameBase(fileName) ?: "SillyTavern Regex Pack"
                    val preset = savePromptPresetFromRegex(name = name, scripts = asset.asset.regexScripts)
                    return AssetImportResult.PromptPresetImported(
                        preset = preset,
                        regexScripts = asset.asset.regexScripts,
                    )
                }
                return AssetImportResult.SettingsImported(
                    regexScripts = asset.asset.regexScripts,
                    authorNote = asset.asset.authorNote,
                )
            }

            null -> Unit
        }

        val parsedCard = RpCardCodec.parseCardToV4(rawJson = jsonText)
        return persistParsedCharacterCard(parsedCard)
    }

    /**
     * Imports a standalone setting asset (Worldbook, Preset, Regex Pack, or Worldbook extracted
     * from a V2/V3 PNG / ZIP character card without creating a character card row).
     */
    suspend fun importSettingAssetUri(uri: Uri, contentResolver: ContentResolver): AssetImportResult {
        val (bytes, fileName) = readUriBytesWithName(uri, contentResolver)
        val isPng = RpCardCodec.isPngBytes(bytes)
        val zipCard = if (!isPng) RpCardCodec.extractCardFromZipBytes(bytes) else null
        if (isPng || zipCard != null) {
            val jsonText = when {
                isPng -> RpCardCodec.extractCardJsonFromPng(bytes)
                    ?: error("PNG missing character card / worldbook metadata")
                else -> zipCard!!.cardJson
            }
            val parsed = RpCardCodec.parseCardToV4(jsonText)
            val wb = parsed.worldbook ?: error("Character card has no embedded worldbook")
            val saved = saveWorldbookWithIndexing(wb.worldbook, wb.entries)
            return AssetImportResult.WorldbookImported(
                worldbook = saved,
                entries = wb.entries,
                deduplicated = false,
            )
        }
        return importBytes(bytes = bytes, fileName = fileName, persistRegexAsPreset = true)
    }

    suspend fun extractRegexScriptsFromUri(
        uri: Uri,
        contentResolver: ContentResolver,
    ): List<RpRegexScript> = withContext(Dispatchers.IO) {
        val (bytes, _) = readUriBytesWithName(uri, contentResolver)
        val text = if (RpCardCodec.isPngBytes(bytes)) {
            RpCardCodec.extractCardJsonFromPng(bytes) ?: error("No card metadata in PNG")
        } else {
            RpCardCodec.extractCardFromZipBytes(bytes)?.cardJson
                ?: bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        val root = AppJson.parseToJsonElement(text.trim())
        TavernAssetCodec.parseRegexScriptsJson(root)
    }

    suspend fun saveWorldbookWithIndexing(
        worldbook: WorldbookEntity,
        entries: List<WorldbookEntryEntity>,
    ): WorldbookEntity {
        val totalTokens = entries.sumOf { it.estimatedTokens.coerceAtLeast(0) }
        val normalizedBook = worldbook.copy(
            entryCount = entries.size,
            totalEstimatedTokens = totalTokens,
            updatedAt = System.currentTimeMillis(),
        )
        val normalizedEntries = entries.map { it.copy(worldbookId = normalizedBook.id) }
        database.withTransaction {
            assetDao.upsertWorldbook(normalizedBook)
            assetDao.replaceWorldbookEntries(normalizedBook.id, normalizedEntries)
        }
        retrievalCoordinator.scheduleWorldbookIndexing(
            scope = scope,
            worldbookId = normalizedBook.id,
            entries = normalizedEntries,
        )
        return normalizedBook
    }

    suspend fun recalculateWorldbookSummary(
        worldbookId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val wb = assetDao.getWorldbookById(worldbookId) ?: return
        val entries = assetDao.getWorldbookEntries(worldbookId)
        val totalTokens = entries.sumOf { it.estimatedTokens.coerceAtLeast(0) }
        assetDao.upsertWorldbook(
            wb.copy(
                entryCount = entries.size,
                totalEstimatedTokens = totalTokens,
                updatedAt = now,
            ),
        )
    }

    suspend fun savePromptPresetFromRegex(
        name: String,
        scripts: List<RpRegexScript>,
    ): PromptPresetEntity {
        val now = System.currentTimeMillis()
        val preset = PromptPresetEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceFormat = "sillytavern_regex",
            blocksJson = "[]",
            regexScriptsJson = AppJson.encodeToString(scripts),
            samplingPatchJson = "{}",
            isDefaultRp = false,
            createdAt = now,
            updatedAt = now,
        )
        assetDao.upsertPromptPreset(preset)
        return preset
    }

    fun formatSettingImportSummary(result: AssetImportResult, isZh: Boolean): String? = when (result) {
        is AssetImportResult.WorldbookImported -> if (isZh) {
            "已导入世界书「${result.worldbook.name}」(${result.entries.size} 条)"
        } else {
            "Imported worldbook \"${result.worldbook.name}\" (${result.entries.size} entries)"
        }
        is AssetImportResult.PromptPresetImported -> {
            if (result.preset.sourceFormat == "sillytavern_regex") {
                if (isZh) "已导入正则预设「${result.preset.name}」(${result.regexScripts.size} 条)"
                else "Imported regex pack \"${result.preset.name}\" (${result.regexScripts.size})"
            } else {
                val blocksCount = runCatching {
                    AppJson.decodeFromString<List<PromptPresetBlock>>(result.preset.blocksJson).size
                }.getOrDefault(0)
                if (blocksCount > 0) {
                    if (isZh) "已导入提示词预设「${result.preset.name}」($blocksCount 个模块)"
                    else "Imported preset \"${result.preset.name}\" ($blocksCount blocks)"
                } else {
                    if (isZh) "已导入预设「${result.preset.name}」" else "Imported preset \"${result.preset.name}\""
                }
            }
        }
        is AssetImportResult.CharacterCardImported -> {
            val cardName = result.card.name.ifBlank { if (isZh) "角色卡" else "Character Card" }
            val wbSuffix = result.worldbook?.let {
                if (isZh) "（附带世界书 ${result.entryCount} 条）" else " (${result.entryCount} lore entries)"
            }.orEmpty()
            if (result.deduplicatedCard) {
                if (isZh) "已更新角色卡「$cardName」$wbSuffix" else "Updated \"$cardName\"$wbSuffix"
            } else {
                if (isZh) "已导入角色卡「$cardName」$wbSuffix" else "Imported \"$cardName\"$wbSuffix"
            }
        }
        else -> null
    }

    private suspend fun readUriBytesWithName(
        uri: Uri,
        contentResolver: ContentResolver,
    ): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Empty file")
        val name = UriUtils.resolveDisplayName(contentResolver, uri)
        raw to name
    }

    private fun fileNameBase(fileName: String?): String? =
        fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun exportCharacterCardToPngUri(
        rp: RpConfig,
        lore: List<NovelLoreEntry>,
        cardEntity: CharacterCardEntity? = null,
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        withContext(Dispatchers.IO) {
            val extraPaths = listOfNotNull(
                cardEntity?.avatarPath,
                cardEntity?.spritePath,
            )
            val cardPng = RpCardCodec.exportCardToPngBytesFromPaths(
                rp = rp,
                lore = lore,
                filesDirPath = filesDir.absolutePath,
                extraPaths = extraPaths,
                assetReader = { rel ->
                    runCatching { appContext.assets.open(rel).use { it.readBytes() } }.getOrNull()
                },
            )
            contentResolver.openOutputStream(uri)?.use { it.write(cardPng) }
                ?: error("Failed to open output stream")
        }
    }

    private suspend fun importBinaryCharacterCard(
        bytes: ByteArray,
        isPng: Boolean,
        zipCardJson: com.gugachat.app.domain.chat.ExtractedZipCard?,
    ): AssetImportResult.CharacterCardImported {
        val rawJson = when {
            isPng -> RpCardCodec.extractCardJsonFromPng(bytes)
                ?: error("No ccv3/chara metadata found in PNG")
            zipCardJson != null -> zipCardJson.cardJson
            else -> error("Invalid binary card payload")
        }

        var avatarRelPath: String? = null
        var spriteRelPath: String? = null
        val expressionRelPaths = LinkedHashMap<String, String>()
        var sceneBgRelPath: String? = null

        withContext(Dispatchers.IO) {
            if (isPng) {
                avatarRelPath = saveMediaBytesSync(bytes, "avatar", "png")
            } else if (zipCardJson != null) {
                zipCardJson.avatarBytes?.let { b ->
                    avatarRelPath = saveMediaBytesSync(b, "avatar", zipCardJson.avatarExt)
                }
                zipCardJson.spriteBytes?.let { b ->
                    spriteRelPath = saveMediaBytesSync(b, "sprite", zipCardJson.spriteExt)
                }
                zipCardJson.expressionSprites.forEach { (exprKey, payload) ->
                    expressionRelPaths[exprKey] = saveMediaBytesSync(payload.first, "expr_${exprKey.take(12)}", payload.second)
                }
                zipCardJson.backgroundBytes?.let { b ->
                    sceneBgRelPath = saveMediaBytesSync(b, "scene", zipCardJson.backgroundExt)
                }
            }
        }

        val parsedV4 = RpCardCodec.parseCardToV4(
            rawJson = rawJson,
            avatarPath = avatarRelPath,
            spritePath = spriteRelPath,
            expressionSprites = expressionRelPaths,
        )
        val sceneUpdatedRp = if (!sceneBgRelPath.isNullOrBlank()) {
            parsedV4.rpConfig.copy(
                state = parsedV4.rpConfig.state.copy(sceneImagePath = sceneBgRelPath),
            )
        } else {
            parsedV4.rpConfig
        }
        return persistParsedCharacterCard(parsedV4.copy(rpConfig = sceneUpdatedRp))
    }

    private suspend fun persistParsedCharacterCard(
        parsed: com.gugachat.app.domain.chat.ParsedCharacterCardV4,
    ): AssetImportResult.CharacterCardImported {
        var persistedWorldbook: WorldbookEntity? = null
        var indexedEntries: List<WorldbookEntryEntity> = emptyList()
        val entryCount = parsed.worldbook?.entries?.size ?: 0
        val regexCount = parsed.rpConfig.regexScripts.size

        val finalCard = database.withTransaction {
            val savedBookResult = parsed.worldbook?.let { wb ->
                persistWorldbookInTransaction(wb.worldbook, wb.entries)
            }
            persistedWorldbook = savedBookResult?.first
            indexedEntries = savedBookResult?.second.orEmpty()

            val existingCard = parsed.card.contentFingerprint
                .takeIf { it.isNotBlank() }
                ?.let { assetDao.getCardByFingerprint(it) }
            val resolvedCard = if (existingCard != null) {
                parsed.card.copy(
                    id = existingCard.id,
                    createdAt = existingCard.createdAt,
                    favorite = existingCard.favorite,
                    avatarPath = parsed.card.avatarPath ?: existingCard.avatarPath,
                    spritePath = parsed.card.spritePath ?: existingCard.spritePath,
                    expressionSpritesJson = if (parsed.card.expressionSpritesJson != "{}") {
                        parsed.card.expressionSpritesJson
                    } else {
                        existingCard.expressionSpritesJson
                    },
                    primaryWorldbookId = persistedWorldbook?.id ?: existingCard.primaryWorldbookId,
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                parsed.card.copy(
                    primaryWorldbookId = persistedWorldbook?.id ?: parsed.card.primaryWorldbookId,
                )
            }
            assetDao.upsertCharacterCard(resolvedCard)
            resolvedCard to (existingCard != null)
        }

        persistedWorldbook?.let { wb ->
            retrievalCoordinator.scheduleWorldbookIndexing(
                scope = scope,
                worldbookId = wb.id,
                entries = indexedEntries,
            )
        }

        return AssetImportResult.CharacterCardImported(
            card = finalCard.first,
            rpConfig = parsed.rpConfig,
            worldbook = persistedWorldbook,
            entryCount = entryCount,
            regexCount = regexCount,
            deduplicatedCard = finalCard.second,
        )
    }

    private suspend fun persistWorldbookDeduped(
        book: WorldbookEntity,
        entries: List<WorldbookEntryEntity>,
    ): Pair<WorldbookEntity, Boolean> {
        val (savedBook, normalizedEntries, deduped) = database.withTransaction {
            persistWorldbookInTransaction(book, entries)
        }
        retrievalCoordinator.scheduleWorldbookIndexing(
            scope = scope,
            worldbookId = savedBook.id,
            entries = normalizedEntries,
        )
        return savedBook to deduped
    }

    private suspend fun persistWorldbookInTransaction(
        book: WorldbookEntity,
        entries: List<WorldbookEntryEntity>,
    ): Triple<WorldbookEntity, List<WorldbookEntryEntity>, Boolean> {
        val existing = book.contentFingerprint
            .takeIf { it.isNotBlank() }
            ?.let { assetDao.getWorldbookByFingerprint(it) }
        val targetId = existing?.id ?: book.id
        val normalizedBook = book.copy(
            id = targetId,
            globalEnabled = existing?.globalEnabled ?: book.globalEnabled,
            createdAt = existing?.createdAt ?: book.createdAt,
            updatedAt = System.currentTimeMillis(),
        )
        val normalizedEntries = entries.map { it.copy(worldbookId = targetId) }
        assetDao.upsertWorldbook(normalizedBook)
        assetDao.replaceWorldbookEntries(targetId, normalizedEntries)
        return Triple(normalizedBook, normalizedEntries, existing != null)
    }

    private fun saveMediaBytesSync(bytes: ByteArray, prefix: String, ext: String): String =
        LocalMediaPathResolver.saveRpMediaBytes(
            filesDir = filesDir,
            bytes = bytes,
            prefix = prefix,
            ext = ext,
        ).first

    private fun resolveUriDisplayName(contentResolver: ContentResolver, uri: Uri): String? =
        com.gugachat.app.core.util.UriUtils.resolveDisplayName(contentResolver, uri)
}
