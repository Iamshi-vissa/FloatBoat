package com.floatboat.app.island.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatboat.app.R
import com.floatboat.app.island.IslandAction
import com.floatboat.app.island.IslandEvent
import kotlinx.coroutines.delay

private val Amber = Color(0xFFFFD54F)
private val Green = Color(0xFF69F0AE)
private val Blue = Color(0xFF82B1FF)
private val Red = Color(0xFFFF5252)
private val Muted = Color(0xFFBDBDBD)

/** Icon, colour and wording for an event, resolved to display strings. */
private data class EventLook(
    @param:DrawableRes val icon: Int,
    val tint: Color,
    /** A few characters shown beside the camera in the compact pill. */
    val compactText: String,
    val title: String,
    val subtitle: String,
)

@Composable
private fun eventLook(event: IslandEvent): EventLook = when (event) {
    IslandEvent.Torch -> EventLook(
        icon = R.drawable.ic_event_torch,
        tint = Amber,
        compactText = stringResource(R.string.event_torch_compact),
        title = stringResource(R.string.event_torch_title),
        subtitle = stringResource(R.string.event_torch_subtitle),
    )
    is IslandEvent.Charging -> EventLook(
        icon = R.drawable.ic_event_charging,
        tint = Green,
        compactText = stringResource(R.string.event_charging_compact, event.percent),
        title = stringResource(R.string.event_charging_title),
        subtitle = stringResource(R.string.event_charging_subtitle, event.percent),
    )
    is IslandEvent.Bluetooth -> EventLook(
        icon = R.drawable.ic_event_bluetooth,
        tint = Blue,
        compactText = "",
        title = event.deviceName,
        subtitle = stringResource(R.string.event_bluetooth_subtitle),
    )
    is IslandEvent.Call -> EventLook(
        icon = R.drawable.ic_event_call,
        tint = Green,
        compactText = stringResource(R.string.event_call_compact),
        title = event.caller.ifBlank { stringResource(R.string.event_call_title) },
        subtitle = event.appName,
    )
    is IslandEvent.Notification -> EventLook(
        icon = R.drawable.ic_event_notification,
        tint = Blue,
        compactText = "",
        title = event.title.ifBlank {
            event.appName.ifBlank { stringResource(R.string.event_notification_fallback_title) }
        },
        subtitle = event.text,
    )
    is IslandEvent.Media -> EventLook(
        icon = R.drawable.ic_event_music,
        tint = Color.White,
        compactText = "",
        title = event.title,
        subtitle = event.artist,
    )
}

/** What a screen reader announces for the island. */
@Composable
fun eventDescription(event: IslandEvent): String {
    val look = eventLook(event)
    return if (look.subtitle.isBlank()) look.title else "${look.title}, ${look.subtitle}"
}

/** Extra height the expanded island needs for this event's buttons. */
fun expandedExtraHeight(event: IslandEvent): Dp = when (event) {
    is IslandEvent.Media -> 88.dp
    is IslandEvent.Call -> if (event.canAnswer || event.canDecline) 56.dp else 0.dp
    else -> 0.dp
}

/** Icon on the left, short status on the right, the middle left clear for the camera. */
@Composable
fun CompactContent(event: IslandEvent, modifier: Modifier = Modifier) {
    val look = eventLook(event)
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (event is IslandEvent.Media) {
            AlbumArt(event, size = 20.dp, corner = 6.dp)
            if (event.isPlaying) {
                EqualizerBars(Modifier.size(width = 16.dp, height = 14.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_media_pause),
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Icon(
                painter = painterResource(look.icon),
                contentDescription = null,
                tint = look.tint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = look.compactText,
                color = look.tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Never run under the camera in the middle of the pill.
                modifier = Modifier.widthIn(max = 36.dp),
            )
        }
    }
}

/** The compact row stays where it was (flanking the camera); details appear underneath. */
@Composable
fun ExpandedContent(
    event: IslandEvent,
    compactRowHeight: Dp,
    onAction: (IslandAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = eventLook(event)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(compactRowHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(look.icon),
                contentDescription = null,
                tint = look.tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (event is IslandEvent.Media) {
                AlbumArt(event, size = 40.dp, corner = 10.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = look.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (look.subtitle.isNotBlank()) {
                    Text(
                        text = look.subtitle,
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when (event) {
            is IslandEvent.Media -> MediaControls(event, onAction)
            is IslandEvent.Call -> CallButtons(event, onAction)
            else -> Unit
        }
    }
}

@Composable
private fun AlbumArt(event: IslandEvent.Media, size: Dp, corner: Dp) {
    val shape = RoundedCornerShape(corner)
    val art = event.art
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF2A2A2A), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_event_music),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

/** Three bars bouncing out of step. Only composed (and so only animating) while playing. */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(420, 560, 340).forEachIndexed { index, period ->
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "bar$index",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(level)
                    .background(Green, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun MediaControls(event: IslandEvent.Media, onAction: (IslandAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        if (event.lengthMs > 0) {
            val position = currentPosition(event)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = Muted, fontSize = 10.sp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(3.dp)
                        .background(Color(0xFF3A3A3A), CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(position.toFloat() / event.lengthMs)
                            .fillMaxHeight()
                            .background(Color.White, CircleShape),
                    )
                }
                Text(formatTime(event.lengthMs), color = Muted, fontSize = 10.sp)
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(
                icon = R.drawable.ic_media_previous,
                label = stringResource(R.string.action_previous),
                onClick = { onAction(IslandAction.Previous) },
            )
            RoundButton(
                icon = if (event.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                label = stringResource(
                    if (event.isPlaying) R.string.action_pause else R.string.action_play,
                ),
                onClick = { onAction(IslandAction.PlayPause) },
            )
            RoundButton(
                icon = R.drawable.ic_media_next,
                label = stringResource(R.string.action_next),
                onClick = { onAction(IslandAction.Next) },
            )
        }
    }
}

@Composable
private fun CallButtons(event: IslandEvent.Call, onAction: (IslandAction) -> Unit) {
    if (!event.canAnswer && !event.canDecline) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.canDecline) {
            RoundButton(
                icon = R.drawable.ic_call_end,
                label = stringResource(R.string.action_decline),
                background = Red,
                onClick = { onAction(IslandAction.Decline) },
            )
        }
        if (event.canAnswer) {
            RoundButton(
                icon = R.drawable.ic_event_call,
                label = stringResource(R.string.action_answer),
                background = Color(0xFF2E7D32),
                onClick = { onAction(IslandAction.Answer) },
            )
        }
    }
}

/** 48 dp touch target with a 40 dp visual, per accessibility guidance. */
@Composable
private fun RoundButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    background: Color = Color.Transparent,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The playback position right now, extrapolated from the last reported position.
 *
 * Ticks once a second, and only while this composable is on screen (the expanded island)
 * and the track is playing, so the compact island and paused tracks cost nothing.
 */
@Composable
private fun currentPosition(event: IslandEvent.Media): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(event.isPlaying, event.positionAtElapsedMs) {
        now = SystemClock.elapsedRealtime()
        while (event.isPlaying) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    return mediaPositionAt(event, now)
}

/** Position of [event] at monotonic time [nowElapsedMs], clamped to the track. */
fun mediaPositionAt(event: IslandEvent.Media, nowElapsedMs: Long): Long {
    val elapsed = if (event.isPlaying && event.positionAtElapsedMs > 0) {
        ((nowElapsedMs - event.positionAtElapsedMs) * event.speed).toLong()
    } else {
        0L
    }
    val position = event.positionMs + elapsed
    return if (event.lengthMs > 0) position.coerceIn(0, event.lengthMs) else position.coerceAtLeast(0)
}

/** `m:ss`, or `h:mm:ss` for an hour or more. */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
