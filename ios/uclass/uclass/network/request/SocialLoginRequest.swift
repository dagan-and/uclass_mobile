import Foundation

struct SocialLoginRequest: Codable {
    let provider: String
    let token: String
    let userType: String
    let branchId: Int
}
