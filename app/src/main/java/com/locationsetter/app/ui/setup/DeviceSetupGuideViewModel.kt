package com.locationsetter.app.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locationsetter.app.util.DeveloperOptionsChecker
import com.locationsetter.app.util.MockLocationChecker
import com.locationsetter.app.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupState(
    val developerOptionsEnabled: Boolean = false,
    val mockLocationAppSelected: Boolean = false,
    val locationPermissionGranted: Boolean = false
)

class DeviceSetupGuideViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val developerOptions = DeveloperOptionsChecker.isDeveloperOptionsEnabled(context)
            val locationPermission = PermissionUtils.hasFineLocationPermission(context)
            val mockAppSelected = withContext(Dispatchers.IO) {
                MockLocationChecker.isSelectedAsMockLocationApp(context)
            }
            _state.value = SetupState(
                developerOptionsEnabled = developerOptions,
                mockLocationAppSelected = mockAppSelected,
                locationPermissionGranted = locationPermission
            )
        }
    }
}
