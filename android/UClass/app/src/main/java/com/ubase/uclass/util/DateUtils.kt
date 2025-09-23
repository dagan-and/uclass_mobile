package com.ubase.uclass.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA)
    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)

    // 입력 문자열 파싱용 포맷터 (API에서 오는 "2025-09-19 22:59:19" 형태)
    private val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 기존 Date 타입 메서드들
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

    // 새로 추가된 String 타입 메서드들
    fun formatDate(dateString: String): String {
        return try {
            val date = inputFormat.parse(dateString)
            date?.let { dateFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            Logger.error("날짜 파싱 오류: $dateString")
            dateString
        }
    }

    fun formatTime(dateString: String): String {
        return try {
            val date = inputFormat.parse(dateString)
            date?.let { timeFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            Logger.error("시간 파싱 오류: $dateString")
            dateString
        }
    }

    fun isSameDay(dateString1: String, dateString2: String): Boolean {
        return try {
            val date1 = inputFormat.parse(dateString1)
            val date2 = inputFormat.parse(dateString2)
            if (date1 != null && date2 != null) {
                isSameDay(date1, date2)
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.error("날짜 비교 오류: $dateString1, $dateString2")
            false
        }
    }
}