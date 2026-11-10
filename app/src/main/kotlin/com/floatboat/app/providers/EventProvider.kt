package com.floatboat.app.providers

import com.floatboat.app.island.IslandSignal
import kotlinx.coroutines.flow.Flow

/**
 * A source of island events. Implementations must be callback-driven (no polling) and must
 * release their system callbacks when collection stops, so an idle island costs nothing.
 */
interface EventProvider {
    val signals: Flow<IslandSignal>
}
