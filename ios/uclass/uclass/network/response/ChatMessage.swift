import Foundation

struct ChatMessage: Identifiable {
    let id = UUID()
    let text: String
    let isMe: Bool
    let timestamp: Date
    
    init(text: String, isMe: Bool, timestamp: Date = Date()) {
        self.text = text
        self.isMe = isMe
        self.timestamp = timestamp
    }
}

// 채팅 아이템 타입 (메시지 또는 날짜 구분선)
enum ChatItem {
    case message(ChatMessage)
    case dateSeparator(Date)
}

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
