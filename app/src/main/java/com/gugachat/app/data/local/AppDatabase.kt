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
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun attachmentDao(): AttachmentDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gugachat_app.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
