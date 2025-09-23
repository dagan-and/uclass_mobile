package com.ubase.uclass.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// -------------------- Manager --------------------
object CustomLoadingManager {
    private val _isPresented = MutableStateFlow(false)
    val isPresented: StateFlow<Boolean> = _isPresented

    fun showLoading() { _isPresented.value = true }
    fun hideLoading() { _isPresented.value = false }
}

// -------------------- Dialog UI --------------------
@Composable
fun CustomLoading() {
    val isPresented = CustomLoadingManager.isPresented.collectAsState()

    if (isPresented.value) {
        Dialog(
            onDismissRequest = { /* 로딩은 보통 취소 불가 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false // 배경 전체 반투명하게
            )
        ) {
            // Dialog content (센터에 스피너)
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = Color(0xFF0022EE),
                    strokeWidth = 6.dp
                )
            }
        }
    }
}
