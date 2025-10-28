package com.ubase.uclass.presentation.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings.LOAD_NO_CACHE
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
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

    fun preloadWebView(url: String) {
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

                        // 페이지 로딩 완료 후 JWT 토큰 설정
                        setTokenToWebView()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)

                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.statusCode == 403) {
                            Logger.error("🚫 403 Forbidden 발생!")
                            ViewCallbackManager.notifyResult(
                                ViewCallbackManager.ResponseCode.NAVIGATION,
                                HOME
                            )
                            ViewCallbackManager.notifyResult(
                                ViewCallbackManager.ResponseCode.RELOAD,
                                true
                            )
                        }
                    }
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                }

                /**
                 * 웹 콘솔 로그를 캡처하여 Logger로 출력
                 */
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    // Logger.web()를 사용하여 웹 콘솔 로그 출력
                    Logger.web(consoleMessage)
                    return true
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
                cacheMode = LOAD_NO_CACHE

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
                    Handler(Looper.getMainLooper()).postDelayed({
                        scriptMessage.value = ""
                    }, 1000)
                },
                "uclass" // window.uclass 로 접근 가능
            )

            Logger.info("## WebView loadUrl 호출: $url")
            val headers = mapOf("JWT-TOKEN" to Constants.jwtToken)
            loadUrl(url, headers)
        }
    }

    /**
     * 웹뷰에 JWT 토큰을 JavaScript로 전달
     */
    private fun setTokenToWebView() {
        preloadedWebView?.let { webView ->
            val token = Constants.jwtToken
            if (token.isNotEmpty()) {
                val script = "javascript:setToken('$token')"
                Logger.info("## WebView setToken 실행: $script")

                mainHandler.post {
                    webView.evaluateJavascript(script) { result ->
                        Logger.info("## setToken 실행 결과: $result")
                    }
                }
            } else {
                Logger.error("## JWT Token이 비어있어 setToken을 실행하지 않습니다")
            }
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