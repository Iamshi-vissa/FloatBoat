package com.floatboat.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.floatboat.app.R
import kotlin.math.roundToInt

/** Manual calibration for devices where the detected position or size is not quite right. */
@Composable
fun PlacementSettingsCard(
    settings: PlacementSettings,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onSizeScaleChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.placement_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.placement_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            LabelledSlider(
                label = stringResource(
                    R.string.placement_offset_y,
                    settings.offsetYDp.roundToInt(),
                ),
                value = settings.offsetYDp,
                range = PlacementSettings.OffsetYRange,
                onValueChange = { onOffsetYChange(it.roundToInt().toFloat()) },
            )
            LabelledSlider(
                label = stringResource(
                    R.string.placement_offset_x,
                    settings.offsetXDp.roundToInt(),
                ),
                value = settings.offsetXDp,
                range = PlacementSettings.OffsetXRange,
                onValueChange = { onOffsetXChange(it.roundToInt().toFloat()) },
            )
            LabelledSlider(
                label = stringResource(
                    R.string.placement_size,
                    (settings.sizeScale * 100).roundToInt(),
                ),
                value = settings.sizeScale,
                range = PlacementSettings.SizeScaleRange,
                // Snap to whole percent so the label and the stored value agree.
                onValueChange = { onSizeScaleChange((it * 100).roundToInt() / 100f) },
            )
            TextButton(
                onClick = onReset,
                enabled = settings != PlacementSettings.Default,
            ) {
                Text(stringResource(R.string.placement_reset))
            }
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.semantics { contentDescription = label },
    )
}
