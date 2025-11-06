package com.biggates.devicemanager.permission.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.biggates.devicemanager.permission.PlatformContext

actual suspend fun PlatformContext.checkNotificationsGranted(): Boolean {
    // 시스템에서 앱 알림 허용(토글)이 꺼져 있으면 무조건 false
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

    // Android 13+는 POST_NOTIFICATIONS 런타임 권한도 있어야 함
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    return true
}