package com.example.gpssimulate.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(
        context: Context,
        forceFreshLocation: Boolean = false,
        onSuccess: (latitude: Double, longitude: Double) -> Unit,
        onFailure: (message: String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onFailure("未授予位置权限")
            return
        }
        if (!isLocationServiceEnabled(context)) {
            onFailure("请在系统设置中开启定位服务（GPS 或网络定位）")
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        var delivered = false
        val mainHandler = Handler(Looper.getMainLooper())
        var timeoutRunnable: Runnable? = null

        fun deliverSuccess(latitude: Double, longitude: Double) {
            if (delivered) return
            delivered = true
            timeoutRunnable?.let(mainHandler::removeCallbacks)
            onSuccess(latitude, longitude)
        }

        fun deliverFailure(message: String) {
            if (delivered) return
            delivered = true
            timeoutRunnable?.let(mainHandler::removeCallbacks)
            onFailure(message)
        }

        timeoutRunnable = Runnable {
            deliverFailure("定位超时，请确保已开启 GPS 并到开阔地带重试")
        }.also { runnable ->
            mainHandler.postDelayed(runnable, LOCATION_TIMEOUT_MS)
        }

        if (forceFreshLocation) {
            requestFreshLocation(
                context = context,
                fusedClient = fusedClient,
                onSuccess = ::deliverSuccess,
                onFailure = ::deliverFailure,
            )
            return
        }

        fusedClient.lastLocation
            .addOnSuccessListener { lastLocation ->
                if (lastLocation != null) {
                    deliverSuccess(lastLocation.latitude, lastLocation.longitude)
                    return@addOnSuccessListener
                }
                requestFreshLocation(
                    context = context,
                    fusedClient = fusedClient,
                    onSuccess = ::deliverSuccess,
                    onFailure = ::deliverFailure,
                )
            }
            .addOnFailureListener {
                requestFreshLocation(
                    context = context,
                    fusedClient = fusedClient,
                    onSuccess = ::deliverSuccess,
                    onFailure = ::deliverFailure,
                )
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(
        context: Context,
        fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
        onSuccess: (Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cancellationToken = CancellationTokenSource()
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                onSuccess(location.latitude, location.longitude)
            } else {
                requestLocationUpdates(context, fusedClient, onSuccess, onFailure)
            }
        }.addOnFailureListener {
            requestLocationUpdates(context, fusedClient, onSuccess, onFailure)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(
        context: Context,
        fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
        onSuccess: (Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .setDurationMillis(15_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedClient.removeLocationUpdates(this)
                val location = result.lastLocation
                if (location != null) {
                    onSuccess(location.latitude, location.longitude)
                } else {
                    onFailure("无法获取当前位置，请到室外开阔处重试，或先停止 GPS 模拟")
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
                fusedClient.removeLocationUpdates(callback)
                onFailure("定位失败，请确认已开启 GPS 且未在模拟定位中")
            }
    }

    private const val LOCATION_TIMEOUT_MS = 3_000L
}
