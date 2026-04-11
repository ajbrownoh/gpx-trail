package com.dirtbike.weartracker.data

data class Waypoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestampMs: Long
)
