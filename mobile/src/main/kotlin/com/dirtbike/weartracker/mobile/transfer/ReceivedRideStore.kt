package com.dirtbike.weartracker.mobile.transfer

import android.content.Context

object ReceivedRideStore {
    private const val PREFS = "received_gpx"
    private const val KEY_FILE_NAME = "file_name"
    private const val KEY_URI = "uri"
    private const val KEY_SAVED_AT_MS = "saved_at_ms"

    data class LatestRide(
        val fileName: String,
        val uriString: String,
        val savedAtMs: Long
    )

    fun saveLatest(context: Context, fileName: String, uriString: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FILE_NAME, fileName)
            .putString(KEY_URI, uriString)
            .putLong(KEY_SAVED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun latest(context: Context): LatestRide? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fileName = prefs.getString(KEY_FILE_NAME, null) ?: return null
        val uriString = prefs.getString(KEY_URI, null) ?: return null
        return LatestRide(
            fileName = fileName,
            uriString = uriString,
            savedAtMs = prefs.getLong(KEY_SAVED_AT_MS, 0L)
        )
    }
}
