package com.locationsetter.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationsetter.app.data.repository.LocationRepository

class SavedLocationsViewModelFactory(private val repository: LocationRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SavedLocationsViewModel::class.java))
        return SavedLocationsViewModel(repository) as T
    }
}
