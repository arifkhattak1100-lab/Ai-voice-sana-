package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [MemoryEntity::class, ChatMessageEntity::class], version = 1, exportSchema = false)
abstract class SanaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: SanaDatabase? = null

        fun getDatabase(context: Context): SanaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SanaDatabase::class.java,
                    "sana_ai_database"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class SanaRepository(private val database: SanaDatabase) {
    val allMemory: Flow<List<MemoryEntity>> = database.memoryDao().getAllMemory()
    val allMessages: Flow<List<ChatMessageEntity>> = database.chatMessageDao().getAllMessages()

    suspend fun getMemory(key: String): String? {
        return database.memoryDao().getMemoryByKey(key)?.value
    }

    suspend fun saveMemory(key: String, value: String, category: String = "preference") {
        database.memoryDao().insertMemory(
            MemoryEntity(key = key, value = value, category = category, timestamp = System.currentTimeMillis())
        )
    }

    suspend fun deleteMemory(id: Long) {
        database.memoryDao().deleteMemoryById(id)
    }

    suspend fun clearMemory() {
        database.memoryDao().clearAllMemory()
    }

    suspend fun saveMessage(entity: ChatMessageEntity) {
        database.chatMessageDao().insertMessage(entity)
    }

    suspend fun clearMessages() {
        database.chatMessageDao().clearHistory()
    }
}
