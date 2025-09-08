import Foundation
import UIKit
import UserNotifications

class PushNotificationManager: ObservableObject {
    static let shared = PushNotificationManager()
    
    private init() {}
    
    // MARK: - 1. 푸시가 오면 이동할 화면값을 저장
    func handlePushNotification(userInfo: [AnyHashable: Any], fromAction : Bool = false) {
        Logger.dev("📱 푸시 알림 수신 fromAction:: \(fromAction)")

        guard let type = userInfo["type"] as? String else {
            Logger.dev("📱 type이 없음 - 처리 중단")
            return
        }
        
        // 채팅 뱃지 표시
        if type == "chat" {
            ChatBadgeViewModel.shared.showBadge()
        }
        
        if(fromAction) {
            // 이동할 화면 저장
            saveNavigationDestination(type, data: userInfo)
            
            // 3. MainScreen이 열려 있다면 바로 이동
            if isMainScreenActive() {
                Logger.dev("📱 MainScreen 활성 상태 - 바로 이동")
                executeNavigation()
            } else {
                Logger.dev("📱 MainScreen 비활성 - 화면 정보만 저장")
            }
        }
    }
    
    // MARK: - 화면 이동 정보 저장
    private func saveNavigationDestination(_ destination: String, data: [AnyHashable: Any]) {
        UserDefaults.standard.set(destination, forKey: "pendingNavigationDestination")
        
        // 추가 데이터도 저장 (필요한 경우)
        if let jsonData = try? JSONSerialization.data(withJSONObject: data),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            UserDefaults.standard.set(jsonString, forKey: "pendingNavigationData")
        }
        
        UserDefaults.standard.synchronize()
        Logger.dev("💾 이동 화면 저장: \(destination)")
    }
    
    // MARK: - MainScreen이 활성 상태인지 확인
    private func isMainScreenActive() -> Bool {
        // MainScreen이 현재 표시되고 있는지 확인
        return UserDefaults.standard.bool(forKey: "isMainScreenActive")
    }
    
    // MARK: - 2. MainScreen이 열릴 때 화면 이동 처리
    func handlePendingNavigationOnMainScreen() {
        guard let destination = UserDefaults.standard.string(forKey: "pendingNavigationDestination") else {
            Logger.dev("📱 대기 중인 화면 이동 없음")
            return
        }
        
        Logger.dev("🎯 MainScreen 열림 - 대기 중인 화면 이동 처리: \(destination)")
        
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
        self.navigateToScreen(destination, data: pushData)
    }
    
    // MARK: - 3. MainScreen이 열려 있을 때 바로 이동
    private func executeNavigation() {
        guard let destination = UserDefaults.standard.string(forKey: "pendingNavigationDestination") else {
            return
        }
        
        // 저장된 데이터 가져오기
        var pushData: [AnyHashable: Any] = [:]
        if let dataString = UserDefaults.standard.string(forKey: "pendingNavigationData"),
           let data = dataString.data(using: .utf8),
           let jsonData = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            pushData = jsonData
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
}
