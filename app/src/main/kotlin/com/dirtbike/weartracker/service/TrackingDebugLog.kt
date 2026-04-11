package com.dirtbike.weartracker.service

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackingDebugLog {
    private const val FILE_NAME = "tracking_debug.log"
    private const val MAX_BYTES = 200_000L
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun write(context: Context, message: String) {
        synchronized(lock) {
            runCatching {
                val file = File(context.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    file.writeText("")
                }
                val line = "${timeFormat.format(Date())} $message\n"
                file.appendText(line)
            }
        }
    }
}
