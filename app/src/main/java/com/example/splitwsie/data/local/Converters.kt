package com.example.splitwsie.data.local

import androidx.room.TypeConverter
import com.example.splitwsie.domain.model.SplitType
import java.util.Date

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromSplitType(splitType: SplitType): String = splitType.name

    /**
     * Unknown values fall back to EQUAL, so a row written by a newer build cannot crash an
     * older one while it is reading the expense list.
     */
    @TypeConverter
    fun toSplitType(value: String): SplitType =
        runCatching { SplitType.valueOf(value) }.getOrDefault(SplitType.EQUAL)
}
