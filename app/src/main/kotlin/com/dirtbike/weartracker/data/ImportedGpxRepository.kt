package com.dirtbike.weartracker.data

import android.content.Context
import com.dirtbike.weartracker.gpx.RideGpxParser
import java.io.File
import java.io.InputStream
import java.util.Locale

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
        val importFiles = listImportedGpxFiles(context)
        val savedRideFiles = listGpxFiles(RideRepository.getGpxDir(context))

        return (importFiles + savedRideFiles)
            .distinctBy { it.absolutePath }
            .sortedWith(compareBy<File> { it.parentFile?.name != IMPORT_DIR }.thenBy { it.name.lowercase() })
            .flatMap { file ->
                runCatching { RideGpxParser.readRide(file).waypoints }.getOrDefault(emptyList())
            }
            .distinctBy { it.dedupeKey() }
            .sortedBy { it.name.lowercase() }
    }

    fun listImportedGpxFiles(context: Context): List<File> {
        return listGpxFiles(importDir(context))
    }

    private fun importDir(context: Context): File {
        return File(context.filesDir, IMPORT_DIR)
    }

    private fun listGpxFiles(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile &&
                file.extension.equals("gpx", ignoreCase = true) &&
                file.name != "draft.gpx"
        }.orEmpty().toList()
    }

    private fun sanitizeGpxFileName(rawName: String): String {
        val cleaned = rawName
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "imported_waypoints.gpx" }
        return if (cleaned.endsWith(".gpx", ignoreCase = true)) cleaned else "$cleaned.gpx"
    }

    private fun Waypoint.dedupeKey(): String {
        return listOf(
            name.trim().lowercase(Locale.US),
            "%.5f".format(Locale.US, latitude),
            "%.5f".format(Locale.US, longitude)
        ).joinToString("|")
    }
}
