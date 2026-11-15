package com.example.mylock.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mylock.core.model.LogCategory
import com.example.mylock.core.model.SecurityAuditLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "security_audit_logs")
data class SecurityAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val categoryName: String,
    val title: String,
    val details: String,
    val score: Float? = null,
    val outcomeSuccess: Boolean
)

@Entity(tableName = "voice_profile_metadata")
data class VoiceProfileMetadataEntity(
    @PrimaryKey val profileId: String,
    val maskedPhraseHint: String,
    val modelId: String,
    val modelVersion: String,
    val sampleCount: Int,
    val createdAtMillis: Long,
    val hardwareKeystoreBacked: Boolean
)

@Dao
interface SecurityAuditLogDao {
    @Query("SELECT * FROM security_audit_logs ORDER BY timestampMillis DESC LIMIT 200")
    fun observeAllLogs(): Flow<List<SecurityAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: SecurityAuditLogEntity)

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearAllLogs()
}

@Dao
interface VoiceProfileMetadataDao {
    @Query("SELECT * FROM voice_profile_metadata ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeActiveMetadata(): Flow<VoiceProfileMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entity: VoiceProfileMetadataEntity)

    @Query("DELETE FROM voice_profile_metadata")
    suspend fun deleteAllMetadata()
}

@Database(
    entities = [SecurityAuditLogEntity::class, VoiceProfileMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MyLockDatabase : RoomDatabase() {
    abstract fun securityAuditLogDao(): SecurityAuditLogDao
    abstract fun voiceProfileMetadataDao(): VoiceProfileMetadataDao

    companion object {
        fun build(context: Context): MyLockDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MyLockDatabase::class.java,
                "mylock_security.db"
            ).build()
    }
}

class SecurityAuditRepository(
    private val dao: SecurityAuditLogDao
) {
    val logs: Flow<List<SecurityAuditLog>> = dao.observeAllLogs().map { entities ->
        entities.map { e ->
            SecurityAuditLog(
                id = e.id,
                timestampMillis = e.timestampMillis,
                category = runCatching { LogCategory.valueOf(e.categoryName) }
                    .getOrDefault(LogCategory.SYSTEM_COMPATIBILITY),
                title = e.title,
                details = e.details,
                score = e.score,
                outcomeSuccess = e.outcomeSuccess
            )
        }
    }

    suspend fun recordEvent(
        category: LogCategory,
        title: String,
        details: String,
        outcomeSuccess: Boolean,
        score: Float? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        dao.insertLog(
            SecurityAuditLogEntity(
                timestampMillis = timestampMillis,
                categoryName = category.name,
                title = title,
                details = details,
                score = score,
                outcomeSuccess = outcomeSuccess
            )
        )
    }

    suspend fun clearAll() {
        dao.clearAllLogs()
    }
}
