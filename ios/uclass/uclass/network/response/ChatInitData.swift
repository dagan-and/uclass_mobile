import Foundation

// MARK: - 채팅 초기화 API 응답 데이터 모델
struct ChatInitData: Codable {
    let roomId: String
    let branchName: String
    let hasMore : Bool
    let messages: [ChatMessage]
    
    enum CodingKeys: String, CodingKey {
        case roomId
        case branchName
        case messages
        case hasMore
    }
}
