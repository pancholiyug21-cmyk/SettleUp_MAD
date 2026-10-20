package com.example.splitwsie.domain.model

import java.util.Calendar
import java.util.Date

enum class RepeatInterval {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    val displayName: String
        get() = when (this) {
            NONE -> "Never"
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
            MONTHLY -> "Monthly"
            YEARLY -> "Yearly"
        }

    fun calculateNextDate(fromDate: Date): Date? {
        if (this == NONE) return null
        
        val calendar = Calendar.getInstance()
        calendar.time = fromDate
        
        when (this) {
            DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> calendar.add(Calendar.MONTH, 1)
            YEARLY -> calendar.add(Calendar.YEAR, 1)
            NONE -> return null
        }
        
        return calendar.time
    }
}
