package com.neupanesushant.bhagavadgeeta

import java.time.LocalDate

actual fun currentLocalDateKey(): String = LocalDate.now().toString()
