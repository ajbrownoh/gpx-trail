package com.dirtbike.weartracker.data

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestampMs: Long,
    val segmentId: Int = 0
)
