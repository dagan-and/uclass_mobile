package com.ubase.uclass.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun CustomAlert(
    title: String,
    content: String,
    confirmText: String = "확인",
    cancelText: String = "취소",
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    showCancelButton: Boolean = true
) {
    Dialog(
        onDismissRequest = { onDismiss() },
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
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 내용
                Text(
                    text = content,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (showCancelButton) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.Center
                    }
                ) {
                    // 취소 버튼
                    if (showCancelButton) {
                        Button(
                            onClick = { onDismiss() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = cancelText,
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
                            onConfirm?.invoke()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F63D2)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = confirmText,
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
//
//// 사용 예시
//@Composable
//fun CustomDialogExample() {
//    // 다이얼로그 표시 여부를 관리하는 상태
//    val showDialog = remember { mutableStateOf(false) }
//
//    // 다이얼로그 트리거 버튼
//    Button(onClick = { showDialog.value = true }) {
//        Text("다이얼로그 열기")
//    }
//
//    // 다이얼로그 표시
//    if (showDialog.value) {
//        CustomDialog(
//            title = "알림",
//            content = "정말로 삭제하시겠습니까?\n삭제된 데이터는 복구할 수 없습니다.",
//            confirmText = "삭제",
//            cancelText = "취소",
//            onDismiss = { showDialog.value = false },
//            onConfirm = {
//                // 확인 버튼 클릭 시 실행할 로직
//                println("삭제 확인됨")
//            }
//        )
//    }
//}