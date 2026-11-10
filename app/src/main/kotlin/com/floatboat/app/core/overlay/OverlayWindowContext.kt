package com.floatboat.app.core.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** The context overlay windows are created from and measured with. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OverlayWindowContext

/**
 * From Android 11 a WindowManager must come from a context associated with a display and a
 * window type; the application context is neither, and using it reports the wrong metrics
 * after rotation. Older versions have no such notion, so the app context is correct there.
 */
fun createOverlayWindowContext(appContext: Context): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return appContext
    val display = appContext.getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)
    return appContext.createDisplayContext(display)
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
}

@Module
@InstallIn(SingletonComponent::class)
object OverlayModule {
    @Provides
    @Singleton
    @OverlayWindowContext
    fun provideOverlayWindowContext(@ApplicationContext context: Context): Context =
        createOverlayWindowContext(context)
}
