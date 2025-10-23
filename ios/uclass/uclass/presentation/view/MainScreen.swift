import SwiftUI

struct MainScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @StateObject private var chatBadgeViewModel = ChatBadgeViewModel.shared
    @State private var selectedTab = 0
    @State private var previousTab = 0
    @State private var showChatScreen = false

    var body: some View {
        ZStack {
            // ✅ 메인 컨텐츠 (항상 존재)
            VStack(spacing: 0) {
                // ✅ 모든 탭을 ZStack으로 미리 생성하고 opacity로 제어
                ZStack {
                    // 홈 탭 (WebView)
                    WebViewScreen()
                        .opacity(selectedTab == 0 ? 1 : 0)
                        .zIndex(selectedTab == 0 ? 1 : 0)
                    
                    // 공지사항 탭
                    NoticeScreen()
                        .opacity(selectedTab == 2 ? 1 : 0)
                        .zIndex(selectedTab == 2 ? 1 : 0)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()

                // 커스텀 하단 바
                MainBottomBar(
                    selectedTab: $selectedTab,
                    showChatBadge: $chatBadgeViewModel.showChatBadge,
                    onChatTap: {
                        Logger.dev("💬 채팅 탭 버튼 터치")
                        previousTab = selectedTab
                        withAnimation(.easeInOut(duration: 0.3)) {
                            showChatScreen = true
                        }
                    }
                )
            }
            .opacity(showChatScreen ? 0 : 1)
            .zIndex(showChatScreen ? 0 : 1)
            
            // ✅ 채팅 화면 (항상 존재하지만 숨김, isVisible로 소켓 연결 제어)
            ChatScreen(
                isVisible: showChatScreen,  // ✅ 화면 표시 여부 전달
                onBack: {
                    Logger.dev("🔙 채팅 화면에서 뒤로가기")
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showChatScreen = false
                    }
                    selectedTab = previousTab
                }
            )
            .opacity(showChatScreen ? 1 : 0)
            .zIndex(showChatScreen ? 1 : 0)
        }
        .background(Color.white)
        .navigationBarHidden(true)
        .onChange(of: selectedTab) { newTab in
            if showChatScreen && newTab != 1 {
                Logger.dev("🔄 다른 탭 선택으로 채팅 화면 닫기")
                showChatScreen = false
            }
        }
        .onAppear {
            Logger.dev("📱 MainScreen 나타남")
            PushNotificationManager.shared.setMainScreenActive(true)
        }
        .onDisappear {
            Logger.dev("📱 MainScreen 사라짐")
            PushNotificationManager.shared.setMainScreenActive(false)
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("NavigateToChat")
            )
        ) { notification in
            Logger.dev("🎯 알림으로 채팅 화면 이동")
            previousTab = selectedTab
            showChatScreen = true
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("NavigateToHome")
            )
        ) { notification in
            Logger.dev("🏠 홈 탭으로 이동")
            
            // 홈 탭으로 이동
            if selectedTab != 0 {
                selectedTab = 0
            }
            
            // 채팅 화면이 열려있으면 닫기
            if showChatScreen {
                showChatScreen = false
            }
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("NavigateToUrl")
            )
        ) { notification in
            Logger.dev("🎯 알림으로 URL 이동")
            
            // 홈 탭으로 이동
            if selectedTab != 0 {
                selectedTab = 0
            }
            
            // 채팅 화면이 열려있으면 닫기
            if showChatScreen {
                showChatScreen = false
            }
            
            // URL 로드
            if let urlString = notification.object as? String {
                Logger.dev("🌐 WebView URL 로드: \(urlString)")
                webViewManager.loadUrl(urlString)
            }
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("ChatBadgeOff")
            )
        ) { notification in
            Logger.dev("🔴 채팅 뱃지 숨기기")
            chatBadgeViewModel.hideBadge()
        }
    }
}
