package com.biggates.devicemanager.permission.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.biggates.devicemanager.permission.PlatformContext
import com.biggates.devicemanager.permission.AppPermission
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.requestWithAutoRetryAndSettings

actual suspend fun PermissionController.requestLocationAlways(): PermissionState {
    // API 29 미만: 별도 권한 없음
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionState.Granted

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
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

actual fun PlatformContext.checkLocationAlwaysGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}