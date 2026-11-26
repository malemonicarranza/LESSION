package com.example.app_lession.core.database

data class SessionStatisticsRow(
    val id: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val durationMillis: Long,
    val measurementCount: Long,
    val averageAdc: Double,
    val minimumAdc: Int,
    val maximumAdc: Int
)
