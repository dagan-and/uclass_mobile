import UIKit
import KakaoSDKAuth
import KakaoSDKCommon
import UserNotifications
import NidThirdPartyLogin
import Firebase
import FirebaseMessaging
import FirebaseCrashlytics

class AppDelegate: NSObject, UIApplicationDelegate {
    
    // 👇 앱 실행 시 푸시로 실행되었는지 여부 (cold start 구분)
    static var fromAppLaunch: Bool = false
    
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // 앱 기본값 설정
        Constants.baseURL = "https://dev-umanager.ubase.kr"
        Constants.isDebug = true
        NetworkAPI.shared.initialize()
        
        // SNS 로그인 설정
        KakaoSDK.initSDK(appKey: "cc0faae5b1dd0468f0440656b12b8601")
        NidOAuth.shared.initialize()
        
        // FCM 설정
        FirebaseApp.configure()


        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        
        // FCM 토큰 받기 위해서는 remote notification 등록
        application.registerForRemoteNotifications()
        
        // 푸시 알림으로 앱이 실행된 경우
         if let notification = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
             Logger.dev("🚀 앱이 푸시 알림으로 실행됨")
             
             // 약간의 지연 후 처리 (앱 초기화 완료 후)
             DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                 PushNotificationManager.shared.handlePushNotification(userInfo: notification , fromAction: true)
             }
         }
        
        return true
    }
    
    // MARK: - APNs 등록 성공
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Logger.dev("APNs 디바이스 토큰 등록 성공")
        Messaging.messaging().apnsToken = deviceToken
    }
    
    // MARK: - APNs 등록 실패
    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Logger.dev("APNs 등록 실패: \(error.localizedDescription)")
    }
    
    // ✅ 로그인 관련 URL 처리
    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        if (AuthApi.isKakaoTalkLoginUrl(url)) {
            return AuthController.handleOpenUrl(url: url)
        }
        if NidOAuth.shared.handleURL(url) {
            return true
        }
        return false
    }
}

extension UIApplication {
    func topViewController(base: UIViewController? = nil) -> UIViewController? {
        let baseVC = base ?? connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.windows
            .first { $0.isKeyWindow }?.rootViewController
        
        if let nav = baseVC as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = baseVC as? UITabBarController {
            return topViewController(base: tab.selectedViewController)
        }
        if let presented = baseVC?.presentedViewController {
            return topViewController(base: presented)
        }
        return baseVC
    }
}

extension AppDelegate: MessagingDelegate {
    // FCM Token 업데이트 시
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Logger.dev("FCM Token:: \(fcmToken ?? "nil")")
        Constants.fcmToken = fcmToken
    }

}


extension AppDelegate: UNUserNotificationCenterDelegate {
    
    // 앱이 포그라운드에 있을 때 알림 수신
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        
        let userInfo = notification.request.content.userInfo
        Logger.dev("📱 포그라운드에서 알림 수신")
        Logger.dev("📩 willPresent 페이로드: \(userInfo)")
        
        // 푸시 처리
        PushNotificationManager.shared.handlePushNotification(userInfo: userInfo)
        
        // 포그라운드에서도 알림 표시
        completionHandler([.banner, .sound, .badge])
    }
    
    // 사용자가 알림을 탭했을 때
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        
        let userInfo = response.notification.request.content.userInfo
        Logger.dev("📱 사용자가 알림을 탭함")
        Logger.dev("📩 didReceive 페이로드: \(userInfo)")
        // 푸시 처리
        PushNotificationManager.shared.handlePushNotification(userInfo: userInfo , fromAction : true)
        
        completionHandler()
    }
}
