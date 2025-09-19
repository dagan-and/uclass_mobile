package com.ubase.uclass.network.response

import java.util.*

/**
 * 채팅 메시지 데이터 모델
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isMe: Boolean,
    val timestamp: Date = Date()
)

sealed class ChatItem {
    data class MessageItem(val message: ChatMessage) : ChatItem()
    data class DateItem(val date: String) : ChatItem()
}