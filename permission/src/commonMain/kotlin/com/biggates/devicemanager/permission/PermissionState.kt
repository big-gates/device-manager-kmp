package com.biggates.devicemanager.permission

enum class AppPermission {
    /**
     * iOS/Android 공통: 포그라운드 위치
     */
    LocationWhenInUse,

    /**
     * iOS/Android: 백그라운드 위치 (Android Q+ 별도)
     */
    LocationAlways,

    /**
     * 알림
     */
    Notifications
}

interface PermissionController{
    val context: PlatformContext
    suspend fun launchPermissions(permissions: Array<AppPermission>): Map<AppPermission, Boolean>
    suspend fun shouldShowRationale(permission: AppPermission): Boolean
    suspend fun openAppSettings()
    suspend fun recheckPermissions(permissions: Array<AppPermission>): Map<AppPermission, Boolean>
}

sealed class PermissionState {
    data object Granted : PermissionState()
    data class Denied(val canAskAgain: Boolean, val reason: String? = null) : PermissionState()
    data object NotDetermined : PermissionState()
}

/**
 * 사용자가 아무것도 신경 쓰지 않아도 되도록:
 * - 1차 요청
 * - 거부되었고 '설명 필요' 상태면, 자동으로 한 번 더 재요청
 * - 여전히 거부이면서 '다시는 묻지 않기(영구 거부)'면, 설정 화면 열고 복귀 후 재확인
 */
suspend fun PermissionController.requestWithAutoRetryAndSettings(
    permissions: Array<AppPermission>
): Boolean {
    var result = launchPermissions(permissions)

    fun isGrantedAll() = permissions.all { p -> result[p] == true }
    if (isGrantedAll()) return true

    // 아직 거부된 항목들
    fun stillDenied(): List<AppPermission> = permissions.filter { p -> result[p] != true }
    var denied = stillDenied()
    if (denied.isEmpty()) return true

    // 설명 필요(라쇼날)인 항목이 하나라도 있으면, "한 번 더" 자동 재요청
    val needsRationale = denied.any { p -> shouldShowRationale(p) }
    if (needsRationale) {
        result = launchPermissions(permissions)
        if (isGrantedAll()) return true
        denied = stillDenied()
        if (denied.isEmpty()) return true
    }

    // 여기까지 왔는데도 거부라면 대개 '다시는 묻지 않기' 상태
    // 설정으로 보내고, 돌아오면 다시 한 번 확인
    openAppSettings()
    result = recheckPermissions(permissions)
    return isGrantedAll()
}

