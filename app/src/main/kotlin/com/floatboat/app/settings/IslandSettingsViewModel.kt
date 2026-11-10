package com.floatboat.app.settings

import androidx.lifecycle.ViewModel
import com.floatboat.app.island.IslandPreferences
import com.floatboat.app.providers.NotificationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class IslandSettingsViewModel @Inject constructor(
    private val repository: IslandPreferencesRepository,
    notifications: NotificationProvider,
) : ViewModel() {

    val preferences: StateFlow<IslandPreferences> = repository.preferences

    /** Package name to label of apps that recently sent a notification. */
    val recentApps: StateFlow<Map<String, String>> = notifications.recentApps

    fun update(transform: (IslandPreferences) -> IslandPreferences) = repository.update(transform)

    fun setEnabled(enabled: Boolean) = repository.update { it.copy(enabled = enabled) }

    fun setAppMuted(packageName: String, muted: Boolean) = repository.update {
        it.copy(
            mutedPackages = if (muted) it.mutedPackages + packageName else it.mutedPackages - packageName,
        )
    }
}
