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

        val sanitizedName = sanitizeGpxFileName(rawFileName)
        val tempFile = File.createTempFile("import_", ".gpx", dir)

        return try {
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }

            listGpxFiles(dir)
                .firstOrNull { existing ->
                    existing.absolutePath != tempFile.absolutePath && filesHaveSameContent(existing, tempFile)
                }
                ?.also {
                    tempFile.delete()
                    return it
                }

            val target = uniqueGpxFile(dir, sanitizedName)
            if (tempFile.renameTo(target)) {
                target
            } else {
                tempFile.copyTo(target, overwrite = false)
                tempFile.delete()
                target
            }
        } catch (throwable: Throwable) {
            tempFile.delete()
            throw throwable
        }
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

    private fun uniqueGpxFile(dir: File, fileName: String): File {
        val firstChoice = File(dir, fileName)
        if (!firstChoice.exists()) return firstChoice

        val extensionStart = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val baseName = fileName.substring(0, extensionStart)
        val extension = fileName.substring(extensionStart)

        var suffix = 2
        while (suffix < 10_000) {
            val candidate = File(dir, "${baseName}_$suffix$extension")
            if (!candidate.exists()) return candidate
            suffix++
        }

        return File(dir, "${baseName}_${System.currentTimeMillis()}$extension")
    }

    private fun filesHaveSameContent(first: File, second: File): Boolean {
        if (first.length() != second.length()) return false

        first.inputStream().buffered().use { firstInput ->
            second.inputStream().buffered().use { secondInput ->
                val firstBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val secondBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    val firstRead = firstInput.read(firstBuffer)
                    val secondRead = secondInput.read(secondBuffer)
                    if (firstRead != secondRead) return false
                    if (firstRead < 0) return true

                    for (index in 0 until firstRead) {
                        if (firstBuffer[index] != secondBuffer[index]) return false
                    }
                }
            }
        }
    }

    private fun Waypoint.dedupeKey(): String {
        return listOf(
            name.trim().lowercase(Locale.US),
            "%.5f".format(Locale.US, latitude),
            "%.5f".format(Locale.US, longitude)
        ).joinToString("|")
    }
}
