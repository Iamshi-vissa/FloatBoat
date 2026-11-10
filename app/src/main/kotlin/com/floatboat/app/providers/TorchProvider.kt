package com.floatboat.app.providers

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Shows the island while the flashlight is on.
 *
 * Torch state is public: observing it needs no camera permission and does not open the camera.
 */
class TorchProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EventProvider {

    override val signals: Flow<IslandSignal> = callbackFlow {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        // A device can have several flash units; the torch is "on" while any of them is.
        val lit = HashSet<String>()
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                val wasOn = lit.isNotEmpty()
                if (enabled) lit.add(cameraId) else lit.remove(cameraId)
                val isOn = lit.isNotEmpty()
                if (isOn == wasOn) return
                trySend(
                    if (isOn) {
                        IslandSignal.Show(IslandEvent.Torch)
                    } else {
                        IslandSignal.End(IslandEvent.Torch.key)
                    },
                )
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                // The camera app took the flash unit; it is no longer a torch.
                onTorchModeChanged(cameraId, false)
            }
        }
        // Registration immediately reports the current state of every flash unit.
        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { cameraManager.unregisterTorchCallback(callback) }
    }
}
