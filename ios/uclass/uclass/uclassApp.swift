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
    @State private var appKey = UUID() // 앱 전체를 재시작하기 위한 키 추가


    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup {
            SplashView()
                .environmentObject(webViewManager)
                .id(appKey) // 이 키가 변경되면 전체 뷰가 재생성됨
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

                    // 새로운 키로 전체 뷰 재생성
                    withAnimation(.easeInOut(duration: 0.3)) {
                        appKey = UUID()
                    }
                }
                .overlay(AlertContainer())  // 여기에 AlertContainer 추가
                .overlay(LoadingContainer())  // Loading 컨테이너 추가

        }
    }

}
