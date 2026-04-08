package com.codingdrama.vlmwebrtc.permission

/**
 * JVM/Desktop implementation of Permission handling.
 * Desktop platforms typically don't have explicit permission dialogs
 * for audio/camera - they're handled at OS level or always available.
 */
actual fun checkPermission(permission: Permission): PermissionStatus {
    // On desktop, we assume permissions are always available
    // The actual recording/capture may fail if hardware is not accessible
    return PermissionStatus.GRANTED
}

actual fun requestPermission(permission: Permission, onResult: OnPermissionResult) {
    // On desktop, immediately return granted
    // The actual audio/video capture will fail at runtime if not available
    onResult(PermissionResult(permission, PermissionStatus.GRANTED))
}
