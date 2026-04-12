package com.dirtbike.weartracker.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.dirtbike.weartracker.mobile.transfer.ReceivedRideStore

class ShareRideActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_URI)
            ?: ReceivedRideStore.latest(this)?.uriString
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: ReceivedRideStore.latest(this)?.fileName

        if (uriString.isNullOrBlank()) {
            Toast.makeText(this, "No GPX file received yet.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            putExtra(Intent.EXTRA_SUBJECT, fileName ?: "GPX Trail session")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, "Share GPX"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can share this GPX file.", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    companion object {
        private const val EXTRA_URI = "com.dirtbike.weartracker.mobile.extra.URI"
        private const val EXTRA_FILE_NAME = "com.dirtbike.weartracker.mobile.extra.FILE_NAME"

        fun createIntent(context: Context, uriString: String, fileName: String): Intent {
            return Intent(context, ShareRideActivity::class.java).apply {
                putExtra(EXTRA_URI, uriString)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
        }
    }
}
