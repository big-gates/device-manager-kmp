package com.biggates.devicemanager.permission

import android.Manifest
import android.os.Build
import com.biggates.devicemanager.permission.location.checkLocationAlwaysGranted
import com.biggates.devicemanager.permission.location.checkLocationWhenInUseGranted
import com.biggates.devicemanager.permission.notification.checkNotificationsGranted

fun Array<AppPermission>.toAndroid(): List<String> {
    val out = mutableSetOf<String>()
    forEach { p ->
        when (p) {
            AppPermission.LocationWhenInUse -> {
                out += Manifest.permission.ACCESS_FINE_LOCATION
                out += Manifest.permission.ACCESS_COARSE_LOCATION
            }
            AppPermission.LocationAlways -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    out += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }
                // 28 이하는 런타임 퍼미션 없음
            }
            AppPermission.Notifications -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    out += Manifest.permission.POST_NOTIFICATIONS
                }
                // 32 이하는 런타임 퍼미션 없음
            }
        }
    }
    return out.toList()
}

fun Map<String, Boolean>.toAppPermission(): Map<AppPermission, Boolean> {
    return buildMap {
        put(AppPermission.LocationWhenInUse, locationWhenInUseFromResult())
        put(AppPermission.LocationAlways, locationAlwaysFromResult())
        put(AppPermission.Notifications, notificationsFromResult())
    }
}

// WhenInUse(정밀 또는 대략) 판정
fun Map<String, Boolean>.locationWhenInUseFromResult(): Boolean {
    return (this[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (this[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
}

// Always(백그라운드 위치) 판정
fun Map<String, Boolean>.locationAlwaysFromResult(): Boolean {
    return if (Build.VERSION.SDK_INT >= 29) {
        this[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
    } else {
        // 안드로이드 28 이하에서는 런타임 권한 개념이 없으므로 승인으로 간주
        true
    }
}

// 알림 권한 판정
fun Map<String, Boolean>.notificationsFromResult(): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        this[Manifest.permission.POST_NOTIFICATIONS] == true
    } else {
        // 안드로이드 32 이하에서는 런타임 알림 권한이 없으므로
        // 이 집계기에서는 판단하지 않음(필요 시 정책에 맞게 true 처리도 가능)
        false
    }
}

suspend fun Array<AppPermission>.toCurrentState(context: PlatformContext): Map<AppPermission, Boolean> {
    val map = mutableMapOf<AppPermission, Boolean>()
    forEach { permission ->
        when (permission) {
            AppPermission.LocationWhenInUse -> map[permission] = context.checkLocationWhenInUseGranted()
            AppPermission.LocationAlways -> map[permission] = context.checkLocationAlwaysGranted()
            AppPermission.Notifications -> map[permission] = context.checkNotificationsGranted()
        }
    }
    return map
}