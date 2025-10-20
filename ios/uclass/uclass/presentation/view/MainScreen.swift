import SwiftUI

struct MainScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @StateObject private var chatBadgeViewModel = ChatBadgeViewModel.shared
    @State private var selectedTab = 0
    @State private var previousTab = 0
    @State private var showChatScreen = false

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // 메인 컨텐츠
                if showChatScreen {
                    // 채팅 화면을 전체 화면으로 표시
                    ChatScreen(onBack: {
                        Logger.dev("🔙 채팅 화면에서 뒤로가기")
                        showChatScreen = false
                        selectedTab = previousTab
                    })
                } else {
                    ZStack {
                        if selectedTab == 0 {
                            WebViewScreen()
                                .transition(.opacity)
                                .zIndex(1)
                        }

                        if selectedTab == 2 {
                            NoticeScreen()
                                .transition(.opacity)
                                .zIndex(1)
                        }
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
            }
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
