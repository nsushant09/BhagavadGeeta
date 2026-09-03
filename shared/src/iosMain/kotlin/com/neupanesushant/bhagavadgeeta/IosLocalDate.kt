package com.neupanesushant.bhagavadgeeta

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun currentLocalDateKey(): String = NSDateFormatter().run {
    dateFormat = "yyyy-MM-dd"
    stringFromDate(NSDate())
}
