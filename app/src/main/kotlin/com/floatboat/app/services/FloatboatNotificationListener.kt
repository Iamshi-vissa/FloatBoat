package com.floatboat.app.services

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.floatboat.app.providers.NotificationInfo
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras
        provider.onPosted(
            NotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName(sbn),
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                category = notification.category,
                isOngoing = sbn.isOngoing,
                isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            ),
            ownPackage = packageName,
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
        val embedded: ApplicationInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sbn.notification.extras.getParcelable(EXTRA_APP_INFO, ApplicationInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                sbn.notification.extras.getParcelable(EXTRA_APP_INFO)
            }
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
    }
}
