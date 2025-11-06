package com.biggates.devicemanager.permission

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * 권한 컨트롤러 (UI에서 런처를 연결)
 */
class AndroidPermissionController(
    override val context: PlatformContext,
    private val activityRef: WeakReference<ComponentActivity>,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val settingsLauncher: ActivityResultLauncher<Intent>
) : PermissionController {

    // 진행 중인 요청을 이어줄 콜백들 (동시 요청 보호)
    private var pendingPermission: ((Map<AppPermission, Boolean>) -> Unit)? = null
    private var pendingSettings: (() -> Unit)? = null

    override suspend fun launchPermissions(
        permissions: Array<AppPermission>
    ): Map<AppPermission, Boolean> {
        val androidStrings = permissions.toAndroid().toTypedArray()

        return if (androidStrings.isEmpty()) {
            permissions.toCurrentState(context)
        } else {
            suspendCancellableCoroutine { cont ->
                check(pendingPermission == null) { "Permission request already in progress" }
                pendingPermission = { result ->
                    if (!cont.isCompleted) cont.resume(result)
                    pendingPermission = null
                }
                permissionLauncher.launch(androidStrings)
                cont.invokeOnCancellation { pendingPermission = null }
            }
        }
    }

    override suspend fun shouldShowRationale(permission: AppPermission): Boolean {
        val activity = activityRef.get() ?: return false
        return when (permission) {
            AppPermission.LocationWhenInUse -> {
                // FINE/COARSE 중 하나라도 라쇼날이면 true
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            AppPermission.LocationAlways -> {
                // Android 11+(API 30)부터는 다이얼로그로 바로 요청 못 하고
                // 보통 설정 유도 흐름이므로 라쇼날 표시 타이밍이 애매 → false로 두고 교육 UI에서 처리
                false
            }
            AppPermission.Notifications -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // 런타임 퍼미션이 없으니 라쇼날 개념도 없음
                    false
                }
            }
        }
    }

    override suspend fun openAppSettings(): Unit = suspendCancellableCoroutine { cont ->
        check(pendingSettings == null) { "Settings flow already in progress" }
        pendingSettings = {
            if (!cont.isCompleted) cont.resume(Unit)
            pendingSettings = null
        }
        val activity = activityRef.get()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity?.packageName ?: context.packageName, null)
        )
        settingsLauncher.launch(intent)
        cont.invokeOnCancellation { pendingSettings = null }
    }

    override suspend fun recheckPermissions(
        permissions: Array<AppPermission>
    ): Map<AppPermission, Boolean> = permissions.toCurrentState(context)


    // --- 런처 콜백에서 호출될 진입점 ---
    fun onPermissionsResult(result: Map<String, Boolean>) {
        val aggregated = result.toAppPermission()
        pendingPermission?.invoke(aggregated)
        pendingPermission = null
    }

    fun onReturnedFromSettings() {
        pendingSettings?.invoke()
        pendingSettings = null
    }
}

fun createDefaultAndroidPermissionController(
    activity: ComponentActivity
): PermissionController {
    var controller: AndroidPermissionController? = null
    val context = activity.applicationContext as Application

    // 런처 먼저 만들고, 콜백에서 controller로 결과 전달
    val permLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> controller?.onPermissionsResult(result) }

    val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> controller?.onReturnedFromSettings() }

    controller = AndroidPermissionController(
        context = context,
        activityRef = WeakReference(activity),
        permissionLauncher = permLauncher,
        settingsLauncher = settingsLauncher
    )
    return controller
}