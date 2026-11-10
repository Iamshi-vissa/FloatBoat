package com.floatboat.app.island

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Turns signals from event sources and user gestures into an [IslandState].
 *
 * Rules:
 *  - With no active event the island is [IslandState.Hidden].
 *  - The active event with the highest priority owns the island; ties go to the newest.
 *  - Momentary events remove themselves after their duration. The countdown is paused while
 *    the event is expanded and restarts when it collapses.
 *  - Tap toggles between compact and expanded. An expanded island collapses by itself after
 *    [expandedTimeoutMs], or immediately if another event takes over.
 *  - Dismiss removes the current event; whatever is next in line (if anything) shows.
 *
 * Not thread-safe: call everything from the thread [scope] dispatches on.
 */
@MainThread
class IslandStateMachine(
    private val scope: CoroutineScope,
    private val expandedTimeoutMs: Long = 6_000,
) {
    private val _state = MutableStateFlow<IslandState>(IslandState.Hidden)
    val state: StateFlow<IslandState> = _state.asStateFlow()

    /** Insertion order is recency: re-shown events are moved to the end. */
    private val active = LinkedHashMap<String, IslandEvent>()
    private val timeouts = HashMap<String, Job>()
    private var expandedKey: String? = null
    private var collapseJob: Job? = null

    fun onSignal(signal: IslandSignal) {
        when (signal) {
            is IslandSignal.Show -> {
                val event = signal.event
                active.remove(event.key)
                active[event.key] = event
                if (expandedKey != event.key) scheduleTimeout(event)
            }
            is IslandSignal.End -> remove(signal.key)
        }
        publish()
    }

    fun onTap() {
        val current = currentEvent() ?: return
        if (expandedKey == current.key) {
            stopExpanding()
        } else {
            expandedKey = current.key
            timeouts.remove(current.key)?.cancel()
            collapseJob?.cancel()
            collapseJob = scope.launch {
                delay(expandedTimeoutMs)
                stopExpanding()
                publish()
            }
        }
        publish()
    }

    fun onDismiss() {
        val current = currentEvent() ?: return
        remove(current.key)
        publish()
    }

    /** Back to compact, e.g. after the user acted on the expanded island. */
    fun collapse() {
        stopExpanding()
        publish()
    }

    /** Drops everything, e.g. when the island is switched off. */
    fun clear() {
        active.clear()
        timeouts.values.forEach { it.cancel() }
        timeouts.clear()
        expandedKey = null
        collapseJob?.cancel()
        collapseJob = null
        publish()
    }

    private fun remove(key: String) {
        active.remove(key)
        timeouts.remove(key)?.cancel()
    }

    private fun stopExpanding() {
        val key = expandedKey ?: return
        expandedKey = null
        collapseJob?.cancel()
        collapseJob = null
        active[key]?.let(::scheduleTimeout)
    }

    private fun scheduleTimeout(event: IslandEvent) {
        timeouts.remove(event.key)?.cancel()
        val duration = event.durationMs ?: return
        timeouts[event.key] = scope.launch {
            delay(duration)
            timeouts.remove(event.key)
            active.remove(event.key)
            publish()
        }
    }

    private fun currentEvent(): IslandEvent? =
        active.values.withIndex()
            .maxWithOrNull(compareBy({ it.value.priority }, { it.index }))
            ?.value

    private fun publish() {
        val current = currentEvent()
        // The expanded event ended or was pre-empted by a more important one.
        if (expandedKey != null && expandedKey != current?.key) stopExpanding()
        _state.value = when {
            current == null -> IslandState.Hidden
            expandedKey == current.key -> IslandState.Expanded(current)
            else -> IslandState.Compact(current)
        }
    }
}
