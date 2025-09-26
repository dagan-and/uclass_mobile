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

    @SerializedName("hasMore")
    val hasMore: Boolean,

    @SerializedName("page")
    val page: Int,

    @SerializedName("totalPages")
    val totalPages: Int,

    @SerializedName("totalMessageCount")
    val totalMessageCount: Int,

    @SerializedName("messages")
    val messages: List<ChatMessage>
)