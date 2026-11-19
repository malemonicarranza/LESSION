package com.example.app_lession.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertMeasurement(measurement: SessionMeasurementEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMillis DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query(
        "SELECT * FROM sessions WHERE endedAtEpochMillis IS NULL " +
            "ORDER BY startedAtEpochMillis DESC LIMIT 1"
    )
    suspend fun getActiveSession(): SessionEntity?

    @Query(
        "SELECT * FROM session_measurements WHERE sessionId = :sessionId " +
            "ORDER BY receivedAtEpochMillis, id"
    )
    suspend fun getMeasurements(sessionId: Long): List<SessionMeasurementEntity>

    @Query(
        "UPDATE sessions SET sampleCount = sampleCount + 1 " +
            "WHERE id = :sessionId AND endedAtEpochMillis IS NULL"
    )
    suspend fun incrementSampleCount(sessionId: Long)

    @Query(
        "UPDATE sessions SET endedAtEpochMillis = :endedAtEpochMillis, " +
            "durationMillis = :durationMillis, " +
            "sampleCount = (SELECT COUNT(*) FROM session_measurements " +
            "WHERE sessionId = :sessionId) " +
            "WHERE id = :sessionId AND endedAtEpochMillis IS NULL"
    )
    suspend fun finishSession(
        sessionId: Long,
        endedAtEpochMillis: Long,
        durationMillis: Long
    )

    @Transaction
    suspend fun createSessionIfNoneActive(session: SessionEntity): Long? {
        if (getActiveSession() != null) return null
        return insertSession(session)
    }

    @Transaction
    suspend fun insertMeasurementIfActive(measurement: SessionMeasurementEntity): Boolean {
        val session = getSession(measurement.sessionId) ?: return false
        if (session.endedAtEpochMillis != null) return false
        insertMeasurement(measurement)
        incrementSampleCount(measurement.sessionId)
        return true
    }
}
