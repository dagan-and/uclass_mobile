/**
 * SNS 체크/로그인 응답 데이터 모델
 */
struct SNSCheckData: Codable {
    let isExistingUser: Bool
    let redirectUrl : String?
    
    private enum CodingKeys: String, CodingKey {
        case isExistingUser = "existingUser"
        case redirectUrl = "redirectUrl"
    }
}
