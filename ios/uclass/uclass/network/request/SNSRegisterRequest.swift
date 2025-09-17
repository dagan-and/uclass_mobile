/**
 * SNS 회원가입 요청 모델
 */
struct SNSRegisterRequest: Codable {
    let provider: String
    let snsId: String
    let name: String
    let email: String
    let phoneNumber: String
    let profileImageUrl: String
    let userType: String
    let branchId: Int
    let termsAgreed: Bool
    let privacyAgreed: Bool
}
