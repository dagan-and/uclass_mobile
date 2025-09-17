package com.ubase.uclass.util

object Constants {
    var baseURL = "https://dev-umanager.ubase.kr"
    var jwtToken : String = ""
    var fcmToken : String = ""
    var refreshToken : String = ""
    var tokenExpired : String = ""

    //앱 디버깅 설정
    var isDebug = false

    //푸시로 실행됐는지 Flag
    var isPushStart : Boolean = false

    //AppConfig 메모리 정리 여부 체크
    fun isClearedData() : Boolean{
        //API 객체가 잘있는지 체크하기
        return false
    }
}