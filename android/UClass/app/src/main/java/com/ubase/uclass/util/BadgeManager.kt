package com.ubase.uclass.util


import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi

class BadgeManager private constructor() {

    companion object {
        private const val PREFS_NAME = "badge_prefs"
        private const val KEY_BADGE_COUNT = "badge_count"
        private const val SHORTCUT_ID = "main_shortcut"

        @Volatile
        private var INSTANCE: BadgeManager? = null

        fun getInstance(): BadgeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BadgeManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * SharedPreferences 가져오기 (메모리 누수 방지)
     */
    private fun getSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * NotificationManager 가져오기 (메모리 누수 방지)
     */
    private fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * 현재 배지 카운트 가져오기
     */
    fun getBadgeCount(context: Context): Int {
        return getSharedPrefs(context).getInt(KEY_BADGE_COUNT, 0)
    }

    /**
     * 배지 카운트 증가 (푸시 수신 시)
     */
    fun incrementBadgeCount(context: Context) {
        val currentCount = getBadgeCount(context)
        val newCount = currentCount + 1
        setBadgeCount(context, newCount)
        Logger.info("배지 카운트 증가: $currentCount -> $newCount")
    }

    /**
     * 배지 카운트 설정
     */
    private fun setBadgeCount(context: Context, count: Int) {
        getSharedPrefs(context).edit().putInt(KEY_BADGE_COUNT, count).apply()
        updateAppBadge(context, count)
    }

    /**
     * 배지 카운트 초기화 (앱 실행 시)
     */
    fun clearBadgeCount(context: Context) {
        setBadgeCount(context, 0)
        clearAllNotifications(context)
        Logger.info("배지 카운트 및 알림 초기화")
    }

    /**
     * 모든 알림 제거
     */
    private fun clearAllNotifications(context: Context) {
        getNotificationManager(context).cancelAll()
    }

    /**
     * Android 기본 배지 업데이트 (API 26+)
     */
    private fun updateAppBadge(context: Context, count: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updateNotificationBadge(context, count)
        }
    }

    /**
     * 알림 채널 배지 업데이트 (API 26+)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNotificationBadge(context: Context, count: Int) {
        try {
            val notificationManager = getNotificationManager(context)
            // 알림 채널에서 배지 카운트 설정
            val channels = notificationManager.notificationChannels
            for (channel in channels) {
                if (channel.canShowBadge()) {
                    channel.setShowBadge(count > 0)
                    notificationManager.createNotificationChannel(channel)
                }
            }
        } catch (e: Exception) {
            Logger.error("알림 배지 업데이트 실패: ${e.message}")
        }
    }
}