package com.example.gpssimulate.location

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices

/**
 * 通过系统 Test Provider + Google 融合定位 Mock 注入模拟坐标。
 * 多数第三方 App 使用 FusedLocationProvider，仅 mock GPS/NETWORK 往往无效。
 */
class MockLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isActive = false
    private var fusedMockReady = false
    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var pushSequence = 0L
    private val activeProviders = mutableListOf<ProviderConfig>()

    fun startMocking(latitude: Double, longitude: Double) {
        isActive = true
        fusedMockReady = false
        pushSequence = 0L
        lastLatitude = latitude
        lastLongitude = longitude

        activeProviders.clear()
        activeProviders += ProviderConfig(
            name = LocationManager.GPS_PROVIDER,
            requiresNetwork = false,
            requiresSatellite = true,
            requiresCell = false,
            criteriaAccuracy = Criteria.ACCURACY_FINE,
            locationAccuracy = 3f,
        )
        activeProviders += ProviderConfig(
            name = LocationManager.NETWORK_PROVIDER,
            requiresNetwork = false,
            requiresSatellite = false,
            requiresCell = false,
            criteriaAccuracy = Criteria.ACCURACY_COARSE,
            locationAccuracy = 20f,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeProviders += ProviderConfig(
                name = LocationManager.FUSED_PROVIDER,
                requiresNetwork = false,
                requiresSatellite = false,
                requiresCell = false,
                criteriaAccuracy = Criteria.ACCURACY_FINE,
                locationAccuracy = 3f,
            )
        }

        activeProviders.forEach { config ->
            setupProvider(config)
        }

        fusedClient.setMockMode(true)
            .addOnSuccessListener {
                fusedMockReady = true
                Log.d(TAG, "Fused mock mode enabled")
                pushAllLocations(latitude, longitude)
                burstPush()
            }
            .addOnFailureListener { error ->
                fusedMockReady = false
                Log.w(TAG, "Fused mock mode unavailable, using LocationManager only", error)
                pushAllLocations(latitude, longitude)
                burstPush()
            }
    }

    fun setLocation(latitude: Double, longitude: Double) {
        if (!isActive) return
        lastLatitude = latitude
        lastLongitude = longitude
        pushAllLocations(latitude, longitude)
    }

    fun tick() {
        if (!isActive) return
        pushAllLocations(lastLatitude, lastLongitude)
    }

    fun stopMocking() {
        if (!isActive) return
        isActive = false
        fusedMockReady = false
        mainHandler.removeCallbacksAndMessages(null)

        fusedClient.setMockMode(false)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to disable fused mock mode", error)
            }

        activeProviders.toList().forEach { config ->
            teardownProvider(config.name)
        }
        activeProviders.clear()
    }

    private fun burstPush() {
        repeat(BURST_COUNT) { index ->
            mainHandler.postDelayed({
                if (isActive) {
                    pushAllLocations(lastLatitude, lastLongitude)
                }
            }, (index + 1) * BURST_DELAY_MS)
        }
    }

    private fun pushAllLocations(latitude: Double, longitude: Double) {
        val (time, elapsed) = nextTimestamps()
        activeProviders.forEach { config ->
            pushToTestProvider(
                provider = config.name,
                latitude = latitude,
                longitude = longitude,
                accuracy = config.locationAccuracy,
                time = time,
                elapsed = elapsed,
            )
        }
        pushToFused(latitude, longitude, time, elapsed)
    }

    private fun nextTimestamps(): Pair<Long, Long> {
        pushSequence += 1
        return System.currentTimeMillis() to
            SystemClock.elapsedRealtimeNanos() + pushSequence
    }

    private fun setupProvider(config: ProviderConfig) {
        val provider = config.name
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {
        }

        try {
            locationManager.addTestProvider(
                provider,
                config.requiresNetwork,
                config.requiresSatellite,
                config.requiresCell,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                config.criteriaAccuracy,
            )
            locationManager.setTestProviderEnabled(provider, true)
            locationManager.setTestProviderStatus(
                provider,
                LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis(),
            )
        } catch (error: Exception) {
            Log.e(TAG, "setup provider failed: $provider", error)
            activeProviders.removeAll { it.name == provider }
        }
    }

    private fun teardownProvider(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {
        }
    }

    private fun pushToTestProvider(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        time: Long,
        elapsed: Long,
    ) {
        try {
            val location = buildLocation(
                provider = provider,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                time = time,
                elapsed = elapsed,
            )
            LocationSanitizer.stripMockFlags(location)
            locationManager.setTestProviderLocation(provider, location)
        } catch (error: SecurityException) {
            Log.e(TAG, "push location failed for $provider", error)
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "push location failed for $provider", error)
        }
    }

    private fun pushToFused(
        latitude: Double,
        longitude: Double,
        time: Long,
        elapsed: Long,
    ) {
        if (!fusedMockReady) return
        val location = buildLocation(
            provider = FUSED_PROVIDER_NAME,
            latitude = latitude,
            longitude = longitude,
            accuracy = 3f,
            time = time,
            elapsed = elapsed,
        )
        LocationSanitizer.stripMockFlags(location)
        fusedClient.setMockLocation(location)
            .addOnFailureListener { error ->
                Log.w(TAG, "setMockLocation failed", error)
            }
    }

    private fun buildLocation(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        time: Long,
        elapsed: Long,
    ): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 35.0
            this.accuracy = accuracy
            bearing = 0f
            speed = 0f
            this.time = time
            elapsedRealtimeNanos = elapsed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.1f
                verticalAccuracyMeters = 1f
                speedAccuracyMetersPerSecond = 0.01f
            }
            extras = Bundle().apply {
                putInt("satellites", 8)
            }
        }
    }

    private data class ProviderConfig(
        val name: String,
        val requiresNetwork: Boolean,
        val requiresSatellite: Boolean,
        val requiresCell: Boolean,
        val criteriaAccuracy: Int,
        val locationAccuracy: Float,
    )

    companion object {
        private const val TAG = "MockLocationProvider"
        private const val FUSED_PROVIDER_NAME = "fused"
        private const val BURST_COUNT = 5
        private const val BURST_DELAY_MS = 80L
    }
}
