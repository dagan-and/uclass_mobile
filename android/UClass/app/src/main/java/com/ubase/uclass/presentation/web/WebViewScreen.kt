package com.ubase.uclass.presentation.web


import android.text.TextUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.CHAT
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.util.Logger
import org.json.JSONObject

@Composable
fun WebViewScreen(webViewManager: WebViewManager) {
    val message = webViewManager.scriptMessage.value

    Box(modifier = Modifier.fillMaxSize()) {
        if (webViewManager.preloadedWebView != null && webViewManager.isWebViewLoaded.value) {
            // WebView 로딩 완료 시 표시
            AndroidView(
                factory = { webViewManager.preloadedWebView!! },
                modifier = Modifier.fillMaxSize()
            )

            if (message != null) {
                try {
                    val json = JSONObject(message)
                    val action = json.optString("action", "")

                    Logger.dev("📌 Action: $action")

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

                            CustomAlertManager.showAlert(title = alertTitle, content = alertMessage ,
                                onConfirm = {
                                    if(!TextUtils.isEmpty(callBack)) {
                                        webViewManager.preloadedWebView!!.loadUrl(callBack)
                                    }
                                })
                        }

                        "showconfirm" -> {
                            val alertTitle = json.optString("title", "")
                            val alertMessage = json.optString("message", "")
                            val callBack = json.optString("callback", "")

                            CustomAlertManager.showConfirmAlert(title = alertTitle, content = alertMessage ,
                                onConfirm = {
                                    if(!TextUtils.isEmpty(callBack)) {
                                        webViewManager.preloadedWebView!!.loadUrl(callBack)
                                    }
                                })
                        }

                        "goclose" -> {
                            // 웹뷰 닫기
                            Logger.dev("웹뷰 닫기 요청")
                        }

                        "godm" -> {
                            ViewCallbackManager.notifyResult(NAVIGATION, CHAT)
                        }

                        else -> {
                            Logger.dev("⚠️ Unknown action: $action")
                        }
                    }
                } catch (e: Exception) {
                    Logger.error("❌ JSON parsing error: ${e.message}")
                }
            }
        }
    }
}
