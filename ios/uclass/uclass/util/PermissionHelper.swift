import Foundation
import UIKit
import UserNotifications
import Photos

/**
 * 권한 관리를 위한 헬퍼 클래스
 */
class PermissionHelper {
    
    // MARK: - Constants
    private static let permissionRequestShownKey = "permission_request_shown"
    
    // MARK: - Permission Types
    enum PermissionType: CaseIterable {
        case notifications
        case photoLibrary
        
        var title: String {
            switch self {
            case .notifications:
                return "[선택] 알림"
            case .photoLibrary:
                return "[선택] 사진"
            }
        }
        
        var description: String {
            switch self {
            case .notifications:
                return "공지사항 및 채팅 알림을 수신"
            case .photoLibrary:
                return "프로필 이미지 등록시 사진 찾기"
            }
        }
        
        var icon: String {
            switch self {
            case .notifications:
                return "bell.fill"
            case .photoLibrary:
                return "photo.fill"
            }
        }
    }
    
    // MARK: - Permission Status Check
    
    /**
     * 모든 필수 권한이 승인되었는지 확인
     */
    static func checkAllPermissions(completion: @escaping (Bool) -> Void) {
        let group = DispatchGroup()
        var allGranted = true
        
        // 알림 권한 체크
        group.enter()
        checkNotificationPermission { granted in
            if !granted {
                allGranted = false
            }
            group.leave()
        }
        
        // 사진 권한 체크
        group.enter()
        checkPhotoLibraryPermission { granted in
            if !granted {
                allGranted = false
            }
            group.leave()
        }
        
        group.notify(queue: .main) {
            completion(allGranted)
        }
    }
    
    /**
     * 알림 권한 상태 확인
     */
    static func checkNotificationPermission(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                completion(settings.authorizationStatus == .authorized)
            }
        }
    }
    
    /**
     * 사진 라이브러리 권한 상태 확인
     */
    static func checkPhotoLibraryPermission(completion: @escaping (Bool) -> Void) {
        if #available(iOS 14, *) {
            let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            DispatchQueue.main.async {
                completion(status == .authorized || status == .limited)
            }
        } else {
            let status = PHPhotoLibrary.authorizationStatus()
            DispatchQueue.main.async {
                completion(status == .authorized)
            }
        }
    }
    
    // MARK: - Permission Request
    
    /**
     * 모든 권한 요청
     */
    static func requestAllPermissions(completion: @escaping (Bool) -> Void) {
        let group = DispatchGroup()
        var results: [Bool] = []
        
        // 알림 권한 요청
        group.enter()
        requestNotificationPermission { granted in
            results.append(granted)
            group.leave()
        }
        
        // 사진 권한 요청
        group.enter()
        requestPhotoLibraryPermission { granted in
            results.append(granted)
            group.leave()
        }
        
        group.notify(queue: .main) {
            // 선택적 권한이므로 거부되어도 진행 가능
            completion(true)
            Logger.info("권한 요청 완료 - 결과: \(results)")
        }
    }
    
    /**
     * 알림 권한 요청
     */
    static func requestNotificationPermission(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if let error = error {
                Logger.error("알림 권한 요청 실패: \(error.localizedDescription)")
            }
            DispatchQueue.main.async {
                Logger.info("알림 권한 요청 결과: \(granted)")
                completion(granted)
            }
        }
    }
    
    /**
     * 사진 라이브러리 권한 요청
     */
    static func requestPhotoLibraryPermission(completion: @escaping (Bool) -> Void) {
        if #available(iOS 14, *) {
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                DispatchQueue.main.async {
                    let granted = (status == .authorized || status == .limited)
                    Logger.info("사진 권한 요청 결과: \(granted) (status: \(status))")
                    completion(granted)
                }
            }
        } else {
            PHPhotoLibrary.requestAuthorization { status in
                DispatchQueue.main.async {
                    let granted = (status == .authorized)
                    Logger.info("사진 권한 요청 결과: \(granted) (status: \(status))")
                    completion(granted)
                }
            }
        }
    }
    
    // MARK: - Permission Request Screen Management
    
    /**
     * 권한 요청 화면을 보여줘야 하는지 확인
     * 앱을 처음 실행할 때만 보여줌
     */
    static func shouldShowPermissionRequest() -> Bool {
        return !UserDefaults.standard.bool(forKey: permissionRequestShownKey)
    }
    
    /**
     * 권한 요청 화면을 보여줬음을 기록
     */
    static func markPermissionRequestShown() {
        UserDefaults.standard.set(true, forKey: permissionRequestShownKey)
        Logger.info("권한 요청 화면 표시 완료로 기록됨")
    }
    
    /**
     * 권한 요청 기록을 초기화 (테스트용)
     */
    static func clearPermissionRequestHistory() {
        UserDefaults.standard.removeObject(forKey: permissionRequestShownKey)
        Logger.info("권한 요청 기록 초기화됨")
    }
    
    // MARK: - Settings Navigation
    
    /**
     * 앱 설정으로 이동
     */
    static func openAppSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            Logger.error("설정 URL을 생성할 수 없습니다")
            return
        }
        
        if UIApplication.shared.canOpenURL(settingsUrl) {
            UIApplication.shared.open(settingsUrl) { success in
                Logger.info("앱 설정 열기 결과: \(success)")
            }
        }
    }
    
    // MARK: - Utility Methods
    
    /**
     * 권한 상태 문자열 반환
     */
    static func getPermissionStatusString(for type: PermissionType, completion: @escaping (String) -> Void) {
        switch type {
        case .notifications:
            checkNotificationPermission { granted in
                completion(granted ? "승인됨" : "거부됨")
            }
        case .photoLibrary:
            checkPhotoLibraryPermission { granted in
                completion(granted ? "승인됨" : "거부됨")
            }
        }
    }
}
