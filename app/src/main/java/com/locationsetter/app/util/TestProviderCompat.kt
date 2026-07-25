package com.locationsetter.app.util

import android.location.Criteria
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build

/**
 * android.location.provider.ProviderProperties (with its Builder) is a platform class that only
 * exists starting Android 12 (API 31) — it's not an AndroidX compat shim, so calling it on an
 * older device throws NoClassDefFoundError. Below API 31, LocationManager only exposes the older
 * addTestProvider(String, boolean..., int, int) overload using Criteria power/accuracy constants.
 */
object TestProviderCompat {

    fun addTestProvider(locationManager: LocationManager, providerName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.addTestProvider(
                providerName,
                ProviderProperties.Builder()
                    .setHasSatelliteRequirement(false)
                    .setHasCellRequirement(false)
                    .setHasNetworkRequirement(false)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                providerName,
                false, false, false, false,
                false, true, true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
        }
    }
}
