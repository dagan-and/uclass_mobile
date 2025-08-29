package com.ubase.uclass.presentation.web


import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf

class WebViewManager(private val context: Context) {

    var preloadedWebView: WebView? = null
        private set

    val isWebViewLoaded = mutableStateOf(false)
    val isWebViewLoading = mutableStateOf(false)
    val webViewLoadingProgress = mutableStateOf(0)

    fun preloadWebView(url: String = "https://naver.com") {
        isWebViewLoading.value = true
        isWebViewLoaded.value = false

        preloadedWebView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isWebViewLoading.value = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isWebViewLoading.value = false
                    isWebViewLoaded.value = true
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    webViewLoadingProgress.value = 90
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    webViewLoadingProgress.value = newProgress
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }

            loadUrl(url)
        }
    }

    fun reload() {
        preloadedWebView?.reload()
    }

    fun loadUrl(url: String) {
        preloadedWebView?.loadUrl(url)
    }

    fun destroy() {
        preloadedWebView?.destroy()
        preloadedWebView = null
    }
}