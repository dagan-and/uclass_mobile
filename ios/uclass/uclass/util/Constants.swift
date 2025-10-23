import Foundation

/**
 * 앱 상수 관리 클래스
 */
struct Constants {
    static var uclassURL = "https://dev-uclass.ubase.kr"
    static var umanagerURL = "https://dev-umanager.ubase.kr"
    static var webURL = "http://dev-uclass.ubase.kr"
    static var isDebug = false
    static var jwtToken: String? = nil
    static var fcmToken: String? = nil
    static var mainUrl: String = ""
    static var noticeUrl: String = ""
    
    /**
    * 사용자 ID 반환
    */
   static func getUserId() -> Int {
       return UserDefaults.standard.integer(forKey: "user_id")
   }
   
   /**
    * 사용자 ID 설정
    */
   static func setUserId(_ userId: Int) {
       UserDefaults.standard.set(userId, forKey: "user_id")
   }
   
   /**
    * 브랜치 ID 반환
    */
   static func getBranchId() -> Int {
       return UserDefaults.standard.integer(forKey: "branch_id")
   }
   
   /**
    * 브랜치 ID 설정
    */
   static func setBranchId(_ branchId: Int) {
       UserDefaults.standard.set(branchId, forKey: "branch_id")
   }
    
    /**
     * 브랜치 ID 반환
     */
    static func getBranchName() -> String {
        return UserDefaults.standard.string(forKey: "branch_name") ?? ""
    }
    
    /**
     * 브랜치 ID 설정
     */
    static func setBranchName(_ branchName: String) {
        UserDefaults.standard.set(branchName, forKey: "branch_name")
    }
}
