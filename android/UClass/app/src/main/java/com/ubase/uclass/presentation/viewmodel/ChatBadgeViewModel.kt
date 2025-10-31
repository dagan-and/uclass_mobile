package com.ubase.uclass.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ubase.uclass.App
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.CHAT_BADGE
import com.ubase.uclass.util.BadgeManager

class ChatBadgeViewModel : ViewModel() {

    var chatBadgeVisible by mutableStateOf(false)
        private set

    private val callbackKey = "ChatBadge"

    init {
        // ViewCallbackManager 에 콜백 등록
        ViewCallbackManager.registerCallback(callbackKey, object : ViewCallbackManager.ViewCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == CHAT_BADGE) {
                    chatBadgeVisible = (result as? Boolean) == true
                }
            }
        })
        initBadge()
    }

    private fun initBadge() {
        if(BadgeManager.getInstance().getBadgeCount(App.context()) > 0) {
            chatBadgeVisible = true
        }
    }

    fun hideBadge() {
        chatBadgeVisible = false
    }

    override fun onCleared() {
        super.onCleared()
    }
}
