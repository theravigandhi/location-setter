package com.locationsetter.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationsetter.app.data.repository.LocationRepository
import com.locationsetter.app.data.subscription.SubscriptionRepository

class MapViewModelFactory(
    private val repository: LocationRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java))
        return MapViewModel(repository, subscriptionRepository) as T
    }
}
