import Combine
import SwiftUI
import UIKit

// MARK: - UITableView Wrapper
struct ChatTableView: UIViewRepresentable {
    @Binding var messages: [ChatMessage]
    @Binding var isScrollAtBottom: Bool
    @Binding var tableViewRef: UITableView?
    @Binding var isLoadingPreviousMessages: Bool
    
    // 스크롤 최상단 감지 시 호출할 콜백 추가
    let onScrollToTop: (() -> Void)?
    
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

        // 데이터 업데이트
        coordinator.messages = messages
        coordinator.parent = self
        coordinator.updateChatItems()

        Logger.dev("📄 [RELOAD] 리로드 실행")
        uiView.reloadData()
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
            
            for (_, message) in messages.enumerated() {
                let messageDate = Calendar.current.startOfDay(for: message.timestamp)
                
                // 날짜가 바뀌면 날짜 구분선 추가
                if currentDate != messageDate {
                    chatItems.append(.dateSeparator(messageDate))
                    currentDate = messageDate
                }
                
                chatItems.append(.message(message))
            }
            
        }
        
        func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
            return chatItems.count
        }
        
        func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
            // 역순으로 표시 (최신 메시지가 아래에 오도록)
            let reverseIndex = chatItems.count - 1 - indexPath.row
            guard reverseIndex >= 0 && reverseIndex < chatItems.count else {
                Logger.dev("⌚ [CELL] 인덱스 범위 오류: reverseIndex=\(reverseIndex), chatItems.count=\(chatItems.count)")
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
            }
        }
        
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            if !isUpdatingScrollState && !isProgrammaticScrolling {
                checkScrollPosition(scrollView)
                checkScrollTop(scrollView)
            }
        }
        
        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            isUpdatingScrollState = false
            isProgrammaticScrolling = false
            checkScrollPosition(scrollView)
        }
        
        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
            isUpdatingScrollState = false
            isProgrammaticScrolling = false
            hasCheckedTopScroll = false
        }
        
        private func checkScrollPosition(_ scrollView: UIScrollView) {
            let contentOffset = scrollView.contentOffset.y
            let isAtBottom = contentOffset <= 50
            
            if parent.isScrollAtBottom != isAtBottom {
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
                hasCheckedTopScroll = true
                
                // 👇 여기서 ChatScreen으로 콜백 전달
                parent.onScrollToTop?()
            }
        }
        
        /// 데이터 로딩 후 스크롤 위치 조정
        private func adjustScrollPositionAfterDataLoad(newMessageCount: Int) {
            
            // 스크롤 위치를 새로 추가된 메시지만큼 아래로 이동
            // 이렇게 하면 사용자가 보고 있던 메시지가 그대로 화면에 유지됨
            hasCheckedTopScroll = false
        }
        
        func willStartProgrammaticScroll() {
            isUpdatingScrollState = true
            isProgrammaticScrolling = true
        }
    }
}
