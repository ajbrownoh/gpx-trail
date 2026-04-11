package com.dirtbike.weartracker.data

import java.util.Locale

fun Waypoint.visibilityKey(): String {
    return listOf(
        name.trim().lowercase(Locale.US),
        "%.6f".format(Locale.US, latitude),
        "%.6f".format(Locale.US, longitude)
    ).joinToString("|")
}
