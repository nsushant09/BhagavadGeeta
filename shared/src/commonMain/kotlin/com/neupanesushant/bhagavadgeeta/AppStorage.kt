package com.neupanesushant.bhagavadgeeta

/** Small KMP persistence boundary. Replace its implementation without touching UI or domain code. */
interface AppStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class MemoryAppStorage : AppStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String) { values[key] = value }
}

object StorageKeys {
    const val Theme = "theme"
    const val Bookmarks = "bookmarks"
    const val Highlights = "highlights"
    const val ReadingMode = "reading_mode"
    const val LastChapter = "last_chapter"
    const val ReadingIndexPrefix = "reading_index_"
    const val ReadingOffsetPrefix = "reading_offset_"
    const val FontScale = "font_scale"
    const val NotificationEnabled = "notification_enabled"
}
