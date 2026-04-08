package com.codingdrama.vlmwebrtc.permission

/**
 * JS/Browser implementation of Permission handling.
 * Browser permissions are requested via navigator.mediaDevices.getUserMedia()
 * which is handled when actually accessing the media.
 */
actual fun checkPermission(permission: Permission): PermissionStatus {
    // In browser, we can't easily check permission status beforehand
    // without using the Permissions API which has limited support
    return PermissionStatus.NOT_DETERMINED
}

actual fun requestPermission(permission: Permission, onResult: OnPermissionResult) {
    // In browser, permissions are requested when getUserMedia is called
    // For now, return NOT_DETERMINED and let the actual media access handle it
    console.log("Permission request for ${permission.name} - will be prompted when accessing media")
    onResult(PermissionResult(permission, PermissionStatus.NOT_DETERMINED))
}
