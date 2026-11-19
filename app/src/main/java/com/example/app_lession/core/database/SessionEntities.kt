package com.example.app_lession.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val durationMillis: Long = 0,
    val sampleCount: Long = 0
)

@Entity(
    tableName = "session_measurements",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SessionMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val deviceTimeMs: Long,
    val adcValue: Int,
    val receivedAtEpochMillis: Long
)
