package com.codingdrama.vlmwebrtc

import kotlin.js.JsName

@JsName("Date")
external class JsDate {
    companion object {
        fun now(): Double
    }
}

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun currentTimeMillis(): Long = JsDate.now().toLong()