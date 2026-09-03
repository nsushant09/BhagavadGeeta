package com.neupanesushant.bhagavadgeeta

import platform.Foundation.NSUserDefaults

class IosAppStorage : AppStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(key: String): String? = defaults.stringForKey(key)
    override fun write(key: String, value: String) { defaults.setObject(value, forKey = key) }
}
