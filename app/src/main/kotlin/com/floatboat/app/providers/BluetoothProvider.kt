package com.floatboat.app.providers

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Pops the island when a Bluetooth device (earbuds, speaker, car) connects.
 *
 * From Android 12 the system only delivers these broadcasts, and only reveals the device
 * name, to apps holding the "Nearby devices" permission. Without it this provider is silent.
 */
class BluetoothProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EventProvider {

    override val signals: Flow<IslandSignal> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val name = deviceName(intent) ?: return
                        trySend(IslandSignal.Show(IslandEvent.Bluetooth(name)))
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                        trySend(IslandSignal.End(IslandEvent.Bluetooth.KEY))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // Exported on purpose: the Bluetooth stack runs under its own uid, not the system
        // uid, so a non-exported receiver would never hear it. These are protected
        // broadcasts, so ordinary apps cannot send them.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun deviceName(intent: Intent): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        ) ?: return null
        return try {
            device.name?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
    }
}
