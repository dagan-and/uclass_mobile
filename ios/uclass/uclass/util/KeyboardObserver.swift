import SwiftUI
import Combine

// MARK: - 키보드 높이 관찰자 (개선된 버전)
class KeyboardObserver: ObservableObject {
    @Published var keyboardHeight: CGFloat = 0
    @Published var isKeyboardVisible: Bool = false
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupKeyboardObservers()
    }
    
    private func setupKeyboardObservers() {
        let keyboardWillShow = NotificationCenter.default
            .publisher(for: UIResponder.keyboardWillShowNotification)
            .compactMap { notification -> CGFloat? in
                guard let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
                    return nil
                }
                let keyboardHeight = keyboardFrame.height
                // Safe Area 하단 인셋 제거
                let bottomSafeArea = UIApplication.shared.windows.first?.safeAreaInsets.bottom ?? 0
                return keyboardHeight - bottomSafeArea
            }
        
        let keyboardWillHide = NotificationCenter.default
            .publisher(for: UIResponder.keyboardWillHideNotification)
            .map { _ -> CGFloat in 0 }
        
        // 키보드 높이 업데이트
        Publishers.Merge(keyboardWillShow, keyboardWillHide)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] height in
                withAnimation(.easeOut(duration: 0.25)) {
                    self?.keyboardHeight = height
                    self?.isKeyboardVisible = height > 0
                }
            }
            .store(in: &cancellables)
    }
}
