package com.ubase.uclass.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION

class NavigationViewModel : ViewModel() {

    var navigation by mutableStateOf(0)
        private set

    private val callbackKey = "Navigation"



    init {
        // ViewCallbackManager 에 콜백 등록
        ViewCallbackManager.registerCallback(callbackKey, object : ViewCallbackManager.ViewCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == NAVIGATION) {
                    navigation = ((result as? Int)!!)
                }
            }
        })
    }


    override fun onCleared() {
        super.onCleared()
    }
}
