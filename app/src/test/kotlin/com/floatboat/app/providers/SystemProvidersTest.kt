package com.floatboat.app.providers

import android.app.Application
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Looper
import app.cash.turbine.test
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
class SystemProvidersTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `charging provider reports plug-in with battery level and unplug`() = runTest {
        // Before Android 13, androidx guards non-exported receivers with a permission the
        // app declares for itself. A real install holds it automatically; Robolectric does not.
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(app.getSystemService(BatteryManager::class.java))
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 73)

        ChargingProvider(app).signals.test {
            idle()
            app.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED))
            idle()
            assertEquals(IslandSignal.Show(IslandEvent.Charging(73)), awaitItem())

            app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
            idle()
            assertEquals(IslandSignal.End("charging"), awaitItem())
        }
    }

    @Test
    fun `torch provider follows the flashlight`() = runTest {
        val cameraManager = app.getSystemService(CameraManager::class.java)
        shadowOf(cameraManager).addCamera(
            "0",
            Shadow.newInstanceOf(CameraCharacteristics::class.java),
        )

        TorchProvider(app).signals.test {
            idle()
            cameraManager.setTorchMode("0", true)
            idle()
            assertEquals(IslandSignal.Show(IslandEvent.Torch), awaitItem())

            cameraManager.setTorchMode("0", false)
            idle()
            assertEquals(IslandSignal.End("torch"), awaitItem())
        }
    }
}
