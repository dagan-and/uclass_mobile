import Foundation

class UserDefaultsManager {
    private enum Keys {
        static let snsType = "sns_type"
        static let snsToken = "sns_token"
        static let snsId = "sns_id"
        static let userEmail = "user_email"
        static let userName = "user_name"
        static let isLoggedIn = "is_logged_in"
    }
    
    // MARK: - SNS 로그인 정보 저장/조회
    
    /// SNS 타입 저장
    static func setSNSType(_ type: String) {
        UserDefaults.standard.set(type, forKey: Keys.snsType)
    }
    
    /// SNS 타입 조회
    static func getSNSType() -> String {
        return UserDefaults.standard.string(forKey: Keys.snsType) ?? ""
    }
    
    /// SNS 토큰 저장
    static func setSNSToken(_ token: String) {
        UserDefaults.standard.set(token, forKey: Keys.snsToken)
    }
    
    /// SNS 토큰 조회
    static func getSNSToken() -> String {
        return UserDefaults.standard.string(forKey: Keys.snsToken) ?? ""
    }
    
    /// 사용자 ID 저장
    static func setSNSId(_ userId: String) {
        UserDefaults.standard.set(userId, forKey: Keys.snsId)
    }
    
    /// 사용자 ID 조회
    static func getSNSId() -> String {
        return UserDefaults.standard.string(forKey: Keys.snsId) ?? ""
    }
    
    /// 사용자 이메일 저장
    static func setUserEmail(_ email: String) {
        UserDefaults.standard.set(email, forKey: Keys.userEmail)
    }
    
    /// 사용자 이메일 조회
    static func getUserEmail() -> String {
        return UserDefaults.standard.string(forKey: Keys.userEmail) ?? ""
    }
    
    /// 사용자 이름 저장
    static func setUserName(_ name: String) {
        UserDefaults.standard.set(name, forKey: Keys.userName)
    }
    
    /// 사용자 이름 조회
    static func getUserName() -> String {
        return UserDefaults.standard.string(forKey: Keys.userName) ?? ""
    }
    
    /// 로그인 상태 저장
    static func setLoggedIn(_ isLoggedIn: Bool) {
        UserDefaults.standard.set(isLoggedIn, forKey: Keys.isLoggedIn)
    }
    
    /// 로그인 상태 조회
    static func isLoggedIn() -> Bool {
        return UserDefaults.standard.bool(forKey: Keys.isLoggedIn)
    }
    
    // MARK: - 전체 로그인 정보 저장
    
    /// 모든 로그인 정보를 한번에 저장
    static func saveLoginInfo(snsType: String, snsToken: String, userId: String, email: String, name: String) {
        setSNSType(snsType)
        setSNSToken(snsToken)
        setSNSId(userId)
        setUserEmail(email)
        setUserName(name)
        setLoggedIn(true)
    }
    
    /// 모든 로그인 정보 삭제 (로그아웃)
    static func clearLoginInfo() {
        UserDefaults.standard.removeObject(forKey: Keys.snsType)
        UserDefaults.standard.removeObject(forKey: Keys.snsToken)
        UserDefaults.standard.removeObject(forKey: Keys.snsId)
        UserDefaults.standard.removeObject(forKey: Keys.userEmail)
        UserDefaults.standard.removeObject(forKey: Keys.userName)
        setLoggedIn(false)
    }
    
    /// 저장된 로그인 정보 출력 (디버그용)
    static func printSavedLoginInfo() {
        Logger.dev("=== 저장된 로그인 정보 ===")
        Logger.dev("SNS Type: \(getSNSType())")
        Logger.dev("SNS Token: \(getSNSToken())")
        Logger.dev("User ID: \(getSNSId())")
        Logger.dev("Email: \(getUserEmail())")
        Logger.dev("Name: \(getUserName())")
        Logger.dev("Is Logged In: \(isLoggedIn())")
        Logger.dev("========================")
    }
    
    // MARK: - 편의 기능들
    
    /// 현재 저장된 로그인 정보를 딕셔너리로 반환
    static func getLoginInfoAsDictionary() -> [String: Any] {
        return [
            "snsType": getSNSType(),
            "snsToken": getSNSToken(),
            "userId": getSNSId(),
            "email": getUserEmail(),
            "name": getUserName(),
            "isLoggedIn": isLoggedIn()
        ]
    }
    
    /// 특정 SNS 타입인지 확인
    static func isCurrentSNSType(_ type: String) -> Bool {
        return getSNSType().lowercased() == type.lowercased()
    }
    
    /// 토큰이 유효한지 확인 (빈 문자열이 아닌지만 체크)
    static func hasValidToken() -> Bool {
        return !getSNSToken().isEmpty
    }
    
    /// 사용자 정보가 완전한지 확인
    static func hasCompleteUserInfo() -> Bool {
        return !getSNSId().isEmpty && !getUserEmail().isEmpty
    }
    
    /// 로그인 시간 저장 (선택사항)
    static func setLoginTime(_ time: Date = Date()) {
        UserDefaults.standard.set(time, forKey: "login_time")
    }
    
    /// 로그인 시간 조회
    static func getLoginTime() -> Date? {
        return UserDefaults.standard.object(forKey: "login_time") as? Date
    }
    
    /// 자동 로그인 가능한지 확인 (모든 조건 체크)
    static func canAutoLogin() -> Bool {
        return isLoggedIn() && hasValidToken() && hasCompleteUserInfo()
    }
}
