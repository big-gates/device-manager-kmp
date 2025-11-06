package com.biggates.devicemanager.permission

import com.biggates.devicemanager.permission.location.checkLocationAlwaysGranted
import com.biggates.devicemanager.permission.location.checkLocationWhenInUseGranted
import com.biggates.devicemanager.permission.location.requestLocationAlways
import com.biggates.devicemanager.permission.location.requestLocationWhenInUse
import com.biggates.devicemanager.permission.notification.checkNotificationsGranted
import com.biggates.devicemanager.permission.notification.requestNotifications
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

class IosPermissionController(
    override val context: PlatformContext,
) : PermissionController {

    override suspend fun launchPermissions(
        permissions: Array<AppPermission>
    ): Map<AppPermission, Boolean> {
        val out = mutableMapOf<AppPermission, Boolean>()
        for (permission in permissions) {
            val granted = when (permission) {
                AppPermission.LocationWhenInUse -> { requestLocationWhenInUse() == PermissionState.Granted }
                AppPermission.LocationAlways -> { requestLocationAlways() == PermissionState.Granted }
                AppPermission.Notifications -> { requestNotifications() == PermissionState.Granted }
            }
            out[permission] = granted
        }
        return out
    }

    override suspend fun shouldShowRationale(permission: AppPermission): Boolean {
        // iOS에는 공식 라쇼날 신호가 없으므로 상태로 에뮬레이트
        return when (permission) {
            AppPermission.LocationWhenInUse -> {
                // 아직 결정 전일 때만 라쇼날 true
                val s = CLLocationManager.authorizationStatus()
                s != kCLAuthorizationStatusDenied &&
                        s != kCLAuthorizationStatusAuthorizedWhenInUse &&
                        s != kCLAuthorizationStatusAuthorizedAlways
            }
            AppPermission.LocationAlways -> {
                // WhenInUse가 이미 있으면 승격 요청 안내 가능
                CLLocationManager.authorizationStatus() == kCLAuthorizationStatusAuthorizedWhenInUse
            }
            AppPermission.Notifications -> {
                isNotificationsNotDetermined()
            }
        }
    }

    private suspend fun isNotificationsNotDetermined(): Boolean {
        val settings = suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { s -> cont.resume(s) {} }
        }
        return when (settings?.authorizationStatus) {
            // Authorized/Provisional/Ephemeral/Denied → 라쇼날 아님
            UNAuthorizationStatusAuthorized,
            UNAuthorizationStatusProvisional,
            UNAuthorizationStatusEphemeral,
            UNAuthorizationStatusDenied -> false
            else -> true
        }
    }

    override suspend fun openAppSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(
            url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = { _ -> }
        )
    }

    override suspend fun recheckPermissions(
        permissions: Array<AppPermission>
    ): Map<AppPermission, Boolean> {
        // 재확인은 "요청 없이 현재 상태를 조회"하는 의미로 구현
        val out = mutableMapOf<AppPermission, Boolean>()
        permissions.forEach { p ->
            val granted = when (p) {
                AppPermission.LocationWhenInUse -> context.checkLocationWhenInUseGranted()
                AppPermission.LocationAlways -> context.checkLocationAlwaysGranted()
                AppPermission.Notifications -> context.checkNotificationsGranted()
            }
            out[p] = granted
        }
        return out
    }
}

fun createDefaultIosPermissionController(): PermissionController {
    return IosPermissionController(context = Unit)
}