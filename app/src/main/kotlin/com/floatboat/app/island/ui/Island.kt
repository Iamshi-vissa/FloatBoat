package com.floatboat.app.island.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.floatboat.app.core.cutout.PillMetrics
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandState

/** Dimensions of the island before cutout fitting and user calibration. */
object PillDefaults {
    val Metrics = PillMetrics(
        widthDp = 112f,
        heightDp = 32f,
        minTopMarginDp = 6f,
        cutoutPaddingDp = 4f,
        expandedWidthDp = 340f,
        expandedExtraHeightDp = 56f,
        screenMarginDp = 12f,
    )

    /** The expanded island stops being a full capsule and uses this corner radius instead. */
    val MaxCornerRadius = 30.dp

    /**
     * Extra room around the island while it animates, so a spring that overshoots its target
     * size is not cut off by the edge of the overlay window.
     */
    const val ANIMATION_SLACK = 1.08f

    /** Upward drag distance that counts as "swipe away". */
    val DismissDragThreshold = 14.dp

    const val MORPH_DAMPING = 0.78f
    const val MORPH_STIFFNESS = Spring.StiffnessMediumLow
}

/**
 * The island. A pure function of [state]: hidden draws nothing, compact and expanded morph
 * into each other with a spring.
 *
 * Sizing matters here because the overlay window wraps this composable. Resizing a window on
 * every animation frame stutters, so instead the outer box jumps to the larger size when a
 * morph starts and back when it has finished, and the black pill animates inside it. System
 * animation scale (including "remove animations") is honoured by Compose automatically.
 */
@Composable
fun Island(
    state: IslandState,
    compactSize: DpSize,
    expandedSize: DpSize,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep drawing the last event while the island animates out.
    var lastEvent by remember { mutableStateOf<IslandEvent?>(null) }
    state.eventOrNull?.let { lastEvent = it }
    val event = lastEvent

    // A 1 dp floor keeps the overlay window alive (never zero-sized) while hidden.
    Box(modifier = modifier.defaultMinSize(1.dp, 1.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = state != IslandState.Hidden && event != null,
            enter = fadeIn() + scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(
                    dampingRatio = PillDefaults.MORPH_DAMPING,
                    stiffness = PillDefaults.MORPH_STIFFNESS,
                ),
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            if (event != null) {
                IslandBody(
                    event = event,
                    expanded = state is IslandState.Expanded,
                    compactSize = compactSize,
                    expandedSize = expandedSize,
                    onTap = onTap,
                    onLongPress = onLongPress,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun IslandBody(
    event: IslandEvent,
    expanded: Boolean,
    compactSize: DpSize,
    expandedSize: DpSize,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
) {
    val morph = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = PillDefaults.MORPH_DAMPING,
        stiffness = PillDefaults.MORPH_STIFFNESS,
    )
    val target = if (expanded) expandedSize else compactSize
    val width by animateDpAsState(target.width, morph, label = "islandWidth")
    val height by animateDpAsState(target.height, morph, label = "islandHeight")
    val morphing = width != target.width || height != target.height

    val content = islandContent(event)
    val view = LocalView.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val windowSize = if (expanded || morphing) {
        DpSize(
            expandedSize.width * PillDefaults.ANIMATION_SLACK,
            expandedSize.height * PillDefaults.ANIMATION_SLACK,
        )
    } else {
        compactSize
    }
    Box(modifier = Modifier.size(windowSize), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(min(height / 2, PillDefaults.MaxCornerRadius)))
                .background(Color.Black)
                .semantics { contentDescription = content.description }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            currentOnTap()
                        },
                        onLongPress = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            currentOnLongPress()
                        },
                    )
                }
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, amount -> dragged += amount },
                        onDragEnd = {
                            if (dragged < -PillDefaults.DismissDragThreshold.toPx()) {
                                currentOnDismiss()
                            }
                        },
                    )
                },
        ) {
            Crossfade(targetState = expanded, label = "islandContent") { showExpanded ->
                if (showExpanded) {
                    ExpandedContent(content, compactRowHeight = compactSize.height)
                } else {
                    CompactContent(content)
                }
            }
        }
    }
}
