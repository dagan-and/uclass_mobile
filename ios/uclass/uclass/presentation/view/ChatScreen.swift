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
    @State private var lastVisibleMessageId: UUID? = nil
    @State private var isScrollAtBottom: Bool = true
    @State private var tableViewRef: UITableView?
    @State private var messageCount: Int = 0
    
    // 새로운 메시지 대기열 관련 상태 - 수정된 부분
    @State private var pendingMessages: [ChatMessage] = []
    @State private var showPendingMessagesAlert: Bool = false
    @State private var isProcessingPendingMessages: Bool = false
    @State private var processingQueue: [ChatMessage] = [] // 처리 중인 메시지들의 복사본

    @Environment(\.presentationMode) var presentationMode
    let onBack: () -> Void

    init(onBack: @escaping () -> Void) {
        self.onBack = onBack
        // 200개의 더미 테스트 메시지 생성 (9월 1일 ~ 9월 10일)
        _messages = State(initialValue: generateDummyMessages())
    }
    
    // 더미 메시지 생성 함수
    private func generateDummyMessages() -> [ChatMessage] {
        var messages: [ChatMessage] = []
        let calendar = Calendar.current
        let baseDate = calendar.date(from: DateComponents(year: 2024, month: 9, day: 1))!
        
        let messageTexts = [
            "안녕하세요!", "오늘 날씨가 정말 좋네요", "점심 뭐 드실 예정이세요?",
            "회의 시간이 변경되었습니다", "네, 알겠습니다", "감사합니다",
            "내일 일정 확인 부탁드려요", "지금 출발합니다", "조금 늦을 것 같아요",
            "괜찮습니다", "화이팅!", "수고하세요", "좋은 하루 되세요",
            "프로젝트 진행 상황은 어떤가요?", "거의 완료되었습니다",
            "훌륭하네요", "다음 주에 발표 예정입니다", "준비 잘 부탁드려요",
            "물론입니다", "도움이 필요하면 언제든 말씀해주세요"
        ]
        
        for i in 0..<200 {
            // 10일 동안 균등하게 분배
            let dayOffset = i / 20
            let messageInDay = i % 20
            
            // 하루 안에서 시간 분산 (9시 ~ 18시)
            let hourOffset = messageInDay / 2
            let minuteOffset = (messageInDay % 2) * 30
            
            let messageDate = calendar.date(byAdding: .day, value: dayOffset, to: baseDate)!
            let finalDate = calendar.date(byAdding: .hour, value: 9 + hourOffset, to: messageDate)!
            let timestampDate = calendar.date(byAdding: .minute, value: minuteOffset, to: finalDate)!
            
            let isMe = i % 3 == 0 // 대략 1/3은 내 메시지
            let text = messageTexts[i % messageTexts.count]
            
            let message = ChatMessage(
                text: text,
                isMe: isMe,
                timestamp: timestampDate
            )
            
            messages.append(message)
        }
        
        return messages
    }

    var body: some View {
        GeometryReader { _ in
            VStack(spacing: 0) {
                titleBar
                chatContainer
            }
            .background(Color.white)
            .navigationBarHidden(true)
            .onAppear {
                NotificationCenter.default.post(
                    name: Notification.Name("ChatBadgeOff"),
                    object: false
                )
                messageCount = messages.count
            }
            .onChange(of: isScrollAtBottom) { _, newValue in
                handleScrollToBottom(newValue)
            }
        }
    }

    private var titleBar: some View {
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
        ZStack {
            VStack(spacing: 0) {
                // UITableView로 변경된 채팅 리스트
                ChatTableView(
                    messages: $messages,
                    isScrollAtBottom: $isScrollAtBottom,
                    tableViewRef: $tableViewRef
                )
                .onTapGesture {
                    hideKeyboard()
                }
                .onChange(of: text) { _, newValue in
                    updateTextEditorHeight(for: newValue)
                }
                
                // 입력창
                inputView
            }
            
            // 대기 중인 메시지 알림 (Z축으로 떠있음)
            if showPendingMessagesAlert && !isProcessingPendingMessages {
                VStack {
                    Spacer()
                    
                    Button(action: showPendingMessages) {
                        HStack(spacing: 8) {
                            Text("새로운 메시지가 왔습니다.")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.white)
                            
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.blue)
                                .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                        )
                    }
                    .padding(.bottom, textEditorHeight + 32) // 입력창 위에 위치
                }
                .transition(.asymmetric(
                    insertion: .move(edge: .bottom).combined(with: .opacity),
                    removal: .opacity
                ))
                .animation(.easeInOut(duration: 0.3), value: showPendingMessagesAlert)
            }
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
                        .onChange(of: text) { oldValue, newValue in
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

    // MARK: - 메시지 대기열 관련 함수들 - 수정된 부분
    
    /// 새로운 메시지를 추가 (대기열 방식)
    private func addNewMessage(_ message: ChatMessage) {
        Logger.dev("📩 [NEW_MSG] 새 메시지 수신 : '\(message.text)' (내 메시지: \(message.isMe))")
        
        if message.isMe {
            // 내가 보낸 메시지는 항상 즉시 추가
            Logger.dev("⬇️ [ADD_DIRECT] 내 메시지 즉시 추가")
            messages.append(message)
        } else {
            // 상대방 메시지는 항상 대기열에 추가 (순서 보장을 위해)
            Logger.dev("⏳ [ADD_PENDING] 상대방 메시지 대기열에 추가")
            pendingMessages.append(message)
            
            if isScrollAtBottom && !isProcessingPendingMessages {
                // 최하단에 있으면 즉시 처리 (알림 없이)
                Logger.dev("🚀 [AUTO_PROCESS] 최하단 위치로 즉시 처리")
                showPendingMessages()
            } else if !isProcessingPendingMessages {
                // 중간에 있으면 알림 표시
                Logger.dev("🔔 [SHOW_ALERT] 새 메시지 알림 표시")
                showPendingMessagesAlert = true
            }
        }
    }
    
    /// 스크롤이 하단에 도달했을 때 처리
    private func handleScrollToBottom(_ isAtBottom: Bool) {
        if isAtBottom && !pendingMessages.isEmpty && !isProcessingPendingMessages {
            Logger.dev("🔥 [FLUSH_PENDING] 대기 중인 메시지들을 메인 리스트에 추가")
            showPendingMessages()
        }
    }
    
    /// 대기 중인 메시지들을 보여주기 - 수정된 부분
    private func showPendingMessages() {
        guard !pendingMessages.isEmpty && !isProcessingPendingMessages else { return }
        
        Logger.dev("🚀 [SHOW_PENDING] \(pendingMessages.count)개의 대기 메시지 추가 시작")
        
        // 처리 시작 - 현재 pendingMessages를 복사하여 처리 큐에 저장
        isProcessingPendingMessages = true
        processingQueue = pendingMessages
        pendingMessages.removeAll() // 원본 배열은 비우기
        
        // 알림 숨기기
        showPendingMessagesAlert = false
        
        // 먼저 스크롤을 하단으로 이동시키되, 사용자가 직접 이동한 것으로 처리
        scrollToBottomAsUserAction()
        
        // 추가 지연 후 메시지들 순차 추가
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            addPendingMessagesWithAnimation()
        }
    }
    
    /// 사용자 액션으로 스크롤을 하단으로 이동 - 새로 추가된 함수
    private func scrollToBottomAsUserAction() {
        Logger.dev("👆 [USER_SCROLL] 사용자 액션으로 스크롤 이동")
        
        guard let tableView = tableViewRef, !messages.isEmpty else { return }
        
        // isScrollAtBottom을 먼저 true로 설정
        isScrollAtBottom = true
        
        let indexPath = IndexPath(row: 0, section: 0)
        DispatchQueue.main.async {
            // 프로그래매틱 스크롤이지만 사용자 액션으로 처리하기 위해
            // willStartProgrammaticScroll을 호출하지 않음
            tableView.scrollToRow(at: indexPath, at: .top, animated: true)
        }
    }
    
    /// 대기 메시지들을 애니메이션과 함께 순차적으로 추가 - 수정된 부분
    private func addPendingMessagesWithAnimation() {
        guard !processingQueue.isEmpty else {
            Logger.dev("✅ [PENDING_COMPLETE] 모든 대기 메시지 추가 완료")
            
            // 처리 완료
            isProcessingPendingMessages = false
            
            // 처리 중에 새로 들어온 메시지가 있다면 알림 다시 표시
            if !pendingMessages.isEmpty {
                showPendingMessagesAlert = true
                Logger.dev("📬 [NEW_PENDING] 처리 중에 새로운 대기 메시지 발견, 알림 다시 표시")
            }
            
            // 스크롤을 다시 하단으로
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                scrollToBottom()
            }
            return
        }
        
        // 첫 번째 대기 메시지를 가져와서 메인 리스트에 추가
        let nextMessage = processingQueue.removeFirst()
        
        withAnimation(.easeInOut(duration: 0.25)) {
            messages.append(nextMessage)
        }
        
        // 다음 메시지를 위해 짧은 지연 후 재귀 호출
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            addPendingMessagesWithAnimation()
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

        let myMessage = ChatMessage(text: text, isMe: true, timestamp: Date())
        
        // 내 메시지는 항상 즉시 추가 (대기열 방식 사용)
        addNewMessage(myMessage)

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
        
        // 자동 메시지 테스트 시작
        if(text == "자동테스트") {
            autoMessage()
            text = ""
            textEditorHeight = ChatScreen.textEditorDefault
            return
        }
        
        text = ""
        textEditorHeight = ChatScreen.textEditorDefault
        
        // 자동 응답 (테스트용) - 대기열 방식 사용
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            let autoReply = ChatMessage(
                text: "[[\(myMessage.text)]]",
                isMe: false,
                timestamp: Date()
            )
            addNewMessage(autoReply)
        }
    }
    
    private func autoMessage() {
        Logger.dev("🤖 [AUTO_TEST] 자동 메시지 테스트 시작 - 100개 메시지 전송")
        
        sendAutoMessageRecursively(index: 1)
    }
    
    private func sendAutoMessageRecursively(index: Int) {
        guard index <= 100 else {
            Logger.dev("✅ [AUTO_TEST] 자동 메시지 테스트 완료")
            return
        }
        
        let messageText = String(repeating: "\(index)", count: index)
        
        let autoReply = ChatMessage(
            text: messageText,
            isMe: false,
            timestamp: Date()
        )
        addNewMessage(autoReply)
        
        Logger.dev("📤 [AUTO_MSG] \(index)번째 메시지 전송: '\(messageText)'")
        
        // 0.5초 ~ 2초 사이의 랜덤 지연 후 다음 메시지 전송
        let randomDelay = Double.random(in: 0.5...2.0)
        DispatchQueue.main.asyncAfter(deadline: .now() + randomDelay) {
            sendAutoMessageRecursively(index: index + 1)
        }
    }
    
    private func scrollToBottom() {
        Logger.dev("⬇️ [SCROLL] 수동 스크롤 버튼 클릭")
        
        guard let tableView = tableViewRef, !messages.isEmpty else { return }
        
        let indexPath = IndexPath(row: 0, section: 0)
        DispatchQueue.main.async {
            tableView.scrollToRow(at: indexPath, at: .top, animated: true)
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
