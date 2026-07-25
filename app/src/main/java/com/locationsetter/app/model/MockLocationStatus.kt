package com.locationsetter.app.model

data class MockLocationStatus(
    val isRunning: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val label: String? = null,
    val lastUpdateMillis: Long = 0L
)
