package com.dirtbike.weartracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.dirtbike.weartracker.ui.theme.*

@Composable
fun HomeScreen(
    currentTimeFormatted: String,
    trackingModeLabel: String,
    trackingModeDescription: String,
    onStart: () -> Unit,
    onCycleTrackingMode: () -> Unit,
    onSavedRides: () -> Unit,
    hasDraft: Boolean = false,
    onRecoverDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = currentTimeFormatted,
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "GPX TRAIL",
                color = OrangeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Button(
                onClick = onCycleTrackingMode,
                modifier = Modifier
                    .height(30.dp)
                    .widthIn(min = 108.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "GPS: $trackingModeLabel",
                        color = TextWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = trackingModeDescription,
                        color = TextGray,
                        fontSize = 6.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Interrupted-session recovery banner
            if (hasDraft) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Unsaved session found",
                        color = OrangeAccent,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRecoverDraft,
                            modifier = Modifier
                                .height(26.dp)
                                .widthIn(min = 60.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = GreenStart)
                        ) {
                            Text(
                                text = "Recover",
                                color = TextWhite,
                                fontSize = 9.sp
                            )
                        }
                        Button(
                            onClick = onDiscardDraft,
                            modifier = Modifier
                                .height(26.dp)
                                .widthIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                        ) {
                            Text(
                                text = "Discard",
                                color = TextWhite,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Big START button
            Button(
                onClick = onStart,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = GreenStart)
            ) {
                Text(
                    text = "START",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Saved sessions link
            Button(
                onClick = onSavedRides,
                modifier = Modifier
                    .height(28.dp)
                    .widthIn(min = 80.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
            ) {
                Text(
                    text = "Saved Sessions",
                    color = TextWhite,
                    fontSize = 10.sp
                )
            }
        }
    }
}
