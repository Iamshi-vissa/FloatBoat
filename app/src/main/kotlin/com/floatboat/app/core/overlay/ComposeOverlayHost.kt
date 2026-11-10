package com.floatboat.app.core.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts a [ComposeView] outside of any Activity so it can live in an overlay window.
 *
 * Compose looks up three owners on the view tree when it first attaches:
 *  - a [LifecycleOwner], which drives the recomposer (no RESUMED lifecycle = no frames),
 *  - a [SavedStateRegistryOwner], needed by `rememberSaveable`,
 *  - a [ViewModelStoreOwner], needed by `viewModel()`.
 *
 * An Activity normally provides all three. A service has none, and a missing owner is an
 * `IllegalStateException` on attach, so this class plays that role itself.
 *
 * Ordering matters: the saved-state registry must be attached and restored while the
 * lifecycle is still INITIALIZED, and the owners must be set before the view is added
 * to the window.
 *
 * A host is single-use: after [destroy] create a new one.
 */
class ComposeOverlayHost(context: Context) :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    val view: ComposeView = ComposeView(context)

    init {
        savedStateController.performAttach()
        // Nothing to restore: overlay state is rebuilt from the service, not from a Bundle.
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        // Tie the composition to this host's lifecycle rather than to window attachment,
        // so a detach/re-attach (e.g. a failed updateViewLayout) does not lose state.
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun setContent(content: @Composable () -> Unit) = view.setContent(content)

    /** Call once the view has been added to the window. */
    fun onShown() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Call after the view has been removed from the window. Disposes the composition. */
    fun destroy() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
