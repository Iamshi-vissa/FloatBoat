package com.floatboat.app.island.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatboat.app.R
import com.floatboat.app.island.IslandEvent

/** What the island draws for an event, already resolved to display strings. */
data class IslandContent(
    @param:DrawableRes val icon: Int,
    val tint: Color,
    /** A few characters shown beside the camera in the compact pill. */
    val compactText: String,
    val title: String,
    val subtitle: String,
) {
    val description: String get() = if (subtitle.isBlank()) title else "$title, $subtitle"
}

@Composable
fun islandContent(event: IslandEvent): IslandContent = when (event) {
    IslandEvent.Torch -> IslandContent(
        icon = R.drawable.ic_event_torch,
        tint = Color(0xFFFFD54F),
        compactText = stringResource(R.string.event_torch_compact),
        title = stringResource(R.string.event_torch_title),
        subtitle = stringResource(R.string.event_torch_subtitle),
    )
    is IslandEvent.Charging -> IslandContent(
        icon = R.drawable.ic_event_charging,
        tint = Color(0xFF69F0AE),
        compactText = stringResource(R.string.event_charging_compact, event.percent),
        title = stringResource(R.string.event_charging_title),
        subtitle = stringResource(R.string.event_charging_subtitle, event.percent),
    )
    is IslandEvent.Call -> IslandContent(
        icon = R.drawable.ic_event_call,
        tint = Color(0xFF69F0AE),
        compactText = stringResource(R.string.event_call_compact),
        title = event.caller.ifBlank { stringResource(R.string.event_call_title) },
        subtitle = event.appName,
    )
    is IslandEvent.Notification -> IslandContent(
        icon = R.drawable.ic_event_notification,
        tint = Color(0xFF82B1FF),
        compactText = "",
        title = event.title.ifBlank {
            event.appName.ifBlank { stringResource(R.string.event_notification_fallback_title) }
        },
        subtitle = event.text,
    )
}

/** Icon on the left, short status on the right, the middle left clear for the camera. */
@Composable
fun CompactContent(content: IslandContent, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painter = painterResource(content.icon),
            contentDescription = null,
            tint = content.tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = content.compactText,
            color = content.tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Never run under the camera in the middle of the pill.
            modifier = Modifier.widthIn(max = 36.dp),
        )
    }
}

/** The compact row stays where it was (flanking the camera); details appear underneath. */
@Composable
fun ExpandedContent(
    content: IslandContent,
    compactRowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(compactRowHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(content.icon),
                contentDescription = null,
                tint = content.tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = content.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.subtitle.isNotBlank()) {
                Text(
                    text = content.subtitle,
                    color = Color(0xFFBDBDBD),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
