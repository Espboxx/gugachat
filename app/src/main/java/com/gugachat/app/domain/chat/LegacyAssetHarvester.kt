package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.core.util.HashUtils
import com.gugachat.app.data.local.AssetDao
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ChatDao
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.toDomain
import kotlinx.serialization.encodeToString

data class LegacyHarvestResult(
    val harvestedCardCount: Int = 0,
    val harvestedPersonaCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
)

/**
 * One-time, strictly idempotent harvester that mirrors legacy RP conversations into
 * deduplicated [CharacterCardEntity] and [PersonaEntity] records in the global asset tables.
 *
 * CRITICAL: Never mutates legacy `ConversationEntity` rows (`rpJson`, `characterCardId`,
 * `personaId`, `presetId`, or prompts) so existing conversations keep 100% identical prompts
 * and prompt-cache hit rates.
 */
object LegacyAssetHarvester {

    fun computeCardFingerprint(
        title: String,
        greeting: String,
        leadDesc: String,
    ): String {
        val canonical = "${title.trim()}|${greeting.trim()}|${leadDesc.trim()}"
        return sha256Hex(canonical)
    }

    suspend fun harvestIfNeeded(
        chatDao: ChatDao,
        assetDao: AssetDao,
        nowMs: () -> Long = { System.currentTimeMillis() },
    ): LegacyHarvestResult {
        val allConversations = chatDao.getAllConversations()
        val rpConversations = allConversations.filter { it.mode == ConversationMode.rp.name }
        if (rpConversations.isEmpty()) {
            return LegacyHarvestResult()
        }

        val existingCards = assetDao.getAllCharacterCards()
        val knownFingerprints = existingCards
            .mapNotNull { it.contentFingerprint.trim().takeIf { fp -> fp.isNotEmpty() } }
            .toHashSet()

        val existingPersonas = assetDao.getAllPersonas()
        val knownPersonaKeys = existingPersonas
            .map { personaDedupeKey(it.name, it.description) }
            .toHashSet()

        var harvestedCards = 0
        var harvestedPersonas = 0
        var skippedDuplicates = 0
        val now = nowMs()

        for (convEntity in rpConversations) {
            val conv = convEntity.toDomain()
            val rp = conv.rp ?: continue
            val leadChar = rp.cast.firstOrNull { !it.isPlayer } ?: rp.cast.firstOrNull()
            val leadDesc = (
                leadChar?.background?.takeIf { it.isNotBlank() }
                    ?: leadChar?.appearance.orEmpty()
                ).trim()

            val fingerprint = computeCardFingerprint(
                title = rp.title,
                greeting = rp.greeting,
                leadDesc = leadDesc,
            )

            val dbDuplicate = assetDao.getCardByFingerprint(fingerprint) != null
            if (!knownFingerprints.add(fingerprint) || dbDuplicate) {
                skippedDuplicates++
            } else {
                val cardName = leadChar?.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: rp.title.trim().ifBlank { conv.title.ifBlank { "未命名角色卡" } }
                val card = CharacterCardEntity(
                    id = "card_legacy_${fingerprint.take(16)}",
                    name = cardName,
                    creator = "",
                    characterVersion = "legacy",
                    avatarPath = leadChar?.avatarPath?.takeIf { it.isNotBlank() },
                    spritePath = leadChar?.spritePath?.takeIf { it.isNotBlank() },
                    expressionSpritesJson = CoreAppJson.encodeToString(leadChar?.expressionSprites.orEmpty()),
                    tagsJson = "[]",
                    playStyle = rp.playStyle.name,
                    description = leadDesc,
                    personality = leadChar?.personality.orEmpty().trim(),
                    scenario = rp.worldview.trim(),
                    firstMes = rp.greeting,
                    alternateGreetingsJson = CoreAppJson.encodeToString(rp.alternateGreetings),
                    groupOnlyGreetingsJson = "[]",
                    mesExample = rp.mesExample,
                    systemPrompt = rp.systemRules,
                    postHistoryInstructions = rp.postHistoryInstructions,
                    creatorNotes = rp.narratorStyle,
                    depthPromptText = rp.authorNote,
                    depthPromptDepth = rp.authorNoteDepth.coerceIn(0, 12),
                    depthPromptRole = "system",
                    castJson = CoreAppJson.encodeToString(rp.cast),
                    regexScriptsJson = CoreAppJson.encodeToString(rp.regexScripts),
                    visualThemeJson = CoreAppJson.encodeToString(rp.visualTheme),
                    primaryWorldbookId = null,
                    sourceSpec = rp.cardSource?.takeIf { it.isNotBlank() } ?: "legacy_rp",
                    contentFingerprint = fingerprint,
                    favorite = false,
                    createdAt = conv.createdAt,
                    updatedAt = now,
                )
                assetDao.upsertCharacterCard(card)
                harvestedCards++
            }

            // Harvest distinct player Persona when non-trivial
            val playerChar = rp.cast.firstOrNull { it.isPlayer }
            val playerName = rp.playerName.trim()
                .ifBlank { playerChar?.name?.trim().orEmpty() }
            val playerDesc = rp.playerPersona.trim()
                .ifBlank { playerChar?.background?.trim().orEmpty() }
            val isMeaningfulPersona = playerName.isNotEmpty() &&
                (playerName != "旅人" || playerDesc.isNotEmpty())
            if (isMeaningfulPersona) {
                val pKey = personaDedupeKey(playerName, playerDesc)
                if (knownPersonaKeys.add(pKey)) {
                    val pHash = sha256Hex(pKey)
                    val persona = PersonaEntity(
                        id = "persona_legacy_${pHash.take(16)}",
                        name = playerName,
                        avatarPath = playerChar?.avatarPath?.takeIf { it.isNotBlank() },
                        description = playerDesc,
                        injectionPosition = 0,
                        injectionDepth = 4,
                        isDefault = false,
                        createdAt = conv.createdAt,
                        updatedAt = now,
                    )
                    assetDao.upsertPersona(persona)
                    harvestedPersonas++
                }
            }
        }

        return LegacyHarvestResult(
            harvestedCardCount = harvestedCards,
            harvestedPersonaCount = harvestedPersonas,
            skippedDuplicateCount = skippedDuplicates,
        )
    }

    private fun personaDedupeKey(name: String, description: String): String =
        "${name.trim().lowercase()}|${description.trim()}"

    private fun sha256Hex(text: String): String =
        com.gugachat.app.core.util.HashUtils.sha256Hex(text)
}
