import SwiftUI

struct MainScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @StateObject private var chatBadgeViewModel = ChatBadgeViewModel.shared
    @State private var selectedTab = 0
    @State private var previousTab = 0 // 이전 탭 저장
    @State private var showChatScreen = false // 채팅 화면 표시 여부

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // 메인 컨텐츠
                if showChatScreen {
                    // 채팅 화면을 전체 화면으로 표시
                    ChatScreen(onBack: {
                        Logger.dev("🔙 채팅 화면에서 뒤로가기")
                        showChatScreen = false
                        selectedTab = previousTab // 이전 탭으로 복원
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

                    // 커스텀 하단 바 (채팅 화면일 때는 숨김)
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
        .onChange(of: selectedTab) { oldTab, newTab in
            // 채팅 탭이 아닌 다른 탭 선택 시 채팅 화면 닫기
            if showChatScreen && newTab != 1 {
                Logger.dev("🔄 다른 탭 선택으로 채팅 화면 닫기")
                showChatScreen = false
            }
        }
        .onAppear {
            Logger.dev("📱 MainScreen 나타남")
            PushNotificationManager.shared.setMainScreenActive(true)
            PushNotificationManager.shared.displayResetBadge()
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
                for: Notification.Name("ChatBadgeOff")
            )
        ) { notification in
            Logger.dev("🔴 채팅 뱃지 숨기기")
            chatBadgeViewModel.hideBadge()
        }
    }
}
