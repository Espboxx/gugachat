package com.gugachat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    // ==================== Character Cards ====================

    @Query("SELECT * FROM character_cards ORDER BY favorite DESC, updatedAt DESC")
    fun observeCharacterCards(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards ORDER BY favorite DESC, updatedAt DESC")
    suspend fun getAllCharacterCards(): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getCharacterCardsPaged(limit: Int, offset: Int): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards WHERE id = :id LIMIT 1")
    suspend fun getCharacterCardById(id: String): CharacterCardEntity?

    @Query("SELECT * FROM character_cards WHERE id IN (:ids)")
    suspend fun getCharacterCardsByIds(ids: List<String>): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards WHERE contentFingerprint = :fingerprint LIMIT 1")
    suspend fun getCardByFingerprint(fingerprint: String): CharacterCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterCard(entity: CharacterCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterCards(entities: List<CharacterCardEntity>)

    @Query("DELETE FROM character_cards WHERE id = :id")
    suspend fun deleteCharacterCard(id: String)

    @Query("DELETE FROM character_cards")
    suspend fun clearAllCharacterCards()

    // ==================== Worldbooks ====================

    @Query("SELECT * FROM worldbooks ORDER BY updatedAt DESC")
    fun observeWorldbooks(): Flow<List<WorldbookEntity>>

    @Query("SELECT * FROM worldbooks WHERE globalEnabled = 1 ORDER BY updatedAt DESC")
    fun observeGlobalWorldbooks(): Flow<List<WorldbookEntity>>

    @Query("SELECT * FROM worldbooks ORDER BY updatedAt DESC")
    suspend fun getAllWorldbooks(): List<WorldbookEntity>

    @Query("SELECT * FROM worldbooks WHERE globalEnabled = 1 ORDER BY updatedAt DESC")
    suspend fun getGlobalEnabledWorldbooks(): List<WorldbookEntity>

    @Query("SELECT * FROM worldbooks ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getWorldbooksPaged(limit: Int, offset: Int): List<WorldbookEntity>

    @Query("SELECT * FROM worldbooks WHERE id = :id LIMIT 1")
    suspend fun getWorldbookById(id: String): WorldbookEntity?

    @Query("SELECT * FROM worldbooks WHERE id IN (:ids)")
    suspend fun getWorldbooksByIds(ids: List<String>): List<WorldbookEntity>

    @Query("SELECT * FROM worldbooks WHERE contentFingerprint = :fingerprint LIMIT 1")
    suspend fun getWorldbookByFingerprint(fingerprint: String): WorldbookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldbook(entity: WorldbookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldbooks(entities: List<WorldbookEntity>)

    @Query("DELETE FROM worldbooks WHERE id = :id")
    suspend fun deleteWorldbook(id: String)

    @Query("DELETE FROM worldbooks")
    suspend fun clearAllWorldbooks()

    // ==================== Worldbook Entries ====================

    @Query("SELECT * FROM worldbook_entries WHERE worldbookId = :worldbookId ORDER BY insertionOrder DESC, uid ASC")
    fun observeWorldbookEntries(worldbookId: String): Flow<List<WorldbookEntryEntity>>

    @Query("SELECT * FROM worldbook_entries WHERE worldbookId = :worldbookId ORDER BY insertionOrder DESC, uid ASC")
    suspend fun getWorldbookEntries(worldbookId: String): List<WorldbookEntryEntity>

    @Query("SELECT * FROM worldbook_entries WHERE worldbookId IN (:worldbookIds) AND enabled = 1 ORDER BY insertionOrder DESC, uid ASC")
    suspend fun getEnabledEntriesForWorldbooks(worldbookIds: List<String>): List<WorldbookEntryEntity>

    @Query("SELECT * FROM worldbook_entries ORDER BY worldbookId ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getWorldbookEntriesPaged(limit: Int, offset: Int): List<WorldbookEntryEntity>

    @Query("SELECT * FROM worldbook_entries WHERE id = :id LIMIT 1")
    suspend fun getWorldbookEntryById(id: String): WorldbookEntryEntity?

    @Query("SELECT * FROM worldbook_entries WHERE id IN (:ids)")
    suspend fun getWorldbookEntriesByIds(ids: List<String>): List<WorldbookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldbookEntry(entity: WorldbookEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldbookEntries(entities: List<WorldbookEntryEntity>)

    @Query("DELETE FROM worldbook_entries WHERE id = :id")
    suspend fun deleteWorldbookEntry(id: String)

    @Query("DELETE FROM worldbook_entries WHERE worldbookId = :worldbookId")
    suspend fun deleteEntriesForWorldbook(worldbookId: String)

    @Query("DELETE FROM worldbook_entries WHERE worldbookId IN (:worldbookIds)")
    suspend fun deleteEntriesForWorldbooks(worldbookIds: List<String>)

    @Query("DELETE FROM worldbook_entries")
    suspend fun clearAllWorldbookEntries()

    @Transaction
    suspend fun replaceWorldbookEntries(worldbookId: String, entries: List<WorldbookEntryEntity>) {
        deleteEntriesForWorldbook(worldbookId)
        if (entries.isNotEmpty()) {
            upsertWorldbookEntries(entries.map { it.copy(worldbookId = worldbookId) })
        }
    }

    @Transaction
    suspend fun deleteWorldbookCascade(worldbookId: String) {
        deleteEntriesForWorldbook(worldbookId)
        deleteWorldbookRefsByWorldbookId(worldbookId)
        deleteWorldbook(worldbookId)
    }

    // ==================== Personas ====================

    @Query("SELECT * FROM personas ORDER BY isDefault DESC, updatedAt DESC")
    fun observePersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY isDefault DESC, updatedAt DESC")
    suspend fun getAllPersonas(): List<PersonaEntity>

    @Query("SELECT * FROM personas ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getPersonasPaged(limit: Int, offset: Int): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE id IN (:ids)")
    suspend fun getPersonasByIds(ids: List<String>): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPersona(): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(entity: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonas(entities: List<PersonaEntity>)

    @Query("UPDATE personas SET isDefault = CASE WHEN id = :personaId THEN 1 ELSE 0 END")
    suspend fun setDefaultPersona(personaId: String)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deletePersona(id: String)

    @Query("DELETE FROM personas")
    suspend fun clearAllPersonas()

    // ==================== Prompt Presets ====================

    @Query("SELECT * FROM prompt_presets ORDER BY isDefaultRp DESC, updatedAt DESC")
    fun observePromptPresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets ORDER BY isDefaultRp DESC, updatedAt DESC")
    suspend fun getAllPromptPresets(): List<PromptPresetEntity>

    @Query("SELECT * FROM prompt_presets ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getPromptPresetsPaged(limit: Int, offset: Int): List<PromptPresetEntity>

    @Query("SELECT * FROM prompt_presets WHERE id = :id LIMIT 1")
    suspend fun getPromptPresetById(id: String): PromptPresetEntity?

    @Query("SELECT * FROM prompt_presets WHERE id IN (:ids)")
    suspend fun getPromptPresetsByIds(ids: List<String>): List<PromptPresetEntity>

    @Query("SELECT * FROM prompt_presets WHERE isDefaultRp = 1 LIMIT 1")
    suspend fun getDefaultRpPromptPreset(): PromptPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPromptPreset(entity: PromptPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPromptPresets(entities: List<PromptPresetEntity>)

    @Query("UPDATE prompt_presets SET isDefaultRp = CASE WHEN id = :presetId THEN 1 ELSE 0 END")
    suspend fun setDefaultRpPromptPreset(presetId: String)

    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun deletePromptPreset(id: String)

    @Query("DELETE FROM prompt_presets")
    suspend fun clearAllPromptPresets()

    // ==================== Conversation Worldbooks ====================

    @Query("SELECT * FROM conversation_worldbooks WHERE conversationId = :convId ORDER BY sortOrder ASC")
    fun observeMountedWorldbookRefs(convId: String): Flow<List<ConversationWorldbookCrossRef>>

    @Query("SELECT * FROM conversation_worldbooks WHERE conversationId = :convId ORDER BY sortOrder ASC")
    suspend fun getMountedWorldbookRefs(convId: String): List<ConversationWorldbookCrossRef>

    @Query(
        "SELECT w.* FROM worldbooks w " +
            "INNER JOIN conversation_worldbooks cw ON w.id = cw.worldbookId " +
            "WHERE cw.conversationId = :convId AND cw.enabled = 1 " +
            "ORDER BY cw.sortOrder ASC",
    )
    suspend fun getMountedWorldbooks(convId: String): List<WorldbookEntity>

    @Query("SELECT * FROM conversation_worldbooks ORDER BY conversationId ASC, worldbookId ASC LIMIT :limit OFFSET :offset")
    suspend fun getConversationWorldbooksPaged(limit: Int, offset: Int): List<ConversationWorldbookCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversationWorldbooks(refs: List<ConversationWorldbookCrossRef>)

    @Query("DELETE FROM conversation_worldbooks WHERE conversationId = :convId")
    suspend fun deleteConversationWorldbooks(convId: String)

    @Query("DELETE FROM conversation_worldbooks WHERE conversationId IN (:convIds)")
    suspend fun deleteConversationWorldbooksForConversations(convIds: List<String>)

    @Query("DELETE FROM conversation_worldbooks WHERE worldbookId = :worldbookId")
    suspend fun deleteWorldbookRefsByWorldbookId(worldbookId: String)

    @Query("DELETE FROM conversation_worldbooks")
    suspend fun clearAllConversationWorldbooks()

    @Transaction
    suspend fun replaceMountedWorldbooks(convId: String, refs: List<ConversationWorldbookCrossRef>) {
        deleteConversationWorldbooks(convId)
        if (refs.isNotEmpty()) {
            upsertConversationWorldbooks(refs.map { it.copy(conversationId = convId) })
        }
    }
}
