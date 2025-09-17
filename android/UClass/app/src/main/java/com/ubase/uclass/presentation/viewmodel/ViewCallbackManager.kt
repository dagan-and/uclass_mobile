package com.ubase.uclass.network

import com.ubase.uclass.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * View 상태 관리자
 * 콜백 등록/제거/알림을 담당하는 중앙 관리 클래스
 * View 의 모든 응답은 이 클래스를 통해 등록된 콜백들에게 전달됩니다.
 */
object ViewCallbackManager {


    // 응답 코드 정의
    object ResponseCode {
        //채팅 뱃지
        const val CHAT_BADGE: Int = 1
        //화면 이동
        const val NAVIGATION : Int = 2
        //로그아웃
        const val LOGOUT : Int = 3
    }

    // 페이지 코드 정의
    object PageCode {
        const val HOME: Int = 0
        const val CHAT : Int = 1
        const val NOTICE : Int = 2
    }

    /**
     * 뷰 콜백 인터페이스
     */
    interface ViewCallback {
        fun onResult(code: Int, result: Any?)
    }

    // 콜백 저장소 - ConcurrentHashMap을 사용하여 스레드 안전성 보장
    private val callbacks = ConcurrentHashMap<String, ViewCallback>()

    /**
     * 네트워크 콜백 등록
     * @param key 콜백을 식별할 고유 키 (보통 클래스명 또는 고유 식별자)
     * @param callback 등록할 콜백
     */
    fun registerCallback(key: String, callback: ViewCallback) {
        callbacks[key] = callback
        Logger.dev("ViewCallback registered: $key (Total: ${callbacks.size})")
    }

    /**
     * 네트워크 콜백 제거
     * @param key 제거할 콜백의 키
     */
    fun unregisterCallback(key: String) {
        callbacks.remove(key)?.let {
            Logger.dev("ViewCallback unregistered: $key (Remaining: ${callbacks.size})")
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
        Logger.dev("All ViewCallbacks cleared: $count callbacks removed")
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