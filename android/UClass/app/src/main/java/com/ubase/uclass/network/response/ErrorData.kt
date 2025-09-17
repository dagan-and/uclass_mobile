package com.ubase.uclass.network.response

/**
 * 에러 데이터 클래스
 * 네트워크 API 에러 정보를 담는 데이터 클래스
 */
data class ErrorData(
    val code: Int,
    val msg: String?
)