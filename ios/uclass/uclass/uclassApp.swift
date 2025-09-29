//
//  uclassApp.swift
//  uclass
//
//  Created by 김용식 on 8/27/25.
//

import KakaoSDKAuth
import KakaoSDKCommon
import NidThirdPartyLogin
import SwiftUI

@main
struct uclassApp: App {
    @StateObject private var webViewManager = WebViewManager()
    @State private var appKey = UUID()

    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            SplashView()
                .environmentObject(webViewManager)
                .id(appKey)
                .onOpenURL { url in
                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                    }
                    if NidOAuth.shared.handleURL(url) {
                        _ = true
                    }
                }
                .onReceive(
                    NotificationCenter.default.publisher(
                        for: Notification.Name("RestartApp")
                    )
                ) { _ in
                    Logger.dev("🔄 앱 재시작")
                    withAnimation(.easeInOut(duration: 0.3)) {
                        appKey = UUID()
                    }
                }
                // 🆕 백그라운드에서 포그라운드로 돌아올 때마다 뱃지 리셋
                .onReceive(
                    NotificationCenter.default.publisher(
                        for: UIApplication.didBecomeActiveNotification
                    )
                ) { _ in
                    Logger.dev("🔄 앱이 활성화됨 - 뱃지 리셋")
                    PushNotificationManager.shared.displayResetBadge()
                }
                .overlay(AlertContainer())
                .overlay(LoadingContainer())
        }
    }
}
