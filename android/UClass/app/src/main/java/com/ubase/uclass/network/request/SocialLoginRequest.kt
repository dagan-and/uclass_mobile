package com.ubase.uclass.network.request

data class SocialLoginRequest(
    val provider: String,
    val token: String,
    val userType: String,
    val branchId: Int
)