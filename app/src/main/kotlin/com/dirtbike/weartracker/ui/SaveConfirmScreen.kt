package com.dirtbike.weartracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.GreenStart
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.OrangeDim
import com.dirtbike.weartracker.ui.theme.TextWhite
import com.dirtbike.weartracker.ui.theme.WaypointYellow

@Composable
fun SaveConfirmScreen(
    elapsedFormatted: String,
    distanceFormatted: String,
    waypointCount: Int,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "Session Complete",
                color = OrangeAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "$elapsedFormatted | $distanceFormatted",
                color = TextWhite,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            if (waypointCount > 0) {
                Text(
                    text = "$waypointCount spot${if (waypointCount != 1) "s" else ""} marked",
                    color = WaypointYellow,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDiscard,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                ) {
                    Text(
                        text = "Discard",
                        color = TextWhite,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreenStart)
                ) {
                    Text(
                        text = "Name",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
