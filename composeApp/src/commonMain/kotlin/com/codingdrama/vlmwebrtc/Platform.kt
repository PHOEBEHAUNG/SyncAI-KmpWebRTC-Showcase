package com.codingdrama.vlmwebrtc

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform