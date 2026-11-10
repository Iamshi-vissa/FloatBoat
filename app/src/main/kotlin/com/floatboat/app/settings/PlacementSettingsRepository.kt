package com.floatboat.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.floatboat.app.core.di.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds [PlacementSettings] in memory and mirrors them to DataStore.
 *
 * Sliders produce dozens of changes per second and the overlay should follow them live, so
 * the in-memory value is the source of truth: updates apply immediately and are written to
 * disk only once the value has been stable for [PERSIST_DELAY_MS].
 */
@Singleton
class PlacementSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow(PlacementSettings.Default)
    val settings: StateFlow<PlacementSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            val stored = readStored()
            // Do not clobber an edit made before the load finished.
            _settings.compareAndSet(PlacementSettings.Default, stored)
            persistChanges(alreadyStored = stored)
        }
    }

    fun update(transform: (PlacementSettings) -> PlacementSettings) {
        _settings.update { transform(it).coerced() }
    }

    private suspend fun readStored(): PlacementSettings = try {
        val prefs = dataStore.data.first()
        PlacementSettings(
            offsetXDp = prefs[OFFSET_X] ?: PlacementSettings.Default.offsetXDp,
            offsetYDp = prefs[OFFSET_Y] ?: PlacementSettings.Default.offsetYDp,
            sizeScale = prefs[SIZE_SCALE] ?: PlacementSettings.Default.sizeScale,
        ).coerced()
    } catch (_: IOException) {
        // Unreadable file: start from defaults rather than fail to show the island.
        PlacementSettings.Default
    }

    @OptIn(FlowPreview::class)
    private suspend fun persistChanges(alreadyStored: PlacementSettings) {
        var lastWritten = alreadyStored
        _settings.debounce(PERSIST_DELAY_MS).collect { value ->
            if (value == lastWritten) return@collect
            try {
                dataStore.edit { prefs ->
                    prefs[OFFSET_X] = value.offsetXDp
                    prefs[OFFSET_Y] = value.offsetYDp
                    prefs[SIZE_SCALE] = value.sizeScale
                }
                lastWritten = value
            } catch (_: IOException) {
                // Disk full or similar. The in-memory value still applies; retry on next change.
            }
        }
    }

    companion object {
        const val PERSIST_DELAY_MS = 300L
        private val OFFSET_X = floatPreferencesKey("pill_offset_x_dp")
        private val OFFSET_Y = floatPreferencesKey("pill_offset_y_dp")
        private val SIZE_SCALE = floatPreferencesKey("pill_size_scale")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
