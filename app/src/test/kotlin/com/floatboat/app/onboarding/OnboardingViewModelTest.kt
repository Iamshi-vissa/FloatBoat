package com.floatboat.app.onboarding

import app.cash.turbine.test
import com.floatboat.app.core.permissions.PermissionChecker
import com.floatboat.app.services.IslandServiceState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val permissions = mockk<PermissionChecker>()
    private val serviceState = IslandServiceState()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { permissions.canDrawOverlays() } returns false
        every { permissions.canPostNotifications() } returns false
        every { permissions.notificationPermissionIsRuntime } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects the permissions at creation`() = runTest {
        val viewModel = OnboardingViewModel(permissions, serviceState)

        assertEquals(
            OnboardingUiState(
                overlayGranted = false,
                showNotificationStep = true,
                notificationsGranted = false,
                islandRunning = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `refresh picks up a permission granted in system settings`() = runTest {
        val viewModel = OnboardingViewModel(permissions, serviceState)

        viewModel.uiState.test {
            assertFalse(awaitItem().overlayGranted)

            every { permissions.canDrawOverlays() } returns true
            viewModel.refreshPermissions()

            assertTrue(awaitItem().overlayGranted)
        }
    }

    @Test
    fun `island running follows the service`() = runTest {
        every { permissions.canDrawOverlays() } returns true
        val viewModel = OnboardingViewModel(permissions, serviceState)

        viewModel.uiState.test {
            assertFalse(awaitItem().islandRunning)

            serviceState.setRunning(true)
            assertTrue(awaitItem().islandRunning)

            serviceState.setRunning(false)
            assertFalse(awaitItem().islandRunning)
        }
    }

    @Test
    fun `island is never reported running without the overlay permission`() = runTest {
        every { permissions.canDrawOverlays() } returns true
        val viewModel = OnboardingViewModel(permissions, serviceState)

        viewModel.uiState.test {
            awaitItem()
            serviceState.setRunning(true)
            assertTrue(awaitItem().islandRunning)

            every { permissions.canDrawOverlays() } returns false
            viewModel.refreshPermissions()

            val revoked = awaitItem()
            assertFalse(revoked.overlayGranted)
            assertFalse(revoked.islandRunning)
        }
    }

    @Test
    fun `notification step is hidden where the permission is not a runtime one`() = runTest {
        every { permissions.notificationPermissionIsRuntime } returns false
        every { permissions.canPostNotifications() } returns true

        val viewModel = OnboardingViewModel(permissions, serviceState)

        assertFalse(viewModel.uiState.value.showNotificationStep)
        assertTrue(viewModel.uiState.value.notificationsGranted)
    }
}
