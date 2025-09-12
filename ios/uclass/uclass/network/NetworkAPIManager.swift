import Foundation

/**
 * 네트워크 API 관리자
 * 콜백 등록/제거/알림을 담당하는 중앙 관리 클래스
 * NetworkAPI의 모든 응답은 이 클래스를 통해 등록된 콜백들에게 전달됩니다.
 */
class NetworkAPIManager {
    static let shared = NetworkAPIManager()
    
    private init() {}
    
    // API 엔드포인트 정의
    struct Endpoint {
        static let AUTH_SOCIAL_LOGIN = "/auth/social-login"
    }
    
    // 응답 코드 정의
    struct ResponseCode {
        static let API_ERROR: Int = 9999
        
        // 인증 관련
        static let API_AUTH_SOCIAL_LOGIN: Int = 1001
    }
    
    /**
     * 네트워크 콜백 프로토콜
     */
    protocol NetworkCallback: AnyObject {
        func onResult(code: Int, result: Any?)
    }
    
    // 콜백 저장소 - 스레드 안전성을 위한 concurrent queue 사용
    private var callbacks: [String: NetworkCallback] = [:]
    private let callbackQueue = DispatchQueue(label: "com.uclass.network.callbacks", attributes: .concurrent)
    
    /**
     * 네트워크 콜백 등록
     * @param key 콜백을 식별할 고유 키 (보통 클래스명 또는 고유 식별자)
     * @param callback 등록할 콜백
     */
    func registerCallback(key: String, callback: NetworkCallback) {
        callbackQueue.async(flags: .barrier) {
            self.callbacks[key] = callback
            DispatchQueue.main.async {
                Logger.dev("NetworkCallback registered: \(key) (Total: \(self.callbacks.count))")
            }
        }
    }
    
    /**
     * 네트워크 콜백 제거
     * @param key 제거할 콜백의 키
     */
    func unregisterCallback(key: String) {
        callbackQueue.async(flags: .barrier) {
            if self.callbacks.removeValue(forKey: key) != nil {
                DispatchQueue.main.async {
                    Logger.dev("NetworkCallback unregistered: \(key) (Remaining: \(self.callbacks.count))")
                }
            }
        }
    }
    
    /**
     * 특정 콜백이 등록되어 있는지 확인
     */
    func isCallbackRegistered(key: String) -> Bool {
        var result = false
        callbackQueue.sync {
            result = callbacks.keys.contains(key)
        }
        return result
    }
    
    /**
     * 등록된 콜백 수 반환
     */
    func getRegisteredCallbackCount() -> Int {
        var count = 0
        callbackQueue.sync {
            count = callbacks.count
        }
        return count
    }
    
    /**
     * 모든 콜백 제거
     */
    func clearAllCallbacks() {
        callbackQueue.async(flags: .barrier) {
            let count = self.callbacks.count
            self.callbacks.removeAll()
            DispatchQueue.main.async {
                Logger.dev("All NetworkCallbacks cleared: \(count) callbacks removed")
            }
        }
    }
    
    /**
     * API 결과를 모든 등록된 콜백에 전달
     * NetworkAPI에서 호출되는 메인 메서드
     */
    func notifyResult(code: Int, result: Any?) {
        callbackQueue.async {
            let currentCallbacks = Array(self.callbacks.values)
            
            DispatchQueue.main.async {
                if !currentCallbacks.isEmpty {
                    for callback in currentCallbacks {
                        callback.onResult(code: code, result: result)
                    }
                } else {
                    Logger.dev("No callbacks registered - Result ignored (Code: \(code))")
                }
            }
        }
    }
}
