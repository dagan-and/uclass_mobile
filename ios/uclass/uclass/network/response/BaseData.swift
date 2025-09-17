/**
 * 기본 API 응답 데이터 모델 (제네릭 버전)
 */
struct BaseData<T: Codable>: Codable {
    let isSuccess: Bool
    let message: String?
    let data: T?
    let timestamp: String?
    
    private enum CodingKeys: String, CodingKey {
        case isSuccess = "success"
        case message
        case data
        case timestamp
    }
}


struct EmptyData: Codable {}
