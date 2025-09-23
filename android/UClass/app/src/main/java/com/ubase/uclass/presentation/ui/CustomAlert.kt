package com.ubase.uclass.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// -------------------- Alert Data Model --------------------
data class AlertData(
    val title: String,
    val content: String,
    val confirmText: String = "확인",
    val cancelText: String = "취소",
    val showCancelButton: Boolean = true,
    val onConfirm: (() -> Unit)? = null
)

// -------------------- Manager --------------------
object CustomAlertManager {
    private val _isPresented = MutableStateFlow(false)
    val isPresented: StateFlow<Boolean> = _isPresented

    private val _alertData = MutableStateFlow<AlertData?>(null)
    val alertData: StateFlow<AlertData?> = _alertData

    /**
     * 기본 알림 (확인 버튼만)
     */
    fun showAlert(
        title: String = "알림",
        content: String,
        confirmText: String = "확인",
        onConfirm: (() -> Unit)? = null
    ) {
        _alertData.value = AlertData(
            title = title,
            content = content,
            confirmText = confirmText,
            showCancelButton = false,
            onConfirm = onConfirm
        )
        _isPresented.value = true
    }

    /**
     * 확인/취소 알림
     */
    fun showConfirmAlert(
        title: String = "확인",
        content: String,
        confirmText: String = "확인",
        cancelText: String = "취소",
        onConfirm: (() -> Unit)? = null
    ) {
        _alertData.value = AlertData(
            title = title,
            content = content,
            confirmText = confirmText,
            cancelText = cancelText,
            showCancelButton = true,
            onConfirm = onConfirm
        )
        _isPresented.value = true
    }

    /**
     * 에러 알림
     */
    fun showErrorAlert(
        title: String = "오류",
        content: String,
        confirmText: String = "확인",
        onConfirm: (() -> Unit)? = null
    ) {
        _alertData.value = AlertData(
            title = title,
            content = content,
            confirmText = confirmText,
            showCancelButton = false,
            onConfirm = onConfirm
        )
        _isPresented.value = true
    }

    /**
     * 로그인 정보 알림 (특화된 함수)
     */
    fun showLoginInfoAlert(
        userName: String,
        branchName: String,
        approvalStatus: String,
        loginAt: String,
        userType: String = ""
    ) {
        val statusMessage = when (approvalStatus) {
            "PENDING" -> "승인 대기 중"
            "APPROVED" -> "승인됨"
            "REJECTED" -> "승인 거부됨"
            else -> approvalStatus
        }

        val content = """
            환영합니다, ${userName}님!
            
            소속: $branchName
            상태: $statusMessage
            접속 시간: $loginAt
            ${if (userType.isNotEmpty()) "사용자 타입: $userType" else ""}
        """.trimIndent()

        showAlert(
            title = "로그인 성공",
            content = content,
            confirmText = "확인"
        )
    }

    /**
     * 알림 닫기
     */
    fun hideAlert() {
        _isPresented.value = false
        _alertData.value = null
    }
}

// -------------------- Alert UI Component --------------------
@Composable
fun CustomAlert() {
    val isPresented = CustomAlertManager.isPresented.collectAsState()
    val alertData = CustomAlertManager.alertData.collectAsState()

    if (isPresented.value && alertData.value != null) {
        val data = alertData.value!!

        Dialog(
            onDismissRequest = { CustomAlertManager.hideAlert() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 제목
                    Text(
                        text = data.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 내용
                    Text(
                        text = data.content,
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (data.showCancelButton) {
                            Arrangement.SpaceEvenly
                        } else {
                            Arrangement.Center
                        }
                    ) {
                        // 취소 버튼
                        if (data.showCancelButton) {
                            Button(
                                onClick = { CustomAlertManager.hideAlert() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5F5F5)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = data.cancelText,
                                    color = Color(0xFF666666),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        // 확인 버튼
                        Button(
                            onClick = {
                                data.onConfirm?.invoke()
                                CustomAlertManager.hideAlert()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0022EE)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = data.confirmText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}