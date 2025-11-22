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
) : PermissionController {

    override suspend fun launchPermissions(
        permissions: List<AppPermission>
    ): Map<AppPermission, Boolean> {
        val activity = activityRef.get() ?: return permissions.toCurrentState(context)
        val androidStrings = permissions.toAndroid().toTypedArray()

        if (androidStrings.isEmpty()) {
            return permissions.toCurrentState(context)
        }

        return suspendCancellableCoroutine { cont ->
            val registry = activity.activityResultRegistry
            val key = "perm-multi-${hashCode()}-${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = registry.register(
                key,
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                if (!cont.isCompleted) {
                    cont.resume(result.toAppPermission())
                }
                launcher?.unregister()
            }

            launcher.launch(androidStrings)

            cont.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }

    override suspend fun launchPermission(permission: AppPermission): Boolean {
        val activity = activityRef.get() ?: return permission.toCurrentState(context)
        val androidStrings = permission.toAndroid().toTypedArray()

        if (androidStrings.isEmpty()) {
            return permission.toCurrentState(context)
        }

        return suspendCancellableCoroutine { cont ->
            val registry = activity.activityResultRegistry
            val key = "perm-single-${hashCode()}-${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = registry.register(
                key,
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val granted = result.toAppPermission()[permission] ?: false
                if (!cont.isCompleted) {
                    cont.resume(granted)
                }
                launcher?.unregister()
            }

            launcher.launch(androidStrings)

            cont.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }

    override suspend fun shouldShowRationale(permission: AppPermission): Boolean {
        val activity = activityRef.get() ?: return false
        return when (permission) {
            AppPermission.LocationWhenInUse -> {
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            AppPermission.LocationAlways -> false
            AppPermission.Notifications -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    false
                }
            }
        }
    }

    override suspend fun openAppSettings() {
        val activity = activityRef.get() ?: return

        return suspendCancellableCoroutine { cont ->
            val registry = activity.activityResultRegistry
            val key = "settings-${hashCode()}-${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<Intent>? = null
            launcher = registry.register(
                key,
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (!cont.isCompleted) {
                    cont.resume(Unit)
                }
                launcher?.unregister()
            }

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
            launcher.launch(intent)

            cont.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }

    override suspend fun checkPermissionsGranted(
        permissions: List<AppPermission>
    ): Map<AppPermission, Boolean> = permissions.toCurrentState(context)

    override suspend fun checkPermissionGranted(permission: AppPermission): Boolean =
        permission.toCurrentState(context)
}

fun createDefaultAndroidPermissionController(
    activity: ComponentActivity
): PermissionController {
    val context = activity.applicationContext as Application
    return AndroidPermissionController(
        context = context,
        activityRef = WeakReference(activity)
    )
}