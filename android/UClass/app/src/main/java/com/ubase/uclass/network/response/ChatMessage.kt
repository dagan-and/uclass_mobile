package com.ubase.uclass.network.response

import com.google.gson.annotations.SerializedName
import com.ubase.uclass.util.Constants

/**
 * 메시지 데이터 모델
 */
data class ChatMessage(
    @SerializedName("messageId")
    val messageId: String,

    @SerializedName("senderId")
    val senderId: Int,

    @SerializedName("senderType")
    val senderType: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("receiverId")
    val receiverId: Int,

    @SerializedName("receiverType")
    val receiverType: String,

    @SerializedName("receiverName")
    val receiverName: String,

    @SerializedName("branchId")
    val branchId: Int,

    @SerializedName("branchName")
    val branchName: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("isRead")
    val isRead: Boolean,

    @SerializedName("readAt")
    val readAt: String?,

    @SerializedName("sentAt")
    val sentAt: String,

    @SerializedName("roomId")
    val roomId: String
) {
    // isMe 계산 프로퍼티
    val isMe: Boolean
        get() = senderId.toInt() == Constants.getUserId()

    // 표시할 텍스트 (content 필드 사용)
    val text: String
        get() = content

    // 타임스탬프 (sentAt 필드 사용)
    val timestamp: String
        get() = sentAt
}