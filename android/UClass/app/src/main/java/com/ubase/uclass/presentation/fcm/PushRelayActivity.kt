package com.ubase.uclass.presentation.fcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import com.ubase.uclass.App
import com.ubase.uclass.presentation.MainActivity
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.Constants

class PushRelayActivity : Activity() {
    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        Constants.isPushStart = true
        super.onCreate(savedInstanceState)
        updateIdleTime()
        val bundle = intent.extras
        if (bundle != null) {
            if(App.instance.getRunningActivity().isNotEmpty() && !Constants.isClearedData()) {
                for (activity in App.instance.getRunningActivity().reversed()) {
                    if(activity is MainActivity) {
                        activity.setFCMIntent(bundle)
                        finish()
                        return
                    }
                }
                onPushNotification(bundle)
                finish()
            } else {
                onPushNotification(bundle)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun onPushNotification(bundle: Bundle) {
        val intentForPushActionActivity = Intent(this, MainActivity::class.java)
        intentForPushActionActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (!wasLaunchedFromRecents()) {
            AppUtil.setFCMIntent(intentForPushActionActivity,null, bundle)
            intentForPushActionActivity.putExtra("RESTART", true)
        }
        startActivity(intentForPushActionActivity)
    }

    protected fun wasLaunchedFromRecents(): Boolean {
        return (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
    }

    private val IDLE_PREF = "IDLE_PREF"
    private val IDLE_KEY = "idleTime"

    private fun updateIdleTime() {
        val prefs = getSharedPreferences(IDLE_PREF, Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putLong(IDLE_KEY, System.currentTimeMillis())
        editor.apply()
    }
}