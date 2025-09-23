package com.ubase.uclass.network

import com.ubase.uclass.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 네트워크 API 관리자
 * 콜백 등록/제거/알림을 담당하는 중앙 관리 클래스
 * NetworkAPI의 모든 응답은 이 클래스를 통해 등록된 콜백들에게 전달됩니다.
 */
object NetworkAPIManager {

    // API 엔드포인트 정의
    object Endpoint {
        //인증관리
        const val API_AUTH_SNS_CHECK = "/api/auth/sns/check"
        const val API_AUTH_SNS_LOGIN = "/api/auth/sns/login"
        const val API_AUTH_SNS_REGISTER = "/api/auth/sns/register"

        //채팅
        const val API_DM_NATIVE_INIT = "/api/dm/native/init"
        const val API_DM_NATIVE_MESSAGES = "/api/dm/native/messages"
        const val API_DM_NATIVE_READ = "/api/dm/native/read"
        const val API_DM_NATIVE_SEND = "/api/dm/native/send"
        const val API_DM_NATIVE_STATUS = "/api/dm/native/status"
        const val API_DM_NATIVE_UNREAD = "/api/dm/native/unread"
    }

    // 응답 코드 정의
    object ResponseCode {
        const val API_ERROR: Int = 9999

        // 인증 관련
        const val API_AUTH_SNS_CHECK : Int = 1001
        const val API_AUTH_SNS_LOGIN : Int = 1002
        const val API_AUTH_SNS_REGISTER : Int = 1003

        // 채팅 관련
        const val API_DM_NATIVE_INIT : Int = 2001
        const val API_DM_NATIVE_MESSAGES : Int = 2002
        const val API_DM_NATIVE_READ : Int = 2003
        const val API_DM_NATIVE_SEND : Int = 2004
        const val API_DM_NATIVE_STATUS : Int = 2005
        const val API_DM_NATIVE_UNREAD : Int = 2006
    }

    /**
     * 네트워크 콜백 인터페이스
     */
    interface NetworkCallback {
        fun onResult(code: Int, result: Any?)
    }

    // 콜백 저장소 - ConcurrentHashMap을 사용하여 스레드 안전성 보장
    private val callbacks = ConcurrentHashMap<String, NetworkCallback>()

    /**
     * 네트워크 콜백 등록
     * @param key 콜백을 식별할 고유 키 (보통 클래스명 또는 고유 식별자)
     * @param callback 등록할 콜백
     */
    fun registerCallback(key: String, callback: NetworkCallback) {
        callbacks[key] = callback
        Logger.dev("NetworkCallback registered: $key (Total: ${callbacks.size})")
    }

    /**
     * 네트워크 콜백 제거
     * @param key 제거할 콜백의 키
     */
    fun unregisterCallback(key: String) {
        callbacks.remove(key)?.let {
            Logger.dev("NetworkCallback unregistered: $key (Remaining: ${callbacks.size})")
        }
    }

    /**
     * 특정 콜백이 등록되어 있는지 확인
     */
    fun isCallbackRegistered(key: String): Boolean {
        return callbacks.containsKey(key)
    }

    /**
     * 등록된 콜백 수 반환
     */
    fun getRegisteredCallbackCount(): Int {
        return callbacks.size
    }

    /**
     * 모든 콜백 제거
     */
    fun clearAllCallbacks() {
        val count = callbacks.size
        callbacks.clear()
        Logger.dev("All NetworkCallbacks cleared: $count callbacks removed")
    }

    /**
     * API 결과를 모든 등록된 콜백에 전달
     * NetworkAPI에서 호출되는 메인 메서드
     */
    fun notifyResult(code: Int, result: Any?) {
        if (callbacks.isNotEmpty()) {
            try {
                callbacks.values.forEach { callback ->
                    try {
                        callback.onResult(code, result)
                    } catch (e: Exception) {
                        Logger.error("Error in callback notification for code: $code\n$e")
                    }
                }
            } catch (e: Exception) {
                Logger.error("Error in notifyResult\n$e")
            }
        } else {
            Logger.dev("No callbacks registered - Result ignored (Code: $code)")
        }
    }

}