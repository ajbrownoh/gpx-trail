package com.dirtbike.weartracker.data

import android.content.Context
import com.dirtbike.weartracker.gpx.RideGpxParser
import java.io.File
import java.io.InputStream

object ImportedGpxRepository {
    private const val IMPORT_DIR = "imported_gpx"

    fun saveImportedGpx(context: Context, rawFileName: String, input: InputStream): File {
        val dir = importDir(context)
        if (!dir.exists()) dir.mkdirs()

        val target = File(dir, sanitizeGpxFileName(rawFileName))
        target.outputStream().use { output ->
            input.copyTo(output)
        }
        return target
    }

    fun listImportedWaypoints(context: Context): List<Waypoint> {
        val dir = importDir(context)
        if (!dir.exists()) return emptyList()

        return dir.listFiles { file -> file.isFile && file.extension.equals("gpx", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .flatMap { file ->
                runCatching { RideGpxParser.readRide(file).waypoints }.getOrDefault(emptyList())
            }
    }

    private fun importDir(context: Context): File {
        return File(context.filesDir, IMPORT_DIR)
    }

    private fun sanitizeGpxFileName(rawName: String): String {
        val cleaned = rawName
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "imported_waypoints.gpx" }
        return if (cleaned.endsWith(".gpx", ignoreCase = true)) cleaned else "$cleaned.gpx"
    }
}
