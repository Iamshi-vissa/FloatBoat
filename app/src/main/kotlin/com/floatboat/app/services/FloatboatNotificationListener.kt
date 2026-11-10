package com.floatboat.app.services

import android.app.Notification
import android.app.PendingIntent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import com.floatboat.app.providers.NotificationAccessState
import com.floatboat.app.providers.NotificationInfo
import com.floatboat.app.providers.NotificationIntents
import com.floatboat.app.providers.NotificationProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives every posted and removed notification once the user grants notification access.
 *
 * Only a handful of fields are read, they are forwarded in memory to [NotificationProvider],
 * and nothing is stored or leaves the device.
 */
@AndroidEntryPoint
class FloatboatNotificationListener : NotificationListenerService() {

    @Inject lateinit var provider: NotificationProvider
    @Inject lateinit var accessState: NotificationAccessState

    override fun onListenerConnected() {
        accessState.setConnected(true)
    }

    override fun onListenerDisconnected() {
        accessState.setConnected(false)
    }

    override fun onDestroy() {
        accessState.setConnected(false)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras
        // Call-style notifications (Android 12+) carry their answer / decline / hang-up
        // intents as extras; that is the only supported way for another app to act on a call.
        val answer = BundleCompat.getParcelable(extras, EXTRA_ANSWER, PendingIntent::class.java)
        val decline = BundleCompat.getParcelable(extras, EXTRA_DECLINE, PendingIntent::class.java)
            ?: BundleCompat.getParcelable(extras, EXTRA_HANG_UP, PendingIntent::class.java)
        provider.onPosted(
            info = NotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName(sbn),
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                category = notification.category,
                isOngoing = sbn.isOngoing,
                isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                hasAnswerAction = answer != null,
                hasDeclineAction = decline != null,
            ),
            ownPackage = packageName,
            actions = NotificationIntents(notification.contentIntent, answer, decline),
            nowMs = SystemClock.elapsedRealtime(),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        provider.onRemoved(sbn.key)
    }

    /**
     * The posting app's label. Package visibility rules (Android 11+) hide most apps from
     * PackageManager, but every notification carries its own ApplicationInfo, so that is
     * tried first.
     */
    private fun appName(sbn: StatusBarNotification): String {
        val embedded = BundleCompat.getParcelable(
            sbn.notification.extras,
            EXTRA_APP_INFO,
            ApplicationInfo::class.java,
        )
        val info = embedded ?: try {
            packageManager.getApplicationInfo(sbn.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return info?.loadLabel(packageManager)?.toString().orEmpty()
    }

    private companion object {
        /** Notification.EXTRA_BUILDER_APPLICATION_INFO, which is not public API. */
        const val EXTRA_APP_INFO = "android.appInfo"

        // Notification.EXTRA_ANSWER_INTENT and friends; public only from API 31.
        const val EXTRA_ANSWER = "android.answerIntent"
        const val EXTRA_DECLINE = "android.declineIntent"
        const val EXTRA_HANG_UP = "android.hangUpIntent"
    }
}
