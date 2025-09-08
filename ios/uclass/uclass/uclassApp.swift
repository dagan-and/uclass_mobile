//
//  uclassApp.swift
//  uclass
//
//  Created by 김용식 on 8/27/25.
//

import SwiftUI
import KakaoSDKCommon
import KakaoSDKAuth
import NidThirdPartyLogin

@main
struct uclassApp: App {
    @StateObject private var webViewManager = WebViewManager()
    
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup {
            SplashView()
                .environmentObject(webViewManager)
                .onOpenURL { url in
                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                    }
                    if NidOAuth.shared.handleURL(url) {
                        _ = true
                    }
                }
                .overlay(AlertContainer()) // 여기에 AlertContainer 추가
                .overlay(LoadingContainer()) // Loading 컨테이너 추가
        }
    }

}
