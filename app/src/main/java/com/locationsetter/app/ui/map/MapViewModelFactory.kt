package com.locationsetter.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationsetter.app.data.license.LicenseRepository
import com.locationsetter.app.data.repository.LocationRepository

class MapViewModelFactory(
    private val repository: LocationRepository,
    private val licenseRepository: LicenseRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java))
        return MapViewModel(repository, licenseRepository) as T
    }
}
