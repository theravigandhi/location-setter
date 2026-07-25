package com.locationsetter.app.service

import com.locationsetter.app.model.MockLocationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide status bridge between [MockLocationService] and the UI layer.
 * Safe because the service and all UI run in a single process (no android:process=":remote").
 */
object MockLocationStatusHolder {

    private val _status = MutableStateFlow(MockLocationStatus())
    val status: StateFlow<MockLocationStatus> = _status.asStateFlow()

    fun update(latitude: Double, longitude: Double, label: String?, lastUpdateMillis: Long) {
        _status.value = MockLocationStatus(
            isRunning = true,
            latitude = latitude,
            longitude = longitude,
            label = label,
            lastUpdateMillis = lastUpdateMillis
        )
    }

    fun stopped() {
        _status.value = _status.value.copy(isRunning = false)
    }
}
