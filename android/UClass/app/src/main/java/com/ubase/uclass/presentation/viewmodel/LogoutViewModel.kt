package com.ubase.uclass.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.CHAT_BADGE
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.LOGOUT

class LogoutViewModel : ViewModel() {

    var logout by mutableStateOf(false)
        private set

    private val callbackKey = "Logout"

    init {
        // ViewCallbackManager 에 콜백 등록
        ViewCallbackManager.registerCallback(callbackKey, object : ViewCallbackManager.ViewCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == LOGOUT) {
                    logout = (result as? Boolean) == true
                }
            }
        })
    }

    fun reset() {
        logout = false
    }

    override fun onCleared() {
        super.onCleared()
        ViewCallbackManager.unregisterCallback(callbackKey)
    }
}
