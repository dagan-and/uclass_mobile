import Foundation

// MARK: - 채팅 초기화 API 응답 데이터 모델
struct ChatMessageData: Codable {
    let hasMore : Bool
    let messages: [ChatMessage]
    
    enum CodingKeys: String, CodingKey {
        case messages
        case hasMore
    }
}
