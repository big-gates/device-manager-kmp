package com.biggates.devicemanager.permission.location

import com.biggates.devicemanager.permission.PlatformContext
import com.biggates.devicemanager.permission.AppPermission
import com.biggates.devicemanager.permission.IosPermissionController
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.requestWithAutoRetryAndSettings
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * 공통 권한 대기 함수:
 * - status: 현재 권한 상태를 읽어 즉시 판단 가능하면 바로 종료
 * - start: 권한 요청을 실제로 시작하는 동작(사용 중일 때 또는 항상)
 * - success: 성공으로 볼 상태인지 판정
 */
private suspend fun awaitAuthorization(
    status: () -> Int,
    start: (CLLocationManager) -> Unit,
    isGranted: (Int) -> Boolean,
): PermissionState = suspendCancellableCoroutine { continuation ->
    val manager = CLLocationManager()
    var finished = false

    fun finish(state: PermissionState) {
        if (!finished) {
            finished = true
            manager.delegate = null
            continuation.resume(state)
        }
    }

    fun isDeniedOrRestricted(status: Int): Boolean = status == kCLAuthorizationStatusDenied
            || status == kCLAuthorizationStatusRestricted

    fun handle(status: Int) {
        when {
            isGranted(status) -> finish(PermissionState.Granted)
            isDeniedOrRestricted(status) -> finish(PermissionState.Denied(canAskAgain = false))
            else -> Unit // 결정되지 않음 → 계속 대기
        }
    }

    // 요청 전에 현재 상태로 즉시 판정 가능하면 바로 종료
    val current = status()
    when {
        isGranted(current) -> {
            finish(PermissionState.Granted)
            return@suspendCancellableCoroutine
        }
        isDeniedOrRestricted(current) -> {
            finish(PermissionState.Denied(canAskAgain = false))
            return@suspendCancellableCoroutine
        }
    }

    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        // 운영체제 14 이상에서 호출되는 콜백
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            handle(CLLocationManager.authorizationStatus())
        }
        // 운영체제 13 이하에서 호출되는 콜백
        override fun locationManager(
            manager: CLLocationManager,
            didChangeAuthorizationStatus: CLAuthorizationStatus
        ) {
            handle(didChangeAuthorizationStatus)
        }
    }

    manager.delegate = delegate
    start(manager)

    continuation.invokeOnCancellation { manager.delegate = null }
}

@OptIn(InternalCoroutinesApi::class)
suspend fun IosPermissionController.requestLocationWhenInUse(): PermissionState {
    return awaitAuthorization(
        status = { CLLocationManager.authorizationStatus() },
        start = { it.requestWhenInUseAuthorization() },
        isGranted = { status -> context.checkLocationWhenInUseGranted() }
    )
}

@OptIn(InternalCoroutinesApi::class)
suspend fun IosPermissionController.requestLocationAlways(): PermissionState {
    val whenInUse = awaitAuthorization(
        status = { CLLocationManager.authorizationStatus() },
        start = { it.requestWhenInUseAuthorization() },
        isGranted = { status -> context.checkLocationWhenInUseGranted() }
    )
    if (whenInUse !is PermissionState.Granted) return whenInUse

    return awaitAuthorization(
        status = { CLLocationManager.authorizationStatus() },
        start = { it.requestAlwaysAuthorization() },
        isGranted = { status -> context.checkLocationAlwaysGranted() }
    )
}

@OptIn(InternalCoroutinesApi::class)
actual suspend fun PermissionController.requestLocationAlways(): PermissionState {
    if (!CLLocationManager.locationServicesEnabled()) {
        // 설정으로 유도(앱 설정 화면) + 안내
        openAppSettings()
        return PermissionState.Denied(false, "location service disabled")
    }
    if (context.checkLocationAlwaysGranted()) return PermissionState.Granted

    // 포그라운드 먼저
    val foregroundPermission = requestLocationWhenInUse()
    if (foregroundPermission !is PermissionState.Granted) {
        // 포그라운드 거부 시 즉시 종료
        val canAskAgain = shouldShowRationale(AppPermission.LocationWhenInUse)
        return PermissionState.Denied(canAskAgain = canAskAgain)

    }

    // 백그라운드 사전 체크 (혹시 방금 사이에 허용됐을 수도)
    if (context.checkLocationAlwaysGranted()) return PermissionState.Granted

    delay(1000L)

    // 백그라운드 요청 플로우
    val backgroundGranted = requestWithAutoRetryAndSettings(arrayOf(AppPermission.LocationAlways))
    if (backgroundGranted || context.checkLocationAlwaysGranted()) {
        return PermissionState.Granted
    }

    // 백그라운드는 기기마다 Rationale 동작이 다를 수 있으므로 두 권한 모두 고려
    val canAskAgain = shouldShowRationale(AppPermission.LocationAlways) ||
            shouldShowRationale(AppPermission.LocationWhenInUse)

    return PermissionState.Denied(canAskAgain = canAskAgain)
}

actual fun PlatformContext.checkLocationWhenInUseGranted(): Boolean {
    val status = CLLocationManager.authorizationStatus()
    return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
}

actual fun PlatformContext.checkLocationAlwaysGranted(): Boolean {
    val status = CLLocationManager.authorizationStatus()
    return status == kCLAuthorizationStatusAuthorizedAlways
}