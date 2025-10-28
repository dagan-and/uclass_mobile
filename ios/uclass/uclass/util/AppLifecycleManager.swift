import Foundation
import UIKit

/**
 * 앱 생명주기 및 재로그인 관리 클래스
 */
class AppLifecycleManager {
    static let shared = AppLifecycleManager()
    
    // 백그라운드 진입 시간 저장
    private var backgroundEnteredTime: Date?
    
    // 재로그인이 필요한 시간 (초 단위, 기본 10분)
    private let reloginThresholdSeconds: TimeInterval = 10 * 60
    
    // 푸시로 시작했는지 여부
    private var isLaunchedFromPush: Bool = false
    
    private init() {}
    
    // MARK: - Public Methods
    
    /**
     * 푸시로 앱이 시작되었음을 설정
     */
    func setLaunchedFromPush(_ value: Bool) {
        isLaunchedFromPush = value
        Logger.dev("📱 푸시로 시작 여부 설정: \(value)")
    }
    
    /**
     * 앱이 백그라운드로 진입할 때 호출
     */
    func didEnterBackground() {
        backgroundEnteredTime = Date()
        Logger.dev("🌙 백그라운드 진입 시간 저장: \(backgroundEnteredTime!)")
    }
    
    /**
     * 앱이 포그라운드로 복귀할 때 호출
     * - Returns: 재로그인이 필요한지 여부
     */
    func willEnterForeground() -> Bool {
        Logger.dev("☀️ 포그라운드 복귀")
        
        // 푸시로 시작한 경우 재로그인 체크 스킵
        if isLaunchedFromPush {
            Logger.dev("✅ 푸시로 시작했으므로 재로그인 체크 스킵")
            isLaunchedFromPush = false // 플래그 초기화
            return false
        }
        
        guard let backgroundTime = backgroundEnteredTime else {
            Logger.dev("⚠️ 백그라운드 진입 시간이 없음")
            return false
        }
        
        let elapsedTime = Date().timeIntervalSince(backgroundTime)
        Logger.dev("⏱️ 백그라운드 경과 시간: \(Int(elapsedTime))초")
        
        // 경과 시간이 임계값을 초과하면 재로그인 필요
        if elapsedTime >= reloginThresholdSeconds {
            Logger.dev("🔒 재로그인 필요 (경과 시간: \(Int(elapsedTime))초 >= \(Int(reloginThresholdSeconds))초)")
            backgroundEnteredTime = nil // 시간 초기화
            return true
        }
        
        Logger.dev("✅ 재로그인 불필요 (경과 시간: \(Int(elapsedTime))초)")
        return false
    }
    
    /**
     * 재로그인 처리 실행
     */
    func performRelogin() {
        Logger.dev("🔄 재로그인 처리 시작")
        
        DispatchQueue.main.async {
            // 재로그인 알림 전송
            NotificationCenter.default.post(
                name: Notification.Name("RestartApp"),
                object: nil
            )
            SocketManager.shared.disconnect()
        }
    }
    
    /**
     * 상태 초기화 (로그인 성공 시 호출)
     */
    func reset() {
        backgroundEnteredTime = nil
        isLaunchedFromPush = false
        Logger.dev("🔄 AppLifecycleManager 상태 초기화")
    }
}
