package com.biggates.devicemanager.device.monitor.location

import android.annotation.SuppressLint
import android.os.Looper
import com.biggates.devicemanager.device.Location
import com.biggates.devicemanager.device.PlatformContext
import com.biggates.devicemanager.permission.location.checkLocationWhenInUseGranted
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.location.Location as AndroidLocation

class AndroidLocationMonitor(
    private val context: PlatformContext
) : LocationMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Location?>(null)
    override val state: StateFlow<Location?>
        get() = _state.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var liveTrackingEnabled: Boolean = false

    override suspend fun start() {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (context.checkLocationWhenInUseGranted()) {
            startLocationUpdatesInternal()
        }
    }

    override fun stop() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        scope.cancel()
    }

    override suspend fun enableLiveTracking(enable: Boolean) {
        liveTrackingEnabled = enable
        if (context.checkLocationWhenInUseGranted()) {
            stop()
            // 재시작
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            startLocationUpdatesInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesInternal() {
        val spec = currentSpec(liveTrackingEnabled)

        val request = LocationRequest.Builder(spec.intervalMillis)
            .setPriority(if (spec.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(spec.minUpdateIntervalMillis)
            .apply {
                spec.minUpdateDistanceMeters?.let {
                    try {
                        setMinUpdateDistanceMeters(it)
                    } catch (_: Throwable) {}
                }
            }
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: AndroidLocation = result.lastLocation ?: return
                _state.update {
                    Location(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                        speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else null,
                        bearingDegrees = if (location.hasBearing())
                            ((location.bearing.toDouble() % 360 + 360) % 360) else null
                    )
                }
            }
        }
        fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun currentSpec(live: Boolean) = LocationRequestSpec(
        intervalMillis = if (live) 5_000L else 60_000L,
        minUpdateIntervalMillis = if (live) 3_000L else 30_000L,
        minUpdateDistanceMeters = if (live) 20f else 100f,
        highAccuracy = live
    )

    data class LocationRequestSpec(
        val intervalMillis: Long,
        val minUpdateIntervalMillis: Long,
        val minUpdateDistanceMeters: Float?,
        val highAccuracy: Boolean
    )
}