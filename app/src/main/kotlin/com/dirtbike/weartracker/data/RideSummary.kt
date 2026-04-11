package com.dirtbike.weartracker.data

import java.io.File

data class RideSummary(
    val file: File,
    val name: String,
    val recordedAtMs: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val waypointCount: Int
)
