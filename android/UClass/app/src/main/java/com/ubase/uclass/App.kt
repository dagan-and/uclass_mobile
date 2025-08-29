package com.ubase.uclass

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat.finishAffinity
import com.ubase.uclass.presentation.fcm.PushRelayActivity
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException
import kotlin.system.exitProcess


class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        fun context(): Context {
            return instance.applicationContext
        }
    }
    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        instance = this

        setupLifecycleObserver()

        //앱 빌드 설정
        Logger.setEnable(true)


        //SNS 로그인 초기화값
        KakaoSdk.init(this, "cc0faae5b1dd0468f0440656b12b8601")
        NaverIdLoginSDK.initialize(this,
            getString(R.string.naver_client_id),
            getString(R.string.naver_client_secret),
            getString(R.string.app_name))


        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.i("UCLASS_INFO", "FCM_TOKEN::" + task.result)

                Constants.fcmToken = task.result

                try {
                    val prefs = getSharedPreferences("IDLE_PREF", Context.MODE_PRIVATE)
                    val editor: SharedPreferences.Editor = prefs.edit()
                    editor.putString("FCM_TOKEN", Constants.fcmToken)
                    editor.apply()
                } catch (e : Exception) {
                    Logger.error(e)
                }
                return@OnCompleteListener
            }
        })
        Log.i("UCLASS_INFO","SSAID = " + Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID))
    }

    private var runningActivity : ArrayList<Activity> = ArrayList()
    private var runningActivityCount = 0
    private val lock = Any()

    private fun setupLifecycleObserver() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks{
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                synchronized(lock) {
                    if(p0 is PushRelayActivity) {
                        return
                    }
                    runningActivity.add(p0)
                    ++runningActivityCount
                }
            }
            override fun onActivityStarted(p0: Activity) {
            }
            override fun onActivityResumed(p0: Activity) {}
            override fun onActivityPaused(p0: Activity) {}
            override fun onActivityStopped(p0: Activity) {
            }
            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
            override fun onActivityDestroyed(p0: Activity) {
                synchronized(lock) {
                    runningActivity.remove(p0)
                    --runningActivityCount
                }
            }
        })

    }

    override fun onTrimMemory(level: Int) {
        Log.i("UCLASS_INFO","onTrimMemory::$level")
        if(runningActivityCount > 0) {
            super.onTrimMemory(level)
        } else {
            when(level) {
                TRIM_MEMORY_RUNNING_MODERATE,
                TRIM_MEMORY_RUNNING_LOW,
                TRIM_MEMORY_RUNNING_CRITICAL,
                TRIM_MEMORY_COMPLETE -> {
                    if(runningActivity.isNotEmpty()) {
                        finishAffinity(runningActivity[0])
                        exitProcess(0)
                    }
                }
                else -> {
                    super.onTrimMemory(level)
                }
            }
        }
    }

    fun getRunningActivity() : ArrayList<Activity> {
        return runningActivity
    }

    fun getTopActivity() : Activity {
        return runningActivity[runningActivity.size - 1]
    }
}
