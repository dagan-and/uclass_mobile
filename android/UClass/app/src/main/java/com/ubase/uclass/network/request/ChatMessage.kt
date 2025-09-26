package com.ubase.uclass.network.request

data class ChatMessage (
    val userId: Int,
    val branchId: Int,
    val page: Int,
    val size: Int
)