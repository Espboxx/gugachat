package com.gugachat.app.domain.chat

import kotlinx.serialization.Serializable

@Serializable
enum class ConversationMode {
    chat,
    novel,
    rp,
}

@Serializable
enum class NovelInputKind {
    director,
    @Suppress("EnumEntryName")
    `continue`,
    branch,
    dialogue,
}

@Serializable
enum class RpInputKind {
    action,
    speech,
    thought,
    gm_command,
}

@Serializable
enum class MemoryCategory {
    preference,
    profile,
    project,
    rule,
    other,
}

@Serializable
enum class MemoryBudget {
    low,
    standard,
    high,
}

@Serializable
enum class AttachmentKind {
    image,
    file,
}

@Serializable
enum class FileInjectMode {
    auto,
    retrieve,
    head_tail,
    full,
}

@Serializable
data class ChatParams(
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val maxTokens: Int? = null,
    val reasoningEffort: String? = null,
    val maxImageTurns: Int = 3,
    val maxFileTurns: Int = 2,
    val enableReasoning: Boolean = true,
    val enableAutoMemory: Boolean = true,
    val enableAutoConsolidate: Boolean = true,
    val contextWindowOverride: Int? = null,
    val maxHistoryMessages: Int? = null,
    val activeWindowStartSeq: Int? = null,
    val enablePromptCaching: Boolean = true,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val stopSequences: List<String> = emptyList(),
    val assistantPrefill: String = "",
)

@Serializable
data class ChatAttachmentMeta(
    val id: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val localPath: String = "",
    val kind: AttachmentKind = AttachmentKind.image,
    val fileName: String = "",
    val textPath: String = "",
    val charCount: Int = 0,
    val estimatedTokens: Int = 0,
    val pageCount: Int = 0,
    val previewSnippet: String = "",
    val extractionNote: String = "",
    val injectMode: FileInjectMode = FileInjectMode.auto,
) {
    val isImage: Boolean get() = kind == AttachmentKind.image
    val isFile: Boolean get() = kind == AttachmentKind.file
}

@Serializable
data class MessageUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cacheReadInputTokens: Int? = null,
    val reasoningDurationMs: Long? = null,
)

@Serializable
data class NovelStatusHud(
    val chapterTitle: String = "",
    val time: String = "",
    val location: String = "",
    val present: List<String> = emptyList(),
    val mood: String = "",
    val conflict: String = "",
    val foreshadows: List<String> = emptyList(),
)

@Serializable
enum class RpPlayStyle {
    chengguang,
    tavern,
    trpg,
}

@Serializable
data class RpEndingRecord(
    val id: String,
    val code: String = "NE",
    val title: String,
    val summary: String = "",
    val unlockedAt: Long = 0L,
    val turnSeq: Int = 0,
)

@Serializable
data class RpTurnDelta(
    val label: String,
    val kind: String = "stat", // hp | mp | gold | affinity | item_add | item_remove | stat | quest | flag | chapter | ending
    val positive: Boolean = true,
)

@Serializable
data class RpRegexScript(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val findRegex: String = "",
    val replaceString: String = "",
    val placement: String = "ai_output", // "ai_output" | "user_input" | "both"
    val markdownOnly: Boolean = true,
    val promptOnly: Boolean = false,
    val flags: String = "gms",
    val trimStrings: List<String> = emptyList(),
)

@Serializable
data class RpCgRecord(
    val id: String,
    val title: String,
    val caption: String = "",
    val imagePath: String,
    val scene: String = "",
    val characterNames: List<String> = emptyList(),
    val unlockedAt: Long = 0L,
    val turnSeq: Int = 0,
)

@Serializable
data class RpVisualTheme(
    val dialogStyle: String = "glass", // "glass" | "classic_vn" | "minimal" | "cyber" | "parchment"
    val dialogOpacity: Float = 0.86f,
    val backdropDim: Float = 0.35f,
    val fontSizeSp: Float = 15.5f,
    val useSerif: Boolean = false,
    val accentHex: String = "",
    val spriteLayout: String = "stage", // "stage" | "portrait_card" | "hidden"
    val spriteHeightRatio: Float = 0.44f,
    val showNameplate: Boolean = true,
    val showTurnDeltas: Boolean = true,
    val showCustomStatusWidget: Boolean = false,
    val customStatusHtml: String = "",
    val customCss: String = "",
)

@Serializable
data class RpCharacter(
    val id: String,
    val name: String,
    val role: String = "companion",
    val gender: String? = null,
    val appearance: String = "",
    val personality: String = "",
    val background: String = "",
    val relationship: String = "",
    val affinity: Int = 0,
    val mood: String = "平静",
    val status: String = "",
    val avatar: String? = null,
    val aliases: List<String> = emptyList(),
    val isPlayer: Boolean = false,
    val speakingStyle: String = "",
    val secret: String = "",
    val avatarPath: String? = null,
    val inScene: Boolean = true,
    val spritePath: String? = null,
    val expressionSprites: Map<String, String> = emptyMap(),
    val stageSlot: String = "auto", // "left" | "center" | "right" | "auto"
    val spriteScale: Float = 1.0f,
    val spriteOffsetY: Float = 0f,
    val appearancePrompt: String = "",
)

@Serializable
data class RpGameState(
    val scene: String = "",
    val time: String = "",
    val weather: String = "",
    val hp: Int = 100,
    val maxHp: Int = 100,
    val mp: Int = 50,
    val maxMp: Int = 50,
    val level: Int = 1,
    val gold: Int = 0,
    val inventory: List<String> = emptyList(),
    val quests: List<String> = emptyList(),
    val flags: Map<String, String> = emptyMap(),
    val stats: Map<String, Int> = emptyMap(),
    val chapterTitle: String = "",
    val actNumber: Int = 1,
    val mood: String = "",
    val sceneImagePath: String? = null,
    val turnDeltas: List<RpTurnDelta> = emptyList(),
    val unlockedEndings: List<RpEndingRecord> = emptyList(),
    val unlockedCgs: List<RpCgRecord> = emptyList(),
    val activeCgId: String? = null,
)

@Serializable
data class RpDiceRoll(
    val expression: String,
    val rolls: List<Int>,
    val modifier: Int = 0,
    val total: Int,
    val difficulty: Int? = null,
    val skill: String? = null,
    val success: Boolean? = null,
    val critical: String? = null, // "nat20" | "nat1" | null
    val mode: String = "normal", // "normal" | "advantage" | "disadvantage"
    val createdAt: Long = 0L,
)

@Serializable
data class RpChoice(
    val id: String,
    val label: String,
    val actionText: String,
    val inputKind: RpInputKind = RpInputKind.action,
    val skillCheck: String? = null,
    val difficulty: Int? = null,
    val tone: String? = null,
    val conditionText: String? = null,
    val requiresStat: String? = null,
    val requiresStatMin: Int? = null,
    val requiresAffinityNpc: String? = null,
    val requiresAffinityMin: Int? = null,
    val requiresItem: String? = null,
    val requiresFlag: String? = null,
    val costGold: Int? = null,
    val consequenceHint: String? = null,
)

@Serializable
data class MessageVariant(
    val id: String,
    val content: String,
    val reasoning: String = "",
    val createdAt: Long,
    val usage: MessageUsage? = null,
    val model: String? = null,
    val rpStateSnapshot: RpGameState? = null,
    val rpChoices: List<RpChoice> = emptyList(),
    val novelStatusSnapshot: NovelStatusHud? = null,
    val novelChoices: List<String> = emptyList(),
    val rpCastSnapshot: List<RpCharacter>? = null,
    val rpDeltas: List<RpTurnDelta> = emptyList(),
    val rpSpeakerName: String? = null,
    val rpEndingUnlocked: RpEndingRecord? = null,
    val reasoningDurationMs: Long? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "system" | "user" | "assistant"
    val content: String,
    val reasoning: String = "",
    val reasoningDurationMs: Long? = null,
    val model: String? = null,
    val createdAt: Long,
    val seq: Int,
    val attachmentIds: List<String> = emptyList(),
    val inputKind: NovelInputKind? = null,
    val rpInputKind: RpInputKind? = null,
    val rpDiceRoll: RpDiceRoll? = null,
    val rpChoices: List<RpChoice> = emptyList(),
    val novelChoices: List<String> = emptyList(),
    val status: String = "done", // "streaming" | "done" | "error" | "aborted"
    val error: String? = null,
    val usage: MessageUsage? = null,
    val tokenEstimate: Int = 0,
    val variants: List<MessageVariant> = emptyList(),
    val activeVariantIndex: Int = 0,
    val editedAt: Long? = null,
    val supersededAtSeq: Int? = null,
    val deletedAt: Long? = null,
)

fun ChatMessage.activeVariant(): MessageVariant? =
    variants.getOrNull(activeVariantIndex) ?: variants.lastOrNull()

@Serializable
data class UserMemory(
    val id: String,
    val category: MemoryCategory = MemoryCategory.other,
    val content: String,
    val aliases: List<String> = emptyList(),
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedTokens: Int = 0,
)

@Serializable
data class MemoryLedgerItem(
    val id: String,
    val conversationId: String,
    val kind: String = "fact", // fact | relation | promise | foreshadow | event | inventory | quest | other
    val title: String,
    val content: String,
    val aliases: List<String> = emptyList(),
    val keyEntities: List<String> = emptyList(),
    val entityType: String? = null,
    val gender: String? = null,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val enabled: Boolean = true,
    val resolved: Boolean = false,
    val stale: Boolean = false,
    val validFromSeq: Int = 0,
    val supersededAtSeq: Int? = null,
    val lastReferencedSeq: Int? = null,
    val toSeq: Int? = null,
    val origin: String = "ai", // "ai" | "user" | "system"
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val estimatedTokens: Int = 0,
)

@Serializable
data class MemoryEpisode(
    val id: String,
    val conversationId: String,
    val level: Int = 1, // 1=episode, 2=arc, 3=chronicle
    val title: String,
    val summary: String,
    val keyEntities: List<String> = emptyList(),
    val fromSeq: Int,
    val toSeq: Int,
    val locked: Boolean = false,
    val stale: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val estimatedTokens: Int = 0,
)

@Serializable
data class NovelLoreEntry(
    val id: String,
    val conversationId: String,
    val category: String = "character", // character | location | item | faction | rule | concept | other
    val title: String,
    val content: String,
    val keywords: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val alwaysActive: Boolean = false,
    val priority: Int = 50,
    val enabled: Boolean = true,
    val validFromSeq: Int = 0,
    val supersededAtSeq: Int? = null,
    val lastReferencedSeq: Int? = null,
    val stale: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val estimatedTokens: Int = 0,
)

@Serializable
data class NovelChapter(
    val id: String,
    val conversationId: String,
    val order: Int,
    val title: String,
    val outline: String = "",
    val summary: String = "",
    val status: String = "writing", // draft | writing | done
    val wordCount: Int = 0,
    val startSeq: Int = 0,
    val endSeq: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class NovelConfig(
    val title: String = "未命名小说",
    val synopsis: String = "",
    val genre: String = "",
    val style: String = "",
    val styleSample: String = "",
    val pov: String = "third_limited", // third_limited | first | third_omniscient
    val playStyle: String = "hybrid", // hybrid | author | interactive
    val antiAiRules: Boolean = true,
    val worldview: String = "",
    val mainOutline: String = "",
    val authorNote: String = "",
    val targetWordsPerChapter: Int = 2500,
    val loreTokenBudget: Int = 1600,
    val enableStatusHud: Boolean = true,
    val autoSyncLore: Boolean = true,
    val autoAdvanceChapter: Boolean = true,
    val enableChoices: Boolean = true,
    val activeChapterId: String? = null,
    val currentStatus: NovelStatusHud? = null,
    val pendingChoices: List<String> = emptyList(),
)

@Serializable
data class RpConfig(
    val title: String = "未命名角色扮演",
    val worldview: String = "",
    val systemRules: String = "",
    val narratorStyle: String = "",
    val playerName: String = "旅人",
    val playerPersona: String = "",
    val authorNote: String = "",
    val enableDiceCheck: Boolean = true,
    val enableStateHud: Boolean = true,
    val enableChoices: Boolean = true,
    val enableLedger: Boolean = true,
    val cast: List<RpCharacter> = emptyList(),
    val state: RpGameState = RpGameState(),
    val pendingChoices: List<RpChoice> = emptyList(),
    val greeting: String = "",
    val cardSource: String? = null,
    val playStyle: RpPlayStyle = RpPlayStyle.chengguang,
    val theaterMode: Boolean = false,
    val alternateGreetings: List<String> = emptyList(),
    val mesExample: String = "",
    val postHistoryInstructions: String = "",
    val authorNoteDepth: Int = 1,
    val loreTokenBudget: Int = 1600,
    val autoDiceSkillBonus: Boolean = true,
    val quickActions: List<String> = emptyList(),
    val visualTheme: RpVisualTheme = RpVisualTheme(),
    val regexScripts: List<RpRegexScript> = emptyList(),
    val enableHtmlCards: Boolean = true,
)

@Serializable
data class RpSaveSlot(
    val id: String,
    val conversationId: String,
    val name: String,
    val note: String = "",
    val createdAt: Long,
    val messageSeq: Int,
    val rpConfig: RpConfig,
    val ledgerItems: List<MemoryLedgerItem> = emptyList(),
    val episodes: List<MemoryEpisode> = emptyList(),
    val activeMessagesSnapshot: List<ChatMessage> = emptyList(),
)

@Serializable
data class ChatConversation(
    val id: String,
    val title: String,
    val mode: ConversationMode = ConversationMode.chat,
    val groupId: Long? = null,
    val model: String? = null,
    val systemPrompt: String = "",
    val params: ChatParams = ChatParams(),
    val novel: NovelConfig? = null,
    val rp: RpConfig? = null,
    val archivedSummary: String = "",
    val summarizedUntilSeq: Int = 0,
    val tokenCalibration: Double = 1.0,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

@Serializable
data class RecalledMemoryInspectItem(
    val id: String,
    val title: String,
    val contentPreview: String,
    val category: String,
    val source: String, // user_memory_archival | episode_archived | novel_lore | rp_ledger
    val score: Double? = null,
    val estimatedTokens: Int = 0,
    val matchedTerms: List<String> = emptyList(),
    val triggerKind: String = "lexical", // constant | pinned_ledger | keyword | semantic | lexical | recursion_1hop
)

@Serializable
data class ContextWaterlineSummary(
    val usedTokens: Int = 0,
    val budgetTokens: Int = 0,
    val effectiveLimitTokens: Int = 0,
    val modelContextWindow: Int = 128000,
    val percentage: Int = 0,
    val exceedsModelWindow: Boolean = false,
    val activeMessageCount: Int = 0,
    val trimmedMessageCount: Int = 0,
    val decayedImageMessageIds: Set<String> = emptySet(),
    val decayedFileMessageIds: Set<String> = emptySet(),
    val fileTokens: Int = 0,
    val memoryTokens: Int = 0,
    val summaryTokens: Int = 0,
    val triggeredLoreIds: List<String> = emptyList(),
    val triggeredLoreTitles: List<String> = emptyList(),
    val novelLoreTokens: Int = 0,
    val retrievedMemoryCount: Int = 0,
    val recalledMemoryTokens: Int = 0,
    val recalledItems: List<RecalledMemoryInspectItem> = emptyList(),
    val sentTokens: Int = 0,
    val sentPercentage: Int = 0,
    val archiveTotalTokens: Int = 0,
    val isSliding: Boolean = false,
    val hardOverflow: Boolean = false,
    val uncompactedOverflowCount: Int = 0,
    val baseSystemTokens: Int = 0,
    val recallBudgetTokens: Int = 0,
    val recalledTotalTokens: Int = 0,
    val retrievalModeUsed: String = "lexical",
    val droppedRecallCount: Int = 0,
)

@Serializable
data class WireChatContentPart(
    val type: String, // "text" | "image_url"
    val text: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class WireChatMessage(
    val role: String,
    val textContent: String? = null,
    val parts: List<WireChatContentPart>? = null,
)

data class BuiltChatContext(
    val wireMessages: List<WireChatMessage>,
    val summary: ContextWaterlineSummary,
)
