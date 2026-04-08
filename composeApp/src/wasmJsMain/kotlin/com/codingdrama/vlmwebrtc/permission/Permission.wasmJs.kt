package com.codingdrama.vlmwebrtc.permission

/**
 * WasmJS/Browser implementation of Permission handling.
 * Similar to JS, browser permissions are requested via getUserMedia().
 */
actual fun checkPermission(permission: Permission): PermissionStatus {
    // In WasmJS browser, we can't easily check permission status beforehand
    return PermissionStatus.NOT_DETERMINED
}

actual fun requestPermission(permission: Permission, onResult: OnPermissionResult) {
    // In browser, permissions are requested when getUserMedia is called
    println("Permission request for ${permission.name} - will be prompted when accessing media")
    onResult(PermissionResult(permission, PermissionStatus.NOT_DETERMINED))
}
