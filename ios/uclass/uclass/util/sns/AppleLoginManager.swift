import Foundation
import AuthenticationServices
import CryptoKit
import Combine

class AppleLoginManager: NSObject, ObservableObject {
    @Published var isLoggedIn = false
    @Published var userInfo: AppleUserInfo?
    @Published var errorMessage: String?
    @Published var isLoading = false
    
    private var currentNonce: String?
    
    // SNS 토큰 및 타입 정보
    var snsType: String {
        return "apple"
    }
    
    var snsToken: String {
        return userInfo?.identityToken ?? ""
    }
    
    override init() {
        super.init()
    }
    
    // Apple 로그인 시작
    func startAppleLogin() {
        isLoading = true
        errorMessage = nil
        
        let nonce = randomNonceString()
        currentNonce = nonce
        
        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)
        
        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()
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
        
        Logger.dev("Apple 로그인 정보 저장 완료")
        UserDefaultsManager.printSavedLoginInfo()
    }
    
    // MARK: - Private Helper Methods
    
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] =
        Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        
        while remainingLength > 0 {
            let randoms: [UInt8] = (0..<16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                if errorCode != errSecSuccess {
                    fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
                }
                return random
            }
            
            randoms.forEach { random in
                if remainingLength == 0 {
                    return
                }
                
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        
        return result
    }
    
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        let hashString = hashedData.compactMap {
            String(format: "%02x", $0)
        }.joined()
        
        return hashString
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension AppleLoginManager: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        DispatchQueue.main.async {
            self.isLoading = false
            
            if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
                let userId = appleIDCredential.user
                let email = appleIDCredential.email
                let fullName = appleIDCredential.fullName
                let identityToken = appleIDCredential.identityToken
                let authorizationCode = appleIDCredential.authorizationCode
                
                // 사용자 정보 생성
                let userInfo = AppleUserInfo(
                    userId: userId,
                    email: email ?? "",
                    fullName: PersonNameComponents(
                        givenName: fullName?.givenName,
                        familyName: fullName?.familyName
                    ),
                    identityToken: identityToken != nil ? String(data: identityToken!, encoding: .utf8) : nil,
                    authorizationCode: authorizationCode != nil ? String(data: authorizationCode!, encoding: .utf8) : nil
                )
                
                self.userInfo = userInfo
                self.isLoggedIn = true
                
                // 로그인 정보 저장
                self.saveLoginInfo()
                
                Logger.dev("Apple 로그인 성공!")
                Logger.dev("Apple 로그인 성공!")
                Logger.dev("User ID: \(userId)")
                Logger.dev("Email: \(email ?? "제공되지 않음")")
                Logger.dev("Full Name: \(fullName?.givenName ?? "") \(fullName?.familyName ?? "")")
                Logger.dev("Identity Token: \(self.snsToken)")
                
            }
        }
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        DispatchQueue.main.async {
            self.isLoading = false
            
            if let authError = error as? ASAuthorizationError {
                switch authError.code {
                case .canceled:
                    Logger.dev("Apple 로그인 취소됨")
                    self.errorMessage = "로그인이 취소되었습니다."
                case .failed:
                    Logger.dev("Apple 로그인 실패: \(authError.localizedDescription)")
                    self.errorMessage = "로그인에 실패했습니다."
                case .invalidResponse:
                    Logger.dev("Apple 로그인 응답이 유효하지 않음")
                    self.errorMessage = "잘못된 응답입니다."
                case .notHandled:
                    Logger.dev("Apple 로그인이 처리되지 않음")
                    self.errorMessage = "로그인이 처리되지 않았습니다."
                case .unknown:
                    Logger.dev("Apple 로그인 알 수 없는 오류")
                    self.errorMessage = "알 수 없는 오류가 발생했습니다."
                default:
                    Logger.dev("Apple 로그인 기타 오류: \(authError.localizedDescription)")
                    self.errorMessage = authError.localizedDescription
                }
            } else {
                Logger.dev("Apple 로그인 일반 오류: \(error.localizedDescription)")
                self.errorMessage = error.localizedDescription
            }
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension AppleLoginManager: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else {
            fatalError("No window found")
        }
        return window
    }
}

// MARK: - AppleUserInfo Model

struct AppleUserInfo: Codable {
    let userId: String
    let email: String
    let fullName: PersonNameComponents
    let identityToken: String?
    let authorizationCode: String?
    
    var displayName: String {
        let firstName = fullName.givenName ?? ""
        let lastName = fullName.familyName ?? ""
        let fullDisplayName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
        return fullDisplayName.isEmpty ? "사용자" : fullDisplayName
    }
    
    init(userId: String, email: String, fullName: PersonNameComponents, identityToken: String?, authorizationCode: String?) {
        self.userId = userId
        self.email = email
        self.fullName = fullName
        self.identityToken = identityToken
        self.authorizationCode = authorizationCode
    }
}
