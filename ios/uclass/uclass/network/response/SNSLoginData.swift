import Foundation

// MARK: - SNS 로그인 응답 데이터 모델
struct SNSLoginData: Codable {
    let userId: Int
    let approvalStatus: String
    let userName: String
    let branchName: String
    let tokenType: String
    let loginAt: String
    let accessToken: String
    let expiresIn: Int
    let userType: String
    let branchId: Int
    let redirectUrl : String
    let reasonUrl : String
    
    enum CodingKeys: String, CodingKey {
        case userId
        case approvalStatus
        case userName
        case branchName
        case tokenType
        case loginAt
        case accessToken
        case expiresIn
        case userType
        case branchId
        case redirectUrl
        case reasonUrl
    }
}
