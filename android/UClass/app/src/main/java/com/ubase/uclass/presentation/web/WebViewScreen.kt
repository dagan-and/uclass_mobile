package com.ubase.uclass.presentation.web


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebViewScreen(webViewManager: WebViewManager) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (webViewManager.preloadedWebView != null && webViewManager.isWebViewLoaded.value) {
            // WebView 로딩 완료 시 표시
            AndroidView(
                factory = { webViewManager.preloadedWebView!! },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
