import Foundation
import Combine
import KakaoSDKAuth
import KakaoSDKUser
import KakaoSDKCommon

class KakaoLoginManager: NSObject, ObservableObject {
    @Published var isLoggedIn = false
    @Published var userInfo: KakaoUserInfo?
    @Published var errorMessage: String?
    @Published var isLoading = false
    
    // SNS 토큰 및 타입 정보
    var snsType: String {
        return "kakao"
    }
    
    var snsToken: String {
        return TokenManager.manager.getToken()?.accessToken ?? ""
    }
    
    override init() {
        super.init()
    }
    
    // 카카오 로그인 시작
    func startKakaoLogin() {
        isLoading = true
        errorMessage = nil
        
        Logger.dev("카카오 로그인 시작")
        
        // 카카오톡 앱이 설치되어 있는지 확인
        if UserApi.isKakaoTalkLoginAvailable() {
            // 카카오톡으로 로그인
            loginWithKakaoTalk()
        } else {
            // 웹으로 로그인
            loginWithKakaoAccount()
        }
    }
    
    // 카카오톡 앱으로 로그인
    private func loginWithKakaoTalk() {
        UserApi.shared.loginWithKakaoTalk { [weak self] (oauthToken, error) in
            DispatchQueue.main.async {
                self?.handleLoginResult(oauthToken: oauthToken, error: error, loginType: "카카오톡")
            }
        }
    }
    
    // 웹으로 로그인
    private func loginWithKakaoAccount() {
        UserApi.shared.loginWithKakaoAccount { [weak self] (oauthToken, error) in
            DispatchQueue.main.async {
                self?.handleLoginResult(oauthToken: oauthToken, error: error, loginType: "웹")
            }
        }
    }
    
    // 로그인 결과 처리
    private func handleLoginResult(oauthToken: OAuthToken?, error: Error?, loginType: String) {
        isLoading = false
        
        if let error = error {
            Logger.dev("\(loginType) 로그인 실패: \(error)")
            handleLoginError(error)
            
            // 카카오톡 로그인 실패 시 웹 로그인으로 재시도
            if loginType == "카카오톡" {
                Logger.dev("웹 로그인으로 재시도")
                loginWithKakaoAccount()
                return
            }
        } else {
            Logger.dev("\(loginType) 로그인 성공")
            if let token = oauthToken {
                Logger.dev("Access Token: \(token.accessToken)")
            }
            fetchUserInfo()
        }
    }
    
    // 로그인 에러 처리
    private func handleLoginError(_ error: Error) {
        if let sdkError = error as? SdkError {
            switch sdkError {
            case .ClientFailed(let reason, let errorMessage):
                switch reason {
                case .Cancelled:
                    self.errorMessage = "로그인이 취소되었습니다."
                case .TokenNotFound:
                    self.errorMessage = "토큰을 찾을 수 없습니다."
                default:
                    self.errorMessage = errorMessage ?? "클라이언트 오류가 발생했습니다."
                }
            default:
                self.errorMessage = "로그인 중 오류가 발생했습니다."
            }
        } else {
            self.errorMessage = error.localizedDescription
        }
    }
    
    // 사용자 정보 가져오기
    private func fetchUserInfo() {
        UserApi.shared.me { [weak self] (user, error) in
            DispatchQueue.main.async {
                if let error = error {
                    Logger.dev("카카오 사용자 정보 가져오기 실패: \(error)")
                    self?.errorMessage = "사용자 정보를 가져오는데 실패했습니다."
                } else if let user = user {
                    let userInfo = KakaoUserInfo(
                        userId: "\(user.id ?? 0)",
                        nickname: user.kakaoAccount?.profile?.nickname ?? "",
                        email: user.kakaoAccount?.email ?? "",
                        profileImageUrl: user.kakaoAccount?.profile?.profileImageUrl,
                        thumbnailImageUrl: user.kakaoAccount?.profile?.thumbnailImageUrl,
                        isEmailVerified: user.kakaoAccount?.isEmailVerified ?? false,
                        isEmailValid: user.kakaoAccount?.isEmailValid ?? false
                    )
                    
                    self?.userInfo = userInfo
                    self?.isLoggedIn = true
                    
                    // 로그인 정보 저장
                    self?.saveLoginInfo()
                    
                    Logger.dev("=== 카카오 로그인 정보 ===")
                    Logger.dev("User ID: \(userInfo.userId)")
                    Logger.dev("Nickname: \(userInfo.nickname)")
                    Logger.dev("Email: \(userInfo.email)")
                    Logger.dev("Profile Image: \(userInfo.profileImageUrl?.absoluteString ?? "없음")")
                    Logger.dev("Email Verified: \(userInfo.isEmailVerified)")
                    Logger.dev("Access Token: \(self?.snsToken ?? "")")
                    Logger.dev("========================")
                }
            }
        }
    }
    
    // 로그인 정보 저장
    private func saveLoginInfo() {
        guard let userInfo = userInfo else { return }
        
        UserDefaultsManager.saveLoginInfo(
            snsType: snsType,
            snsToken: snsToken,
            userId: userInfo.userId,
            email: userInfo.email,
            name: userInfo.displayName
        )
        
        Logger.dev("카카오 로그인 정보 저장 완료")
        UserDefaultsManager.printSavedLoginInfo()
    }
}

// MARK: - KakaoUserInfo Model

struct KakaoUserInfo: Codable {
    let userId: String
    let nickname: String
    let email: String
    let profileImageUrl: URL?
    let thumbnailImageUrl: URL?
    let isEmailVerified: Bool
    let isEmailValid: Bool
    
    var displayName: String {
        return nickname.isEmpty ? "사용자" : nickname
    }
    
    var hasProfileImage: Bool {
        return profileImageUrl != nil
    }
    
    init(userId: String, nickname: String, email: String, profileImageUrl: URL?, thumbnailImageUrl: URL?, isEmailVerified: Bool, isEmailValid: Bool) {
        self.userId = userId
        self.nickname = nickname
        self.email = email
        self.profileImageUrl = profileImageUrl
        self.thumbnailImageUrl = thumbnailImageUrl
        self.isEmailVerified = isEmailVerified
        self.isEmailValid = isEmailValid
    }
}
