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
    @State private var isLoadingPreviousMessages: Bool = false
    @State private var currentMaxMessageSeq: Int = 0
    @State private var pageItemCount: Int = 30
    @State private var pageCount: Int = 0
    @State private var isMoreLoading = false
    

    // 새로운 메시지 대기열 관련 상태
    @State private var pendingMessages: [ChatMessage] = []
    @State private var showPendingMessagesAlert: Bool = false
    @State private var isProcessingPendingMessages: Bool = false
    @State private var processingQueue: [ChatMessage] = []
    
    // ChatInit API 관련 상태
    @StateObject private var networkViewModel = NetworkViewModel(identifier: "ChatScreen")
    @State private var isInitializing: Bool = false
    
    // 소켓 연결 상태 관련 추가
    @State private var isSocketConnecting: Bool = false
    @ObservedObject private var socketManager = SocketManager.shared
    
    // 브랜치명을 위한 상태 추가
    @State private var branchName: String = "채팅"

    @Environment(\.presentationMode) var presentationMode
    let onBack: () -> Void

    init(onBack: @escaping () -> Void) {
        self.onBack = onBack
    }
    

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                VStack(spacing: 0) {
                    titleBar
                    chatContainer
                }
                .background(Color.white)
                
                // ChatInit 로딩 상태 표시
                if isInitializing || isSocketConnecting {
                    VStack {
                        Spacer().frame(height: navigationBarHeight + 8)
                        
                        HStack(spacing: 8) {
                            Text("채팅을 불러오는 중...")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.gray)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                        )
                        .transition(.asymmetric(
                            insertion: .move(edge: .top).combined(with: .opacity),
                            removal: .opacity
                        ))
                        .animation(.easeInOut(duration: 0.3), value: isInitializing)
                        
                        Spacer()
                    }
                }
                
                // 이전 메시지 로딩 상태 표시
                if isLoadingPreviousMessages {
                    VStack {
                        Spacer().frame(height: navigationBarHeight + 8)
                        
                        HStack(spacing: 8) {
                            Text("이전 메시지를 불러오는 중...")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.black)
                                .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                        )
                        .transition(.asymmetric(
                            insertion: .move(edge: .top).combined(with: .opacity),
                            removal: .opacity
                        ))
                        .animation(.easeInOut(duration: 0.3), value: isLoadingPreviousMessages)
                        
                        Spacer()
                    }
                }
            }
            .navigationBarHidden(true)
            .gesture(
                DragGesture()
                    .onEnded { value in
                        // 오른쪽으로 swipe (뒤로가기)
                        if value.translation.width > 100 && abs(value.translation.height) < 100 {
                            Logger.dev("👈 [SWIPE] 오른쪽 스와이프로 뒤로가기")
                            hideKeyboard()
                            onBack()
                        }
                    }
            )
            .onAppear {
                Logger.dev("🎬 [CHAT_SCREEN] ChatScreen 나타남 - ChatInit API 호출")
                NotificationCenter.default.post(
                    name: Notification.Name("ChatBadgeOff"),
                    object: false
                )
                messageCount = messages.count
                
                // ChatScreen이 나타날 때마다 ChatInit API 호출
                initializeChat()
            }
            .onDisappear {
                Logger.dev("🚪 [CHAT_SCREEN] ChatScreen 사라짐 - 소켓 연결 해제")
                disconnectSocket()
            }
            .onChange(of: isScrollAtBottom) { newValue in
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

            Text(branchName)
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
                    tableViewRef: $tableViewRef,
                    isLoadingPreviousMessages: $isLoadingPreviousMessages,
                    onScrollToTop: {
                       // 👇 스크롤 최상단 감지 시 ChatScreen에서 처리할 로직
                       handleScrollToTop()
                   }
                )
                .onTapGesture {
                    hideKeyboard()
                }
                .onChange(of: text) { newValue in
                    updateTextEditorHeight(for: newValue)
                }
                
                // 입력창 (초기화 중일 때 비활성화)
                inputView
                    .disabled(isInitializing || !socketManager.isConnected())
                    .opacity((isInitializing || !socketManager.isConnected()) ? 0.6 : 1.0)
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
                .disabled(text.isEmpty || isInitializing || !socketManager.isConnected())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    // MARK: - ChatInit API 관련 함수들
    
    /// ChatInit API 호출
    private func initializeChat() {
        Logger.dev("🚀 [CHAT_INIT] ChatInit API 호출 시작")
        
        // 상태 초기화
        isInitializing = true
        
        // 현재 메시지 클리어 (새로 불러올 예정)
        messages.removeAll()
        pendingMessages.removeAll()
        showPendingMessagesAlert = false
        
        let userId = Constants.getUserId()
        
        networkViewModel.callChatInit(
            userId: String(userId),
            onSuccess: { [self] result in
                Logger.dev("✅ [CHAT_INIT] ChatInit API 성공")
                DispatchQueue.main.async {
                    self.isInitializing = false
                    
                    // 결과 파싱 및 메시지 설정
                    if let resultData = result as? BaseData<ChatInitData>,
                       let chatData = resultData.data {
                        Logger.dev("📋 [CHAT_INIT] 채팅 데이터 파싱 성공")
                        self.pageCount = 0
                        self.isMoreLoading = chatData.hasMore
                        self.setupInitialMessages(from: chatData)
                    } else {
                        Logger.warning("⚠️ [CHAT_INIT] 채팅 데이터 파싱 실패 - 빈 채팅방으로 시작")
                        self.messages = []
                    }
                    

                    // ChatInit 성공 후 소켓 연결 시작
                    self.connectSocket()
                }
            },
            onError: { [self] error in
                Logger.error("❌ [CHAT_INIT] ChatInit API 실패: \(error)")
                DispatchQueue.main.async {
                    self.isInitializing = false
                    
                    Logger.error("💥 [CHAT_INIT] 초기화 실패: \(error)")
                    
                    // Alert로 에러 표시
                    CustomAlertManager.shared.showAlert(
                        message: "채팅을 불러오지 못했습니다.",
                        completion: {
                            self.onBack()
                        }
                    )
                }
            }
        )
    }

    
    /// 초기 메시지 설정
    private func setupInitialMessages(from chatInitData: ChatInitData) {
        Logger.dev("📋 [CHAT_INIT] 채팅방 정보 - Room: \(chatInitData.roomId), Branch: \(chatInitData.branchName)")
        
        // 브랜치명 업데이트
        self.branchName = chatInitData.branchName
        
        // messageSeq를 기준으로 안전하게 정렬 (nil 값은 0으로 처리)
        let initialMessages = Array(
            chatInitData.messages
                .sorted { lhs, rhs in
                    let lhsSeq = lhs.messageSeq ?? 0
                    let rhsSeq = rhs.messageSeq ?? 0
                    return lhsSeq > rhsSeq  // 내림차순 정렬
                }
                .reversed()  // 역순으로 변환 (결과적으로 오름차순)
        )
        
        
        self.messages = initialMessages
    }
    
    
    private func handleScrollToTop() {
        if(!isMoreLoading) {
            return
        }
        
        pageCount = pageCount + 1
        
        networkViewModel.callChatMessage(
            userId: String(Constants.getUserId()),
            branchId: String(Constants.getBranchId()),
            page: pageCount,
            size: 30,
            onSuccess: { result in
                Logger.dev("✅ [CHAT_MESSAGE] 메시지 응답: \(String(describing: result))")
                DispatchQueue.main.async {
                    self.isLoadingPreviousMessages = false
                    
                    // 결과 파싱 및 메시지 설정
                    if let resultData = result as? BaseData<ChatMessageData>,
                       let chatData = resultData.data {
                        Logger.dev("📋 [CHAT_MESSAGE] 채팅 데이터 파싱 성공")
                        
                        self.isMoreLoading = chatData.hasMore
                        
                        // messageSeq를 기준으로 안전하게 정렬 (nil 값은 0으로 처리)
                        let addMessages = Array(
                            chatData.messages
                                .sorted { lhs, rhs in
                                    let lhsSeq = lhs.messageSeq ?? 0
                                    let rhsSeq = rhs.messageSeq ?? 0
                                    return lhsSeq > rhsSeq  // 내림차순 정렬
                                }
                                .reversed()  // 역순으로 변환 (결과적으로 오름차순)
                        )
                                                
                        // 기존 메시지 앞에 붙이기
                        self.messages.insert(contentsOf: addMessages, at: 0)
                    }
                    
                }
            },
            onError: { error in
                Logger.error("❌ [CHAT_MESSAGE] API 실패: \(error)")
            }
        )
    }

    // MARK: - 소켓 연결 관련 함수들
    
    /// 소켓 연결
    private func connectSocket() {
        Logger.dev("🔌 [SOCKET] 소켓 연결 시작")
        
        isSocketConnecting = true
        
        // SocketManager 초기화
        socketManager.initialize()
        
        // 소켓 연결 - 메시지 콜백과 함께
        socketManager.connect(
            onDmMessage: { [self] chatMessage in
                Logger.dev("📨 [SOCKET_MSG] 소켓으로부터 새 메시지 수신")
                DispatchQueue.main.async {
                    self.addNewMessage(chatMessage)
                }
            },
            onConnected: { [self] connected in
                Logger.dev("👋 [SOCKET_CONNECT]")
                DispatchQueue.main.async {
                    self.isSocketConnecting = false
                }
            }
        )
        
        // 연결 상태 모니터링
        DispatchQueue.main.asyncAfter(deadline: .now() + 10.0) {
            if self.isSocketConnecting {
                // 10초 후에도 연결 중이면 실패로 처리
                self.handleSocketConnectionError()
            }
        }
    }
    
    /// 소켓 연결 실패 처리
    private func handleSocketConnectionError() {
        DispatchQueue.main.async {
            self.isSocketConnecting = false
            
            Logger.error("💥 [SOCKET] 소켓 연결 실패")
            
            // Alert로 에러 표시
            CustomAlertManager.shared.showAlert(
                message: "채팅 서버에 연결하지 못했습니다.",
                completion: {
                    self.onBack()
                }
            )
        }
    }
    
    /// 소켓 연결 해제
    private func disconnectSocket() {
        Logger.dev("🔌 [SOCKET] 소켓 연결 해제")
        socketManager.disconnect()
        isSocketConnecting = false
    }

    // MARK: - 메시지 대기열 관련 함수들
    
    /// 새로운 메시지를 추가 (대기열 방식)
    private func addNewMessage(_ message: ChatMessage) {
        Logger.dev("📩 [NEW_MSG] 새 메시지 수신 : '\(message.text)' (내 메시지: \(message.isMe))")
        currentMaxMessageSeq = currentMaxMessageSeq + 1
        let messageWithSeq = message.withMessageSeq(currentMaxMessageSeq)
        
        if messageWithSeq.isMe {
            // 내가 보낸 메시지는 항상 즉시 추가
            Logger.dev("⬇️ [ADD_DIRECT] 내 메시지 즉시 추가")
            messages.append(messageWithSeq)
        } else {
            // 상대방 메시지는 항상 대기열에 추가 (순서 보장을 위해)
            Logger.dev("⏳ [ADD_PENDING] 상대방 메시지 대기열에 추가")
            pendingMessages.append(messageWithSeq)
            
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
            showPendingMessages()
        }
    }
    
    /// 대기 중인 메시지들을 보여주기
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
    
    /// 사용자 액션으로 스크롤을 하단으로 이동
    private func scrollToBottomAsUserAction() {
        
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
    
    /// 대기 메시지들을 애니메이션과 함께 순차적으로 추가
    private func addPendingMessagesWithAnimation() {
        guard !processingQueue.isEmpty else {
            
            // 처리 완료
            isProcessingPendingMessages = false
            
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

        // 소켓이 연결되어 있는지 확인
        guard socketManager.isConnected() else {
            Logger.error("❌ [SEND_MSG] 소켓이 연결되지 않음")
            CustomAlertManager.shared.showErrorAlert(
                message: "서버 연결이 끊어졌습니다.\n채팅을 다시 시작해주세요."
            )
            return
        }

        // 새 메시지 생성 - 실제 API 스펙에 맞게 생성
        let currentTime = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        currentMaxMessageSeq = currentMaxMessageSeq + 1
        
        let receiverId = Constants.getBranchId()
        let messageContent = text
        let myMessage = ChatMessage(
            messageId: UUID().uuidString,
            senderId: Constants.getUserId(),
            senderType: "STUDENT",
            senderName: "나",
            receiverId: receiverId,
            receiverType: "admin",
            receiverName: "관리자",
            branchId: receiverId,
            branchName: Constants.getBranchName(),
            content: messageContent,
            isRead: false,
            readAt: nil,
            sentAt: dateFormatter.string(from: currentTime),
            roomId: "default_room",
            messageSeq: currentMaxMessageSeq
        )
        
        // 내 메시지는 항상 즉시 추가 (대기열 방식 사용)
        addNewMessage(myMessage)

        // 로그아웃 테스트 코드
        if(messageContent == "로그아웃") {
            // 1. 로그인 정보 삭제
            UserDefaultsManager.clearLoginInfo()
            
            // 2. 앱 재시작 알림 발송
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                NotificationCenter.default.post(
                    name: Notification.Name("RestartApp"),
                    object: nil
                )
            }
            text = ""
            textEditorHeight = ChatScreen.textEditorDefault
            return
        }
        
        // 전화 테스트 코드
        if(messageContent == "전화") {
            if let url = URL(string: "tel://01075761690") {
                if UIApplication.shared.canOpenURL(url) {
                    UIApplication.shared.open(url)
                }
            }
            text = ""
            textEditorHeight = ChatScreen.textEditorDefault
            return
        }
        // 앱로그 테스트 코드
        if(messageContent == "로그") {
            Logger.shareLogFile()
            text = ""
            textEditorHeight = ChatScreen.textEditorDefault
            return
        }
        
        // 실제 소켓을 통한 메시지 전송
        Logger.dev("📤 [SEND_MSG] 소켓으로 메시지 전송: \(messageContent)")
        socketManager.sendDmMessage(messageContent)
        
        // 입력창 초기화
        text = ""
        textEditorHeight = ChatScreen.textEditorDefault
        
        // 스크롤을 하단으로 이동
        self.scrollToBottom()
    }
    
    private func scrollToBottom() {
        
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
