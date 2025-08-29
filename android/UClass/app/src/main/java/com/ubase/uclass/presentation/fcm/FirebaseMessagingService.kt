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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.Constants.MessageNotificationKeys
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ubase.uclass.BuildConfig
import com.ubase.uclass.R
import com.ubase.uclass.util.AppUtil
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
            Log.e("AICFO Firebase", e.localizedMessage!!)
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
                channel.description = "AICFO"
                channel.setShowBadge(false)
                notificationManager.createNotificationChannel(channel)
            }
            builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        } else {
            builder = NotificationCompat.Builder(applicationContext)
        }

        try {
            /* PUSH ALARM  */
            var title: String = ""
            var body: String = ""

            try {
                val extras = message.toIntent().extras
                if (extras != null) {
                    for (key in extras.keySet()) {
                        val value = extras[key]
                        if(key.contains("_key") && value != null) {
                            title = value.toString()
                        }
                        if(key.contains("_d") && value != null) {
                            body = value.toString()
                        }
                    }
                }
            } catch (e : Exception) {
                Logger.error(e)
            }

            if(message.data.containsKey("_key") && message.data.containsKey("_d")) {
                title = message.data["_key"]!!
                body = message.data["_d"]!!
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

            val pendingIntent = PendingIntent.getActivity(this, 1234, intentForPushActionActivity, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentTitle(title)
                .setContentText(body)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.img_cfo_noti)
                .setColor(ContextCompat.getColor(this, R.color.black))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_CALL) //Heads-up 알림을 크게 보여줌
                .setAutoCancel(true)
                .setNumber(0)
                .setFullScreenIntent(pendingIntent ,true)
            val notification: Notification = builder.build()
            notificationManager.notify(Random.nextInt(), notification)
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

    private fun buildNotification(context: Context, pendingIntent: PendingIntent): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🔔 샘플 알림")
            .setContentText("이것은 테스트 알림입니다!")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.img_cfo_noti)
            .setColor(ContextCompat.getColor(context, R.color.black))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setNumber(0)
            .build()
    }
}