package com.ubase.uclass.presentation.web


import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.CHAT
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import org.json.JSONObject

@Composable
fun WebViewScreen(webViewManager: WebViewManager) {
    val context = LocalContext.current
    val message = webViewManager.scriptMessage.value
    val mainHandler = Handler(Looper.getMainLooper())

    // 파일 선택 Launcher 설정
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data
            val contentType = uri?.let { context.contentResolver.getType(it) }

            Logger.info("## 파일 선택 완료: $uri, type: $contentType")

            if (contentType?.contains("image/") == true) {
                // 이미지 파일인 경우 WebViewManager에 전달
                webViewManager.handleFileChooserResult(uri, contentType)
            } else {
                // 이미지가 아닌 경우 토스트 메시지 표시
                Toast.makeText(
                    context,
                    "이미지 파일만 업로드할 수 있습니다. (jpg, jpeg, png)",
                    Toast.LENGTH_SHORT
                ).show()
                webViewManager.cancelFileChooser()
            }
        } else {
            // 파일 선택 취소
            Logger.info("## 파일 선택 취소됨")
            webViewManager.cancelFileChooser()
        }
    }

    // 파일 선택 트리거 감지
    LaunchedEffect(webViewManager.shouldOpenFileChooser.value) {
        if (webViewManager.shouldOpenFileChooser.value) {
            Logger.info("## 파일 선택 다이얼로그 열기")

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            fileChooserLauncher.launch(intent)

            // 트리거 초기화
            webViewManager.shouldOpenFileChooser.value = false
        }
    }

    // 뒤로가기 처리
    BackHandler {
        if(!TextUtils.isEmpty(webViewManager.preloadedWebView!!.url) &&
            !TextUtils.isEmpty(Constants.homeURL) &&
            webViewManager.preloadedWebView!!.url!! == Constants.homeURL
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
        if (webViewManager.preloadedWebView != null && webViewManager.isWebViewLoaded.value) {
            // WebView 로딩 완료 시 표시
            AndroidView(
                factory = { webViewManager.preloadedWebView!! },
                modifier = Modifier.fillMaxSize()
            )

            if (!TextUtils.isEmpty(message)) {
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
                            // Context를 Activity로 캐스팅하여 앱 종료
                            (context as? Activity)?.let { activity ->
                                // 모든 액티비티 종료
                                activity.finishAffinity()
                            } ?: run {
                                Logger.error("Activity를 찾을 수 없습니다")
                            }
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