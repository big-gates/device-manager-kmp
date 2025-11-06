package com.biggates.devicemanager.permission.notification

import com.biggates.devicemanager.permission.AppPermission
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.PlatformContext
import com.biggates.devicemanager.permission.requestWithAutoRetryAndSettings

suspend fun PermissionController.requestNotifications(): PermissionState {
    if (context.checkNotificationsGranted()) return PermissionState.Granted

    val requestPermission = AppPermission.Notifications

    val granted = requestWithAutoRetryAndSettings(arrayOf(requestPermission))
    if (granted) return PermissionState.Granted

    val canAskAgain = shouldShowRationale(requestPermission)
    return PermissionState.Denied(canAskAgain = canAskAgain)
}
expect suspend fun PlatformContext.checkNotificationsGranted(): Boolean