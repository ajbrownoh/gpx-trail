package com.dirtbike.weartracker.data

enum class TrackingQualityMode(
    val label: String,
    val shortDescription: String
) {
    MEDIUM(
        label = "MEDIUM",
        shortDescription = "Balanced"
    ),
    HIGH(
        label = "HIGH",
        shortDescription = "More detail"
    ),
    LOW(
        label = "LOW",
        shortDescription = "Long battery"
    );

    fun next(): TrackingQualityMode {
        return when (this) {
            MEDIUM -> HIGH
            HIGH -> LOW
            LOW -> MEDIUM
        }
    }

    companion object {
        fun fromName(value: String?): TrackingQualityMode {
            return entries.firstOrNull { it.name == value } ?: MEDIUM
        }
    }
}
