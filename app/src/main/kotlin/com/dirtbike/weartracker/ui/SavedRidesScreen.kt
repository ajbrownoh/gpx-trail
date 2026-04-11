package com.dirtbike.weartracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.dirtbike.weartracker.data.RideSummary
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.OrangeDim
import com.dirtbike.weartracker.ui.theme.RedStop
import com.dirtbike.weartracker.ui.theme.TextGray
import com.dirtbike.weartracker.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SavedRidesScreen(
    rides: List<RideSummary>,
    isLoading: Boolean,
    onSendToPhone: (RideSummary) -> Unit,
    onOpenMap: (RideSummary) -> Unit,
    onRename: (RideSummary) -> Unit,
    onDelete: (RideSummary) -> Unit,
    onBack: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d  h:mm a", Locale.US) }
    var pendingDeletePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingDeletePath) {
        if (pendingDeletePath != null) {
            delay(2_500L)
            pendingDeletePath = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Loading rides...",
                    color = OrangeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reading GPX files",
                    color = TextGray,
                    fontSize = 10.sp
                )
            }
        } else if (rides.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No rides saved yet",
                    color = TextGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                ) {
                    Text("Back", color = TextWhite, fontSize = 12.sp)
                }
            }
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 28.dp,
                    bottom = 28.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "Saved Rides",
                        color = OrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(rides) { ride ->
                    val isDeletePending = pendingDeletePath == ride.file.absolutePath

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMap(ride) }
                            .background(
                                color = OrangeDim.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ride.name,
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = dateFmt.format(Date(ride.recordedAtMs)),
                            color = TextGray,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${formatDuration(ride.durationSeconds)} | ${formatDistance(ride.distanceMeters)}",
                            color = TextWhite,
                            fontSize = 10.sp
                        )

                        if (ride.waypointCount > 0) {
                            Text(
                                text = "${ride.waypointCount} spot${if (ride.waypointCount != 1) "s" else ""}",
                                color = TextGray,
                                fontSize = 9.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onRename(ride) },
                                modifier = Modifier
                                    .height(30.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                            ) {
                                Text(
                                    text = "Edit",
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { onSendToPhone(ride) },
                                modifier = Modifier
                                    .height(30.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(backgroundColor = OrangeAccent)
                            ) {
                                Text(
                                    text = "Phone",
                                    color = BackgroundBlack,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (isDeletePending) {
                                        onDelete(ride)
                                        pendingDeletePath = null
                                    } else {
                                        pendingDeletePath = ride.file.absolutePath
                                    }
                                },
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(52.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = RedStop)
                            ) {
                                Text(
                                    text = if (isDeletePending) "Sure?" else "Del",
                                    color = TextWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Back", color = TextWhite, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatDistance(distanceMeters: Double): String {
    val miles = distanceMeters / 1609.344
    return "%.2f mi".format(miles)
}
