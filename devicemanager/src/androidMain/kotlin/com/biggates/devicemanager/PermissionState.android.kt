package com.biggates.devicemanager

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 권한 컨트롤러 (UI에서 런처를 연결)
 */
class AndroidPermissionController(
    val context: PlatformContext,
    val launchPermissions: suspend (Array<String>) -> Map<String, Boolean>,
    val shouldShowRationale: (String) -> Boolean,
    val openAppSettings: suspend () -> Unit,
    val recheckPermissions: suspend (Array<String>) -> Map<String, Boolean>
) : PermissionController

fun createDefaultAndroidPermissionController(
    activity: ComponentActivity
): AndroidPermissionController {
    var contRef: ((Map<String, Boolean>) -> Unit)? = null

    val permLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> contRef?.invoke(result).also { contRef = null } }

    val launchPermissions: suspend (Array<String>) -> Map<String, Boolean> = { perms ->
        suspendCancellableCoroutine { cont ->
            require(contRef == null) { "Permission request already in progress" }
            contRef = { res -> cont.resume(res) { cause, _, _ -> } }
            permLauncher.launch(perms)
            cont.invokeOnCancellation { contRef = null }
        }
    }

    var settingsCont: (() -> Unit)? = null
    val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { settingsCont?.invoke().also { settingsCont = null } }

    val openSettings: suspend () -> Unit = {
        suspendCancellableCoroutine { cont ->
            settingsCont = { cont.resume(Unit) { cause, _, _ -> } }
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
            settingsLauncher.launch(intent)
            cont.invokeOnCancellation { settingsCont = null }
        }
    }

    return AndroidPermissionController(
        context = activity.applicationContext as Application,
        launchPermissions = launchPermissions,
        shouldShowRationale = { perm -> activity.shouldShowRequestPermissionRationale(perm) },
        openAppSettings = openSettings,
        recheckPermissions = launchPermissions
    )
}

/**
 * 사용자가 아무것도 신경 쓰지 않아도 되도록:
 * - 1차 요청
 * - 거부되었고 '설명 필요' 상태면, 자동으로 한 번 더 재요청
 * - 여전히 거부이면서 '다시는 묻지 않기(영구 거부)'면, 설정 화면 열고 복귀 후 재확인
 */
suspend fun requestWithAutoRetryAndSettings(
    controller: AndroidPermissionController,
    permissions: Array<String>
): Boolean {
    var result = controller.launchPermissions(permissions)

    fun isGrantedAll() = permissions.all { p -> result[p] == true }
    if (isGrantedAll()) return true

    // 아직 거부된 항목들
    fun stillDenied(): List<String> = permissions.filter { p -> result[p] != true }
    var denied = stillDenied()
    if (denied.isEmpty()) return true

    // 설명 필요(라쇼날)인 항목이 하나라도 있으면, "한 번 더" 자동 재요청
    val needsRationale = denied.any { p -> controller.shouldShowRationale(p) }
    if (needsRationale) {
        result = controller.recheckPermissions(permissions)
        if (isGrantedAll()) return true
        denied = stillDenied()
        if (denied.isEmpty()) return true
    }

    // 여기까지 왔는데도 거부라면 대개 '다시는 묻지 않기' 상태
    // 설정으로 보내고, 돌아오면 다시 한 번 확인
    controller.openAppSettings()
    result = controller.recheckPermissions(permissions)
    return isGrantedAll()
}