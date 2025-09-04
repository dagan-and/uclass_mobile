package com.ubase.uclass.presentation.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class ChatBadgeViewModel : ViewModel() {
    private val _chatBadgeVisible = mutableStateOf(false)
    val chatBadgeVisible: MutableState<Boolean> = _chatBadgeVisible

    fun setChatBadge(visible: Boolean) {
        if (_chatBadgeVisible.value != visible) {
            _chatBadgeVisible.value = visible
        }
    }

    fun clearAllBadges() {
        setChatBadge(false)
    }
}
