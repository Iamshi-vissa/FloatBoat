package com.floatboat.app.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlacementSettingsViewModel @Inject constructor(
    private val repository: PlacementSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<PlacementSettings> = repository.settings

    fun setOffsetX(dp: Float) = repository.update { it.copy(offsetXDp = dp) }

    fun setOffsetY(dp: Float) = repository.update { it.copy(offsetYDp = dp) }

    fun setSizeScale(scale: Float) = repository.update { it.copy(sizeScale = scale) }

    fun reset() = repository.update { PlacementSettings.Default }
}
