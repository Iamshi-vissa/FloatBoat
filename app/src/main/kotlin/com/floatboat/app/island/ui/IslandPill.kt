package com.floatboat.app.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.floatboat.app.R

/** Dimensions of the resting pill. Shared with window placement so both always agree. */
object PillDefaults {
    val Width = 112.dp
    val Height = 32.dp

    /** Gap kept from the top edge when the pill cannot be centred in the status bar. */
    val MinTopMargin = 6.dp
}

/** The resting island: a black capsule. Stateless; later phases drive it from `IslandState`. */
@Composable
fun IslandPill(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pill_content_description)
    Box(
        modifier = modifier
            .size(width = PillDefaults.Width, height = PillDefaults.Height)
            .background(color = Color.Black, shape = RoundedCornerShape(percent = 50))
            .semantics { contentDescription = description },
    )
}

@Preview
@Composable
private fun IslandPillPreview() {
    IslandPill()
}
