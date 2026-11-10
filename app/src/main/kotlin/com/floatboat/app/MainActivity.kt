package com.floatboat.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatboat.app.core.permissions.PermissionManager
import com.floatboat.app.onboarding.OnboardingScreen
import com.floatboat.app.onboarding.OnboardingViewModel
import com.floatboat.app.services.FloatboatService
import com.floatboat.app.settings.PlacementSettingsCard
import com.floatboat.app.settings.PlacementSettingsViewModel
import com.floatboat.app.ui.theme.FloatboatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionManager: PermissionManager

    private val viewModel: OnboardingViewModel by viewModels()
    private val placementViewModel: PlacementSettingsViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val placement by placementViewModel.settings.collectAsStateWithLifecycle()
            FloatboatTheme {
                OnboardingScreen(
                    state = state,
                    onGrantOverlay = ::openOverlaySettings,
                    onGrantNotifications = ::askForNotifications,
                    onGrantNotificationAccess = ::openNotificationAccessSettings,
                    onIslandToggle = ::setIslandEnabled,
                    placementSettings = {
                        PlacementSettingsCard(
                            settings = placement,
                            onOffsetXChange = placementViewModel::setOffsetX,
                            onOffsetYChange = placementViewModel::setOffsetY,
                            onSizeScaleChange = placementViewModel::setSizeScale,
                            onReset = placementViewModel::reset,
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may be returning from system settings with a permission flipped.
        viewModel.refreshPermissions()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(permissionManager.overlaySettingsIntent())
        } catch (_: ActivityNotFoundException) {
            // A few OEM builds lack the per-app screen; the app-wide list always exists.
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(permissionManager.notificationAccessSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            // Extremely rare (some Android Go builds). The step simply stays ungranted.
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setIslandEnabled(enabled: Boolean) {
        if (enabled) FloatboatService.start(this) else FloatboatService.stop(this)
    }
}
