package com.codingdrama.vlmwebrtc.permission

/**
 * Permission types that may be requested.
 */
enum class Permission {
    RECORD_AUDIO,
    CAMERA
}

/**
 * Permission status.
 */
enum class PermissionStatus {
    /** Permission has been granted */
    GRANTED,
    /** Permission has been denied */
    DENIED,
    /** Permission needs to be requested */
    NOT_DETERMINED
}

/**
 * Result of a permission request.
 */
data class PermissionResult(
    val permission: Permission,
    val status: PermissionStatus
)

/**
 * Callback for permission request results.
 */
typealias OnPermissionResult = (PermissionResult) -> Unit

/**
 * Check if a permission is granted.
 */
expect fun checkPermission(permission: Permission): PermissionStatus

/**
 * Request a permission. The result will be delivered via callback.
 * On some platforms, this may show a system dialog.
 */
expect fun requestPermission(permission: Permission, onResult: OnPermissionResult)
