package com.dirtbike.weartracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dirtbike.weartracker.gpx.RideGpxParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RideRepository {

    private const val GPX_DIR = "gpx"
    private const val FILE_PROVIDER_AUTHORITY = "com.dirtbike.weartracker.fileprovider"
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun getGpxDir(context: Context): File {
        val dir = File(context.filesDir, GPX_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listRides(context: Context): List<RideSummary> {
        val recordedFiles = listGpxFiles(getGpxDir(context)).map { file -> file to false }
        val importedFiles = ImportedGpxRepository.listImportedGpxFiles(context).map { file -> file to true }

        return (recordedFiles + importedFiles)
            .distinctBy { (file, _) -> file.absolutePath }
            .sortedByDescending { (file, _) -> file.lastModified() }
            .map { (file, isImported) -> RideGpxParser.readSummary(file, isImported) }
    }

    fun buildShareIntent(context: Context, ride: RideSummary): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            ride.file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, ride.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun renameRide(ride: RideSummary, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (!RideGpxParser.renameRide(ride.file, trimmedName)) return false
        return renameRideFile(ride.file, trimmedName)
    }

    fun deleteRide(ride: RideSummary): Boolean = ride.file.delete()

    private fun listGpxFiles(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile &&
                file.extension.equals("gpx", ignoreCase = true) &&
                file.name != "draft.gpx"
        }.orEmpty().toList()
    }

    private fun renameRideFile(file: File, rideName: String): Boolean {
        val parsed = runCatching { RideGpxParser.readRide(file) }.getOrNull()
        val rideDateMs = parsed?.trackPoints?.firstOrNull()?.timestampMs
            ?: parsed?.metadataTimestampMs
            ?: file.lastModified()
        val safeBaseName = datedFileBaseName(rideName, rideDateMs)
        val target = uniqueGpxFile(file.parentFile ?: return true, safeBaseName, file)
        if (target == file) return true

        val originalModifiedMs = file.lastModified()
        val renamed = file.renameTo(target)
        if (renamed) {
            target.setLastModified(originalModifiedMs)
        }
        return renamed
    }

    private fun datedFileBaseName(name: String, timestampMs: Long): String {
        val datePrefix = fileDateFormat.format(Date(timestampMs))
        val nameBase = safeFileBaseName(name).removeDatePrefix()
        return "${datePrefix}_$nameBase"
    }

    private fun safeFileBaseName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_', '.', ' ')
            .take(80)
            .ifBlank { "session" }
    }

    private fun String.removeDatePrefix(): String {
        return replace(Regex("^\\d{8}_+"), "").ifBlank { "session" }
    }

    private fun uniqueGpxFile(dir: File, baseName: String, currentFile: File): File {
        var candidate = File(dir, "$baseName.gpx")
        if (candidate.absolutePath == currentFile.absolutePath || !candidate.exists()) {
            return candidate
        }

        var suffix = 2
        while (suffix < 10_000) {
            candidate = File(dir, "${baseName}_$suffix.gpx")
            if (candidate.absolutePath == currentFile.absolutePath || !candidate.exists()) {
                return candidate
            }
            suffix++
        }

        return File(dir, "${baseName}_${System.currentTimeMillis()}.gpx")
    }
}
