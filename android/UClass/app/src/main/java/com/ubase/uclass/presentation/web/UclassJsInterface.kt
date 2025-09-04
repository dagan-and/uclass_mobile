package com.ubase.uclass.presentation.web

import android.content.Context
import android.webkit.JavascriptInterface

class UclassJsInterface(private val context: Context, private val onMessage:  (String) -> Unit) {

    @JavascriptInterface
    fun postMessage(message: String) {
        // 웹에서 호출되는 지점
        onMessage(message)
    }
}
