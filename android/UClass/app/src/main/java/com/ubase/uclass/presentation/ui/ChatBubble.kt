package com.ubase.uclass.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubase.uclass.network.response.ChatMessage
import com.ubase.uclass.util.DateUtils
import com.ubase.uclass.util.Logger
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatBubble(message: ChatMessage) {

    // timestamp를 오전/오후 시:분 형태로 변환
    val displayTime = formatTimestamp(message.timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (message.isMe) {
            // 내 메시지: 시간 표시 왼쪽, 말풍선 오른쪽
            Text(
                text = displayTime,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFF0022EE),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // 상대방 메시지: 말풍선 왼쪽, 시간 표시 오른쪽
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFFE0E0E0), // 연한 회색
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = displayTime,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * timestamp를 오전/오후 시:분 형태로 변환
 * @param timestamp "2025-09-19 22:59:19" 형태의 문자열
 * @return "오후 10:59" 형태의 문자열
 */
private fun formatTimestamp(timestamp: String): String {
    return try {
        // 입력 형태: "2025-09-19 22:59:19"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        // 출력 형태: "오전/오후 h:mm"
        val outputFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)

        val date = inputFormat.parse(timestamp)
        date?.let { outputFormat.format(it) } ?: timestamp
    } catch (e: Exception) {
        Logger.error("timestamp 파싱 실패: $timestamp, error: ${e.message}")
        // 파싱 실패 시 원본 반환
        timestamp
    }
}

@Composable
fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color(0xFFE8E8E8),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = date,
                color = Color(0xFF666666),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}