import Foundation

// MARK: - ChatMessage Model

/**
 * 채팅 메시지 모델
 */
struct ChatMessage: Identifiable, Codable, Equatable {
    let id = UUID()
    let messageId: String
    let senderId: Int
    let senderType: String
    let senderName: String
    let receiverId: Int
    let receiverType: String
    let receiverName: String
    let branchId: Int
    let branchName: String
    let content: String
    let isRead: Bool
    let readAt: String?
    let sentAt: String
    let roomId: String
    let messageSeq : Int?
    
    // MARK: - Computed Properties
    
    // isMe 계산 프로퍼티
    var isMe: Bool {
        return senderId == Constants.getUserId()
    }
    
    // 표시할 텍스트 (content 필드 사용)
    var text: String {
        return content
    }
    
    // messageSeq 안전 접근 (기본값 0)
   var safeMessageSeq: Int {
       return messageSeq ?? 0
   }
    
    /**
        * messageSeq를 설정한 새 인스턴스 생성
        */
       func withMessageSeq(_ newSeq: Int) -> ChatMessage {
           return ChatMessage(
               messageId: self.messageId,
               senderId: self.senderId,
               senderType: self.senderType,
               senderName: self.senderName,
               receiverId: self.receiverId,
               receiverType: self.receiverType,
               receiverName: self.receiverName,
               branchId: self.branchId,
               branchName: self.branchName,
               content: self.content,
               isRead: self.isRead,
               readAt: self.readAt,
               sentAt: self.sentAt,
               roomId: self.roomId,
               messageSeq: newSeq
           )
       }
    
    // 타임스탬프 Date 객체 (sentAt 필드 파싱)
    var timestamp: Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.date(from: sentAt) ?? Date()
    }
    
    // MARK: - Coding Keys
    
    enum CodingKeys: String, CodingKey {
        case messageId, senderId, senderType, senderName
        case receiverId, receiverType, receiverName
        case branchId, branchName, content, isRead, readAt, sentAt, roomId , messageSeq
    }
    
    // MARK: - Equatable
    
    static func == (lhs: ChatMessage, rhs: ChatMessage) -> Bool {
        return lhs.messageId == rhs.messageId
    }
}

// MARK: - ChatMessage Extensions

extension ChatMessage {
    
    // 시간 포맷터
    static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "a h:mm"
        return formatter
    }()
    
    // 날짜 포맷터
    static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일 EEEE"
        return formatter
    }()
    
    // 시간 문자열 반환
    var timeString: String {
        return ChatMessage.timeFormatter.string(from: timestamp)
    }
    
    // 날짜 문자열 반환
    var dateString: String {
        return ChatMessage.dateFormatter.string(from: timestamp)
    }
    
    // 같은 날인지 확인
    func isSameDay(as other: ChatMessage) -> Bool {
        let calendar = Calendar.current
        return calendar.isDate(timestamp, inSameDayAs: other.timestamp)
    }
}

// MARK: - Chat Item Types

/**
 * 채팅 아이템 타입 (메시지 또는 날짜 구분선)
 */
enum ChatItem: Identifiable {
    case message(ChatMessage)
    case dateSeparator(Date)
    
    var id: String {
        switch self {
        case .message(let message):
            return message.messageId
        case .dateSeparator(let date):
            return "date_\(date.timeIntervalSince1970)"
        }
    }
}
