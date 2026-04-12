package com.dirtbike.weartracker.mobile

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.dirtbike.weartracker.mobile.transfer.ReceivedRideStore
import com.dirtbike.weartracker.mobile.transfer.WatchImportTransferClient
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private lateinit var latestTitle: TextView
    private lateinit var latestDetails: TextView
    private lateinit var shareButton: Button
    private lateinit var importStatus: TextView
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContentView(buildContentView())
    }

    override fun onResume() {
        super.onResume()
        renderLatestRide()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(24))
            setBackgroundColor(Color.rgb(17, 24, 32))

            addView(TextView(this@MainActivity).apply {
                text = "GPX Trail"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(this@MainActivity).apply {
                text = "Phone receiver"
                textSize = 14f
                setTextColor(Color.rgb(244, 163, 58))
                gravity = Gravity.CENTER
            })

            latestTitle = TextView(this@MainActivity).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(36), 0, dp(6))
            }
            addView(latestTitle)

            latestDetails = TextView(this@MainActivity).apply {
                textSize = 14f
                setTextColor(Color.rgb(210, 216, 224))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(22))
            }
            addView(latestDetails)

            shareButton = Button(this@MainActivity).apply {
                text = "Share latest GPX"
                setOnClickListener { shareLatestRide() }
            }
            addView(shareButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ))

            addView(Button(this@MainActivity).apply {
                text = "Open Downloads"
                setOnClickListener { openDownloads() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(12)
            })

            importStatus = TextView(this@MainActivity).apply {
                text = "Pick a GPX file here to send waypoints to the watch."
                textSize = 13f
                setTextColor(Color.rgb(210, 216, 224))
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(10))
            }
            addView(importStatus)

            addView(Button(this@MainActivity).apply {
                text = "Send GPX to Watch"
                setOnClickListener { pickGpxForWatch() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ))

            addView(TextView(this@MainActivity).apply {
                text = "Watch to phone: open Saved Sessions and tap Phone. Phone to watch: send a GPX here, then open GPX Trail on the watch."
                textSize = 13f
                setTextColor(Color.rgb(160, 168, 176))
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, 0)
            })
        }
    }

    private fun renderLatestRide() {
        val latest = ReceivedRideStore.latest(this)
        if (latest == null) {
            latestTitle.text = "Waiting for GPX"
            latestDetails.text = "No session has been received yet."
            shareButton.isEnabled = false
            return
        }

        val savedAt = DateFormat.getMediumDateFormat(this).format(Date(latest.savedAtMs)) +
            " " + DateFormat.getTimeFormat(this).format(Date(latest.savedAtMs))
        latestTitle.text = latest.fileName
        latestDetails.text = "Saved to Downloads/GPX Trail\n$savedAt"
        shareButton.isEnabled = true
    }

    private fun shareLatestRide() {
        val latest = ReceivedRideStore.latest(this)
        if (latest == null) {
            Toast.makeText(this, "No GPX file received yet.", Toast.LENGTH_LONG).show()
            return
        }

        startActivity(ShareRideActivity.createIntent(this, latest.uriString, latest.fileName))
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.providers.downloads.documents/root/downloads")))
        }
    }

    private fun pickGpxForWatch() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/gpx+xml",
                    "application/octet-stream",
                    "application/xml",
                    "text/xml",
                    "text/plain"
                )
            )
        }
        startActivityForResult(intent, REQUEST_PICK_GPX)
    }

    @Deprecated("Deprecated in Android API; still simple and reliable for this lightweight Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_GPX || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val fileName = displayNameFor(uri)
        importStatus.text = "Sending $fileName to watch..."

        activityScope.launch {
            val result = WatchImportTransferClient(this@MainActivity).sendGpx(uri, fileName)
            importStatus.text = result.message
            Toast.makeText(
                this@MainActivity,
                result.message,
                if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun displayNameFor(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index).orEmpty().ifBlank { "imported_waypoints.gpx" }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "imported_waypoints.gpx"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }

    companion object {
        private const val REQUEST_PICK_GPX = 77
    }
}
