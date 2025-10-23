package com.ubase.uclass.presentation.view

import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.presentation.web.RegisterWebViewManager
import com.ubase.uclass.util.Logger
import org.json.JSONObject

@Composable
fun RegisterWebViewScreen(
    url: String,
    onRegistrationComplete: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val webViewManager = remember { RegisterWebViewManager(context) }

    val scriptMessage by webViewManager.scriptMessage
    val isLoading by webViewManager.isWebViewLoading
    val isLoaded by webViewManager.isWebViewLoaded
    val registrationCompleted by webViewManager.registrationCompleted

    // 회원가입 완료 감지
    LaunchedEffect(registrationCompleted) {
        if (registrationCompleted) {
            Logger.dev("✅ 회원가입 완료 감지")
            onRegistrationComplete()
        }
    }

    // JavaScript 메시지 처리
    LaunchedEffect(scriptMessage) {
        scriptMessage?.let { message ->
            if (message.isNotBlank()) {
                parseAndHandleScriptMessage(message, webViewManager, onClose)
            }
        }
    }

    DisposableEffect(url) {
        Logger.info("## RegisterWebView URL로 로드: $url")
        webViewManager.preloadWebView(url)

        onDispose {
            webViewManager.destroy()
        }
    }

    // 뒤로가기 처리
    BackHandler {
        Logger.dev("물리적 뒤로가기")
        webViewManager.getWebView()?.evaluateJavascript("goBackPress()", null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 웹뷰 영역
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    isLoaded && webViewManager.preloadedWebView != null -> {
                        AndroidView(
                            factory = { webViewManager.preloadedWebView!! },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    isLoading -> {
                        LoadingView()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

    }
}

// JavaScript 메시지 파싱 및 처리
private fun parseAndHandleScriptMessage(
    message: String,
    webViewManager: RegisterWebViewManager,
    onClose: () -> Unit
) {
    Logger.dev("📩 회원가입 웹뷰에서 받은 메시지: $message")

    if (!TextUtils.isEmpty(message)) {
        try {
            val json = JSONObject(message)
            val action = json.optString("action", "")

            Logger.dev("📌 Action: $action")

            when (action.lowercase()) {
                "gologin" -> {
                    // 회원가입 완료 메시지
                    Logger.dev("✅ 회원가입 완료 (JS 메시지)")

                    webViewManager.setRegistrationCompleted(true)
                }

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
                    onClose()
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