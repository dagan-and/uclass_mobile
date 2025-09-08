import SwiftUI

struct MainScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @StateObject private var chatBadgeViewModel = ChatBadgeViewModel.shared
    @State private var selectedTab = 0

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // 메인 컨텐츠 - Fade 애니메이션
                ZStack {
                    if selectedTab == 0 {
                        WebViewScreen()
                            .transition(.opacity)
                            .zIndex(1)
                    }

                    if selectedTab == 1 {
                        ChatScreen()
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
                    showChatBadge: $chatBadgeViewModel.showChatBadge
                )
            }
        }
        .background(Color.white)
        .navigationBarHidden(true)
        .onChange(of: selectedTab) { newTab in
            // 채팅 탭을 선택하면 뱃지 숨김
            if newTab == 1 {
                chatBadgeViewModel.hideBadge()
            }
        }
        .onAppear {
            Logger.dev("📱 MainScreen 나타남")
            // MainScreen이 활성화됨을 알림
            PushNotificationManager.shared.setMainScreenActive(true)
        }
        .onDisappear {
            Logger.dev("📱 MainScreen 사라짐")
            // MainScreen이 비활성화됨을 알림
            PushNotificationManager.shared.setMainScreenActive(false)
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("NavigateToChat")
            )
        ) { notification in
            Logger.dev("🎯 채팅 화면으로 이동")
            selectedTab = 1  // 채팅 탭으로 이동
        }
    }
}
