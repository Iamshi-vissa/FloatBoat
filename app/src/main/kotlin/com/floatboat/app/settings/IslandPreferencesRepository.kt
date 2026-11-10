package com.floatboat.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.floatboat.app.core.di.ApplicationScope
import com.floatboat.app.island.IslandPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** [IslandPreferences] held in memory for synchronous reads and mirrored to DataStore. */
@Singleton
class IslandPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _preferences = MutableStateFlow(IslandPreferences())
    val preferences: StateFlow<IslandPreferences> = _preferences.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    /** Keeps "change in memory, then write" atomic so writes land in the order of the edits. */
    private val writeLock = Mutex()

    init {
        scope.launch {
            _preferences.value = read()
            loaded.complete(Unit)
        }
    }

    /** The stored preferences, waiting for the initial load if it is still running. */
    suspend fun awaitLoaded(): IslandPreferences {
        loaded.await()
        return _preferences.value
    }

    fun update(transform: (IslandPreferences) -> IslandPreferences) {
        scope.launch {
            // Never apply an edit on top of defaults that are about to be replaced.
            loaded.await()
            writeLock.withLock { write(_preferences.updateAndGet(transform)) }
        }
    }

    private suspend fun read(): IslandPreferences = try {
        val stored = dataStore.data.first()
        val defaults = IslandPreferences()
        IslandPreferences(
            enabled = stored[ENABLED] ?: defaults.enabled,
            startOnBoot = stored[START_ON_BOOT] ?: defaults.startOnBoot,
            haptics = stored[HAPTICS] ?: defaults.haptics,
            hideInFullscreen = stored[HIDE_IN_FULLSCREEN] ?: defaults.hideInFullscreen,
            showTorch = stored[SHOW_TORCH] ?: defaults.showTorch,
            showCharging = stored[SHOW_CHARGING] ?: defaults.showCharging,
            showMedia = stored[SHOW_MEDIA] ?: defaults.showMedia,
            showBluetooth = stored[SHOW_BLUETOOTH] ?: defaults.showBluetooth,
            showNotifications = stored[SHOW_NOTIFICATIONS] ?: defaults.showNotifications,
            showCalls = stored[SHOW_CALLS] ?: defaults.showCalls,
            mutedPackages = stored[MUTED_PACKAGES] ?: defaults.mutedPackages,
        )
    } catch (_: IOException) {
        IslandPreferences()
    }

    private suspend fun write(value: IslandPreferences) {
        try {
            dataStore.edit { prefs ->
                prefs[ENABLED] = value.enabled
                prefs[START_ON_BOOT] = value.startOnBoot
                prefs[HAPTICS] = value.haptics
                prefs[HIDE_IN_FULLSCREEN] = value.hideInFullscreen
                prefs[SHOW_TORCH] = value.showTorch
                prefs[SHOW_CHARGING] = value.showCharging
                prefs[SHOW_MEDIA] = value.showMedia
                prefs[SHOW_BLUETOOTH] = value.showBluetooth
                prefs[SHOW_NOTIFICATIONS] = value.showNotifications
                prefs[SHOW_CALLS] = value.showCalls
                prefs[MUTED_PACKAGES] = value.mutedPackages
            }
        } catch (_: IOException) {
            // The in-memory value still applies for this run; the next edit retries the write.
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("island_enabled")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HIDE_IN_FULLSCREEN = booleanPreferencesKey("hide_in_fullscreen")
        val SHOW_TORCH = booleanPreferencesKey("show_torch")
        val SHOW_CHARGING = booleanPreferencesKey("show_charging")
        val SHOW_MEDIA = booleanPreferencesKey("show_media")
        val SHOW_BLUETOOTH = booleanPreferencesKey("show_bluetooth")
        val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val SHOW_CALLS = booleanPreferencesKey("show_calls")
        val MUTED_PACKAGES = stringSetPreferencesKey("muted_packages")
    }
}
