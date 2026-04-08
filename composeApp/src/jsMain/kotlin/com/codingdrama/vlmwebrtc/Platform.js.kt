package com.codingdrama.vlmwebrtc

import kotlin.js.Date

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun currentTimeMillis(): Long = Date.now().toLong()