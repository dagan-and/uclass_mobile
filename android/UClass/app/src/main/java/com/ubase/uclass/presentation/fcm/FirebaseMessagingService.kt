package com.ubase.uclass.presentation.fcm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.Constants.MessageNotificationKeys
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ubase.uclass.App
import com.ubase.uclass.BuildConfig
import com.ubase.uclass.R
import com.ubase.uclass.presentation.MainActivity
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.BadgeManager
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import kotlin.random.Random


class FirebaseMessagingService : FirebaseMessagingService() {

    var CHANNEL_ID = "UCLASS"
    var CHANNEL_NAME = "UCLASS"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Logger.dev("FirebaseMessagingService::FCM_TOEKN::" +token)

        Constants.fcmToken = token

        try {
            val prefs = getSharedPreferences("IDLE_PREF", Context.MODE_PRIVATE)
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.putString("FCM_TOKEN", Constants.fcmToken)
            editor.apply()
        } catch (e : Exception) {
            Logger.error(e)
        }
    }

    override fun handleIntent(intent: Intent?) {
        try {
            intent?.apply {
                val temp = extras?.apply {
                    remove(MessageNotificationKeys.ENABLE_NOTIFICATION)
                    remove(keyWithOldPrefix(MessageNotificationKeys.ENABLE_NOTIFICATION))
                }
                replaceExtras(temp)
            }
        } catch (e : Exception) {
            Log.e("Firebase", e.localizedMessage!!)
        }
        super.handleIntent(intent)
    }

    private fun keyWithOldPrefix(key: String): String {
        if (!key.startsWith(MessageNotificationKeys.NOTIFICATION_PREFIX)) {
            return key
        }
        return key.replace(
            MessageNotificationKeys.NOTIFICATION_PREFIX,
            MessageNotificationKeys.NOTIFICATION_PREFIX_OLD
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        //알림 권한이 없다면 메시지 받아도 동작하지 않음
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(
            applicationContext
        )

        var builder: NotificationCompat.Builder? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = CHANNEL_ID
                channel.setShowBadge(true)
                notificationManager.createNotificationChannel(channel)
            }
            builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        } else {
            builder = NotificationCompat.Builder(applicationContext)
        }

        try {
            /* PUSH ALARM  */
            var title = ""
            var body = ""

            if(message.data.containsKey("title") && message.data.containsKey("body")) {
                title = message.data["title"]!!
                body = message.data["body"]!!
            }
            if(message.notification != null) {
                title = message.notification!!.title!!
                body = message.notification!!.body!!
            }

            if(BuildConfig.DEBUG) {
                Logger.dev(mapToString(message.data))
            }

            val intentForPushActionActivity = Intent(this, PushRelayActivity::class.java)
            intentForPushActionActivity.addFlags(Intent.FLAG_FROM_BACKGROUND)
            intentForPushActionActivity.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intentForPushActionActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intentForPushActionActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intentForPushActionActivity.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

            AppUtil.setFCMIntent(intentForPushActionActivity , null , null, message.data)
            updateChatBadge(message.data)

            val pendingIntent = PendingIntent.getActivity(this, 1234, intentForPushActionActivity, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentTitle(title)
                .setContentText(body)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(ContextCompat.getColor(this, R.color.black))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_CALL) //Heads-up 알림을 크게 보여줌
                .setAutoCancel(true)
                .setNumber(1)
                .setFullScreenIntent(pendingIntent ,true)
            val notification: Notification = builder.build()

            if(isChatType(message.data)) {
                // 현재 배지 카운트 가져오기
                BadgeManager.getInstance().incrementBadgeCount(this)
                notification.number = BadgeManager.getInstance().getBadgeCount(this)
                notificationManager.notify(123456, notification)
            } else {
                notificationManager.notify(Random.nextInt(), notification)
            }

        } catch (e : Exception) {
            Logger.error(e)
        }
    }

    fun mapToString(map: Map<String?, String?>): String {
        try {
            val stringBuilder = StringBuilder()
            for ((key, value) in map) {
                stringBuilder.append(key)
                    .append("=")
                    .append(value)
                    .append(", ")
            }

            // 마지막 콤마와 공백 제거
            if (stringBuilder.length > 0) {
                stringBuilder.setLength(stringBuilder.length - 2)
            }

            return stringBuilder.toString()
        } catch (e : Exception) {
            Log.e("UCLASS_ERR",e.localizedMessage)
            return ""
        }
    }

    private fun isChatType(map: Map<String, String>?) : Boolean {
        return if(map.isNullOrEmpty() || !map.containsKey("type") || !map["type"].equals("CHAT",true)) {
            false
        } else {
            true
        }
    }

    private fun updateChatBadge(map: Map<String, String>?) {
        if(!isChatType(map)) {
            return
        }

        try {
            val app = application as? App ?: return
            val activities = app.getRunningActivity()

            val mainActivity = activities.filterIsInstance<MainActivity>()
                .firstOrNull { !it.isDestroyed && !it.isFinishing }

            mainActivity?.let { activity ->
                Handler(Looper.getMainLooper()).post {
                    try {
                        activity.updateChatBadgeFromFCM()
                    } catch (e: Exception) {
                        Logger.error("Failed to update chat badge: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error in updateChatBadgeSimple: ${e.message}")
        }
    }
}