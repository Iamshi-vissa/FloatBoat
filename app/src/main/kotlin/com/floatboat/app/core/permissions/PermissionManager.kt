package com.floatboat.app.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only view of the permissions Floatboat depends on.
 *
 * Kept as an interface so view models and the service can be tested with a fake.
 */
interface PermissionChecker {
    /** `SYSTEM_ALERT_WINDOW`: without it no overlay window can be added. */
    fun canDrawOverlays(): Boolean

    /** False only on Android 13+ when the user has not granted `POST_NOTIFICATIONS`. */
    fun canPostNotifications(): Boolean

    /** Whether `POST_NOTIFICATIONS` is a runtime permission on this OS version. */
    val notificationPermissionIsRuntime: Boolean

    /** Notification access: lets the island react to other apps' notifications and calls. */
    fun hasNotificationAccess(): Boolean

    /** False only on Android 12+ when "Nearby devices" has not been granted. */
    fun canSeeBluetoothDevices(): Boolean

    /** Whether the Bluetooth permission is a runtime one on this OS version. */
    val bluetoothPermissionIsRuntime: Boolean

    /** Whether the system is allowed to restrict this app in the background. */
    fun isBatteryOptimized(): Boolean
}

@Singleton
class PermissionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionChecker {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override val notificationPermissionIsRuntime: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun canPostNotifications(): Boolean =
        !notificationPermissionIsRuntime ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override val bluetoothPermissionIsRuntime: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun canSeeBluetoothDevices(): Boolean =
        !bluetoothPermissionIsRuntime ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    override fun isBatteryOptimized(): Boolean =
        !context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system list of battery-optimisation exemptions. The direct "exempt this app" dialog
     * needs a permission Google Play only allows for a few app categories, so the user picks
     * Floatboat from the list instead.
     */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Intent for the system screen where the user grants notification access. */
    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * Intent for the system "Display over other apps" screen for this app.
     *
     * The overlay permission is a special app-op: it cannot be requested with a runtime
     * dialog, the user has to flip the switch in Settings and come back.
     */
    fun overlaySettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {
    @Binds
    abstract fun bindPermissionChecker(impl: PermissionManager): PermissionChecker
}
