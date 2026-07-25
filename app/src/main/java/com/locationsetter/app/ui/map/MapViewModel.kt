package com.locationsetter.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationsetter.app.data.repository.LocationRepository
import com.locationsetter.app.data.subscription.SubscriptionRepository
import com.locationsetter.app.model.MockLocationStatus
import com.locationsetter.app.model.SubscriptionState
import com.locationsetter.app.service.MockLocationStatusHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SelectedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val address: String? = null
)

class MapViewModel(
    private val repository: LocationRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _selectedLocation = MutableStateFlow<SelectedLocation?>(null)
    val selectedLocation: StateFlow<SelectedLocation?> = _selectedLocation.asStateFlow()

    val mockStatus: StateFlow<MockLocationStatus> = MockLocationStatusHolder.status
    val subscriptionState: StateFlow<SubscriptionState> = subscriptionRepository.state

    fun selectLocation(latitude: Double, longitude: Double, label: String? = null) {
        _selectedLocation.value = SelectedLocation(latitude, longitude, label)
    }

    /** Only applies if the selection hasn't moved on to a different point in the meantime. */
    fun updateAddressForCurrentSelection(latitude: Double, longitude: Double, address: String) {
        val current = _selectedLocation.value ?: return
        if (current.latitude == latitude && current.longitude == longitude) {
            _selectedLocation.value = current.copy(address = address)
        }
    }

    fun saveSelectedLocation(name: String) {
        val current = _selectedLocation.value ?: return
        viewModelScope.launch {
            repository.addLocation(name, current.latitude, current.longitude)
        }
    }

    fun recordTrialActivation() {
        subscriptionRepository.recordTrialActivation()
    }
}
