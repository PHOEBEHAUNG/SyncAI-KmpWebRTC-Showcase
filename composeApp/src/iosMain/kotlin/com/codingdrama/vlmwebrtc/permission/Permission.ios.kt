package com.codingdrama.vlmwebrtc.permission

import platform.AVFoundation.*
import platform.Foundation.NSLog

actual fun checkPermission(permission: Permission): PermissionStatus {
    return when (permission) {
        Permission.RECORD_AUDIO -> {
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)) {
                AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
                AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionStatus.DENIED
                else -> PermissionStatus.NOT_DETERMINED
            }
        }
        Permission.CAMERA -> {
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
                AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionStatus.DENIED
                else -> PermissionStatus.NOT_DETERMINED
            }
        }
    }
}

actual fun requestPermission(permission: Permission, onResult: OnPermissionResult) {
    // First check if already granted
    val currentStatus = checkPermission(permission)
    if (currentStatus == PermissionStatus.GRANTED) {
        onResult(PermissionResult(permission, PermissionStatus.GRANTED))
        return
    }
    
    if (currentStatus == PermissionStatus.DENIED) {
        // Already denied, can't request again
        onResult(PermissionResult(permission, PermissionStatus.DENIED))
        return
    }
    
    // Not determined, request permission
    val mediaType = when (permission) {
        Permission.RECORD_AUDIO -> AVMediaTypeAudio
        Permission.CAMERA -> AVMediaTypeVideo
    }
    
    AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
        NSLog("Permission ${permission.name} granted: $granted")
        onResult(PermissionResult(
            permission = permission,
            status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        ))
    }
}
