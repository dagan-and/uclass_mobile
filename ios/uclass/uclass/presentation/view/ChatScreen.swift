import Combine
import SwiftUI

struct ChatScreen: View {
    static let textEditorDefault: CGFloat = 42
    @State private var textEditorHeight: CGFloat = 42
    @State private var navigationBarHeight: CGFloat = 56
    @State private var messages: [ChatMessage] = []
    @State private var text: String = ""
    @State private var keyboardHeight: CGFloat = 0
    @State private var scrollViewPadding: CGFloat = 0
    @State private var lastVisibleMessageId: UUID? = nil  // 👈 맨 아래 보이는 메시지 id 저장
    @State private var isScrollAtBottom: Bool = true  // 스크롤이 최하단에 있는지 확인

    @Environment(\.presentationMode) var presentationMode
    let onBack: () -> Void

    init(onBack: @escaping () -> Void) {
        self.onBack = onBack
        // 테스트용 초기 메시지 생성
        _messages = State(
            initialValue: (0..<3).map { index in
                if index % 2 == 0 {
                    return ChatMessage(text: "내 메시지 \(index)", isMe: true)
                } else {
                    return ChatMessage(text: "상대방 메시지 \(index)", isMe: false)
                }
            }
        )
    }

    var body: some View {
        GeometryReader { _ in
            ZStack(alignment: .top) {
                // 상단 네비게이션 (항상 고정)
                navigationBar
                    .zIndex(1)  // 항상 위에 있도록

                VStack(spacing: 0) {
                    Spacer().frame(height: navigationBarHeight)  // 네비게이션바만큼 공간 확보
                    chatContainer
                }
            }
            .background(Color.white)
            .navigationBarHidden(true)
            .onAppear {
                NotificationCenter.default.post(
                    name: Notification.Name("ChatBadgeOff"),
                    object: false
                )
            }
        }
    }

    private var navigationBar: some View {
        HStack {
            Button(action: {
                hideKeyboard()
                onBack()
            }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.black)
            }

            Spacer().frame(width: 8)

            Text("채팅")
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.black)

            Spacer()
        }
        .padding(.horizontal, 16)
        .frame(height: navigationBarHeight)
        .background(Color.white)
    }

    private var chatContainer: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                VStack(spacing: 0) {
                    ScrollView {
                        LazyVStack {
                            ForEach(messages) { message in
                                ChatBubble(message: message)
                                    .id(message.id)
                                    .background(
                                        GeometryReader { geo in
                                            Color.clear
                                                .preference(
                                                    key: VisibleMessageKey.self,
                                                    value: [
                                                        message.id: geo.frame(
                                                            in: .named("scroll")
                                                        )
                                                    ]
                                                )
                                        }
                                    )
                            }
                        }
                        .rotationEffect(Angle(degrees: 180))
                        .scaleEffect(x: -1.0, y: 1.0, anchor: .center)
                    }
                    .rotationEffect(Angle(degrees: 180))
                    .scaleEffect(x: -1.0, y: 1.0, anchor: .center)
                    .onTapGesture {
                        hideKeyboard()
                    }
                    .onChange(of: messages.count) { _ in
                        withAnimation {
                            if let lastId = messages.last?.id {
                                proxy.scrollTo(lastId, anchor: .bottom)
                                isScrollAtBottom = true
                            }
                        }
                    }
                    .coordinateSpace(name: "scroll")
                    .onPreferenceChange(VisibleMessageKey.self) { frames in
                        updateScrollPosition(frames: frames)
                    }
                    .onAppear {
                        UIScrollView.appearance().bounces = false
                    }

                    // 입력창
                    inputView
                }
               
            }
        }
    }

    // 스크롤 위치 업데이트 및 최하단 여부 확인
    private func updateScrollPosition(frames: [UUID: CGRect]) {
        guard !messages.isEmpty else { return }

        if let lastId = messages.last?.id,
            let lastFrame = frames[lastId]
        {

            let screenHeight = UIScreen.main.bounds.height
            let bottomThreshold: CGFloat = 30

            // 마지막 메시지의 Y좌표가 화면 하단 근처에 있는 경우만 true
            isScrollAtBottom =
                lastFrame.maxY < screenHeight + bottomThreshold
                && lastFrame.maxY > 0
        } else {
            isScrollAtBottom = false
        }
    }

    private var inputView: some View {
        VStack(spacing: 0) {

            HStack(alignment: .bottom, spacing: 8) {
                // 텍스트 입력 필드
                ZStack {
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color(red: 0.94, green: 0.94, blue: 0.94))
                        .frame(height: textEditorHeight)

                    if text.isEmpty {
                        Text("메시지 입력")
                            .foregroundColor(.gray)
                            .font(.system(size: 16))
                            .padding(.horizontal, 16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .frame(height: textEditorHeight, alignment: .center)
                    }

                    TextEditor(text: $text)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .foregroundColor(.black)
                        .background(Color.clear)
                        .font(.system(size: 16))
                        .frame(height: textEditorHeight)
                        .onChange(of: text) { newValue in
                            updateTextEditorHeight(for: newValue)
                        }
                        .modifier(HideTextEditorBackground())
                }

                // 전송 버튼
                Button(action: sendMessage) {
                    Image(
                        systemName: text.isEmpty
                            ? "paperplane" : "paperplane.fill"
                    )
                    .font(.system(size: 18))
                    .foregroundColor(text.isEmpty ? .gray : .blue)
                    .frame(width: 36, height: 36)
                }
                .disabled(text.isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

        }
    }

    private func updateTextEditorHeight(for text: String) {
        let font = UIFont.systemFont(ofSize: 16)

        if text.isEmpty {
            textEditorHeight = ChatScreen.textEditorDefault
            return
        }

        let maxWidth = UIScreen.main.bounds.width - 120
        let boundingRect = text.boundingRect(
            with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font],
            context: nil
        )

        let lineHeight: CGFloat = 22
        let padding: CGFloat = 16
        let maxLines = 4
        let maxHeight = CGFloat(maxLines) * lineHeight + padding
        let minHeight: CGFloat = ChatScreen.textEditorDefault

        let calculatedHeight = boundingRect.height + padding
        textEditorHeight = max(minHeight, min(calculatedHeight, maxHeight))
    }

    private func sendMessage() {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }

        let myMessage = ChatMessage(text: text, isMe: true)
        messages.append(myMessage)

        //TODO 삭제할 내용
        if(text == "로그아웃") {
            Logger.dev("🚪 로그아웃 처리 시작")
            // 1. 로그인 정보 삭제
            UserDefaultsManager.clearLoginInfo()
            
            // 2. 앱 재시작 알림 발송
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                NotificationCenter.default.post(
                    name: Notification.Name("RestartApp"),
                    object: nil
                )
            }
            return
        }
        
        
        text = ""
        textEditorHeight = ChatScreen.textEditorDefault
        
        
        // 자동 응답 (테스트용)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            let autoReply = ChatMessage(text: "메시지를 받았습니다!", isMe: false)
            messages.append(autoReply)
        }
    }

    private func hideKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }
}

struct HideTextEditorBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content
                .scrollContentBackground(.hidden)
        } else {
            content
                .onAppear {
                    UITextView.appearance().backgroundColor = .clear
                }
        }
    }
}

struct VisibleMessageKey: PreferenceKey {
    static var defaultValue: [UUID: CGRect] = [:]
    static func reduce(
        value: inout [UUID: CGRect],
        nextValue: () -> [UUID: CGRect]
    ) {
        value.merge(nextValue()) { $1 }
    }
}
