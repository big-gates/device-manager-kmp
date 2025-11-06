package com.biggates.devicemanager.permission.location

import com.biggates.devicemanager.permission.AppPermission
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.PlatformContext
import com.biggates.devicemanager.permission.requestWithAutoRetryAndSettings

suspend fun PermissionController.requestLocationWhenInUse(): PermissionState {
    if (context.checkLocationWhenInUseGranted()) return PermissionState.Granted

    val requestPermission = AppPermission.LocationWhenInUse

    val granted = requestWithAutoRetryAndSettings(arrayOf(requestPermission))
    if (granted) return PermissionState.Granted

    val canAskAgain = shouldShowRationale(AppPermission.LocationWhenInUse)
    return PermissionState.Denied(canAskAgain = canAskAgain)
}
expect suspend fun PermissionController.requestLocationAlways(): PermissionState

expect fun PlatformContext.checkLocationWhenInUseGranted(): Boolean

expect fun PlatformContext.checkLocationAlwaysGranted(): Boolean