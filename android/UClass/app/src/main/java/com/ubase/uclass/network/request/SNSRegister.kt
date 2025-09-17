package com.ubase.uclass.network.request

data class SNSRegister(
    val provider: String,
    val snsId: String,
    val name: String,
    val email: String,
    val phoneNumber: String,
    val profileImageUrl: String,
    val userType: String,
    val branchId: Int,
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean
)