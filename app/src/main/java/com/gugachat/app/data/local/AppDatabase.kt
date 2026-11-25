package com.gugachat.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gugachat_app.db",
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
