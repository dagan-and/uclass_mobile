package com.ubase.uclass.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.RELOAD

class ReloadViewModel : ViewModel() {

    var reload by mutableStateOf(false)
        private set

    private val callbackKey = "Reload"

    init {
        // ViewCallbackManager 에 콜백 등록
        ViewCallbackManager.registerCallback(callbackKey, object : ViewCallbackManager.ViewCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == RELOAD) {
                    reload = (result as? Boolean) == true
                }
            }
        })
    }

    fun reset() {
        reload = false
    }

    override fun onCleared() {
        super.onCleared()
        ViewCallbackManager.unregisterCallback(callbackKey)
    }
}
