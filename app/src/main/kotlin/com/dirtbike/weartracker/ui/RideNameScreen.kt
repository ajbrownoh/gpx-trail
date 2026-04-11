package com.dirtbike.weartracker.ui

import androidx.compose.runtime.Composable
import com.dirtbike.weartracker.ui.theme.GreenStart

@Composable
fun RideNameScreen(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    NameEntryScreen(
        title = "Name Ride",
        speechPrompt = "Name this ride",
        placeholder = "Say or type a ride name",
        accentColor = GreenStart,
        initialValue = initialName,
        confirmLabel = "Save",
        onConfirm = onConfirm,
        onCancel = onCancel
    )
}
