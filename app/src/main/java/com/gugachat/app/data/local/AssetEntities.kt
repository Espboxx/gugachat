package com.gugachat.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "character_cards",
    indices = [
        Index(value = ["favorite", "updatedAt"]),
        Index(value = ["contentFingerprint"]),
        Index(value = ["primaryWorldbookId"]),
    ],
)
data class CharacterCardEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val avatarPath: String? = null,
    val spritePath: String? = null,
    val expressionSpritesJson: String = "{}",
    val tagsJson: String = "[]",
    val playStyle: String = "chengguang",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val alternateGreetingsJson: String = "[]",
    val groupOnlyGreetingsJson: String = "[]",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val depthPromptText: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    val castJson: String = "[]",
    val regexScriptsJson: String = "[]",
    val visualThemeJson: String = "{}",
    val primaryWorldbookId: String? = null,
    val sourceSpec: String = "v2",
    val contentFingerprint: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
@Entity(
    tableName = "worldbooks",
    indices = [
        Index(value = ["globalEnabled"]),
        Index(value = ["updatedAt"]),
        Index(value = ["contentFingerprint"]),
    ],
)
data class WorldbookEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val description: String = "",
    val sourceFormat: String = "sillytavern",
    val globalEnabled: Boolean = false,
    val scanDepth: Int = 4,
    val tokenBudget: Int = 1600,
    val recursiveScanning: Boolean = true,
    val entryCount: Int = 0,
    val totalEstimatedTokens: Int = 0,
    val contentFingerprint: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
@Entity(
    tableName = "worldbook_entries",
    indices = [
        Index(value = ["worldbookId"]),
        Index(value = ["worldbookId", "enabled", "constant"]),
    ],
)
data class WorldbookEntryEntity(
    @PrimaryKey val id: String,
    val worldbookId: String = "",
    val uid: Int = 0,
    val title: String = "",
    val content: String = "",
    val category: String = "other",
    val keysJson: String = "[]",
    val secondaryKeysJson: String = "[]",
    val selectiveLogic: Int = 0,
    val constant: Boolean = false,
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = false,
    val useRegexKey: Boolean = false,
    val probability: Int = 100,
    val insertionOrder: Int = 100,
    val position: Int = 0,
    val depth: Int = 4,
    val outletRole: String = "system",
    val scanDepthOverride: Int? = null,
    val sticky: Int = 0,
    val cooldown: Int = 0,
    val delay: Int = 0,
    val preventRecursion: Boolean = false,
    val excludeRecursion: Boolean = false,
    val groupName: String = "",
    val groupWeight: Int = 100,
    val estimatedTokens: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
@Entity(
    tableName = "personas",
    indices = [Index(value = ["isDefault"])],
)
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val avatarPath: String? = null,
    val description: String = "",
    val injectionPosition: Int = 0,
    val injectionDepth: Int = 4,
    val isDefault: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
@Entity(
    tableName = "prompt_presets",
    indices = [Index(value = ["isDefaultRp"])],
)
data class PromptPresetEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val sourceFormat: String = "sillytavern",
    val blocksJson: String = "[]",
    val regexScriptsJson: String = "[]",
    val samplingPatchJson: String = "{}",
    val isDefaultRp: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
@Entity(
    tableName = "conversation_worldbooks",
    primaryKeys = ["conversationId", "worldbookId"],
    indices = [
        Index(value = ["conversationId", "sortOrder"]),
        Index(value = ["worldbookId"]),
    ],
)
data class ConversationWorldbookCrossRef(
    val conversationId: String,
    val worldbookId: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
)
