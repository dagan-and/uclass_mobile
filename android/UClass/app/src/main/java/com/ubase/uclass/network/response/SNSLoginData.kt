package com.ubase.uclass.network.response

import com.google.gson.annotations.SerializedName

/**
 * SNS 로그인 응답 데이터 모델
 */
data class SNSLoginData(
    @SerializedName("userId")
    val userId: Int,

    @SerializedName("approvalStatus")
    val approvalStatus: String,

    @SerializedName("userName")
    val userName: String,

    @SerializedName("branchName")
    val branchName: String,

    @SerializedName("tokenType")
    val tokenType: String,

    @SerializedName("loginAt")
    val loginAt: String,

    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("expiresIn")
    val expiresIn: Int,

    @SerializedName("userType")
    val userType: String,

    @SerializedName("branchId")
    val branchId: Int,

    @SerializedName("redirectUrl")
    val redirectUrl: String,

    @SerializedName("reasonUrl")
    val reasonUrl: String
)