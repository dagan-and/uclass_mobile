import Foundation
import Combine
import NidThirdPartyLogin

class NaverLoginManager: NSObject, ObservableObject {
    @Published var isLoggedIn = false
    @Published var userInfo: NaverUserInfo?
    @Published var errorMessage: String?
    @Published var isLoading = false
    
    override init() {
        super.init()
        setupNaverLogin()
    }
    
    // 네이버 로그인 초기 설정
    private func setupNaverLogin() {
        // NidOAuth 초기화는 AppDelegate에서 수행
        // 로그인 방식 설정: 네이버 앱 우선, 실패 시 SafariViewController
        NidOAuth.shared.setLoginBehavior(.appPreferredWithInAppBrowserFallback)
    }
    
    // 네이버 로그인 시작
    func startNaverLogin() {
        isLoading = true
        errorMessage = nil
        
        print("네이버 로그인 시작")
        
        NidOAuth.shared.requestLogin { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    // 로그인 결과 처리
    private func handleLoginResult(_ result: Result<LoginResult, NidError>) {
        isLoading = false
        
        switch result {
        case .success(let loginResult):
            print("네이버 로그인 성공")
            print("Access Token: \(loginResult.accessToken.tokenString)")
            fetchUserProfile(accessToken: loginResult.accessToken)
            
        case .failure(let error):
            print("네이버 로그인 실패: \(error)")
            handleLoginError(error)
        }
    }
    
    // 로그인 에러 처리
    private func handleLoginError(_ error: NidError) {
        // NidError는 Error 프로토콜을 따르므로 localizedDescription을 사용
        self.errorMessage = error.localizedDescription
        
        print("네이버 로그인 에러: \(error)")
    }
    
    // 사용자 프로필 정보 가져오기
    private func fetchUserProfile(accessToken: AccessToken) {
        NidOAuth.shared.getUserProfile(accessToken: accessToken.tokenString) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let profileResult):
                    let userInfo = NaverUserInfo(
                        userId: profileResult["id"] ?? "",
                        nickname: profileResult["nickname"] ?? "",
                        name: profileResult["name"] ?? "",
                        email: profileResult["email"] ?? "",
                        profileImageUrl: URL(string: profileResult["profile_image"] ?? ""),
                        age: profileResult["age"] ?? "",
                        gender: profileResult["gender"] ?? "",
                        birthday: profileResult["birthday"] ?? "",
                        birthyear: profileResult["birthyear"] ?? "",
                        mobile: profileResult["mobile"] ?? ""
                    )
                    
                    self?.userInfo = userInfo
                    self?.isLoggedIn = true
                    
                    print("=== 네이버 로그인 정보 ===")
                    print("User ID: \(userInfo.userId)")
                    print("Nickname: \(userInfo.nickname)")
                    print("Name: \(userInfo.name)")
                    print("Email: \(userInfo.email)")
                    print("Profile Image: \(userInfo.profileImageUrl?.absoluteString ?? "없음")")
                    print("Age: \(userInfo.age)")
                    print("Gender: \(userInfo.gender)")
                    print("Birthday: \(userInfo.birthday)")
                    print("Birth Year: \(userInfo.birthyear)")
                    print("Mobile: \(userInfo.mobile)")
                    print("========================")
                    
                case .failure(let error):
                    print("네이버 사용자 정보 가져오기 실패: \(error)")
                    self?.errorMessage = "사용자 정보를 가져오는데 실패했습니다."
                }
            }
        }
    }
    
    // 로그아웃 (클라이언트 토큰만 삭제)
    func logout() {
        NidOAuth.shared.logout()
        
        DispatchQueue.main.async {
            self.isLoggedIn = false
            self.userInfo = nil
            self.errorMessage = nil
            print("네이버 로그아웃 완료")
        }
    }
    
    // 연동 해제 (서버와 클라이언트 토큰 모두 삭제)
    func disconnect() {
        isLoading = true
        
        NidOAuth.shared.disconnect { [weak self] result in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                switch result {
                case .success:
                    self?.isLoggedIn = false
                    self?.userInfo = nil
                    self?.errorMessage = nil
                    print("네이버 연동 해제 완료")
                    
                case .failure(let error):
                    print("네이버 연동 해제 실패: \(error)")
                    self?.errorMessage = "연동 해제에 실패했습니다."
                }
            }
        }
    }
    
    // 재인증 (보안 수준 향상을 위한 재로그인)
    func reauthenticate() {
        isLoading = true
        errorMessage = nil
        
        NidOAuth.shared.reauthenticate { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    // 재동의 (거부된 프로필 항목에 대한 재동의 요청)
    func repromptPermissions() {
        isLoading = true
        errorMessage = nil
        
        NidOAuth.shared.repromptPermissions { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    // 현재 액세스 토큰 상태 확인
    var hasValidAccessToken: Bool {
        if let accessToken = NidOAuth.shared.accessToken {
            return !accessToken.isExpired
        }
        return false
    }
    
    // 토큰 검증
    func verifyAccessToken() {
        guard let accessToken = NidOAuth.shared.accessToken?.tokenString else {
            errorMessage = "액세스 토큰이 없습니다."
            return
        }
        
        NidOAuth.shared.verifyAccessToken(accessToken) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let isValid):
                    print("토큰 유효성: \(isValid)")
                    if !isValid {
                        self?.errorMessage = "토큰이 유효하지 않습니다."
                    }
                    
                case .failure(let error):
                    print("토큰 검증 실패: \(error)")
                    self?.errorMessage = "토큰 검증에 실패했습니다."
                }
            }
        }
    }
}

// MARK: - NaverUserInfo Model

struct NaverUserInfo: Codable {
    let userId: String
    let nickname: String
    let name: String
    let email: String
    let profileImageUrl: URL?
    let age: String
    let gender: String
    let birthday: String
    let birthyear: String
    let mobile: String
    
    var displayName: String {
        if !name.isEmpty {
            return name
        } else if !nickname.isEmpty {
            return nickname
        } else {
            return "사용자"
        }
    }
    
    var hasProfileImage: Bool {
        return profileImageUrl != nil
    }
    
    var formattedBirthday: String {
        if !birthyear.isEmpty && !birthday.isEmpty {
            return "\(birthyear)년 \(birthday)"
        } else if !birthyear.isEmpty {
            return "\(birthyear)년"
        } else if !birthday.isEmpty {
            return birthday
        } else {
            return "정보 없음"
        }
    }
    
    var genderDescription: String {
        switch gender.lowercased() {
        case "m", "male":
            return "남성"
        case "f", "female":
            return "여성"
        default:
            return "정보 없음"
        }
    }
    
    init(userId: String, nickname: String, name: String, email: String, profileImageUrl: URL?, age: String, gender: String, birthday: String, birthyear: String, mobile: String) {
        self.userId = userId
        self.nickname = nickname
        self.name = name
        self.email = email
        self.profileImageUrl = profileImageUrl
        self.age = age
        self.gender = gender
        self.birthday = birthday
        self.birthyear = birthyear
        self.mobile = mobile
    }
}
