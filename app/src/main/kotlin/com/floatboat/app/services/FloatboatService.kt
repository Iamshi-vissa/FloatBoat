package com.floatboat.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.floatboat.app.MainActivity
import com.floatboat.app.R
import com.floatboat.app.core.cutout.CutoutDetector
import com.floatboat.app.core.cutout.PillLayout
import com.floatboat.app.core.cutout.PillPlacement
import com.floatboat.app.core.overlay.OverlayPosition
import com.floatboat.app.core.overlay.OverlayWindowManager
import com.floatboat.app.core.permissions.PermissionChecker
import com.floatboat.app.island.ui.IslandPill
import com.floatboat.app.island.ui.PillDefaults
import com.floatboat.app.settings.PlacementSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the island overlay for as long as the user wants it visible.
 *
 * A foreground service is what keeps the process (and therefore the overlay window) alive
 * while the app has no visible Activity. Its lifetime *is* the island's lifetime: the overlay
 * is added in [onStartCommand] and always removed in [onDestroy].
 */
@AndroidEntryPoint
class FloatboatService : Service() {

    @Inject lateinit var overlay: OverlayWindowManager
    @Inject lateinit var permissions: PermissionChecker
    @Inject lateinit var serviceState: IslandServiceState
    @Inject lateinit var cutoutDetector: CutoutDetector
    @Inject lateinit var placementSettings: PlacementSettingsRepository

    private val scope = MainScope()
    private val pillLayout = MutableStateFlow(PillLayout(0, 0, 0, 0))
    private var placementJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() obliges us to call startForeground() within a few seconds
        // on every start, including the ones we are about to abandon, or the app is killed.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP || !permissions.canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val layout = computeLayout()
        pillLayout.value = layout
        val shown = overlay.show(layout.position()) {
            IslandOverlayContent(pillLayout, onConfigurationChanged = ::applyLayout)
        }
        if (!shown) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (placementJob == null) {
            // Follow calibration changes live while the user drags the sliders.
            placementJob = scope.launch { placementSettings.settings.collect { applyLayout() } }
        }
        serviceState.setRunning(true)
        // If the process is killed for memory, bring the island back (intent will be null).
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / fold: the window itself survives, its position and size are recomputed.
        applyLayout()
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        serviceState.setRunning(false)
        super.onDestroy()
    }

    private fun computeLayout(): PillLayout = PillPlacement.compute(
        screen = cutoutDetector.detect(),
        settings = placementSettings.settings.value,
        metrics = PillDefaults.Metrics,
    )

    private fun applyLayout() {
        val layout = computeLayout()
        pillLayout.value = layout
        overlay.updatePosition(layout.position())
    }

    private fun PillLayout.position() = OverlayPosition(xFromCentrePx, yPx)

    /** Returns false if the system refused to promote the service to the foreground. */
    private fun enterForeground(): Boolean {
        // Android 14+ requires a declared type; older versions ignore it (0 = none).
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: IllegalStateException) {
            // Android 12+: ForegroundServiceStartNotAllowedException when started from the
            // background. Apps holding the overlay permission are exempt, so this means the
            // permission is gone and there is nothing to show anyway.
            Log.w(TAG, "Not allowed to start in the foreground", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing foreground service permission", e)
            false
        }
    }

    private fun createNotificationChannel() {
        // LOW: no sound, no heads-up. (MIN is not honoured for foreground services.)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatboatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_floatboat)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_action_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "FloatboatService"
        private const val CHANNEL_ID = "floatboat_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.floatboat.app.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatboatService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatboatService::class.java))
        }
    }
}

/**
 * Overlay content: the pill at its computed size.
 *
 * The overlay view receives configuration changes together with its window, which can be a
 * moment after the service does, so the layout is recomputed from here as well. That way the
 * final layout is always based on up-to-date screen metrics.
 */
@Composable
private fun IslandOverlayContent(
    layout: StateFlow<PillLayout>,
    onConfigurationChanged: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { onConfigurationChanged() }

    val current by layout.collectAsState()
    with(LocalDensity.current) {
        IslandPill(width = current.widthPx.toDp(), height = current.heightPx.toDp())
    }
}
