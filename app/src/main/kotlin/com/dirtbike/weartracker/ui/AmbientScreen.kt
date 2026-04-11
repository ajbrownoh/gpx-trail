package com.dirtbike.weartracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.dirtbike.weartracker.ui.theme.*

/**
 * Minimal ambient (always-on) display.
 *
 * Shown when the watch dims to ambient mode during an active ride.
 * Renders only elapsed time and distance on a pure-black background —
 * no animations, no canvas drawing, no color fills — to minimise
 * AMOLED power draw.
 */
@Composable
fun AmbientScreen(
    currentTimeFormatted: String,
    elapsedFormatted: String,
    distanceFormatted: String,
    gpsStatusText: String,
    batteryPercentage: Int?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = currentTimeFormatted,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            batteryPercentage?.let { percentage ->
                Text(
                    text = "BAT $percentage%",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = elapsedFormatted,
                color = TextWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )

            Text(
                text = distanceFormatted,
                color = TextGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = gpsStatusText,
            color = TextGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        )
    }
}
