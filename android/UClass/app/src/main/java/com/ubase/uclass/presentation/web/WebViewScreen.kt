package com.ubase.uclass.presentation.web


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.presentation.ui.CustomAlert
import com.ubase.uclass.util.Logger

@Composable
fun WebViewScreen(webViewManager: WebViewManager) {
    val scriptMsg = webViewManager.scriptMessage.value

    Box(modifier = Modifier.fillMaxSize()) {
        if (webViewManager.preloadedWebView != null && webViewManager.isWebViewLoaded.value) {
            // WebView 로딩 완료 시 표시
            AndroidView(
                factory = { webViewManager.preloadedWebView!! },
                modifier = Modifier.fillMaxSize()
            )

            if (scriptMsg != null) {
                //여기에서 스크립트및  UI 처리
                if(scriptMsg.contains("chat")) {
                    //chatBadgeViewModel.setChatBadge(true)
                }
            }
        }
    }
}
