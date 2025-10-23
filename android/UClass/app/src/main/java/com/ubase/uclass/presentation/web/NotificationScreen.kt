package com.ubase.uclass.presentation.web

import android.text.TextUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.util.Logger
import org.json.JSONObject

@Composable
fun NotificationScreen(webViewManager: WebViewManager) {
    val message = webViewManager.scriptMessage.value

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