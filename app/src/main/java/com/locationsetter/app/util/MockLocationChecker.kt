package com.locationsetter.app.util

import android.content.Context
import android.location.LocationManager

object MockLocationChecker {

    /**
     * There is no public getter for "is this app selected as the mock location app" in
     * Developer Options. The only reliable signal is to attempt the real operation and treat
     * a SecurityException as "not selected". A dedicated probe provider name is used (distinct
     * from Constants.MOCK_PROVIDER_NAME) so this check never collides with a mock session the
     * MockLocationService may currently have active.
     */
    fun isSelectedAsMockLocationApp(context: Context): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            TestProviderCompat.addTestProvider(locationManager, Constants.PROBE_PROVIDER_NAME)
            locationManager.setTestProviderEnabled(Constants.PROBE_PROVIDER_NAME, true)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            try {
                locationManager.removeTestProvider(Constants.PROBE_PROVIDER_NAME)
            } catch (_: Exception) {
                // Provider was never successfully added; nothing to clean up.
            }
        }
    }
}
