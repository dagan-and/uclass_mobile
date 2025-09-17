package com.ubase.uclass.network.response

import com.google.gson.annotations.SerializedName

/**
 * 기본 API 응답 데이터 모델 (제네릭 버전)
 */
data class BaseData<T>(
    @SerializedName("success")
    val isSuccess: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: T? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * 빈 데이터용 클래스
 */
class EmptyData