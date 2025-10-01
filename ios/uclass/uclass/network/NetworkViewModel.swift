import Foundation
import SwiftUI

/**
 * 범용 네트워크 API 처리 ViewModel
 * 모든 네트워크 요청을 공통으로 처리할 수 있는 재사용 가능한 ViewModel
 */
class NetworkViewModel: ObservableObject, NetworkAPIManager.NetworkCallback {
    
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isCompleted = false
    @Published var responseData: Any?
    
    private let callbackKey: String
    private var targetResponseCode: Int?
    private var onSuccess: ((Any?) -> Void)?
    private var onError: ((String) -> Void)?
    
    init(identifier: String = UUID().uuidString) {
        self.callbackKey = "NetworkViewModel_\(identifier)"
        Logger.dev("NetworkViewModel Init: \(callbackKey)")
        registerNetworkCallback()
    }
    
    deinit {
        Logger.dev("NetworkViewModel Clear: \(callbackKey)")
        unregisterNetworkCallback()
    }
    
    /**
     * SNS Check API 호출
     */
    func callSNSCheck(
        snsType: String,
        snsId: String,
        onSuccess: ((Any?) -> Void)? = nil,
        onError: ((String) -> Void)? = nil
    ) {
        executeAPI(targetCode: NetworkAPIManager.ResponseCode.API_AUTH_SNS_CHECK, onSuccess: onSuccess, onError: onError)
        NetworkAPI.shared.snsCheck(snsType: snsType, snsId: snsId)
    }
    
    /**
     * SNS Login API 호출
     */
    func callSNSLogin(
        snsType: String,
        snsId: String,
        onSuccess: ((Any?) -> Void)? = nil,
        onError: ((String) -> Void)? = nil
    ) {
        executeAPI(targetCode: NetworkAPIManager.ResponseCode.API_AUTH_SNS_LOGIN, onSuccess: onSuccess, onError: onError)
        NetworkAPI.shared.snsLogin(snsType: snsType, snsId: snsId)
    }
    
    /**
     * SNS Register API 호출
     */
    func callSNSRegister(
        snsType: String,
        snsId: String,
        name: String,
        email: String,
        phoneNumber: String = "010-1234-5678",
        profileImageUrl: String = "",
        userType: String = "STUDENT",
        branchId: Int = 10000001,
        termsAgreed: Bool = true,
        privacyAgreed: Bool = true,
        onSuccess: ((Any?) -> Void)? = nil,
        onError: ((String) -> Void)? = nil
    ) {
        executeAPI(targetCode: NetworkAPIManager.ResponseCode.API_AUTH_SNS_REGISTER, onSuccess: onSuccess, onError: onError)
        
        NetworkAPI.shared.snsRegister(
            snsType: snsType,
            snsId: snsId,
            name: name,
            email: email,
            phoneNumber: phoneNumber,
            profileImageUrl: profileImageUrl,
            userType: userType,
            branchId: branchId,
            termsAgreed: termsAgreed,
            privacyAgreed: privacyAgreed
        )
    }
    
    /**
     * 채팅방 초기 데이터 호출
     */
    func callChatInit(
        userId: String,
        onSuccess: ((Any?) -> Void)? = nil,
        onError: ((String) -> Void)? = nil
    ) {
        executeAPI(targetCode: NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT, onSuccess: onSuccess, onError: onError)
        NetworkAPI.shared.chatInit(userId: userId)
    }
    
    /**
     * 채팅 추가 메시지 호출
     */
    func callChatMessage(
        userId: String,
        branchId: String,
        page: Int,
        size: Int,
        onSuccess: ((Any?) -> Void)? = nil,
        onError: ((String) -> Void)? = nil
    ) {
        executeAPI(targetCode: NetworkAPIManager.ResponseCode.API_DM_NATIVE_MESSAGES, onSuccess: onSuccess, onError: onError)
        NetworkAPI.shared.chatMessage(userId: userId, branchId: branchId, page: page, size: size)
    }
    
    /**
     * 일반적인 API 호출 메서드
     * @param targetCode 응답을 기다릴 응답 코드
     * @param onSuccess 성공 시 콜백
     * @param onError 실패 시 콜백
     */
    func executeAPI(targetCode: Int, onSuccess: ((Any?) -> Void)? = nil, onError: ((String) -> Void)? = nil) {
        DispatchQueue.main.async {
            self.isLoading = true
            self.errorMessage = nil
            self.isCompleted = false
            self.responseData = nil
        }
        
        self.targetResponseCode = targetCode
        self.onSuccess = onSuccess
        self.onError = onError
    }
    
    /**
     * 상태 초기화
     */
    func resetState() {
        DispatchQueue.main.async {
            self.isLoading = false
            self.errorMessage = nil
            self.isCompleted = false
            self.responseData = nil
            self.targetResponseCode = nil
            self.onSuccess = nil
            self.onError = nil
        }
    }
    
    /**
     * 에러 메시지 클리어
     */
    func clearError() {
        DispatchQueue.main.async {
            self.errorMessage = nil
        }
    }
    
    // MARK: - Network Callback Management
    
    private func registerNetworkCallback() {
        // NetworkAPI가 초기화되었는지 확인
        if !NetworkAPI.shared.getIsInitialized() {
            Logger.error("NetworkAPI is not initialized. Make sure AppDelegate initialized it.")
            return
        }
        
        NetworkAPIManager.shared.registerCallback(key: callbackKey, callback: self)
        Logger.dev("NetworkViewModel callback registered: \(callbackKey)")
    }
    
    func unregisterNetworkCallback() {
        NetworkAPIManager.shared.unregisterCallback(key: callbackKey)
        Logger.dev("NetworkViewModel callback unregistered: \(callbackKey)")
    }
    
    // MARK: - NetworkCallback 구현
    
    func onResult(code: Int, result: Any?) {
        DispatchQueue.main.async {
            // 타겟 응답 코드와 일치하는지 확인
            if let targetCode = self.targetResponseCode, code == targetCode {
                self.handleTargetResponse(result: result)
            } else if code == NetworkAPIManager.ResponseCode.API_ERROR {
                self.handleError(result: result)
            } else {
                Logger.dev("Received non-target response code: \(code) (expecting: \(self.targetResponseCode ?? -1))")
            }
        }
    }
    
    // MARK: - Private Methods
    
    private func handleTargetResponse(result: Any?) {
            Logger.dev("Target API response received for callback: \(callbackKey)")
            
            // 응답 데이터 저장
            self.responseData = result
            
            // 상태 업데이트
            self.isLoading = false
            
            // 성공 콜백 호출
            self.onSuccess?(result)
        }
    
    private func handleError(result: Any?) {
        self.isLoading = false
        self.isCompleted = false
        
        var errorMsg = "알 수 없는 오류가 발생했습니다."
        
        if let errorData = result as? ErrorData {
            let errorCode = errorData.getCode()
            let errorMessage = errorData.getMsg() ?? "알 수 없는 오류가 발생했습니다."
            
            Logger.error("API Error - Code: \(errorCode), Message: \(errorMessage)")
            errorMsg = "\(errorMessage)"
        } else {
            Logger.error("Unknown API error occurred")
            errorMsg = "네트워크 오류가 발생했습니다. 다시 시도해주세요."
        }
        
        self.errorMessage = errorMsg
        
        // 에러 콜백 호출
        self.onError?(errorMsg)
    }
    
    func handleSuccess() {
        self.isCompleted = true
    }
}
