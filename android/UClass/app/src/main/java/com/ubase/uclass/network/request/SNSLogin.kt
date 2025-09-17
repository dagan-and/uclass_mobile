package com.ubase.uclass.network.request

data class SNSLogin(
    val provider: String,
    val snsId: String,
    val pushToken : String
)