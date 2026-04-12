package com.dirtbike.weartracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.data.visibilityKey
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.GreenStart
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.OrangeDim
import com.dirtbike.weartracker.ui.theme.TextGray
import com.dirtbike.weartracker.ui.theme.TextWhite
import com.dirtbike.weartracker.ui.theme.WaypointBlue
import java.util.Locale

@Composable
fun WaypointManagerScreen(
    waypoints: List<Waypoint>,
    hiddenWaypointKeys: Set<String>,
    onHideWaypoints: (List<Waypoint>) -> Unit,
    onShowWaypoints: (List<Waypoint>) -> Unit,
    onStartTracking: (Waypoint) -> Unit,
    onBack: () -> Unit
) {
    var showHiddenWaypoints by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val sortedWaypoints = remember(waypoints) {
        waypoints.sortedBy { it.name.lowercase(Locale.US) }
    }
    val hiddenCount = remember(waypoints, hiddenWaypointKeys) {
        waypoints.count { it.visibilityKey() in hiddenWaypointKeys }
    }
    val displayedWaypoints = remember(sortedWaypoints, hiddenWaypointKeys, showHiddenWaypoints) {
        sortedWaypoints.filter { showHiddenWaypoints || it.visibilityKey() !in hiddenWaypointKeys }
    }
    val selectedWaypoints = remember(sortedWaypoints, selectedKeys) {
        sortedWaypoints.filter { it.visibilityKey() in selectedKeys }
    }

    LaunchedEffect(displayedWaypoints) {
        val displayedKeys = displayedWaypoints.map { it.visibilityKey() }.toSet()
        selectedKeys = selectedKeys.intersect(displayedKeys)
    }

    ScalingLazyColumn(
        modifier = Modifier.background(BackgroundBlack),
        contentPadding = PaddingValues(top = 26.dp, bottom = 28.dp, start = 10.dp, end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Waypoints",
                    color = OrangeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (hiddenCount == 0) {
                        "${waypoints.size} shown"
                    } else if (showHiddenWaypoints) {
                        "${waypoints.size - hiddenCount} shown | $hiddenCount hidden visible"
                    } else {
                        "${waypoints.size - hiddenCount} shown | $hiddenCount hidden"
                    },
                    color = TextGray,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                if (hiddenCount > 0) {
                    Button(
                        onClick = {
                            showHiddenWaypoints = !showHiddenWaypoints
                            selectedKeys = emptySet()
                        },
                        modifier = Modifier
                            .height(28.dp)
                            .width(112.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                    ) {
                        Text(
                            text = if (showHiddenWaypoints) "Hide hidden" else "Show hidden",
                            color = TextWhite,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (selectedWaypoints.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                onHideWaypoints(selectedWaypoints)
                                selectedKeys = emptySet()
                            },
                            modifier = Modifier
                                .height(28.dp)
                                .weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                        ) {
                            Text(
                                text = "Hide ${selectedWaypoints.size}",
                                color = TextWhite,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                onShowWaypoints(selectedWaypoints)
                                selectedKeys = emptySet()
                            },
                            modifier = Modifier
                                .height(28.dp)
                                .weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = GreenStart)
                        ) {
                            Text(
                                text = "Show ${selectedWaypoints.size}",
                                color = BackgroundBlack,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (displayedWaypoints.isEmpty()) {
            item {
                Text(
                    text = if (waypoints.isEmpty()) {
                        "No waypoints found"
                    } else {
                        "Hidden waypoints are hidden"
                    },
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            itemsIndexed(displayedWaypoints) { index, waypoint ->
                val waypointKey = waypoint.visibilityKey()
                val isHidden = waypoint.visibilityKey() in hiddenWaypointKeys
                WaypointVisibilityRow(
                    waypoint = waypoint,
                    color = if (isHidden) TextGray else SavedWaypointColors[index % SavedWaypointColors.size],
                    isHidden = isHidden,
                    selected = waypointKey in selectedKeys,
                    onToggleSelected = {
                        selectedKeys = if (waypointKey in selectedKeys) {
                            selectedKeys - waypointKey
                        } else {
                            selectedKeys + waypointKey
                        }
                    },
                    onStartTracking = { onStartTracking(waypoint) }
                )
            }
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
private fun WaypointVisibilityRow(
    waypoint: Waypoint,
    color: Color,
    isHidden: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onStartTracking: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = OrangeDim.copy(alpha = if (selected) 0.34f else 0.16f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VisibilityDot(color = color)

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.name,
                    color = if (isHidden) TextGray else TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        selected && isHidden -> "Selected | hidden"
                        selected -> "Selected | shown"
                        isHidden -> "Hidden"
                        else -> "Shown"
                    },
                    color = TextGray,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onStartTracking,
                modifier = Modifier
                    .height(28.dp)
                    .weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = WaypointBlue)
            ) {
                Text(
                    text = "Track",
                    color = BackgroundBlack,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onToggleSelected,
                modifier = Modifier
                    .height(28.dp)
                    .weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selected) GreenStart else OrangeDim
                )
            ) {
                Text(
                    text = if (selected) "Picked" else "Pick",
                    color = if (selected) BackgroundBlack else TextWhite,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VisibilityDot(color: Color) {
    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
        drawCircle(
            color = BackgroundBlack,
            radius = size.minDimension * 0.42f,
            style = Stroke(width = 1.2f)
        )
    }
}
