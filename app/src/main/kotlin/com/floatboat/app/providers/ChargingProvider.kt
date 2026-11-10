package com.floatboat.app.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/** Pops the island with the battery level when a charger is plugged in. */
class ChargingProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EventProvider {

    override val signals: Flow<IslandSignal> = callbackFlow {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        val percent = batteryManager
                            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            .coerceIn(0, 100)
                        trySend(IslandSignal.Show(IslandEvent.Charging(percent)))
                    }
                    Intent.ACTION_POWER_DISCONNECTED ->
                        trySend(IslandSignal.End(CHARGING_KEY))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // These are system broadcasts; NOT_EXPORTED keeps other apps from faking them.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private companion object {
        const val CHARGING_KEY = "charging"
    }
}
