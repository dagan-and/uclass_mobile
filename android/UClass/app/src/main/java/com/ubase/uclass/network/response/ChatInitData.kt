package com.ubase.uclass.network.response

import com.google.gson.annotations.SerializedName

/**
 * 채팅 초기화 API 응답 데이터 모델
 */
data class ChatInitData(
    @SerializedName("roomId")
    val roomId: String,

    @SerializedName("branchName")
    val branchName: String,

    @SerializedName("messages")
    val messages: List<ChatMessage>
)