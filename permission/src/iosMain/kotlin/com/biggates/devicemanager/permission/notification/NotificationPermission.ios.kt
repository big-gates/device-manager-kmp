package com.biggates.devicemanager.permission.notification

import com.biggates.devicemanager.permission.IosPermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.PlatformContext
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

suspend fun IosPermissionController.requestNotifications(): PermissionState {
    if (context.checkNotificationsGranted()) return PermissionState.Granted

    val center = UNUserNotificationCenter.currentNotificationCenter()
    val granted = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ -> cont.resume(granted) }
    }
    return if(granted) PermissionState.Granted else PermissionState.Denied(false)
}

actual suspend fun PlatformContext.checkNotificationsGranted(): Boolean {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val settings = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { s -> cont.resume(s) }
    }
    return when (settings?.authorizationStatus) {
        UNAuthorizationStatusAuthorized,

        // 조용한 알림 허용
        UNAuthorizationStatusProvisional,

        // 일시적 허용(웹앱 등)
        UNAuthorizationStatusEphemeral -> true

        else -> false
    }
}