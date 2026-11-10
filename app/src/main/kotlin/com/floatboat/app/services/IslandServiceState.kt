package com.floatboat.app.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide "is the island currently on screen" flag.
 *
 * Android has no supported way to ask whether your own service is running, so
 * [FloatboatService] reports it here and the UI observes it.
 */
@Singleton
class IslandServiceState @Inject constructor() {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
    }
}
