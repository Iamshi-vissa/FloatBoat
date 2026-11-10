package com.floatboat.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.floatboat.app.island.IslandPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IslandPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("settings.preferences_pb")
        }

    @Test
    fun `starts with defaults`() = runTest {
        val repository = IslandPreferencesRepository(dataStore(), backgroundScope)

        assertEquals(IslandPreferences(), repository.awaitLoaded())
    }

    @Test
    fun `updates are applied in order and survive a restart`() = runTest {
        val store = dataStore()
        val first = IslandPreferencesRepository(store, backgroundScope)

        first.update { it.copy(enabled = true) }
        first.update { it.copy(haptics = false, mutedPackages = setOf("com.chat")) }
        first.update { it.copy(mutedPackages = it.mutedPackages + "com.mail") }
        runCurrent()

        val expected = IslandPreferences(
            enabled = true,
            haptics = false,
            mutedPackages = setOf("com.chat", "com.mail"),
        )
        assertEquals(expected, first.preferences.value)
        assertEquals(expected, IslandPreferencesRepository(store, backgroundScope).awaitLoaded())
    }

    @Test
    fun `an edit made before loading finishes is applied on top of the stored values`() = runTest {
        val store = dataStore()
        val first = IslandPreferencesRepository(store, backgroundScope)
        first.update { it.copy(showTorch = false) }
        runCurrent()

        val second = IslandPreferencesRepository(store, backgroundScope)
        second.update { it.copy(enabled = true) }
        runCurrent()

        assertEquals(IslandPreferences(enabled = true, showTorch = false), second.preferences.value)
    }
}
