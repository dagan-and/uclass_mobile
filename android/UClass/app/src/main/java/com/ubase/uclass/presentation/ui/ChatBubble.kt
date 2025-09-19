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

@Composable
fun ChatBubble(message: ChatMessage) {
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
                text = DateUtils.formatTime(message.timestamp),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFF2E7D32), // 진한 초록
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
                text = DateUtils.formatTime(message.timestamp),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
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