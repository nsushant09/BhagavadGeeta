package com.neupanesushant.bhagavadgeeta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform