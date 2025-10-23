package com.ubase.uclass.network.response

import com.google.gson.annotations.SerializedName

/**
 * SNS 체크 응답 데이터 모델
 */
data class SNSCheckData(
    @SerializedName("existingUser")
    val isExistingUser: Boolean,

    @SerializedName("redirectUrl")
    val redirectUrl: String
)