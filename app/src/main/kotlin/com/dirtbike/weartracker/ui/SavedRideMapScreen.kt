package com.dirtbike.weartracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.dirtbike.weartracker.data.RideMapDetails
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.data.visibilityKey
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.GreenStart
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.OrangeDim
import com.dirtbike.weartracker.ui.theme.TextGray
import com.dirtbike.weartracker.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedRideMapScreen(
    rideMap: RideMapDetails,
    hiddenWaypointKeys: Set<String>,
    onHideWaypoint: (Waypoint) -> Unit,
    onShowWaypoint: (Waypoint) -> Unit,
    onShowAllWaypoints: () -> Unit,
    onBack: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    val visibleWaypoints = remember(rideMap.waypoints, hiddenWaypointKeys) {
        rideMap.waypoints.filterNot { it.visibilityKey() in hiddenWaypointKeys }
    }
    val sortedWaypoints = remember(rideMap.waypoints) {
        rideMap.waypoints.sortedBy { it.name.lowercase(Locale.US) }
    }
    val hiddenCount = remember(rideMap.waypoints, hiddenWaypointKeys) {
        rideMap.waypoints.count { it.visibilityKey() in hiddenWaypointKeys }
    }

    ScalingLazyColumn(
        modifier = Modifier
            .background(BackgroundBlack),
        contentPadding = PaddingValues(
            top = 26.dp,
            bottom = 28.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = rideMap.summary.name,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Start ${dateFmt.format(Date(rideMap.summary.recordedAtMs))}",
                    color = OrangeAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    SavedRideDetailStat(
                        label = "TIME",
                        value = formatMapDuration(rideMap.summary.durationSeconds),
                        modifier = Modifier.weight(1f)
                    )
                    SavedRideDetailStat(
                        label = "DIST",
                        value = formatMapDistance(rideMap.summary.distanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(
                        color = OrangeDim.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                RideMapCanvas(
                    trackPoints = rideMap.trackPoints,
                    waypoints = visibleWaypoints,
                    heading = 0f,
                    zoomFactor = 1f,
                    centerOnCurrent = false,
                    showEndPoint = true,
                    waypointColors = visibleWaypoints.mapIndexed { index, _ ->
                        SavedWaypointColors[index % SavedWaypointColors.size]
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = GreenStart)
                Text(
                    text = "Start",
                    color = TextGray,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
                LegendDot(color = OrangeAccent)
                Text(
                    text = "End",
                    color = TextGray,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = if (rideMap.waypoints.isEmpty()) "No waypoints marked" else "Waypoints",
                    color = OrangeAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                if (hiddenCount > 0) {
                    Button(
                        onClick = onShowAllWaypoints,
                        modifier = Modifier.height(26.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                    ) {
                        Text(
                            text = "Show all $hiddenCount hidden",
                            color = TextWhite,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        itemsIndexed(sortedWaypoints) { index, waypoint ->
            val isHidden = waypoint.visibilityKey() in hiddenWaypointKeys
            val visibleIndex = visibleWaypoints.indexOfFirst {
                it.visibilityKey() == waypoint.visibilityKey()
            }.takeIf { it >= 0 } ?: index
            WaypointLegendRow(
                waypoint = waypoint,
                color = if (isHidden) TextGray else SavedWaypointColors[visibleIndex % SavedWaypointColors.size],
                isHidden = isHidden,
                onToggleVisibility = {
                    if (isHidden) {
                        onShowWaypoint(waypoint)
                    } else {
                        onHideWaypoint(waypoint)
                    }
                }
            )
        }

        item {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Back",
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SavedRideDetailStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = OrangeDim.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WaypointLegendRow(
    waypoint: Waypoint,
    color: Color,
    isHidden: Boolean,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = OrangeDim.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = color)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (isHidden) "${waypoint.name} (hidden)" else waypoint.name,
            color = if (isHidden) TextGray else TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Button(
            onClick = onToggleVisibility,
            modifier = Modifier
                .height(26.dp)
                .width(48.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isHidden) GreenStart else OrangeDim
            )
        ) {
            Text(
                text = if (isHidden) "Show" else "Hide",
                color = if (isHidden) BackgroundBlack else TextWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LegendDot(
    color: Color
) {
    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
        drawCircle(
            color = BackgroundBlack,
            radius = size.minDimension * 0.42f,
            style = Stroke(width = 1.2f)
        )
    }
}

private fun formatMapDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatMapDistance(distanceMeters: Double): String {
    val miles = distanceMeters / 1609.344
    return "%.2f mi".format(miles)
}
