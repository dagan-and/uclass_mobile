import UIKit
import KakaoSDKAuth
import KakaoSDKCommon
import UserNotifications
import NidThirdPartyLogin

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        
        KakaoSDK.initSDK(appKey: "cc0faae5b1dd0468f0440656b12b8601")
        NidOAuth.shared.initialize()
        
        return true
    }
    
    // 예: 카카오/구글 로그인에서 URL 처리할 때 필요
    
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        if (AuthApi.isKakaoTalkLoginUrl(url)) {
            return AuthController.handleOpenUrl(url: url)
        }
        if NidOAuth.shared.handleURL(url) {
            return true
        }
        return false
    }
}
