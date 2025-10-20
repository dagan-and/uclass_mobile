package com.ubase.uclass.presentation.fcm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ubase.uclass.presentation.MainActivity
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.BadgeManager
import com.ubase.uclass.util.Logger

/**
 * FCM 푸시 알림을 클릭했을 때 데이터를 MainActivity로 전달하는 중계 액티비티
 */
class PushRelayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.info("## PushRelayActivity 시작")

        //알림 누르면 카운트 초기화
        BadgeManager.getInstance().clearBadgeCount(this)

        // 앱이 이미 실행 중인지 확인
        val isAppRunning = isAppInForeground()

        if (isAppRunning) {
            // 앱이 실행 중이면 MainActivity에 FCM 데이터 전달
            sendDataToMainActivity()
        } else {
            // 앱이 실행 중이 아니면 MainActivity를 새로 시작
            startMainActivity()
        }

        // 중계 액티비티는 즉시 종료
        finish()
    }

    protected fun wasLaunchedFromRecents(): Boolean {
        return (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
    }

    /**
     * 앱이 포그라운드에서 실행 중인지 확인
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)

        return if (runningTasks.isNotEmpty()) {
            val topActivity = runningTasks[0].topActivity
            topActivity?.packageName == packageName
        } else {
            false
        }
    }

    /**
     * 실행 중인 MainActivity에 FCM 데이터 전달
     */
    private fun sendDataToMainActivity() {
        try {
            val app = application as? com.ubase.uclass.App
            val activities = app?.getRunningActivity()

            val mainActivity = activities?.filterIsInstance<MainActivity>()
                ?.firstOrNull { !it.isDestroyed && !it.isFinishing }

            if (mainActivity != null) {
                Logger.info("## 실행 중인 MainActivity에 FCM 데이터 전달")
                mainActivity.setFCMIntent(intent.extras)
            } else {
                Logger.info("## 실행 중인 MainActivity를 찾을 수 없음 - 새로 시작")
                startMainActivity()
            }
        } catch (e: Exception) {
            Logger.error("## MainActivity에 데이터 전달 실패: ${e.message}")
            startMainActivity()
        }
    }

    /**
     * MainActivity를 새로 시작하면서 FCM 데이터 전달
     */
    private fun startMainActivity() {
        Logger.info("## MainActivity 새로 시작")


        val mainIntent = Intent(this, MainActivity::class.java).apply {
            // 기존 액티비티 스택을 클리어하고 새로 시작
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // FCM 데이터 전달
            intent.extras?.let { extras ->
                putExtras(extras)
                Logger.info("## FCM 데이터를 MainActivity에 전달: ${extras.keySet().joinToString { "$it=${extras.getString(it)}" }}")
            }
        }

        startActivity(mainIntent)
    }
}