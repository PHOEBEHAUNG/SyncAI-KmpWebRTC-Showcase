package com.codingdrama.vlmwebrtc.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Store the context and launcher for permission requests
private var permissionLauncher: ActivityResultLauncher<String>? = null
private var pendingCallback: OnPermissionResult? = null
private var pendingPermission: Permission? = null

/**
 * Initialize permission handling for an Activity.
 * Must be called in onCreate() before any permission requests.
 */
fun initPermissionHandler(activity: ComponentActivity) {
    permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val permission = pendingPermission
        val callback = pendingCallback
        pendingPermission = null
        pendingCallback = null
        
        if (permission != null && callback != null) {
            callback(PermissionResult(
                permission = permission,
                status = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
            ))
        }
    }
}

// Store context reference
private var appContext: Context? = null
private var activityRef: Activity? = null

fun setPermissionContext(context: Context, activity: Activity? = null) {
    appContext = context.applicationContext
    activityRef = activity
}

private fun Permission.toAndroidPermission(): String = when (this) {
    Permission.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
    Permission.CAMERA -> Manifest.permission.CAMERA
}

actual fun checkPermission(permission: Permission): PermissionStatus {
    val context = appContext ?: return PermissionStatus.NOT_DETERMINED
    
    return when (ContextCompat.checkSelfPermission(context, permission.toAndroidPermission())) {
        PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
        else -> PermissionStatus.NOT_DETERMINED
    }
}

actual fun requestPermission(permission: Permission, onResult: OnPermissionResult) {
    val launcher = permissionLauncher
    
    // First check if already granted
    if (checkPermission(permission) == PermissionStatus.GRANTED) {
        onResult(PermissionResult(permission, PermissionStatus.GRANTED))
        return
    }
    
    if (launcher != null) {
        pendingPermission = permission
        pendingCallback = onResult
        launcher.launch(permission.toAndroidPermission())
    } else {
        // Fallback: try using ActivityCompat if launcher not initialized
        val activity = activityRef
        if (activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission.toAndroidPermission()),
                1001
            )
            // Note: Result will be delivered via onRequestPermissionsResult
            // For simplicity, we'll just report NOT_DETERMINED
            onResult(PermissionResult(permission, PermissionStatus.NOT_DETERMINED))
        } else {
            onResult(PermissionResult(permission, PermissionStatus.DENIED))
        }
    }
}
