package com.neupanesushant.bhagavadgeeta

import android.content.Context

class AndroidAppStorage(context: Context) : AppStorage {
    private val preferences = context.getSharedPreferences("gita_reader", Context.MODE_PRIVATE)
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String) { preferences.edit().putString(key, value).apply() }
}
