import SwiftUI

struct ChatBubble: View {
    let message: ChatMessage
    
    var body: some View {
        HStack {
            if message.isMe {
                Spacer()
                
                Text(message.text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.18, green: 0.49, blue: 0.20)) // #2E7D32 진한 초록
                    .foregroundColor(.white)
                    .cornerRadius(16)
                    .frame(maxWidth: 280, alignment: .trailing)
            } else {
                Text(message.text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.88, green: 0.88, blue: 0.88)) // #E0E0E0 연한 회색
                    .foregroundColor(.black)
                    .cornerRadius(16)
                    .frame(maxWidth: 280, alignment: .leading)
                
                Spacer()
            }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 16)
    }
}
