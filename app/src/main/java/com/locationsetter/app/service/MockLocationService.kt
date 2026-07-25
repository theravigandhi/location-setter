package com.locationsetter.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.locationsetter.app.MainActivity
import com.locationsetter.app.R
import com.locationsetter.app.util.Constants
import com.locationsetter.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that continuously feeds a chosen coordinate into Android's official
 * Mock Location / test-provider framework (LocationManager.setTestProviderLocation), once
 * per second, for as long as it is running. Requires the app to be selected as the device's
 * "mock location app" in Developer Options; if it isn't, addTestProvider/setTestProviderEnabled
 * throw SecurityException and the service stops itself and reports failure.
 */
class MockLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var publishJob: Job? = null
    private var locationManager: LocationManager? = null
    private var providersRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP_MOCKING -> {
                stopMocking()
            }
            else -> {
                val latitude = intent?.getDoubleExtra(Constants.EXTRA_LATITUDE, Double.NaN) ?: Double.NaN
                val longitude = intent?.getDoubleExtra(Constants.EXTRA_LONGITUDE, Double.NaN) ?: Double.NaN
                val label = intent?.getStringExtra(Constants.EXTRA_LABEL)
                if (latitude.isNaN() || longitude.isNaN()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startMocking(latitude, longitude, label)
            }
        }
        return START_NOT_STICKY
    }

    private fun startMocking(latitude: Double, longitude: Double, label: String?) {
        if (!PermissionUtils.hasFineLocationPermission(this)) {
            MockLocationStatusHolder.stopped()
            stopSelf()
            return
        }

        val notification = buildNotification(label, latitude, longitude)
        try {
            ServiceCompat.startForeground(
                this,
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            MockLocationStatusHolder.stopped()
            stopSelf()
            return
        }

        val success = registerTestProviders()
        if (!success) {
            // Not selected as the mock location app in Developer Options.
            MockLocationStatusHolder.stopped()
            stopForegroundCompat()
            stopSelf()
            return
        }

        publishJob?.cancel()
        publishJob = serviceScope.launch {
            while (isActive) {
                publishMockLocation(latitude, longitude)
                MockLocationStatusHolder.update(latitude, longitude, label, System.currentTimeMillis())
                delay(Constants.LOCATION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun registerTestProviders(): Boolean {
        val manager = locationManager ?: return false
        return try {
            Constants.MOCK_PROVIDER_NAMES.forEach { providerName ->
                manager.addTestProvider(
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
                manager.setTestProviderEnabled(providerName, true)
            }
            providersRegistered = true
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            // A provider with this name already exists (e.g. leftover from a previous session);
            // treat as already registered rather than failing the whole start.
            providersRegistered = true
            true
        }
    }

    private fun publishMockLocation(latitude: Double, longitude: Double) {
        val manager = locationManager ?: return
        Constants.MOCK_PROVIDER_NAMES.forEach { providerName ->
            val location = Location(providerName).apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 0.0
                accuracy = 5f
                bearing = 0f
                speed = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bearingAccuracyDegrees = 0.1f
                    verticalAccuracyMeters = 0.1f
                    speedAccuracyMetersPerSecond = 0.01f
                }
            }
            try {
                manager.setTestProviderLocation(providerName, location)
            } catch (e: SecurityException) {
                // Mock-app selection was revoked mid-session; stop cleanly.
                stopMocking()
            }
        }
    }

    private fun stopMocking() {
        publishJob?.cancel()
        publishJob = null
        removeTestProviders()
        MockLocationStatusHolder.stopped()
        stopForegroundCompat()
        stopSelf()
    }

    private fun removeTestProviders() {
        if (!providersRegistered) return
        val manager = locationManager ?: return
        Constants.MOCK_PROVIDER_NAMES.forEach { providerName ->
            try {
                manager.removeTestProvider(providerName)
            } catch (e: Exception) {
                // Already removed or never fully registered; safe to ignore.
            }
        }
        providersRegistered = false
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(label: String?, latitude: Double, longitude: Double): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val coordinateText = getString(R.string.notification_coordinates_format, latitude, longitude)
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(label?.takeIf { it.isNotBlank() } ?: getString(R.string.notification_title))
            .setContentText(coordinateText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        publishJob?.cancel()
        removeTestProviders()
        serviceScope.cancel()
        super.onDestroy()
    }
}
