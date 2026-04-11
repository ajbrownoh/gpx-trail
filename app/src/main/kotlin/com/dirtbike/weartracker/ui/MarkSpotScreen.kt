package com.dirtbike.weartracker.ui

import androidx.compose.runtime.Composable
import com.dirtbike.weartracker.ui.theme.WaypointYellow

@Composable
fun MarkSpotScreen(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    NameEntryScreen(
        title = "Name Spot",
        speechPrompt = "Name this spot",
        placeholder = "Say or type a spot name",
        accentColor = WaypointYellow,
        initialValue = "",
        confirmLabel = "Add",
        onConfirm = onConfirm,
        onCancel = onCancel
    )
}
