package com.thesuperagent.agent.core

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val lang: String,
    val ts: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val ts: Long = System.currentTimeMillis(),
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val message: String,
    val ts: Long = System.currentTimeMillis(),
)

@Entity(tableName = "clipboard_history")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY ts DESC LIMIT 200") fun all(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY ts ASC LIMIT 400") fun chronological(): List<MemoryEntity>
    @Insert suspend fun insert(m: MemoryEntity)
    @Query("DELETE FROM memories") suspend fun clear()

    @Query("SELECT * FROM notes ORDER BY ts DESC") fun notes(): Flow<List<NoteEntity>>
    @Insert suspend fun addNote(n: NoteEntity)
    @Query("DELETE FROM notes WHERE id=:id") suspend fun delNote(id: Long)

    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT 500") fun logs(): Flow<List<LogEntity>>
    @Insert suspend fun log(l: LogEntity)
    @Query("DELETE FROM logs") suspend fun clearLogs()

    @Query("SELECT * FROM clipboard_history ORDER BY ts DESC LIMIT 50") fun clips(): Flow<List<ClipEntity>>
    @Insert suspend fun addClip(c: ClipEntity)
    @Query("DELETE FROM clipboard_history") suspend fun clearClips()
}

@Database(
    entities = [MemoryEntity::class, NoteEntity::class, LogEntity::class, ClipEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MemoryDb : RoomDatabase() {
    abstract fun dao(): MemoryDao

    companion object {
        private var db: MemoryDb? = null
        fun init(ctx: Context) {
            if (db == null) {
                db = Room.databaseBuilder(ctx.applicationContext, MemoryDb::class.java, "super_agent.db")
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
        fun dao(): MemoryDao = db!!.dao()
    }
}

object LogStore {
    suspend fun log(tag: String, message: String) {
        try { MemoryDb.dao().log(LogEntity(tag = tag, message = message.take(500))) } catch (t: Throwable) {}
    }
}
