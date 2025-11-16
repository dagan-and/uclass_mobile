package com.ubase.uclass.presentation.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PreferenceManager

class RegisterWebViewManager(private val context: Context) {

    var preloadedWebView: WebView? = null
        private set

    val isWebViewLoaded = mutableStateOf(false)
    val isWebViewLoading = mutableStateOf(false)
    val registrationCompleted = mutableStateOf(false)

    // JS 메시지 전달용 상태
    val scriptMessage = mutableStateOf<String?>(null)

    private val mainHandler = Handler(Looper.getMainLooper())


    // JavaScript 안전 문자열 변환 함수
    fun escapeJavaScriptString(str: String): String {
        return str
            .replace("\\", "\\\\")   // \ -> \\
            .replace("\"", "\\\"")   // " -> \"
            .replace("\'", "\\'")    // ' -> \'
            .replace("\n", "\\n")    // 개행
            .replace("\r", "\\r")    // 캐리지 리턴
            .replace("\t", "\\t")    // 탭
    }

    fun preloadWebView(url: String) {
        clearWebViewData()
        Logger.info("## RegisterWebView preload 시작: $url")
        isWebViewLoading.value = true
        isWebViewLoaded.value = false

        preloadedWebView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Logger.info("## RegisterWebView onPageStarted: $url")
                    isWebViewLoading.value = true
                    CustomLoadingManager.showLoading()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Logger.info("## RegisterWebView onPageFinished: $url")

                    // Handler.post 사용하여 다음 프레임에 상태 업데이트
                    mainHandler.post {
                        Logger.info("## RegisterWebView 상태 업데이트: loading=false, loaded=true")
                        isWebViewLoading.value = false
                        isWebViewLoaded.value = true
                        CustomLoadingManager.hideLoading()
                    }

                    // 사용 코드
                    val jsonObject = PreferenceManager.getLoginInfoAsJson(context)
                    val jsonString = jsonObject.toString()
                    val escapedJson = escapeJavaScriptString(jsonString)
                    val script = "javascript:nativeBinding('$escapedJson')"

                    mainHandler.post {
                        Logger.info("전송 전: $jsonString")
                        Logger.info("전송 스크립트: $script")

                        preloadedWebView?.evaluateJavascript(script) { result ->
                            Logger.dev("JavaScript 실행 결과: $result")
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Logger.error("## RegisterWebView onReceivedError: $errorCode - $description")

                    // 에러가 발생해도 로딩 완료로 처리
                    mainHandler.post {
                        isWebViewLoading.value = false
                        isWebViewLoaded.value = true
                        CustomLoadingManager.hideLoading()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let { currentUrl ->
                        Logger.dev("웹뷰 네비게이션: $currentUrl")
                    }
                    return false
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = true
                displayZoomControls = false

                // 디버그 설정
                if (Constants.isDebug) {
                    setWebContentsDebuggingEnabled(true)
                }
            }

            // JS 인터페이스 연결
            addJavascriptInterface(
                UclassJsInterface(context) { msg ->
                    Logger.info("회원가입 웹뷰에서 받은 메시지: $msg")
                    scriptMessage.value = msg
                    Handler(Looper.getMainLooper()).postDelayed({
                        scriptMessage.value = ""
                    },1000)
                },
                "uclass" // window.uclass 로 접근 가능
            )

            Logger.info("## RegisterWebView loadUrl 호출: $url")
            loadUrl(url)
        }
    }

    fun setRegistrationCompleted(completed: Boolean) {
        mainHandler.post {
            registrationCompleted.value = completed
        }
    }

    fun reload() {
        Logger.info("## RegisterWebView reload")
        preloadedWebView?.reload()
    }

    fun loadUrl(url: String) {
        Logger.info("## RegisterWebView loadUrl: $url")
        preloadedWebView?.loadUrl(url)
    }

    fun getWebView() : WebView? {
        return preloadedWebView
    }

    fun destroy() {
        Logger.info("## RegisterWebView destroy")
        preloadedWebView?.destroy()
        preloadedWebView = null
        isWebViewLoaded.value = false
        isWebViewLoading.value = false
        registrationCompleted.value = false
    }

    fun clearWebViewData() {
        mainHandler.post {
            preloadedWebView?.apply {
                // 히스토리 클리어
                clearHistory()

                // 캐시 클리어
                clearCache(true)

                // Form 데이터 클리어
                clearFormData()
            }

            // 쿠키 클리어
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()

            // 로컬 스토리지 클리어
            WebStorage.getInstance().deleteAllData()

            Logger.info("## WebView 데이터 전체 클리어 완료")
        }
    }

    fun clearHistoryOnly() {
        preloadedWebView?.clearHistory()
        Logger.info("## WebView 히스토리만 클리어")
    }
}