import SwiftUI

struct ChatBubble: View {
    let message: ChatMessage
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            if message.isMe {
                Spacer()
                
                HStack(alignment: .bottom, spacing: 4) {
                    Text(message.timeString)
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                    Text(message.text)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Color(red: 0.18, green: 0.49, blue: 0.20)) // #2E7D32
                        .foregroundColor(.white)
                        .cornerRadius(16)
                        .fixedSize(horizontal: false, vertical: true)
                   
                }
            } else {
             
                HStack(alignment: .bottom, spacing: 4) {
                    Text(message.text)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Color(red: 0.88, green: 0.88, blue: 0.88)) // #E0E0E0
                        .foregroundColor(.black)
                        .cornerRadius(16)
                        .fixedSize(horizontal: false, vertical: true)
                    
                    Text(message.timeString)
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                }
                
                Spacer()
            }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 16)
    }
}

// 날짜 구분선 뷰 (버블 형태로 가운데 정렬)
struct DateSeparator: View {
    let dateString: String
    
    var body: some View {
        HStack {
            Spacer()
            
            Text(dateString)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.gray.opacity(0.6))
                .cornerRadius(16)
            
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
