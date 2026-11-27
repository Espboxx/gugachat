package com.gugachat.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        UserMemoryEntity::class,
        NovelChapterEntity::class,
        NovelLoreEntity::class,
        MemoryLedgerEntity::class,
        MemoryEpisodeEntity::class,
        RpSaveSlotEntity::class,
        AttachmentMetaEntity::class,
        MemoryEmbeddingEntity::class,
        CharacterCardEntity::class,
        WorldbookEntity::class,
        WorldbookEntryEntity::class,
        PersonaEntity::class,
        PromptPresetEntity::class,
        ConversationWorldbookCrossRef::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryEmbeddingDao(): MemoryEmbeddingDao
    abstract fun assetDao(): AssetDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN kind TEXT NOT NULL DEFAULT 'image'")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN fileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN textPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN charCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN estimatedTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN previewSnippet TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN extractionNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attachment_meta ADD COLUMN injectMode TEXT NOT NULL DEFAULT 'auto'")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memory_embeddings` (" +
                        "`ownerType` TEXT NOT NULL, " +
                        "`ownerId` TEXT NOT NULL, " +
                        "`conversationId` TEXT NOT NULL, " +
                        "`profileKey` TEXT NOT NULL, " +
                        "`contentHash` TEXT NOT NULL, " +
                        "`dim` INTEGER NOT NULL, " +
                        "`quantType` TEXT NOT NULL, " +
                        "`scale` REAL NOT NULL, " +
                        "`vectorBlob` BLOB NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerType`, `ownerId`, `profileKey`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memory_embeddings_conversationId_profileKey` " +
                        "ON `memory_embeddings` (`conversationId`, `profileKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memory_embeddings_profileKey_contentHash` " +
                        "ON `memory_embeddings` (`profileKey`, `contentHash`)",
                )
            }
        }

        internal val MIGRATION_3_4_STATEMENTS: List<String> = listOf(
            "ALTER TABLE `conversations` ADD COLUMN `characterCardId` TEXT",
            "ALTER TABLE `conversations` ADD COLUMN `personaId` TEXT",
            "ALTER TABLE `conversations` ADD COLUMN `presetId` TEXT",
            "ALTER TABLE `conversations` ADD COLUMN `loreTimedStatesJson` TEXT NOT NULL DEFAULT '{}'",
            "CREATE TABLE IF NOT EXISTS `character_cards` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`creator` TEXT NOT NULL, " +
                "`characterVersion` TEXT NOT NULL, " +
                "`avatarPath` TEXT, " +
                "`spritePath` TEXT, " +
                "`expressionSpritesJson` TEXT NOT NULL, " +
                "`tagsJson` TEXT NOT NULL, " +
                "`playStyle` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`personality` TEXT NOT NULL, " +
                "`scenario` TEXT NOT NULL, " +
                "`firstMes` TEXT NOT NULL, " +
                "`alternateGreetingsJson` TEXT NOT NULL, " +
                "`groupOnlyGreetingsJson` TEXT NOT NULL, " +
                "`mesExample` TEXT NOT NULL, " +
                "`systemPrompt` TEXT NOT NULL, " +
                "`postHistoryInstructions` TEXT NOT NULL, " +
                "`creatorNotes` TEXT NOT NULL, " +
                "`depthPromptText` TEXT NOT NULL, " +
                "`depthPromptDepth` INTEGER NOT NULL, " +
                "`depthPromptRole` TEXT NOT NULL, " +
                "`castJson` TEXT NOT NULL, " +
                "`regexScriptsJson` TEXT NOT NULL, " +
                "`visualThemeJson` TEXT NOT NULL, " +
                "`primaryWorldbookId` TEXT, " +
                "`sourceSpec` TEXT NOT NULL, " +
                "`contentFingerprint` TEXT NOT NULL, " +
                "`favorite` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_character_cards_favorite_updatedAt` " +
                "ON `character_cards` (`favorite`, `updatedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_character_cards_contentFingerprint` " +
                "ON `character_cards` (`contentFingerprint`)",
            "CREATE INDEX IF NOT EXISTS `index_character_cards_primaryWorldbookId` " +
                "ON `character_cards` (`primaryWorldbookId`)",
            "CREATE TABLE IF NOT EXISTS `worldbooks` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`sourceFormat` TEXT NOT NULL, " +
                "`globalEnabled` INTEGER NOT NULL, " +
                "`scanDepth` INTEGER NOT NULL, " +
                "`tokenBudget` INTEGER NOT NULL, " +
                "`recursiveScanning` INTEGER NOT NULL, " +
                "`entryCount` INTEGER NOT NULL, " +
                "`totalEstimatedTokens` INTEGER NOT NULL, " +
                "`contentFingerprint` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_worldbooks_globalEnabled` " +
                "ON `worldbooks` (`globalEnabled`)",
            "CREATE INDEX IF NOT EXISTS `index_worldbooks_updatedAt` " +
                "ON `worldbooks` (`updatedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_worldbooks_contentFingerprint` " +
                "ON `worldbooks` (`contentFingerprint`)",
            "CREATE TABLE IF NOT EXISTS `worldbook_entries` (" +
                "`id` TEXT NOT NULL, " +
                "`worldbookId` TEXT NOT NULL, " +
                "`uid` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`keysJson` TEXT NOT NULL, " +
                "`secondaryKeysJson` TEXT NOT NULL, " +
                "`selectiveLogic` INTEGER NOT NULL, " +
                "`constant` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`caseSensitive` INTEGER NOT NULL, " +
                "`matchWholeWords` INTEGER NOT NULL, " +
                "`useRegexKey` INTEGER NOT NULL, " +
                "`probability` INTEGER NOT NULL, " +
                "`insertionOrder` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`depth` INTEGER NOT NULL, " +
                "`outletRole` TEXT NOT NULL, " +
                "`scanDepthOverride` INTEGER, " +
                "`sticky` INTEGER NOT NULL, " +
                "`cooldown` INTEGER NOT NULL, " +
                "`delay` INTEGER NOT NULL, " +
                "`preventRecursion` INTEGER NOT NULL, " +
                "`excludeRecursion` INTEGER NOT NULL, " +
                "`groupName` TEXT NOT NULL, " +
                "`groupWeight` INTEGER NOT NULL, " +
                "`estimatedTokens` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_worldbook_entries_worldbookId` " +
                "ON `worldbook_entries` (`worldbookId`)",
            "CREATE INDEX IF NOT EXISTS `index_worldbook_entries_worldbookId_enabled_constant` " +
                "ON `worldbook_entries` (`worldbookId`, `enabled`, `constant`)",
            "CREATE TABLE IF NOT EXISTS `personas` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`avatarPath` TEXT, " +
                "`description` TEXT NOT NULL, " +
                "`injectionPosition` INTEGER NOT NULL, " +
                "`injectionDepth` INTEGER NOT NULL, " +
                "`isDefault` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_personas_isDefault` " +
                "ON `personas` (`isDefault`)",
            "CREATE TABLE IF NOT EXISTS `prompt_presets` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sourceFormat` TEXT NOT NULL, " +
                "`blocksJson` TEXT NOT NULL, " +
                "`regexScriptsJson` TEXT NOT NULL, " +
                "`samplingPatchJson` TEXT NOT NULL, " +
                "`isDefaultRp` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_prompt_presets_isDefaultRp` " +
                "ON `prompt_presets` (`isDefaultRp`)",
            "CREATE TABLE IF NOT EXISTS `conversation_worldbooks` (" +
                "`conversationId` TEXT NOT NULL, " +
                "`worldbookId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`conversationId`, `worldbookId`))",
            "CREATE INDEX IF NOT EXISTS `index_conversation_worldbooks_conversationId_sortOrder` " +
                "ON `conversation_worldbooks` (`conversationId`, `sortOrder`)",
            "CREATE INDEX IF NOT EXISTS `index_conversation_worldbooks_worldbookId` " +
                "ON `conversation_worldbooks` (`worldbookId`)",
        )

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_3_4_STATEMENTS) {
                    db.execSQL(sql)
                }
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gugachat_app.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
