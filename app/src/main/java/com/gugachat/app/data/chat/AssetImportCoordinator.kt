package com.gugachat.app.data.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.ParsedQuickRepliesAsset
import com.gugachat.app.domain.chat.ParsedTavernAsset
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.TavernAssetCodec
import com.gugachat.app.domain.chat.UuSaveCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun importUri(uri: Uri, contentResolver: ContentResolver): AssetImportResult {
        val (bytes, displayName) = withContext(Dispatchers.IO) {
            val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Empty file")
            val name = resolveUriDisplayName(contentResolver, uri)
            raw to name
        }
        return importBytes(bytes = bytes, fileName = displayName)
    }

    suspend fun importBytes(bytes: ByteArray, fileName: String? = null): AssetImportResult {
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
                return AssetImportResult.RegexScriptsImported(asset.scripts)
            }

            is ParsedTavernAsset.Settings -> {
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
        val savedBookResult = parsed.worldbook?.let { wb ->
            persistWorldbookDeduped(wb.worldbook, wb.entries)
        }
        val persistedWorldbook = savedBookResult?.first
        val entryCount = parsed.worldbook?.entries?.size ?: 0
        val regexCount = parsed.rpConfig.regexScripts.size

        val existingCard = parsed.card.contentFingerprint
            .takeIf { it.isNotBlank() }
            ?.let { assetDao.getCardByFingerprint(it) }
        val finalCard = if (existingCard != null) {
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
        assetDao.upsertCharacterCard(finalCard)

        return AssetImportResult.CharacterCardImported(
            card = finalCard,
            rpConfig = parsed.rpConfig,
            worldbook = persistedWorldbook,
            entryCount = entryCount,
            regexCount = regexCount,
            deduplicatedCard = existingCard != null,
        )
    }

    private suspend fun persistWorldbookDeduped(
        book: WorldbookEntity,
        entries: List<WorldbookEntryEntity>,
    ): Pair<WorldbookEntity, Boolean> {
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
        retrievalCoordinator.scheduleWorldbookIndexing(
            scope = scope,
            worldbookId = targetId,
            entries = normalizedEntries,
        )
        return normalizedBook to (existing != null)
    }

    private fun saveMediaBytesSync(bytes: ByteArray, prefix: String, ext: String): String {
        val dir = File(filesDir, "rp_media")
        if (!dir.exists()) dir.mkdirs()
        val safeExt = ext.trim().lowercase().ifBlank { "png" }
        val outFile = File(dir, "${prefix}_${UUID.randomUUID()}.$safeExt")
        outFile.writeBytes(bytes)
        return LocalMediaPathResolver.toRelativePath(filesDir.absolutePath, outFile.absolutePath)
            ?: "rp_media/${outFile.name}"
    }

    private fun resolveUriDisplayName(contentResolver: ContentResolver, uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
