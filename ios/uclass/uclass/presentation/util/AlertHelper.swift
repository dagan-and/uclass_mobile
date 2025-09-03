import SwiftUI
import UIKit

/**
 * Android AlertDialog 스타일의 독립적인 Alert 헬퍼
 * 어느 곳에서나 호출 가능한 전역 Alert 시스템
 */
class AlertHelper {
    
    static let shared = AlertHelper()
    private init() {}
    
    /**
     * 간단한 확인 Alert 표시
     */
    func showAlert(title: String, message: String, buttonTitle: String = "확인", completion: (() -> Void)? = nil) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                return
            }
            
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            
            alert.addAction(UIAlertAction(title: buttonTitle, style: .default) { _ in
                completion?()
            })
            
            self.presentAlert(alert, from: rootViewController)
        }
    }
    
    /**
     * 확인/취소 Alert 표시
     */
    func showConfirmAlert(
        title: String,
        message: String,
        confirmTitle: String = "확인",
        cancelTitle: String = "취소",
        onConfirm: (() -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                return
            }
            
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            
            // 확인 버튼
            alert.addAction(UIAlertAction(title: confirmTitle, style: .default) { _ in
                onConfirm?()
            })
            
            // 취소 버튼
            alert.addAction(UIAlertAction(title: cancelTitle, style: .cancel) { _ in
                onCancel?()
            })
            
            self.presentAlert(alert, from: rootViewController)
        }
    }
    
    /**
     * 에러 Alert 표시 (빨간색 스타일)
     */
    func showErrorAlert(title: String = "오류", message: String, completion: (() -> Void)? = nil) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                return
            }
            
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            
            alert.addAction(UIAlertAction(title: "확인", style: .destructive) { _ in
                completion?()
            })
            
            self.presentAlert(alert, from: rootViewController)
        }
    }
    
    /**
     * 다중 선택 Alert 표시
     */
    func showMultipleChoiceAlert(
        title: String,
        message: String,
        choices: [(title: String, style: UIAlertAction.Style, action: (() -> Void)?)],
        includeCancel: Bool = true
    ) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                return
            }
            
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            
            // 선택지들 추가
            for choice in choices {
                alert.addAction(UIAlertAction(title: choice.title, style: choice.style) { _ in
                    choice.action?()
                })
            }
            
            // 취소 버튼 추가 (옵션)
            if includeCancel {
                alert.addAction(UIAlertAction(title: "취소", style: .cancel))
            }
            
            self.presentAlert(alert, from: rootViewController)
        }
    }
    
    // MARK: - Private Methods
    
    private func presentAlert(_ alert: UIAlertController, from viewController: UIViewController) {
        // 이미 다른 Alert이 표시 중인 경우 처리
        if let presentedViewController = viewController.presentedViewController {
            if let presentedAlert = presentedViewController as? UIAlertController {
                // 기존 Alert을 닫고 새 Alert 표시
                presentedAlert.dismiss(animated: false) {
                    viewController.present(alert, animated: true)
                }
            } else {
                // 다른 ViewController가 표시 중인 경우 그 위에 표시
                presentedViewController.present(alert, animated: true)
            }
        } else {
            viewController.present(alert, animated: true)
        }
    }
}

// MARK: - SwiftUI Extensions

extension View {
    /**
     * SwiftUI View에서 쉽게 Alert을 호출할 수 있는 extension
     */
    func showAlert(title: String, message: String, completion: (() -> Void)? = nil) {
        AlertHelper.shared.showAlert(title: title, message: message, completion: completion)
    }
    
    func showErrorAlert(message: String, completion: (() -> Void)? = nil) {
        AlertHelper.shared.showErrorAlert(message: message, completion: completion)
    }
    
    func showConfirmAlert(
        title: String,
        message: String,
        onConfirm: (() -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        AlertHelper.shared.showConfirmAlert(
            title: title,
            message: message,
            onConfirm: onConfirm,
            onCancel: onCancel
        )
    }
}
