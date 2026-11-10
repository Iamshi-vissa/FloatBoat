package com.floatboat.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlacementSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("settings.preferences_pb")
        }

    private fun TestScope.repository(store: DataStore<Preferences>) =
        PlacementSettingsRepository(store, backgroundScope)

    @Test
    fun `starts with defaults when nothing is stored`() = runTest {
        val repository = repository(dataStore())
        runCurrent()

        assertEquals(PlacementSettings.Default, repository.settings.value)
    }

    @Test
    fun `updates apply immediately`() = runTest {
        val repository = repository(dataStore())
        runCurrent()

        repository.update { it.copy(offsetYDp = 12f) }

        assertEquals(12f, repository.settings.value.offsetYDp)
    }

    @Test
    fun `out of range values are clamped`() = runTest {
        val repository = repository(dataStore())
        runCurrent()

        repository.update { PlacementSettings(offsetXDp = 999f, offsetYDp = -999f, sizeScale = 9f) }

        assertEquals(
            PlacementSettings(
                offsetXDp = PlacementSettings.OffsetXRange.endInclusive,
                offsetYDp = PlacementSettings.OffsetYRange.start,
                sizeScale = PlacementSettings.SizeScaleRange.endInclusive,
            ),
            repository.settings.value,
        )
    }

    @Test
    fun `changes are persisted once they settle and read back by a new instance`() = runTest {
        val store = dataStore()
        val first = repository(store)
        runCurrent()

        first.update { it.copy(offsetXDp = -8f) }
        first.update { it.copy(offsetYDp = 20f, sizeScale = 1.25f) }
        advanceTimeBy(PlacementSettingsRepository.PERSIST_DELAY_MS + 1)
        runCurrent()

        val second = repository(store)
        runCurrent()

        assertEquals(
            PlacementSettings(offsetXDp = -8f, offsetYDp = 20f, sizeScale = 1.25f),
            second.settings.value,
        )
    }

    @Test
    fun `an edit made before loading finishes is kept`() = runTest {
        val store = dataStore()
        val first = repository(store)
        runCurrent()
        first.update { it.copy(offsetYDp = 30f) }
        advanceTimeBy(PlacementSettingsRepository.PERSIST_DELAY_MS + 1)
        runCurrent()

        val second = repository(store)
        second.update { it.copy(offsetYDp = 5f) }
        runCurrent()

        assertEquals(5f, second.settings.value.offsetYDp)
    }
}
