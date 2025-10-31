package com.ubase.uclass.presentation.web

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import org.json.JSONObject

@Composable
fun NotificationScreen(webViewManager: WebViewManager) {
    val context = LocalContext.current
    val message = webViewManager.scriptMessage.value
    val mainHandler = Handler(Looper.getMainLooper())

    // 뒤로가기 처리
    BackHandler {
        if(!TextUtils.isEmpty(webViewManager.preloadedWebView!!.url) &&
            !TextUtils.isEmpty(Constants.noticeURL) &&
            webViewManager.preloadedWebView!!.url!! == Constants.noticeURL
        ) {
            // Context를 Activity로 캐스팅하여 앱 종료
            (context as? Activity)?.let { activity ->
                // 모든 액티비티 종료
                activity.finishAffinity()
            } ?: run {
                Logger.error("Activity를 찾을 수 없습니다")
            }
        }

        if(webViewManager.preloadedWebView != null) {
            val script = "javascript:goBackPress()"
            Logger.dev("javascript:goBackPress()")
            mainHandler.post {
                webViewManager.preloadedWebView!!.evaluateJavascript(script) { result ->

                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // URL이 있을 경우에만 WebView 로드
        if (webViewManager.preloadedWebView != null && webViewManager.isWebViewLoaded.value) {
            // WebView 로딩 완료 시 표시
            AndroidView(
                factory = { webViewManager.preloadedWebView!! },
                modifier = Modifier.fillMaxSize()
            )

            // JS 메시지 처리
            if (!TextUtils.isEmpty(message)) {
                try {
                    val json = JSONObject(message)
                    val action = json.optString("action", "")

                    Logger.dev("📌 Notification Action: $action")

                    when (action.lowercase()) {
                        "showloading" -> {
                            CustomLoadingManager.showLoading()
                        }

                        "hideloading" -> {
                            CustomLoadingManager.hideLoading()
                        }

                        "showalert" -> {
                            val alertTitle = json.optString("title", "")
                            val alertMessage = json.optString("message", "")
                            val callBack = json.optString("callback", "")

                            CustomAlertManager.showAlert(
                                title = alertTitle,
                                content = alertMessage,
                                onConfirm = {
                                    if (!TextUtils.isEmpty(callBack)) {
                                        webViewManager.preloadedWebView!!.loadUrl(callBack)
                                    }
                                })
                        }

                        "showconfirm" -> {
                            val alertTitle = json.optString("title", "")
                            val alertMessage = json.optString("message", "")
                            val callBack = json.optString("callback", "")

                            CustomAlertManager.showConfirmAlert(
                                title = alertTitle,
                                content = alertMessage,
                                onConfirm = {
                                    if (!TextUtils.isEmpty(callBack)) {
                                        webViewManager.preloadedWebView!!.loadUrl(callBack)
                                    }
                                })
                        }

                        else -> {
                            Logger.dev("⚠️ Unknown action: $action")
                        }
                    }
                } catch (e: Exception) {
                    Logger.error("❌ JSON parsing error: ${e.message}")
                }
            }
        } else if (webViewManager.isWebViewLoading.value) {
            // 로딩 중 표시
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}