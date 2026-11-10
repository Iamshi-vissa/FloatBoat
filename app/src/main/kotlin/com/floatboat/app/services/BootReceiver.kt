package com.floatboat.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.floatboat.app.core.di.ApplicationScope
import com.floatboat.app.core.permissions.PermissionChecker
import com.floatboat.app.settings.IslandPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Brings the island back after a reboot or an app update, if the user had it on and has not
 * opted out of starting on boot.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): IslandPreferencesRepository
        fun permissions(): PermissionChecker

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            Dependencies::class.java,
        )
        // Preferences load from disk; keep the receiver alive until that is done.
        val pending = goAsync()
        deps.scope().launch {
            try {
                val prefs = deps.preferences().awaitLoaded()
                if (shouldStart(prefs.enabled, prefs.startOnBoot, deps.permissions().canDrawOverlays())) {
                    FloatboatService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun shouldStart(enabled: Boolean, startOnBoot: Boolean, canDrawOverlays: Boolean): Boolean =
            enabled && startOnBoot && canDrawOverlays
    }
}
