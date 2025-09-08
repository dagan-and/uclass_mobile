package com.ubase.uclass.presentation.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger

class WebViewManager(private val context: Context) {

    var preloadedWebView: WebView? = null
        private set

    val isWebViewLoaded = mutableStateOf(false)
    val isWebViewLoading = mutableStateOf(false)

    // JS Alert 메시지 전달용 상태
    val scriptMessage = mutableStateOf<String?>(null)

    private val mainHandler = Handler(Looper.getMainLooper())

    fun preloadWebView(url: String = "https://m.naver.com") {
        Logger.info("## WebView preload 시작: $url")
        isWebViewLoading.value = true
        isWebViewLoaded.value = false

        preloadedWebView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Logger.info("## WebView onPageStarted: $url")
                    isWebViewLoading.value = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Logger.info("## WebView onPageFinished: $url")

                    // Handler.post 사용하여 다음 프레임에 상태 업데이트
                    mainHandler.post {
                        Logger.info("## WebView 상태 업데이트: loading=false, loaded=true")
                        isWebViewLoading.value = false
                        isWebViewLoaded.value = true
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Logger.info("## WebView onReceivedError: $errorCode - $description")

                    // 에러가 발생해도 로딩 완료로 처리
                    mainHandler.post {
                        isWebViewLoading.value = false
                        isWebViewLoaded.value = true
                    }
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
                    Logger.info("웹에서 받은 메시지: $msg")
                    scriptMessage.value = msg
                },
                "uclass" // window.uclass 로 접근 가능
            )

            Logger.info("## WebView loadUrl 호출: $url")
            loadUrl(url)
        }
    }

    fun reload() {
        Logger.info("## WebView reload")
        preloadedWebView?.reload()
    }

    fun loadUrl(url: String) {
        Logger.info("## WebView loadUrl: $url")
        preloadedWebView?.loadUrl(url)
    }

    fun destroy() {
        Logger.info("## WebView destroy")
        preloadedWebView?.destroy()
        preloadedWebView = null
        isWebViewLoaded.value = false
        isWebViewLoading.value = false
    }
}