import Combine
import SwiftUI
import UIKit

// MARK: - UITableView Wrapper
struct ChatTableView: UIViewRepresentable {
    @Binding var messages: [ChatMessage]
    @Binding var isScrollAtBottom: Bool
    @Binding var tableViewRef: UITableView?
    
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
        
        Logger.dev("🔄 [RELOAD] 리로드 실행")
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
        
        init(_ parent: ChatTableView) {
            self.parent = parent
            self.messages = parent.messages
            super.init()
            updateChatItems()
            Logger.dev("🎯 [COORD] Coordinator 초기화 완료")
        }
        
        // 메시지 배열을 ChatItem 배열로 변환 (날짜 구분선 포함)
        func updateChatItems() {
            Logger.dev("🔄 [ITEMS] ChatItems 업데이트 시작")
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
            }
        }
        
        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if !decelerate && !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
                Logger.dev("👆 [SCROLL] 드래그 종료 - contentOffset: \(scrollView.contentOffset)")
            }
        }
        
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            if !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
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
        
        func willStartProgrammaticScroll() {
            Logger.dev("🤖 [SCROLL] 프로그래매틱 스크롤 시작")
            isUpdatingScrollState = true
            isProgrammaticScrolling = true
        }
    }
}
