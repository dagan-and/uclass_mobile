import Foundation
import UIKit
import UserNotifications

class PushNotificationManager: ObservableObject {
    static let shared = PushNotificationManager()
    
    private init() {}
    
    // MARK: - 1. 푸시가 오면 이동할 화면값을 저장
    func handlePushNotification(userInfo: [AnyHashable: Any], fromAction: Bool = false) {
        Logger.dev("📱 푸시 알림 수신 fromAction:: \(fromAction)")

        guard let type = userInfo["type"] as? String else {
            Logger.dev("📱 type이 없음 - 처리 중단")
            return
        }
        
        // 채팅 뱃지 표시
        if type == "chat" {
            ChatBadgeViewModel.shared.showBadge()
        }
        
        if fromAction {
            // URL이 있는지 확인
            let hasUrl = userInfo["url"] as? String != nil
            
            // 이동할 화면 저장
            saveNavigationDestination(type, data: userInfo, hasUrl: hasUrl)
            
            // MainScreen이 열려 있다면 바로 이동
            if isMainScreenActive() {
                Logger.dev("📱 MainScreen 활성 상태 - 바로 이동")
                executeNavigation()
            } else {
                Logger.dev("📱 MainScreen 비활성 - 화면 정보만 저장")
            }
        }
    }
    
    // MARK: - 화면 이동 정보 저장
    private func saveNavigationDestination(_ destination: String, data: [AnyHashable: Any], hasUrl: Bool) {
        UserDefaults.standard.set(destination, forKey: "pendingNavigationDestination")
        UserDefaults.standard.set(hasUrl, forKey: "pendingNavigationHasUrl")
        
        // 추가 데이터도 저장 (필요한 경우)
        if let jsonData = try? JSONSerialization.data(withJSONObject: data),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            UserDefaults.standard.set(jsonString, forKey: "pendingNavigationData")
        }
        
        UserDefaults.standard.synchronize()
        Logger.dev("💾 이동 화면 저장: \(destination), hasUrl: \(hasUrl)")
    }
    
    // MARK: - MainScreen이 활성 상태인지 확인
    private func isMainScreenActive() -> Bool {
        return UserDefaults.standard.bool(forKey: "isMainScreenActive")
    }
    
    // MARK: - 2. MainScreen이 열릴 때 화면 이동 처리
    func handlePendingNavigationOnMainScreen() {
        guard let destination = UserDefaults.standard.string(forKey: "pendingNavigationDestination") else {
            Logger.dev("📱 대기 중인 화면 이동 없음")
            return
        }
        
        let hasUrl = UserDefaults.standard.bool(forKey: "pendingNavigationHasUrl")
        Logger.dev("🎯 MainScreen 열림 - 대기 중인 화면 이동 처리: \(destination), hasUrl: \(hasUrl)")
        
        // 저장된 데이터 가져오기
        var pushData: [AnyHashable: Any] = [:]
        if let dataString = UserDefaults.standard.string(forKey: "pendingNavigationData"),
           let data = dataString.data(using: .utf8),
           let jsonData = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            pushData = jsonData
        }
        
        // URL이 있으면 WebView 로딩 대기 - 일단 홈 탭으로 이동만 함
        if hasUrl {
            Logger.dev("🌐 URL이 있음 - 홈 탭으로 이동 후 WebView 로딩 완료 대기")
            // 홈 탭으로 이동
            NotificationCenter.default.post(
                name: Notification.Name("NavigateToHome"),
                object: nil
            )
            // WebView 로딩 완료를 기다림 (대기 정보는 삭제하지 않음)
            return
        }
        
        // 대기 정보 삭제
        clearPendingNavigation()
        
        // 화면 이동 실행
        self.navigateToScreen(destination, data: pushData)
    }
    
    // MARK: - 3. WebView 로딩 완료 후 처리
    func handlePendingNavigationAfterWebViewLoaded() {
        guard let destination = UserDefaults.standard.string(forKey: "pendingNavigationDestination") else {
            return
        }
        
        let hasUrl = UserDefaults.standard.bool(forKey: "pendingNavigationHasUrl")
        
        // URL이 있는 경우에만 처리
        if hasUrl {
            Logger.dev("🌐 WebView 로딩 완료 - URL로 이동")
            
            // 저장된 데이터 가져오기
            var pushData: [AnyHashable: Any] = [:]
            if let dataString = UserDefaults.standard.string(forKey: "pendingNavigationData"),
               let data = dataString.data(using: .utf8),
               let jsonData = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                pushData = jsonData
            }
            
            // 대기 정보 삭제
            clearPendingNavigation()
            
            // 화면 이동 실행
            DispatchQueue.main.async {
                self.navigateToScreen(destination, data: pushData)
            }
        }
    }
    
    // MARK: - 4. MainScreen이 열려 있을 때 바로 이동
    private func executeNavigation() {
        guard let destination = UserDefaults.standard.string(forKey: "pendingNavigationDestination") else {
            return
        }
        
        let hasUrl = UserDefaults.standard.bool(forKey: "pendingNavigationHasUrl")
        
        // 저장된 데이터 가져오기
        var pushData: [AnyHashable: Any] = [:]
        if let dataString = UserDefaults.standard.string(forKey: "pendingNavigationData"),
           let data = dataString.data(using: .utf8),
           let jsonData = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            pushData = jsonData
        }
        
        // URL이 있으면 바로 이동
        if hasUrl {
            Logger.dev("🌐 URL이 있음 - 바로 홈 탭으로 이동하고 URL 로드")
            
            // 대기 정보 삭제
            clearPendingNavigation()
            
            // 즉시 화면 이동
            DispatchQueue.main.async {
                self.navigateToScreen(destination, data: pushData)
            }
            return
        }
        
        // 대기 정보 삭제
        clearPendingNavigation()
        
        // 즉시 화면 이동
        DispatchQueue.main.async {
            self.navigateToScreen(destination, data: pushData)
        }
    }
    
    // MARK: - 실제 화면 이동 처리
    private func navigateToScreen(_ destination: String, data: [AnyHashable: Any]) {
        Logger.dev("🚀 화면 이동 실행: \(destination)")
        
        // URL이 있는 경우 WebView로 로드
        if let urlString = data["url"] as? String {
            Logger.dev("🌐 URL로 이동: \(urlString)")
            NotificationCenter.default.post(
                name: Notification.Name("NavigateToUrl"),
                object: urlString
            )
            return
        }
        
        // URL이 없는 경우 기존 로직
        switch destination {
        case "chat":
            NotificationCenter.default.post(
                name: Notification.Name("NavigateToChat"),
                object: data
            )
        case "profile":
            NotificationCenter.default.post(
                name: Notification.Name("NavigateToProfile"),
                object: data
            )
        case "notice":
            NotificationCenter.default.post(
                name: Notification.Name("NavigateToNotice"),
                object: data
            )
        default:
            Logger.dev("❌ 알 수 없는 화면: \(destination)")
        }
    }
    
    // MARK: - 대기 중인 화면 이동 정보 삭제
    private func clearPendingNavigation() {
        UserDefaults.standard.removeObject(forKey: "pendingNavigationDestination")
        UserDefaults.standard.removeObject(forKey: "pendingNavigationData")
        UserDefaults.standard.removeObject(forKey: "pendingNavigationHasUrl")
        UserDefaults.standard.synchronize()
        Logger.dev("🗑️ 대기 중인 화면 이동 정보 삭제")
    }
    
    // MARK: - MainScreen 활성 상태 설정
    func setMainScreenActive(_ isActive: Bool) {
        UserDefaults.standard.set(isActive, forKey: "isMainScreenActive")
        Logger.dev("📱 MainScreen 활성 상태: \(isActive)")
        
        // MainScreen이 활성화되면 대기 중인 네비게이션 처리
        if isActive {
            handlePendingNavigationOnMainScreen()
        }
    }
    
    // MARK: - 대기 중인 화면 이동이 있는지 확인
    func hasPendingNavigation() -> Bool {
        return UserDefaults.standard.string(forKey: "pendingNavigationDestination") != nil
    }
    
    // MARK: - 모든 대기 정보 초기화 (필요한 경우)
    func clearAllPendingNavigations() {
        clearPendingNavigation()
        Logger.dev("🗑️ 모든 대기 중인 화면 이동 정보 삭제")
    }
    
    func displayResetBadge() {
        if #available(iOS 16.0, *) {
            UNUserNotificationCenter.current().setBadgeCount(0)
        } else {
            UIApplication.shared.applicationIconBadgeNumber = 0
        }
    }
}
