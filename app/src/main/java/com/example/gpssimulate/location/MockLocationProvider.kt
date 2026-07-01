package com.example.gpssimulate.location

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

/**
 * 通过系统 Test Provider 注入模拟坐标。
 * 同时覆盖 GPS 与 NETWORK 两种来源，并周期性推送更新，
 * 与市面上多数「模拟 GPS」类 App 的做法一致。
 */
class MockLocationProvider(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var isActive = false
    private var lastLatitude = 0.0
    private var lastLongitude = 0.0

    fun startMocking(latitude: Double, longitude: Double) {
        isActive = true
        lastLatitude = latitude
        lastLongitude = longitude
        setupProvider(
            provider = LocationManager.GPS_PROVIDER,
            requiresNetwork = false,
            requiresCell = false,
            accuracy = Criteria.ACCURACY_FINE,
        )
        setupProvider(
            provider = LocationManager.NETWORK_PROVIDER,
            requiresNetwork = true,
            requiresCell = true,
            accuracy = Criteria.ACCURACY_COARSE,
        )
        pushLocation(latitude, longitude)
    }

    fun setLocation(latitude: Double, longitude: Double) {
        if (!isActive) return
        lastLatitude = latitude
        lastLongitude = longitude
        pushLocation(latitude, longitude)
    }

    /** 周期性调用，让依赖位置监听的 App 持续收到更新 */
    fun tick() {
        if (!isActive) return
        pushLocation(lastLatitude, lastLongitude)
    }

    fun stopMocking() {
        if (!isActive) return
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
        isActive = false
    }

    private fun setupProvider(
        provider: String,
        requiresNetwork: Boolean,
        requiresCell: Boolean,
        accuracy: Int,
    ) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {
        }

        locationManager.addTestProvider(
            provider,
            requiresNetwork,
            false,
            requiresCell,
            false,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            accuracy,
        )
        locationManager.setTestProviderEnabled(provider, true)
    }

    private fun pushLocation(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()

        pushToProvider(
            provider = LocationManager.GPS_PROVIDER,
            latitude = latitude,
            longitude = longitude,
            accuracy = 1f,
            time = now,
            elapsed = elapsed,
        )
        pushToProvider(
            provider = LocationManager.NETWORK_PROVIDER,
            latitude = latitude,
            longitude = longitude,
            accuracy = 10f,
            time = now,
            elapsed = elapsed,
        )
    }

    private fun pushToProvider(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        time: Long,
        elapsed: Long,
    ) {
        try {
            val location = Location(provider).apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 10.0
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
                    putInt("satellites", 12)
                }
            }
            locationManager.setTestProviderLocation(provider, location)
        } catch (e: SecurityException) {
            Log.e(TAG, "push location failed for $provider", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "push location failed for $provider", e)
        }
    }

    companion object {
        private const val TAG = "MockLocationProvider"
    }
}
