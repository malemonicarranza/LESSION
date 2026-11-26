package com.example.app_lession.core.session

import com.example.app_lession.core.database.SessionDao
import com.example.app_lession.core.database.SessionEntity
import com.example.app_lession.core.database.SessionMeasurementEntity
import com.example.app_lession.core.model.EmgSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class EmgSession(
    val id: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val durationMillis: Long,
    val sampleCount: Long
)

data class SessionStatistics(
    val id: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val durationMillis: Long,
    val sampleCount: Long,
    val averageAdc: Double,
    val minimumAdc: Int,
    val maximumAdc: Int
)

class SessionRepository(private val sessionDao: SessionDao) {
    val sessions: Flow<List<EmgSession>> = sessionDao.observeSessions().map { sessions ->
        sessions.map { it.toModel() }
    }

    suspend fun sessionStatistics(): List<SessionStatistics> =
        sessionDao.getSessionStatistics().map { row ->
            SessionStatistics(
                id = row.id,
                startedAtEpochMillis = row.startedAtEpochMillis,
                endedAtEpochMillis = row.endedAtEpochMillis,
                durationMillis = row.durationMillis,
                sampleCount = row.measurementCount,
                averageAdc = row.averageAdc,
                minimumAdc = row.minimumAdc,
                maximumAdc = row.maximumAdc
            )
        }

    suspend fun getActiveSession(): EmgSession? =
        sessionDao.getActiveSession()?.let { session ->
            session.toModel().copy(sampleCount = sessionDao.getMeasurementCount(session.id))
        }

    suspend fun startSession(startedAtEpochMillis: Long): EmgSession? {
        val sessionId = sessionDao.createSessionIfNoneActive(
            SessionEntity(startedAtEpochMillis = startedAtEpochMillis)
        ) ?: return null
        return sessionDao.getSession(sessionId)?.toModel()
    }

    suspend fun recordMeasurement(sessionId: Long, sample: EmgSample): Boolean =
        sessionDao.insertMeasurementIfActive(
            SessionMeasurementEntity(
                sessionId = sessionId,
                deviceTimeMs = sample.deviceTimeMs,
                adcValue = sample.adcValue,
                receivedAtEpochMillis = sample.receivedAtEpochMillis
            )
        )

    suspend fun finishSession(session: EmgSession, endedAtEpochMillis: Long): EmgSession? {
        sessionDao.finishSession(
            sessionId = session.id,
            endedAtEpochMillis = endedAtEpochMillis,
            durationMillis = (endedAtEpochMillis - session.startedAtEpochMillis).coerceAtLeast(0)
        )
        return sessionDao.getSession(session.id)?.toModel()
    }

    suspend fun measurements(sessionId: Long): List<EmgSample> =
        sessionDao.getMeasurements(sessionId).map { measurement ->
            EmgSample(
                deviceTimeMs = measurement.deviceTimeMs,
                adcValue = measurement.adcValue,
                receivedAtEpochMillis = measurement.receivedAtEpochMillis
            )
        }

    suspend fun measurementsCsv(sessionId: Long): String =
        SessionCsvFormatter.format(measurements(sessionId))

    private fun SessionEntity.toModel() = EmgSession(
        id = id,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        durationMillis = durationMillis,
        sampleCount = sampleCount
    )
}
