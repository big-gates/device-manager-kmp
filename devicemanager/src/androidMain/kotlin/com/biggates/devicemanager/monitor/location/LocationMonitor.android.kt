package com.biggates.devicemanager.monitor.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.biggates.devicemanager.AndroidPermissionController
import com.biggates.devicemanager.Location
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
import com.biggates.devicemanager.requestWithAutoRetryAndSettings
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
        if (hasForegroundLocationPermissionGranted()) {
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
        if (hasForegroundLocationPermissionGranted()) {
            stop()
            // 재시작
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            startLocationUpdatesInternal()
        }
    }

    /**
     * 권한 요청 규칙
     * 1) 먼저 포그라운드 위치 권한(정밀 또는 대략)을 요청한다.
     * 2) 안드로이드 10(API 29) 이상에서만 백그라운드 위치 권한을 별도로 추가 요청한다.
     *    안드로이드 11(API 30) 이상에서는 시스템 정책에 따라 설정 화면으로 이동될 수 있다.
     */
    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        controller as AndroidPermissionController

        // 포그라운드 권한(정밀/대략) 먼저 요청
        val foregroundPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val foregroundGranted = requestWithAutoRetryAndSettings(
            controller = controller,
            permissions = foregroundPermissions
        )
        if (!foregroundGranted) {
            return PermissionState.Denied(canAskAgain = true)
        }

        // 안드로이드 10(API 29)+ 에서만 백그라운드 권한 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 이미 허용되어 있으면 통과
            if (hasBackgroundLocationPermissionGranted()) return PermissionState.Granted

            val backgroundGranted = requestBackgroundWithAutoFlow(controller)
            return if (backgroundGranted) PermissionState.Granted
            else PermissionState.Denied(canAskAgain = true)
        }

        // 안드로이드 9 이하: 포그라운드만 있으면 충분
        return PermissionState.Granted
    }

    /**
     * R(30)+에서는 ACCESS_BACKGROUND_LOCATION을 같은 다이얼로그에서 함께 허용시키기 어려움.
     * 정책상 설정 이동이 섞이는 경우가 흔하므로, 아래처럼 처리:
     * - 먼저 단독으로 백그라운드 권한 요청(가능한 장치에서는 바로 나옴)
     * - 여전히 미허용이면 설정 이동 → 복귀 후 재확인
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun requestBackgroundWithAutoFlow(
        controller: AndroidPermissionController
    ): Boolean {
        if (hasBackgroundLocationPermissionGranted()) return true

        // 우선 다이얼로그 요청을 시도(일부 기기에서 바로 지원될 수 있음)
        val result = controller.launchPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        val granted = result[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true || hasBackgroundLocationPermissionGranted()
        if (granted) return true

        // 그래도 불가 → 설정 이동 후 재확인
        controller.openAppSettings()
        val re = controller.recheckPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        return re[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true || hasBackgroundLocationPermissionGranted()
    }
    private fun hasForegroundLocationPermissionGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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