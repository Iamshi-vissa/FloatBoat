package com.floatboat.app.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.floatboat.app.providers.MediaProvider
import com.floatboat.app.providers.NotificationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Carries out what the user asks for on the island: open the source app, press a button. */
@Singleton
class IslandActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val media: MediaProvider,
    private val notifications: NotificationProvider,
) {
    /** Opens whatever the event came from. Does nothing for events with no app behind them. */
    fun open(event: IslandEvent) {
        when (event) {
            is IslandEvent.Media ->
                send(media.sessionActivity()) || launch(event.packageName)
            is IslandEvent.Notification ->
                send(notifications.intentsFor(event.key)?.content) || launch(event.packageName)
            is IslandEvent.Call ->
                send(notifications.intentsFor(event.key)?.content) || launch(event.packageName)
            IslandEvent.Torch, is IslandEvent.Charging, is IslandEvent.Bluetooth -> Unit
        }
    }

    fun perform(event: IslandEvent, action: IslandAction) {
        when (action) {
            IslandAction.PlayPause -> media.playPause()
            IslandAction.Next -> media.next()
            IslandAction.Previous -> media.previous()
            IslandAction.Answer -> send(notifications.intentsFor(event.key)?.answer)
            IslandAction.Decline -> send(notifications.intentsFor(event.key)?.decline)
        }
    }

    /**
     * Fires another app's PendingIntent from the background. Since Android 14 the sender has
     * to opt in explicitly to lending its "may start activities" privilege, which Floatboat
     * has because it shows an overlay window.
     */
    private fun send(intent: PendingIntent?): Boolean {
        if (intent == null) return false
        return try {
            val options = backgroundStartOptions()
            intent.send(context, 0, null, null, null, null, options)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Intent no longer valid", e)
            false
        }
    }

    private fun backgroundStartOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                )
                .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            @Suppress("DEPRECATION")
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        else -> null
    }

    private fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private companion object {
        const val TAG = "IslandActionHandler"
    }
}
