import Combine
import SwiftUI
import UIKit

// MARK: - UITableView Wrapper
struct ChatTableView: UIViewRepresentable {
    @Binding var messages: [ChatMessage]
    @Binding var isScrollAtBottom: Bool
    @Binding var tableViewRef: UITableView?
    @Binding var isLoadingPreviousMessages: Bool
    
    func makeUIView(context: Context) -> UITableView {
        let tableView = UITableView()
        tableView.delegate = context.coordinator
        tableView.dataSource = context.coordinator
        tableView.backgroundColor = .white
        tableView.separatorStyle = .none
        tableView.allowsSelection = false
        tableView.showsVerticalScrollIndicator = false
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.transform = CGAffineTransform(scaleX: 1, y: -1)
        
        // 셀 등록
        tableView.register(ChatTableViewCell.self, forCellReuseIdentifier: "ChatCell")
        tableView.register(DateSeparatorCell.self, forCellReuseIdentifier: "DateSeparatorCell")
        
        // 테이블뷰 참조 저장
        DispatchQueue.main.async {
            tableViewRef = tableView
        }
        
        Logger.dev("📱 [ChatTableView] TableView 생성 완료")
        return tableView
    }
    
    func updateUIView(_ uiView: UITableView, context: Context) {
        let coordinator = context.coordinator
        let previousMessageCount = coordinator.messages.count
        let currentMessageCount = messages.count
        
        Logger.dev("📄 [UPDATE] 업데이트 시작")
        Logger.dev("📊 [UPDATE] 이전 메시지 수: \(previousMessageCount) -> 현재: \(currentMessageCount)")
        
        // 데이터 업데이트
        coordinator.messages = messages
        coordinator.parent = self
        coordinator.updateChatItems()
        
        let isNewMessage = currentMessageCount > previousMessageCount
        let shouldAutoScroll = isNewMessage && (messages.last?.isMe == true || isScrollAtBottom)
        
        Logger.dev("📄 [RELOAD] 리로드 실행")
        uiView.reloadData()
        
        // 자동 스크롤 처리
        if shouldAutoScroll && !coordinator.chatItems.isEmpty {
            Logger.dev("⬇️ [SCROLL] 자동 스크롤 실행")
            DispatchQueue.main.async {
                coordinator.willStartProgrammaticScroll()
                let indexPath = IndexPath(row: 0, section: 0)
                uiView.scrollToRow(at: indexPath, at: .top, animated: true)
            }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UITableViewDataSource, UITableViewDelegate {
        var parent: ChatTableView
        var messages: [ChatMessage] = []
        var chatItems: [ChatItem] = []
        private var isUpdatingScrollState = false
        private var isProgrammaticScrolling = false
        
        // 스크롤 최상단 감지 및 데이터 로딩 관련 변수
        private var isLoadingMoreData = false
        private var hasCheckedTopScroll = false
        
        init(_ parent: ChatTableView) {
            self.parent = parent
            self.messages = parent.messages
            super.init()
            updateChatItems()
            Logger.dev("🎯 [COORD] Coordinator 초기화 완료")
        }
        
        // 메시지 배열을 ChatItem 배열로 변환 (날짜 구분선 포함)
        func updateChatItems() {
            Logger.dev("📄 [ITEMS] ChatItems 업데이트 시작")
            chatItems.removeAll()
            
            guard !messages.isEmpty else {
                Logger.dev("🔭 [ITEMS] 메시지가 비어있음")
                return
            }
            
            var currentDate: Date?
            
            for (index, message) in messages.enumerated() {
                let messageDate = Calendar.current.startOfDay(for: message.timestamp)
                
                // 날짜가 바뀌면 날짜 구분선 추가
                if currentDate != messageDate {
                    chatItems.append(.dateSeparator(messageDate))
                    currentDate = messageDate
                }
                
                chatItems.append(.message(message))
                if index < 3 || index >= messages.count - 3 {
                    Logger.dev("📝 [ITEMS] 메시지 추가 [\(index)]: '\(message.text)' (내 메시지: \(message.isMe))")
                }
            }
            
            Logger.dev("✅ [ITEMS] ChatItems 업데이트 완료 - 총 \(chatItems.count)개")
        }
        
        func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
            return chatItems.count
        }
        
        func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
            // 역순으로 표시 (최신 메시지가 아래에 오도록)
            let reverseIndex = chatItems.count - 1 - indexPath.row
            guard reverseIndex >= 0 && reverseIndex < chatItems.count else {
                Logger.dev("❌ [CELL] 인덱스 범위 오류: reverseIndex=\(reverseIndex), chatItems.count=\(chatItems.count)")
                return UITableViewCell()
            }
            
            let chatItem = chatItems[reverseIndex]
            
            switch chatItem {
            case .message(let message):
                let cell = tableView.dequeueReusableCell(withIdentifier: "ChatCell", for: indexPath) as! ChatTableViewCell
                cell.configure(with: message)
                cell.transform = CGAffineTransform(scaleX: 1, y: -1)
                return cell
                
            case .dateSeparator(let date):
                let cell = tableView.dequeueReusableCell(withIdentifier: "DateSeparatorCell", for: indexPath) as! DateSeparatorCell
                cell.configure(with: date)
                cell.transform = CGAffineTransform(scaleX: 1, y: -1)
                return cell
            }
        }
        
        func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
            let reverseIndex = chatItems.count - 1 - indexPath.row
            guard reverseIndex >= 0 && reverseIndex < chatItems.count else {
                return 60
            }
            
            let chatItem = chatItems[reverseIndex]
            
            switch chatItem {
            case .message:
                return UITableView.automaticDimension
            case .dateSeparator:
                return 50
            }
        }
        
        func tableView(_ tableView: UITableView, estimatedHeightForRowAt indexPath: IndexPath) -> CGFloat {
            let reverseIndex = chatItems.count - 1 - indexPath.row
            guard reverseIndex >= 0 && reverseIndex < chatItems.count else {
                return 60
            }
            
            let chatItem = chatItems[reverseIndex]
            
            switch chatItem {
            case .message:
                return 80
            case .dateSeparator:
                return 50
            }
        }
        
        // MARK: - 스크롤 상태 추적
        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            if !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
                checkScrollTop(scrollView)
            }
        }
        
        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if !decelerate && !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
                checkScrollTop(scrollView)
                Logger.dev("👆 [SCROLL] 드래그 종료 - contentOffset: \(scrollView.contentOffset)")
            }
        }
        
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            if !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
                checkScrollTop(scrollView)
                Logger.dev("🛑 [SCROLL] 감속 종료 - contentOffset: \(scrollView.contentOffset)")
            }
        }
        
        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            isUpdatingScrollState = false
            isProgrammaticScrolling = false
            checkScrollPosition(scrollView)
            Logger.dev("🎬 [SCROLL] 애니메이션 종료 - contentOffset: \(scrollView.contentOffset)")
        }
        
        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
            isUpdatingScrollState = false
            isProgrammaticScrolling = false
            hasCheckedTopScroll = false
            Logger.dev("👆 [SCROLL] 드래그 시작 - contentOffset: \(scrollView.contentOffset)")
        }
        
        private func checkScrollPosition(_ scrollView: UIScrollView) {
            let contentOffset = scrollView.contentOffset.y
            let isAtBottom = contentOffset <= 50
            
            if parent.isScrollAtBottom != isAtBottom {
                Logger.dev("📍 [SCROLL] 스크롤 상태 변경: \(parent.isScrollAtBottom) -> \(isAtBottom) (offset: \(contentOffset))")
                DispatchQueue.main.async { [weak self] in
                    self?.parent.isScrollAtBottom = isAtBottom
                }
            }
        }
        
        // MARK: - 스크롤 최상단 감지 및 더미 데이터 로딩
        private func checkScrollTop(_ scrollView: UIScrollView) {
            guard !isLoadingMoreData && !hasCheckedTopScroll else { return }
            
            let contentOffset = scrollView.contentOffset.y
            let contentHeight = scrollView.contentSize.height
            let frameHeight = scrollView.frame.height
            
            // 최상단 감지 (역순 스크롤이므로 contentOffset이 contentHeight - frameHeight에 가까우면 최상단)
            let maxOffset = contentHeight - frameHeight
            let isAtTop = contentOffset >= maxOffset - 50
            
            if isAtTop && contentHeight > frameHeight {
                Logger.dev("⬆️ [SCROLL_TOP] 스크롤 최상단 감지 - 더미 데이터 로딩 시작")
                hasCheckedTopScroll = true
                loadMoreDummyData()
            }
        }
        
        /// 더미 데이터 추가 로딩
        private func loadMoreDummyData() {
            guard !isLoadingMoreData else { return }
            
            isLoadingMoreData = true
            Logger.dev("⏳ [LOAD_DATA] 더미 데이터 로딩 시작 (1초 후)")
            
            // ChatScreen에 로딩 상태 알림
            DispatchQueue.main.async { [weak self] in
                self?.parent.isLoadingPreviousMessages = true
            }
            
            // 1초 후 더미 데이터 추가
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                self?.addDummyMessages()
            }
        }
        
        /// 더미 메시지 생성 및 추가
        private func addDummyMessages() {
            Logger.dev("📝 [DUMMY_DATA] 더미 메시지 생성 시작")
            
            let newMessages = generateDummyMessages()
            
            // 기존 메시지 앞쪽에 새로운 메시지들 추가 (시간순으로 정렬 유지)
            let updatedMessages = newMessages + parent.messages
            
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                
                Logger.dev("✅ [DUMMY_DATA] \(newMessages.count)개의 더미 메시지 추가 완료")
                
                // 메시지 업데이트
                self.parent.messages = updatedMessages
                
                // 로딩 상태 해제
                self.isLoadingMoreData = false
                
                // ChatScreen에 로딩 완료 알림
                self.parent.isLoadingPreviousMessages = false
                
                // 스크롤 위치 조정 (새로 추가된 데이터로 인한 위치 변경 방지)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    self.adjustScrollPositionAfterDataLoad(newMessageCount: newMessages.count)
                }
            }
        }
        
        /// 더미 메시지 생성
        private func generateDummyMessages() -> [ChatMessage] {
            var messages: [ChatMessage] = []
            let messageTexts = [
                "이전 메시지입니다", "더 오래된 대화", "과거의 채팅 기록",
                "며칠 전 대화", "이전 대화 내용", "예전에 나눈 이야기",
                "지난주 대화", "과거 메시지", "이전 채팅 로그",
                "오래된 대화 기록"
            ]
            
            // 기존 가장 오래된 메시지보다 이전 시간으로 설정
            let oldestExistingDate = parent.messages.first?.timestamp ?? Date()
            let calendar = Calendar.current
            
            for i in 0..<20 {
                // 기존 메시지보다 1-20시간 전으로 설정
                let hoursBack = 20 - i
                let messageDate = calendar.date(byAdding: .hour, value: -hoursBack, to: oldestExistingDate) ?? oldestExistingDate
                
                let isMe = i % 4 == 0 // 대략 1/4은 내 메시지
                let text = messageTexts[i % messageTexts.count] + " #\(i + 1)"
                
                let message = ChatMessage(
                    text: text,
                    isMe: isMe,
                    timestamp: messageDate
                )
                
                messages.append(message)
            }
            
            Logger.dev("📦 [DUMMY_GEN] \(messages.count)개의 더미 메시지 생성 완료")
            return messages
        }
        
        /// 데이터 로딩 후 스크롤 위치 조정
        private func adjustScrollPositionAfterDataLoad(newMessageCount: Int) {
            Logger.dev("📐 [SCROLL_ADJUST] 스크롤 위치 조정 시작 (새 메시지: \(newMessageCount)개)")
            
            // 스크롤 위치를 새로 추가된 메시지만큼 아래로 이동
            // 이렇게 하면 사용자가 보고 있던 메시지가 그대로 화면에 유지됨
            hasCheckedTopScroll = false
        }
        
        func willStartProgrammaticScroll() {
            Logger.dev("🤖 [SCROLL] 프로그래매틱 스크롤 시작")
            isUpdatingScrollState = true
            isProgrammaticScrolling = true
        }
    }
}
