import Foundation

struct ChatMessage: Identifiable, Hashable {
    let id = UUID()
    let text: String
    let isMe: Bool
    let timestamp: Date = Date()
}
