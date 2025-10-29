package com.biggates.devicemanager.monitor.location

import com.biggates.devicemanager.Location
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.darwin.NSObject

class IosLocationMonitor : LocationMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Location?>(null)
    override val state: StateFlow<Location?> get() = _state.asStateFlow()

    private var cLLocationManager: CLLocationManager? = null
    private var delegate: LocationDelegate? = null
    private var liveTrackingEnabled = false

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun start() {
        val manager = cLLocationManager ?: CLLocationManager().also { cLLocationManager = it }

        val locationDelegate = LocationDelegate(
            onUpdate = { cl ->
                _state.update {
                    val (lat, lon) = cl.coordinate().useContents { latitude to longitude }
                    Location(
                        latitude = lat,
                        longitude = lon,
                        horizontalAccuracyMeters = cl.horizontalAccuracy.takeIf { it >= 0 },
                        speedMetersPerSecond = cl.speed.takeIf { it >= 0 },
                        bearingDegrees = cl.course.takeIf { it >= 0 }?.let { (it % 360.0 + 360.0) % 360.0 }
                    )
                }
            },
            onAuthChanged = {
                startInternal()
            }
        )
        delegate = locationDelegate
        manager.delegate = locationDelegate

        // 기본 설정
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 10.0
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = CLActivityTypeOther

        // 권한 체크 (iOS 14+는 인스턴스 프로퍼티 권장)
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse) {
            startInternal()
        }
    }

    override fun stop() {
        cLLocationManager?.stopUpdatingLocation()
        cLLocationManager?.stopMonitoringSignificantLocationChanges()
        cLLocationManager?.delegate = null
        delegate = null
        cLLocationManager = null
        scope.cancel()
    }

    override suspend fun enableLiveTracking(enable: Boolean) {
        liveTrackingEnabled = enable
        cLLocationManager?.let {
            it.stopUpdatingLocation()
            it.stopMonitoringSignificantLocationChanges()
            startInternal()
        }
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        val manager = cLLocationManager ?: CLLocationManager().also { cLLocationManager = it }
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse) {
            return PermissionState.Granted
        }
        manager.requestWhenInUseAuthorization() // Always가 필요하면 별도 분기에서 requestAlwaysAuthorization()
        return PermissionState.NotDetermined
    }

    private fun startInternal() {
        val manager = cLLocationManager ?: return
        if (liveTrackingEnabled) manager.startUpdatingLocation()
        else manager.startMonitoringSignificantLocationChanges()
    }
}

private class LocationDelegate(
    private val onUpdate: (CLLocation) -> Unit,
    private val onAuthChanged: () -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.let(onUpdate)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthChanged()
    }
}