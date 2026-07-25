package com.locationsetter.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationsetter.app.data.repository.LocationRepository
import com.locationsetter.app.data.room.LocationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedLocationsViewModel(private val repository: LocationRepository) : ViewModel() {

    val locations: StateFlow<List<LocationEntity>> = repository.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rename(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameLocation(id, newName) }
    }

    fun delete(location: LocationEntity) {
        viewModelScope.launch { repository.deleteLocation(location) }
    }
}
