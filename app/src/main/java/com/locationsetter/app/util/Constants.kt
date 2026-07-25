package com.locationsetter.app.util

object Constants {
    val MOCK_PROVIDER_NAMES: List<String> = listOf(
        android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER
    )
    const val PROBE_PROVIDER_NAME: String = "location_setter_probe_provider"
    const val LOCATION_UPDATE_INTERVAL_MS: Long = 1000L

    const val NOTIFICATION_CHANNEL_ID: String = "mock_location_channel"
    const val NOTIFICATION_ID: Int = 1001

    const val EXTRA_LATITUDE: String = "extra_latitude"
    const val EXTRA_LONGITUDE: String = "extra_longitude"
    const val EXTRA_LABEL: String = "extra_label"

    const val ACTION_START_MOCKING: String = "com.locationsetter.app.action.START_MOCKING"
    const val ACTION_STOP_MOCKING: String = "com.locationsetter.app.action.STOP_MOCKING"

    const val DATABASE_NAME: String = "location_setter.db"

    const val FREE_TRIAL_ACTIVATIONS: Int = 3
    const val SUBSCRIPTION_PREFS_NAME: String = "subscription_state"
    const val LEMONSQUEEZY_VALIDATE_URL: String = "https://api.lemonsqueezy.com/v1/licenses/validate"
    const val LEMONSQUEEZY_ACTIVATE_URL: String = "https://api.lemonsqueezy.com/v1/licenses/activate"
}
