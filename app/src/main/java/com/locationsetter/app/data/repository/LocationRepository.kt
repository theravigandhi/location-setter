package com.locationsetter.app.data.repository

import com.locationsetter.app.data.room.LocationDao
import com.locationsetter.app.data.room.LocationEntity
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val dao: LocationDao) {

    fun getAllLocations(): Flow<List<LocationEntity>> = dao.getAll()

    suspend fun addLocation(name: String, latitude: Double, longitude: Double): Long =
        dao.insert(LocationEntity(name = name, latitude = latitude, longitude = longitude))

    suspend fun renameLocation(id: Long, newName: String) {
        val location = dao.getById(id) ?: return
        dao.update(location.copy(name = newName))
    }

    suspend fun deleteLocation(location: LocationEntity) {
        dao.delete(location)
    }
}
