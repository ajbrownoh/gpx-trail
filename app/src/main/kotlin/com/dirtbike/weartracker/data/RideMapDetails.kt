package com.dirtbike.weartracker.data

data class RideMapDetails(
    val summary: RideSummary,
    val trackPoints: List<TrackPoint>,
    val waypoints: List<Waypoint>
)
