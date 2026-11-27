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
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryEmbeddingDao(): MemoryEmbeddingDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gugachat_app.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
