package com.floatboat.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floatboat.app.core.permissions.PermissionChecker
import com.floatboat.app.services.IslandServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class OnboardingUiState(
    val overlayGranted: Boolean,
    /** False on Android 12 and below, where there is nothing to ask for. */
    val showNotificationStep: Boolean,
    val notificationsGranted: Boolean,
    val notificationAccessGranted: Boolean,
    /** False on Android 11 and below, where Bluetooth needs no runtime permission. */
    val showBluetoothStep: Boolean,
    val bluetoothGranted: Boolean,
    val batteryOptimized: Boolean,
    val islandRunning: Boolean,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: PermissionChecker,
    serviceState: IslandServiceState,
) : ViewModel() {

    private data class Permissions(
        val overlay: Boolean,
        val notifications: Boolean,
        val notificationAccess: Boolean,
        val bluetooth: Boolean,
        val batteryOptimized: Boolean,
    )

    private val permissionState = MutableStateFlow(readPermissions())

    val uiState: StateFlow<OnboardingUiState> =
        combine(permissionState, serviceState.running, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = toUiState(permissionState.value, serviceState.running.value),
            )

    /**
     * Permissions are changed in system settings, outside the app, and Android offers no
     * callback for the overlay switch. Call this whenever the screen resumes.
     */
    fun refreshPermissions() {
        permissionState.value = readPermissions()
    }

    private fun readPermissions() = Permissions(
        overlay = permissions.canDrawOverlays(),
        notifications = permissions.canPostNotifications(),
        notificationAccess = permissions.hasNotificationAccess(),
        bluetooth = permissions.canSeeBluetoothDevices(),
        batteryOptimized = permissions.isBatteryOptimized(),
    )

    private fun toUiState(permissions: Permissions, running: Boolean) = OnboardingUiState(
        overlayGranted = permissions.overlay,
        showNotificationStep = this.permissions.notificationPermissionIsRuntime,
        notificationsGranted = permissions.notifications,
        notificationAccessGranted = permissions.notificationAccess,
        showBluetoothStep = this.permissions.bluetoothPermissionIsRuntime,
        bluetoothGranted = permissions.bluetooth,
        batteryOptimized = permissions.batteryOptimized,
        // The service removes itself if the overlay permission is revoked; never show "on"
        // without the permission even if the two updates arrive out of order.
        islandRunning = running && permissions.overlay,
    )
}
