package dev.buildgate.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform