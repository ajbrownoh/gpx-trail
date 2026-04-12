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
import androidx.compose.runtime.remember
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
import java.util.Locale

@Composable
fun WaypointManagerScreen(
    waypoints: List<Waypoint>,
    hiddenWaypointKeys: Set<String>,
    onHideWaypoint: (Waypoint) -> Unit,
    onShowWaypoint: (Waypoint) -> Unit,
    onShowAllWaypoints: () -> Unit,
    onBack: () -> Unit
) {
    val sortedWaypoints = remember(waypoints) {
        waypoints.sortedBy { it.name.lowercase(Locale.US) }
    }
    val hiddenCount = remember(waypoints, hiddenWaypointKeys) {
        waypoints.count { it.visibilityKey() in hiddenWaypointKeys }
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
                    } else {
                        "${waypoints.size - hiddenCount} shown | $hiddenCount hidden"
                    },
                    color = TextGray,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                if (hiddenCount > 0) {
                    Button(
                        onClick = onShowAllWaypoints,
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = OrangeDim)
                    ) {
                        Text(
                            text = "Show all hidden",
                            color = TextWhite,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (sortedWaypoints.isEmpty()) {
            item {
                Text(
                    text = "No waypoints found",
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            itemsIndexed(sortedWaypoints) { index, waypoint ->
                val isHidden = waypoint.visibilityKey() in hiddenWaypointKeys
                WaypointVisibilityRow(
                    waypoint = waypoint,
                    color = if (isHidden) TextGray else SavedWaypointColors[index % SavedWaypointColors.size],
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
                text = if (isHidden) "Hidden" else "Shown",
                color = TextGray,
                fontSize = 8.sp,
                maxLines = 1
            )
        }

        Button(
            onClick = onToggleVisibility,
            modifier = Modifier
                .height(28.dp)
                .width(50.dp),
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
