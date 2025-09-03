import UIKit
import KakaoSDKAuth
import KakaoSDKCommon
import UserNotifications
import NidThirdPartyLogin
import Firebase
import FirebaseMessaging

class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        
        //SNS 로그인 설정
        KakaoSDK.initSDK(appKey: "cc0faae5b1dd0468f0440656b12b8601")
        NidOAuth.shared.initialize()
        
        
        //FCM 설정
        FirebaseApp.configure()
        Messaging.messaging().delegate = self

        
        
        // 앱 실행 시 사용자에게 알림 허용 권한을 받는다.
        UNUserNotificationCenter.current().delegate = self
        
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(
            options: authOptions,
            completionHandler: {_, _ in}
        )
        
        // UNUserNotificationCenterDelegate를 구현한 메서드를 실행시킨다.
        application.registerForRemoteNotifications()
        
        return true
    }
    
    // MARK: - APNs 등록 성공
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("APNs 디바이스 토큰 등록 성공")
        
        // FCM에 APNs 토큰 설정
        Messaging.messaging().apnsToken = deviceToken
    }
    
    // MARK: - APNs 등록 실패
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs 등록 실패: \(error.localizedDescription)")
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


extension AppDelegate: MessagingDelegate {
    // FCM Token 업데이트 시
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("🥳", #function, fcmToken ?? "nil")
    }
    
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    // 앱 화면을 보고있는 중(포그라운드)에 푸시 올 때
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        print("😎", #function)
        
        // 푸시 알림 데이터가 userInfo에 담겨있다.
        let userInfo = notification.request.content.userInfo
        print(userInfo)
        
        if #available(iOS 14.0, *) {
            return [.sound, .banner, .list]
        } else {
            return []
        }
    }
}
