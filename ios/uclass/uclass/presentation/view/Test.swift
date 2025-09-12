import SwiftUI

struct Message: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let isFromUser: Bool
    let timestamp: Date
}

struct ChatView: View {
    @State private var messages: [Message] = [
        Message(text: "안녕하세요! 오늘 어떠세요?", isFromUser: false, timestamp: Date().addingTimeInterval(-1000)),
        Message(text: "안녕하세요! 저는 잘 지내고 있어요", isFromUser: true, timestamp: Date().addingTimeInterval(-950)),
        Message(text: "오늘 날씨가 정말 좋네요", isFromUser: false, timestamp: Date().addingTimeInterval(-900)),
        Message(text: "맞아요, 산책하기 딱 좋은 날씨인 것 같아요", isFromUser: true, timestamp: Date().addingTimeInterval(-850)),
        Message(text: "주말에 뭐 하실 예정이세요?", isFromUser: false, timestamp: Date().addingTimeInterval(-800)),
        Message(text: "친구들과 카페에 가려고 해요", isFromUser: true, timestamp: Date().addingTimeInterval(-750)),
        Message(text: "좋은 계획이네요! 어떤 카페인가요?", isFromUser: false, timestamp: Date().addingTimeInterval(-700)),
        Message(text: "홍대에 있는 작은 카페예요", isFromUser: true, timestamp: Date().addingTimeInterval(-650)),
        Message(text: "홍대는 항상 활기차서 좋아요", isFromUser: false, timestamp: Date().addingTimeInterval(-600)),
        Message(text: "네, 그래서 자주 가게 되는 것 같아요", isFromUser: true, timestamp: Date().addingTimeInterval(-550)),
        Message(text: "요즘 읽고 있는 책이 있나요?", isFromUser: false, timestamp: Date().addingTimeInterval(-500)),
        Message(text: "소설 하나 읽고 있어요. 정말 재미있어요", isFromUser: true, timestamp: Date().addingTimeInterval(-450)),
        Message(text: "어떤 소설인지 궁금하네요", isFromUser: false, timestamp: Date().addingTimeInterval(-400)),
        Message(text: "미스터리 소설이에요. 추리하는 재미가 있어요", isFromUser: true, timestamp: Date().addingTimeInterval(-350)),
        Message(text: "미스터리 소설 저도 좋아해요!", isFromUser: false, timestamp: Date().addingTimeInterval(-300)),
        Message(text: "그럼 나중에 추천해드릴게요", isFromUser: true, timestamp: Date().addingTimeInterval(-250)),
        Message(text: "감사해요! 기대할게요", isFromUser: false, timestamp: Date().addingTimeInterval(-200)),
        Message(text: "오늘 저녁은 뭐 드실 예정이세요?", isFromUser: false, timestamp: Date().addingTimeInterval(-150)),
        Message(text: "아직 정하지 못했어요. 추천해주세요!", isFromUser: true, timestamp: Date().addingTimeInterval(-100)),
        Message(text: "파스타는 어떠세요? 간단하고 맛있어요", isFromUser: false, timestamp: Date().addingTimeInterval(-50))
    ]
    
    @State private var messageText = ""
    
    var body: some View {
        VStack {
            // 채팅 메시지 목록
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(messages) { message in
                            MessageRow(message: message)
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top)
                }
                .onChange(of: messages.count) { _ in
                    // 새 메시지가 추가되면 자동으로 맨 아래로 스크롤
                    if let lastMessage = messages.last {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo(lastMessage.id, anchor: .bottom)
                        }
                    }
                }
            }
            
            // 메시지 입력창
            HStack {
                TextField("메시지를 입력하세요...", text: $messageText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .onSubmit {
                        sendMessage()
                    }
                
                Button(action: sendMessage) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                        .background(messageText.isEmpty ? Color.gray : Color.blue)
                        .clipShape(Circle())
                }
                .disabled(messageText.isEmpty)
            }
            .padding()
        }
        .navigationTitle("채팅")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func sendMessage() {
        guard !messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        
        let newMessage = Message(text: messageText, isFromUser: true, timestamp: Date())
        messages.append(newMessage)
        messageText = ""
        
        // 간단한 자동 응답 (선택사항)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            let responses = [
                "좋은 생각이네요!",
                "그렇군요!",
                "흥미로워요",
                "더 자세히 알려주세요",
                "맞아요!",
                "좋아요!"
            ]
            let randomResponse = responses.randomElement() ?? "네!"
            let responseMessage = Message(text: randomResponse, isFromUser: false, timestamp: Date())
            messages.append(responseMessage)
        }
    }
    
    private func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

struct MessageRow: View {
    let message: Message
    
    var body: some View {
        HStack {
            if message.isFromUser {
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(message.text)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    
                    Text(formatTime(message.timestamp))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    Text(message.text)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(.systemGray5))
                        .foregroundColor(.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    
                    Text(formatTime(message.timestamp))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                Spacer()
            }
        }
    }
    
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// 메인 앱 진입점
struct ContentView: View {
    var body: some View {
        NavigationView {
            ChatView()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
