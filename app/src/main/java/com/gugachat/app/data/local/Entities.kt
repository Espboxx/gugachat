package com.gugachat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.AttachmentKind
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.MessageUsage
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.UserMemory
import kotlinx.serialization.encodeToString

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String,
    val groupId: Long?,
    val model: String?,
    val systemPrompt: String,
    val paramsJson: String,
    val novelJson: String?,
    val rpJson: String?,
    val archivedSummary: String,
    val summarizedUntilSeq: Int,
    val tokenCalibration: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("conversationId", "seq")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String,
    val model: String?,
    val createdAt: Long,
    val seq: Int,
    val attachmentIdsJson: String,
    val inputKind: String?,
    val rpInputKind: String?,
    val rpDiceRollJson: String?,
    val rpChoicesJson: String,
    val novelChoicesJson: String,
    val status: String,
    val error: String?,
    val usageJson: String?,
    val tokenEstimate: Int,
    val variantsJson: String,
    val activeVariantIndex: Int,
    val editedAt: Long?,
    val supersededAtSeq: Int?,
    val deletedAt: Long?,
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val id: String,
    val category: String,
    val content: String,
    val aliasesJson: String,
    val pinned: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedTokens: Int,
)

@Entity(
    tableName = "novel_chapters",
    indices = [Index("conversationId")],
)
data class NovelChapterEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sortOrder: Int,
    val title: String,
    val outline: String,
    val summary: String,
    val status: String,
    val wordCount: Int,
    val startSeq: Int,
    val endSeq: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "novel_lore",
    indices = [Index("conversationId")],
)
data class NovelLoreEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val category: String,
    val title: String,
    val content: String,
    val keywordsJson: String,
    val aliasesJson: String,
    val alwaysActive: Boolean,
    val priority: Int,
    val enabled: Boolean,
    val validFromSeq: Int,
    val supersededAtSeq: Int?,
    val lastReferencedSeq: Int?,
    val stale: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedTokens: Int,
)

@Entity(
    tableName = "memory_ledger",
    indices = [Index("conversationId")],
)
data class MemoryLedgerEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val kind: String,
    val title: String,
    val content: String,
    val aliasesJson: String,
    val keyEntitiesJson: String,
    val entityType: String?,
    val gender: String?,
    val pinned: Boolean,
    val locked: Boolean,
    val enabled: Boolean,
    val resolved: Boolean,
    val stale: Boolean,
    val validFromSeq: Int,
    val supersededAtSeq: Int?,
    val lastReferencedSeq: Int?,
    val toSeq: Int?,
    val origin: String,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedTokens: Int,
)

@Entity(
    tableName = "memory_episodes",
    indices = [Index("conversationId")],
)
data class MemoryEpisodeEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val level: Int,
    val title: String,
    val summary: String,
    val keyEntitiesJson: String,
    val fromSeq: Int,
    val toSeq: Int,
    val locked: Boolean,
    val stale: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedTokens: Int,
)

@Entity(
    tableName = "rp_save_slots",
    indices = [Index("conversationId")],
)
data class RpSaveSlotEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val name: String,
    val note: String,
    val createdAt: Long,
    val messageSeq: Int,
    val rpConfigJson: String,
    val ledgerItemsJson: String,
    val episodesJson: String,
    val activeMessagesSnapshotJson: String,
)

@Entity(tableName = "attachment_meta")
data class AttachmentMetaEntity(
    @PrimaryKey val id: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: Long,
    val kind: String = "image",
    val fileName: String = "",
    val textPath: String = "",
    val charCount: Int = 0,
    val estimatedTokens: Int = 0,
    val pageCount: Int = 0,
    val previewSnippet: String = "",
    val extractionNote: String = "",
    val injectMode: String = "auto",
)

private inline fun <reified T> decodeOrDefault(raw: String?, fallback: T): T {
    if (raw.isNullOrBlank()) return fallback
    return try {
        AppJson.decodeFromString<T>(raw)
    } catch (_: Exception) {
        fallback
    }
}

fun ConversationEntity.toDomain(): ChatConversation = ChatConversation(
    id = id,
    title = title,
    mode = ConversationMode.entries.firstOrNull { it.name == mode } ?: ConversationMode.chat,
    groupId = groupId,
    model = model,
    systemPrompt = systemPrompt,
    params = decodeOrDefault(paramsJson, ChatParams()),
    novel = novelJson?.let { decodeOrDefault<NovelConfig?>(it, null) },
    rp = rpJson?.let { decodeOrDefault<RpConfig?>(it, null) },
    archivedSummary = archivedSummary,
    summarizedUntilSeq = summarizedUntilSeq,
    tokenCalibration = tokenCalibration,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
)

fun ChatConversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    mode = mode.name,
    groupId = groupId,
    model = model,
    systemPrompt = systemPrompt,
    paramsJson = AppJson.encodeToString(params),
    novelJson = novel?.let { AppJson.encodeToString(it) },
    rpJson = rp?.let { AppJson.encodeToString(it) },
    archivedSummary = archivedSummary,
    summarizedUntilSeq = summarizedUntilSeq,
    tokenCalibration = tokenCalibration,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
)

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    reasoning = reasoning,
    model = model,
    createdAt = createdAt,
    seq = seq,
    attachmentIds = decodeOrDefault(attachmentIdsJson, emptyList()),
    inputKind = inputKind?.let { raw -> NovelInputKind.entries.firstOrNull { it.name == raw } },
    rpInputKind = rpInputKind?.let { raw -> RpInputKind.entries.firstOrNull { it.name == raw } },
    rpDiceRoll = rpDiceRollJson?.let { decodeOrDefault<RpDiceRoll?>(it, null) },
    rpChoices = decodeOrDefault(rpChoicesJson, emptyList()),
    novelChoices = decodeOrDefault(novelChoicesJson, emptyList()),
    status = status,
    error = error,
    usage = usageJson?.let { decodeOrDefault<MessageUsage?>(it, null) },
    tokenEstimate = tokenEstimate,
    variants = decodeOrDefault(variantsJson, emptyList()),
    activeVariantIndex = activeVariantIndex,
    editedAt = editedAt,
    supersededAtSeq = supersededAtSeq,
    deletedAt = deletedAt,
)

fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    reasoning = reasoning,
    model = model,
    createdAt = createdAt,
    seq = seq,
    attachmentIdsJson = AppJson.encodeToString(attachmentIds),
    inputKind = inputKind?.name,
    rpInputKind = rpInputKind?.name,
    rpDiceRollJson = rpDiceRoll?.let { AppJson.encodeToString(it) },
    rpChoicesJson = AppJson.encodeToString(rpChoices),
    novelChoicesJson = AppJson.encodeToString(novelChoices),
    status = status,
    error = error,
    usageJson = usage?.let { AppJson.encodeToString(it) },
    tokenEstimate = tokenEstimate,
    variantsJson = AppJson.encodeToString(variants),
    activeVariantIndex = activeVariantIndex,
    editedAt = editedAt,
    supersededAtSeq = supersededAtSeq,
    deletedAt = deletedAt,
)

fun UserMemoryEntity.toDomain(): UserMemory = UserMemory(
    id = id,
    category = MemoryCategory.entries.firstOrNull { it.name == category } ?: MemoryCategory.other,
    content = content,
    aliases = decodeOrDefault(aliasesJson, emptyList()),
    pinned = pinned,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun UserMemory.toEntity(): UserMemoryEntity = UserMemoryEntity(
    id = id,
    category = category.name,
    content = content,
    aliasesJson = AppJson.encodeToString(aliases),
    pinned = pinned,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun NovelChapterEntity.toDomain(): NovelChapter = NovelChapter(
    id = id,
    conversationId = conversationId,
    order = sortOrder,
    title = title,
    outline = outline,
    summary = summary,
    status = status,
    wordCount = wordCount,
    startSeq = startSeq,
    endSeq = endSeq,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NovelChapter.toEntity(): NovelChapterEntity = NovelChapterEntity(
    id = id,
    conversationId = conversationId,
    sortOrder = order,
    title = title,
    outline = outline,
    summary = summary,
    status = status,
    wordCount = wordCount,
    startSeq = startSeq,
    endSeq = endSeq,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NovelLoreEntity.toDomain(): NovelLoreEntry = NovelLoreEntry(
    id = id,
    conversationId = conversationId,
    category = category,
    title = title,
    content = content,
    keywords = decodeOrDefault(keywordsJson, emptyList()),
    aliases = decodeOrDefault(aliasesJson, emptyList()),
    alwaysActive = alwaysActive,
    priority = priority,
    enabled = enabled,
    validFromSeq = validFromSeq,
    supersededAtSeq = supersededAtSeq,
    lastReferencedSeq = lastReferencedSeq,
    stale = stale,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun NovelLoreEntry.toEntity(): NovelLoreEntity = NovelLoreEntity(
    id = id,
    conversationId = conversationId,
    category = category,
    title = title,
    content = content,
    keywordsJson = AppJson.encodeToString(keywords),
    aliasesJson = AppJson.encodeToString(aliases),
    alwaysActive = alwaysActive,
    priority = priority,
    enabled = enabled,
    validFromSeq = validFromSeq,
    supersededAtSeq = supersededAtSeq,
    lastReferencedSeq = lastReferencedSeq,
    stale = stale,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun MemoryLedgerEntity.toDomain(): MemoryLedgerItem = MemoryLedgerItem(
    id = id,
    conversationId = conversationId,
    kind = kind,
    title = title,
    content = content,
    aliases = decodeOrDefault(aliasesJson, emptyList()),
    keyEntities = decodeOrDefault(keyEntitiesJson, emptyList()),
    entityType = entityType,
    gender = gender,
    pinned = pinned,
    locked = locked,
    enabled = enabled,
    resolved = resolved,
    stale = stale,
    validFromSeq = validFromSeq,
    supersededAtSeq = supersededAtSeq,
    lastReferencedSeq = lastReferencedSeq,
    toSeq = toSeq,
    origin = origin,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun MemoryLedgerItem.toEntity(): MemoryLedgerEntity = MemoryLedgerEntity(
    id = id,
    conversationId = conversationId,
    kind = kind,
    title = title,
    content = content,
    aliasesJson = AppJson.encodeToString(aliases),
    keyEntitiesJson = AppJson.encodeToString(keyEntities),
    entityType = entityType,
    gender = gender,
    pinned = pinned,
    locked = locked,
    enabled = enabled,
    resolved = resolved,
    stale = stale,
    validFromSeq = validFromSeq,
    supersededAtSeq = supersededAtSeq,
    lastReferencedSeq = lastReferencedSeq,
    toSeq = toSeq,
    origin = origin,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun MemoryEpisodeEntity.toDomain(): MemoryEpisode = MemoryEpisode(
    id = id,
    conversationId = conversationId,
    level = level,
    title = title,
    summary = summary,
    keyEntities = decodeOrDefault(keyEntitiesJson, emptyList()),
    fromSeq = fromSeq,
    toSeq = toSeq,
    locked = locked,
    stale = stale,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun MemoryEpisode.toEntity(): MemoryEpisodeEntity = MemoryEpisodeEntity(
    id = id,
    conversationId = conversationId,
    level = level,
    title = title,
    summary = summary,
    keyEntitiesJson = AppJson.encodeToString(keyEntities),
    fromSeq = fromSeq,
    toSeq = toSeq,
    locked = locked,
    stale = stale,
    createdAt = createdAt,
    updatedAt = updatedAt,
    estimatedTokens = estimatedTokens,
)

fun RpSaveSlotEntity.toDomain(): RpSaveSlot = RpSaveSlot(
    id = id,
    conversationId = conversationId,
    name = name,
    note = note,
    createdAt = createdAt,
    messageSeq = messageSeq,
    rpConfig = decodeOrDefault(rpConfigJson, RpConfig()),
    ledgerItems = decodeOrDefault(ledgerItemsJson, emptyList()),
    episodes = decodeOrDefault(episodesJson, emptyList()),
    activeMessagesSnapshot = decodeOrDefault(activeMessagesSnapshotJson, emptyList()),
)

fun RpSaveSlot.toEntity(): RpSaveSlotEntity = RpSaveSlotEntity(
    id = id,
    conversationId = conversationId,
    name = name,
    note = note,
    createdAt = createdAt,
    messageSeq = messageSeq,
    rpConfigJson = AppJson.encodeToString(rpConfig),
    ledgerItemsJson = AppJson.encodeToString(ledgerItems),
    episodesJson = AppJson.encodeToString(episodes),
    activeMessagesSnapshotJson = AppJson.encodeToString(activeMessagesSnapshot),
)

fun AttachmentMetaEntity.toDomain(): ChatAttachmentMeta = ChatAttachmentMeta(
    id = id,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    localPath = localPath,
    kind = AttachmentKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) } ?: AttachmentKind.image,
    fileName = fileName,
    textPath = textPath,
    charCount = charCount,
    estimatedTokens = estimatedTokens,
    pageCount = pageCount,
    previewSnippet = previewSnippet,
    extractionNote = extractionNote,
    injectMode = FileInjectMode.entries.firstOrNull { it.name.equals(injectMode, ignoreCase = true) } ?: FileInjectMode.auto,
)
