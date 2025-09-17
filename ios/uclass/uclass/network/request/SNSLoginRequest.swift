/**
 * SNS 로그인 요청 모델
 */
struct SNSLoginRequest: Codable {
    let provider: String
    let snsId: String
    let pushToken: String
}
