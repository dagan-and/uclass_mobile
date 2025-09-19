package com.ubase.uclass.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA)
    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)

    fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }

    fun formatTime(date: Date): String {
        return timeFormat.format(date)
    }

    fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}