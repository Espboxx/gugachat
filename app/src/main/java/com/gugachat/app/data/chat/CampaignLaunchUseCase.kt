package com.gugachat.app.data.chat

import android.content.Context
import androidx.room.withTransaction
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.decodedAltGreetings
import com.gugachat.app.data.local.decodedCast
import com.gugachat.app.data.local.decodedExpressionSprites
import com.gugachat.app.data.local.decodedRegexScripts
import com.gugachat.app.data.local.decodedVisualTheme
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresetTemplate
import com.gugachat.app.domain.chat.RpStarterLore
import java.io.File
import java.util.UUID

/**
 * Orchestrates launching new RP campaigns from Character Cards, built-in Preset Templates,
 * or ad-hoc RP configs while keeping macro expansion, opening greeting variant construction,
 * mounted worldbooks, and Room persistence atomic and deduplicated.
 */
class CampaignLaunchUseCase(
    private val appContext: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    private val chatDao = database.chatDao()
    private val assetDao = database.assetDao()
    private val filesDir: File
        get() = appContext.filesDir

    suspend fun launchFromCard(
        card: CharacterCardEntity,
        selectedGreetingIndex: Int,
        personaId: String?,
        worldbookIds: List<String>,
        presetId: String?,
        groupId: Long? = null,
        modelId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val newConvId = UUID.randomUUID().toString()
        val filesDirPath = filesDir.absolutePath
        val settings = settingsStore.currentSettings()
        val persona = personaId?.takeIf { it.isNotBlank() }?.let { assetDao.getPersonaById(it) }
            ?: assetDao.getDefaultPersona()
        val preset = presetId?.takeIf { it.isNotBlank() }?.let { assetDao.getPromptPresetById(it) }
            ?: assetDao.getDefaultRpPromptPreset()

        val rawCast = card.decodedCast()
        val cardAvatarRel = LocalMediaPathResolver.toRelativePath(filesDirPath, card.avatarPath)
        val cardSpriteRel = LocalMediaPathResolver.toRelativePath(filesDirPath, card.spritePath) ?: cardAvatarRel
        val cardExprRels = card.decodedExpressionSprites()
            .mapNotNull { (k, v) -> LocalMediaPathResolver.toRelativePath(filesDirPath, v)?.let { k to it } }
            .toMap()

        val normalizedCast = if (rawCast.isNotEmpty()) {
            val leadIdx = rawCast.indexOfFirst { !it.isPlayer }.takeIf { it >= 0 } ?: 0
            rawCast.mapIndexed { idx, ch ->
                val relAvatar = LocalMediaPathResolver.toRelativePath(filesDirPath, ch.avatarPath)
                    ?: if (idx == leadIdx) cardAvatarRel else null
                val relSprite = LocalMediaPathResolver.toRelativePath(filesDirPath, ch.spritePath)
                    ?: if (idx == leadIdx) cardSpriteRel else null
                val relExpr = ch.expressionSprites
                    .mapNotNull { (k, p) -> LocalMediaPathResolver.toRelativePath(filesDirPath, p)?.let { k to it } }
                    .toMap()
                    .ifEmpty { if (idx == leadIdx) cardExprRels else emptyMap() }
                if (ch.isPlayer && persona != null) {
                    ch.copy(
                        name = persona.name.ifBlank { ch.name },
                        background = persona.description.ifBlank { ch.background },
                        avatarPath = LocalMediaPathResolver.toRelativePath(filesDirPath, persona.avatarPath) ?: relAvatar,
                        spritePath = relSprite,
                        expressionSprites = relExpr,
                    )
                } else {
                    ch.copy(
                        avatarPath = relAvatar,
                        spritePath = relSprite,
                        expressionSprites = relExpr,
                    )
                }
            }
        } else {
            listOf(
                RpCharacter(
                    id = "npc_${card.id.take(8)}",
                    name = card.name.ifBlank { "主角" },
                    role = "companion",
                    background = card.description,
                    personality = card.personality,
                    avatarPath = cardAvatarRel,
                    spritePath = cardSpriteRel,
                    expressionSprites = cardExprRels,
                    inScene = true,
                    stageSlot = "center",
                ),
            )
        }

        val style = RpPlayStyle.entries.firstOrNull { it.name == card.playStyle } ?: RpPlayStyle.chengguang
        val altGreetings = card.decodedAltGreetings()
        val regexScripts = card.decodedRegexScripts()
        val visualTheme = card.decodedVisualTheme()

        val baseRp = RpConfig(
            title = card.name.ifBlank { "未命名剧本" },
            worldview = card.scenario,
            systemRules = card.systemPrompt,
            narratorStyle = card.creatorNotes,
            playerName = persona?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "旅人",
            playerPersona = persona?.description?.trim().orEmpty(),
            authorNote = card.depthPromptText,
            authorNoteDepth = card.depthPromptDepth.coerceIn(0, 12),
            cast = normalizedCast,
            state = RpGameState(
                scene = card.scenario.lineSequence().firstOrNull()?.trim()?.take(48).orEmpty(),
            ),
            greeting = card.firstMes,
            alternateGreetings = altGreetings,
            mesExample = card.mesExample,
            postHistoryInstructions = card.postHistoryInstructions,
            cardSource = card.sourceSpec,
            playStyle = style,
            theaterMode = style == RpPlayStyle.chengguang,
            visualTheme = visualTheme,
            regexScripts = regexScripts,
        )

        val normalizedRp = expandRuntimeRpMacros(baseRp)
        val greetingMsg = buildOpeningGreetingMessage(
            conversationId = newConvId,
            rp = normalizedRp,
            rawGreetings = listOf(card.firstMes) + altGreetings,
            selectedGreetingIndex = selectedGreetingIndex,
            now = now,
        )

        val conv = ChatConversation(
            id = newConvId,
            title = normalizedRp.title,
            mode = ConversationMode.rp,
            groupId = groupId,
            model = modelId,
            params = defaultChatParams(settings),
            rp = normalizedRp,
            characterCardId = card.id,
            personaId = persona?.id,
            presetId = preset?.id,
            createdAt = now,
            updatedAt = now,
        )
        persistCampaignTransaction(
            conv = conv,
            greetingMsg = greetingMsg,
            starterLore = emptyList(),
            worldbookIds = worldbookIds,
            now = now,
        )
        return newConvId
    }

    suspend fun launchFromPreset(
        presetTemplate: RpPresetTemplate,
        selectedGreetingIndex: Int = 0,
        personaId: String? = null,
        worldbookIds: List<String> = emptyList(),
        promptPresetId: String? = null,
        customTitle: String? = null,
        playStyleOverride: RpPlayStyle? = null,
        playerNameOverride: String? = null,
        playerPersonaOverride: String? = null,
        groupId: Long? = null,
        modelId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val newConvId = UUID.randomUUID().toString()
        val filesDirPath = filesDir.absolutePath
        val settings = settingsStore.currentSettings()
        val persona = personaId?.takeIf { it.isNotBlank() }?.let { assetDao.getPersonaById(it) }
            ?: assetDao.getDefaultPersona()
        val promptPreset = promptPresetId?.takeIf { it.isNotBlank() }?.let { assetDao.getPromptPresetById(it) }
            ?: assetDao.getDefaultRpPromptPreset()

        val baseCfg = presetTemplate.config
        val resolvedStyle = playStyleOverride ?: baseCfg.playStyle
        val normalizedCast = baseCfg.cast.map { ch ->
            ch.copy(
                avatarPath = LocalMediaPathResolver.toRelativePath(filesDirPath, ch.avatarPath),
                spritePath = LocalMediaPathResolver.toRelativePath(filesDirPath, ch.spritePath),
            )
        }
        val baseRp = baseCfg.copy(
            title = customTitle?.trim()?.ifBlank { null } ?: baseCfg.title,
            playStyle = resolvedStyle,
            theaterMode = resolvedStyle == RpPlayStyle.chengguang,
            playerName = playerNameOverride?.trim()?.ifBlank { null }
                ?: persona?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseCfg.playerName,
            playerPersona = playerPersonaOverride?.trim()?.ifBlank { null }
                ?: persona?.description?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseCfg.playerPersona,
            cast = normalizedCast,
        )
        val normalizedRp = expandRuntimeRpMacros(baseRp)
        val rawGreetings = listOf(normalizedRp.greeting) + normalizedRp.alternateGreetings
        val greetingMsg = buildOpeningGreetingMessage(
            conversationId = newConvId,
            rp = normalizedRp,
            rawGreetings = rawGreetings,
            selectedGreetingIndex = selectedGreetingIndex,
            now = now,
        )
        val starterLore = buildStarterLoreEntries(
            conversationId = newConvId,
            starterLore = presetTemplate.starterLore,
            now = now,
        )

        val conv = ChatConversation(
            id = newConvId,
            title = normalizedRp.title,
            mode = ConversationMode.rp,
            groupId = groupId,
            model = modelId,
            params = defaultChatParams(settings),
            rp = normalizedRp,
            personaId = persona?.id,
            presetId = promptPreset?.id,
            createdAt = now,
            updatedAt = now,
        )
        persistCampaignTransaction(
            conv = conv,
            greetingMsg = greetingMsg,
            starterLore = starterLore,
            worldbookIds = worldbookIds,
            now = now,
        )
        return newConvId
    }

    suspend fun insertRpConversation(
        rp: RpConfig,
        importedLore: List<NovelLoreEntry> = emptyList(),
        characterCardId: String? = null,
        mountedWorldbookId: String? = null,
        groupId: Long? = null,
        modelId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val convId = UUID.randomUUID().toString()
        val settings = settingsStore.currentSettings()
        val defaultPersona: PersonaEntity? = assetDao.getDefaultPersona()
        val defaultRpPreset = assetDao.getDefaultRpPromptPreset()
        val effectivePlayerName = defaultPersona?.name?.trim()?.takeIf { it.isNotEmpty() } ?: rp.playerName
        val normalizedRp = expandRuntimeRpMacros(rp.copy(playerName = effectivePlayerName))

        val rawGreetings = listOf(normalizedRp.greeting) + normalizedRp.alternateGreetings
        val greetingMsg = buildOpeningGreetingMessage(
            conversationId = convId,
            rp = normalizedRp,
            rawGreetings = rawGreetings,
            selectedGreetingIndex = 0,
            now = now,
        )
        val normalizedLore = importedLore.map { it.copy(conversationId = convId) }
        val conv = ChatConversation(
            id = convId,
            title = normalizedRp.title,
            mode = ConversationMode.rp,
            groupId = groupId,
            model = modelId,
            params = defaultChatParams(settings),
            rp = normalizedRp,
            characterCardId = characterCardId,
            personaId = defaultPersona?.id,
            presetId = defaultRpPreset?.id,
            createdAt = now,
            updatedAt = now,
        )
        val wbIds = listOfNotNull(mountedWorldbookId?.trim()?.takeIf { it.isNotEmpty() })
        persistCampaignTransaction(
            conv = conv,
            greetingMsg = greetingMsg,
            starterLore = normalizedLore,
            worldbookIds = wbIds,
            now = now,
        )
        return convId
    }

    private suspend fun persistCampaignTransaction(
        conv: ChatConversation,
        greetingMsg: ChatMessage?,
        starterLore: List<NovelLoreEntry>,
        worldbookIds: List<String>,
        now: Long,
    ) {
        val refs = worldbookIds.distinct().mapIndexed { idx, wbId ->
            ConversationWorldbookCrossRef(
                conversationId = conv.id,
                worldbookId = wbId,
                sortOrder = idx,
                enabled = true,
                updatedAt = now,
            )
        }
        database.withTransaction {
            chatDao.upsertConversation(conv.toEntity())
            if (greetingMsg != null) {
                chatDao.upsertMessage(greetingMsg.toEntity())
            }
            if (starterLore.isNotEmpty()) {
                chatDao.upsertNovelLoreList(starterLore.map { it.toEntity() })
            }
            if (refs.isNotEmpty()) {
                assetDao.replaceMountedWorldbooks(conv.id, refs)
            }
        }
    }

    companion object {
        fun expandRuntimeRpMacros(rp: RpConfig): RpConfig {
            val expandedState = rp.state.copy(
                scene = RpEngine.replaceMacros(rp.state.scene, rp),
                chapterTitle = RpEngine.replaceMacros(rp.state.chapterTitle, rp),
                inventory = rp.state.inventory.map { RpEngine.replaceMacros(it, rp) },
                quests = rp.state.quests.map { RpEngine.replaceMacros(it, rp) },
            )
            val expandedChoices = rp.pendingChoices.map { ch ->
                ch.copy(
                    label = RpEngine.replaceMacros(ch.label, rp),
                    actionText = RpEngine.replaceMacros(ch.actionText, rp),
                    consequenceHint = ch.consequenceHint?.let { RpEngine.replaceMacros(it, rp) },
                )
            }
            return rp.copy(
                state = expandedState,
                pendingChoices = expandedChoices,
            )
        }

        fun buildOpeningGreetingMessage(
            conversationId: String,
            rp: RpConfig,
            rawGreetings: List<String>,
            selectedGreetingIndex: Int,
            now: Long,
        ): ChatMessage? {
            val cleaned = rawGreetings.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return null
            val leadSpeaker = rp.cast.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() }
            val openingVariants = cleaned.map { raw ->
                val rendered = RpEngine.replaceMacros(raw, rp)
                MessageVariant(
                    id = UUID.randomUUID().toString(),
                    content = rendered,
                    createdAt = now,
                    rpStateSnapshot = rp.state,
                    rpCastSnapshot = rp.cast,
                    rpChoices = rp.pendingChoices,
                    rpSpeakerName = leadSpeaker,
                )
            }
            val activeIdx = selectedGreetingIndex.coerceIn(0, openingVariants.lastIndex)
            val activeContent = openingVariants[activeIdx].content
            return ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "assistant",
                content = activeContent,
                createdAt = now,
                seq = 1,
                rpChoices = rp.pendingChoices,
                status = "done",
                tokenEstimate = ContextBuilder.estimateMessageTokens(activeContent),
                variants = openingVariants,
                activeVariantIndex = activeIdx,
            )
        }

        fun defaultChatParams(
            settings: AppSettings,
            mode: ConversationMode = ConversationMode.rp,
        ): ChatParams = ChatParams(
            maxTokens = settings.defaultMaxOutputTokens.takeIf { it in 256..131_072 },
            contextWindowOverride = settings.defaultContextWindowOverride.takeIf { it in 4096..2_000_000 },
            maxHistoryMessages = settings.defaultMaxHistoryMessages.takeIf { it in 1..500 },
            minOutputChars = settings.defaultMinOutputFor(mode),
            autoContinueShortReply = settings.defaultAutoContinueShort,
        )

        fun buildStarterLoreEntries(
            conversationId: String,
            starterLore: List<RpStarterLore>,
            now: Long,
        ): List<NovelLoreEntry> = starterLore.map { s ->
            val entry = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = s.category,
                title = s.title,
                content = s.content,
                keywords = s.keywords.ifEmpty { listOf(s.title) },
                alwaysActive = s.alwaysActive,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry))
        }
    }
}
