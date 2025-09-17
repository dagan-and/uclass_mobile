import SwiftUI

// MARK: - Alert Data Models
struct AlertData {
    let id = UUID()
    let title: String
    let message: String
    let type: AlertType
    let buttons: [AlertButton]
    
    enum AlertType {
        case info
        case error
        case confirm
        case web
    }
}

struct AlertButton {
    let title: String
    let style: AlertButtonStyle
    let action: (() -> Void)?
    
    enum AlertButtonStyle {
        case `default`
        case destructive
        case cancel
    }
}

// MARK: - Alert Manager (ObservableObject)
class CustomAlertManager: ObservableObject {
    static let shared = CustomAlertManager()
    private init() {}
    
    @Published var currentAlert: AlertData?
    @Published var isPresented: Bool = false
    
    // 기본 알림
    func showAlert(
        title: String = "알림",
        message: String,
        confirmTitle: String = "확인",
        completion: (() -> Void)? = nil
    ) {
        let button = AlertButton(
            title: confirmTitle,
            style: .default,
            action: completion
        )
        
        let alert = AlertData(
            title: title,
            message: message,
            type: .info,
            buttons: [button]
        )
        
        DispatchQueue.main.async {
            self.currentAlert = alert
            self.isPresented = true
        }
    }
    
    // 확인/취소 알림
    func showConfirmAlert(
        title: String = "알림",
        message: String,
        confirmTitle: String = "확인",
        cancelTitle: String = "취소",
        onConfirm: (() -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        let cancelButton = AlertButton(
            title: cancelTitle,
            style: .cancel,
            action: onCancel
        )
        
        let confirmButton = AlertButton(
            title: confirmTitle,
            style: .default,
            action: onConfirm
        )
        
        let alert = AlertData(
            title: title,
            message: message,
            type: .confirm,
            buttons: [cancelButton, confirmButton]
        )
        
        DispatchQueue.main.async {
            self.currentAlert = alert
            self.isPresented = true
        }
    }
    
    // 웹뷰 스타일 알림
    func showWebAlert(
        message: String,
        completion: (() -> Void)? = nil
    ) {
        let cancelButton = AlertButton(
            title: "취소",
            style: .cancel,
            action: nil
        )
        
        let confirmButton = AlertButton(
            title: "확인",
            style: .default,
            action: completion
        )
        
        let alert = AlertData(
            title: "알림",
            message: message,
            type: .web,
            buttons: [cancelButton, confirmButton]
        )
        
        DispatchQueue.main.async {
            self.currentAlert = alert
            self.isPresented = true
        }
    }
    
    // 에러 알림
    func showErrorAlert(
        title: String = "오류",
        message: String,
        completion: (() -> Void)? = nil
    ) {
        let button = AlertButton(
            title: "확인",
            style: .destructive,
            action: completion
        )
        
        let alert = AlertData(
            title: title,
            message: message,
            type: .error,
            buttons: [button]
        )
        
        DispatchQueue.main.async {
            self.currentAlert = alert
            self.isPresented = true
        }
    }
    
    // 다중 선택 알림
    func showMultipleChoiceAlert(
        title: String = "알림",
        message: String,
        choices: [(title: String, style: AlertButton.AlertButtonStyle, action: (() -> Void)?)],
        includeCancel: Bool = true
    ) {
        var buttons: [AlertButton] = []
        
        for choice in choices {
            buttons.append(AlertButton(
                title: choice.title,
                style: choice.style,
                action: choice.action
            ))
        }
        
        if includeCancel {
            buttons.append(AlertButton(
                title: "취소",
                style: .cancel,
                action: nil
            ))
        }
        
        let alert = AlertData(
            title: title,
            message: message,
            type: .info,
            buttons: buttons
        )
        
        DispatchQueue.main.async {
            self.currentAlert = alert
            self.isPresented = true
        }
    }
    
    func dismiss() {
        DispatchQueue.main.async {
            self.isPresented = false
            self.currentAlert = nil
        }
    }
}

// MARK: - Custom Alert View
struct CustomAlertView: View {
    let alertData: AlertData
    let onDismiss: () -> Void
    
    var body: some View {
        ZStack {
            // 반투명 배경
            Color.black.opacity(0.4)
                .ignoresSafeArea(.all)
                .onTapGesture {
                    onDismiss()
                }
            
            // 알림 컨테이너
            VStack(spacing: 0) {
                // 제목과 메시지
                VStack(spacing: 12) {
                    // 제목
                    Text(alertData.title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                    
                    // 메시지
                    Text(alertData.message)
                        .font(.system(size: 14, weight: .regular))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 20)
                
                // 구분선
                Divider()
                    .background(Color.gray.opacity(0.3))
                
                // 버튼 영역
                buttonArea
            }
            .background(Color.white)
            .cornerRadius(14)
            .frame(maxWidth: 270)
            .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 4)
        }
        .transition(.opacity.combined(with:.scale(scale: 0.8)))
        .animation(.easeInOut(duration: 0.2), value: true)
    }
    
    @ViewBuilder
    private var buttonArea: some View {
        if alertData.buttons.count == 1 {
            // 단일 버튼
            singleButton(alertData.buttons[0])
        } else if alertData.buttons.count == 2 {
            // 두 개 버튼 (가로 배치)
            HStack(spacing: 0) {
                alertButton(alertData.buttons[0])
                
                Divider()
                    .background(Color.gray.opacity(0.3))
                    .frame(width: 1)
                
                alertButton(alertData.buttons[1])
            }
            .frame(height: 44)
        } else {
            // 다중 버튼 (세로 배치)
            VStack(spacing: 0) {
                ForEach(Array(alertData.buttons.enumerated()), id: \.offset) { index, button in
                    if index > 0 {
                        Divider()
                            .background(Color.gray.opacity(0.3))
                    }
                    
                    alertButton(button)
                        .frame(height: 44)
                }
            }
        }
    }
    
    private func singleButton(_ button: AlertButton) -> some View {
         Button(action: {
             button.action?()
             onDismiss()
         }) {
             Text(button.title)
                 .font(.system(size: 17, weight: button.style == .cancel ? .regular : .medium))
                 .foregroundColor(colorForButtonStyle(button.style))
                 .frame(maxWidth: .infinity, minHeight: 44)
                 .contentShape(Rectangle())
         }
         .buttonStyle(PlainButtonStyle())
     }
    
    private func alertButton(_ button: AlertButton) -> some View {
         Button(action: {
             button.action?()
             onDismiss()
         }) {
             Text(button.title)
                 .font(.system(size: 17, weight: button.style == .cancel ? .regular : .medium))
                 .foregroundColor(colorForButtonStyle(button.style))
                 .frame(maxWidth: .infinity, maxHeight: .infinity)
                 .contentShape(Rectangle()) 
         }
         .buttonStyle(PlainButtonStyle())
     }
    
    private func colorForButtonStyle(_ style: AlertButton.AlertButtonStyle) -> Color {
        switch style {
        case .default:
            return Color(red: 0x4F/255.0, green: 0x63/255.0, blue: 0xD2/255.0) // #4F63D2
        case .destructive:
            return Color.red
        case .cancel:
            return Color.black
        }
    }
}

// MARK: - Alert Container View (앱의 최상위에 배치)
struct AlertContainer: View {
    @StateObject private var alertManager = CustomAlertManager.shared
    
    var body: some View {
        ZStack {
            if alertManager.isPresented, let alertData = alertManager.currentAlert {
                CustomAlertView(alertData: alertData) {
                    alertManager.dismiss()
                }
            }
        }
    }
}

// MARK: - SwiftUI Extensions
extension View {
    /// SwiftUI View에서 쉽게 Alert을 호출할 수 있는 extension
    func showCustomAlert(
        message: String,
        onConfirm: (() -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        if let onConfirm = onConfirm {
            CustomAlertManager.shared.showConfirmAlert(
                message: message,
                onConfirm: onConfirm,
                onCancel: onDismiss
            )
        } else {
            CustomAlertManager.shared.showAlert(
                message: message,
                completion: onDismiss
            )
        }
    }
    
    func showAlert(
        title: String = "알림",
        message: String,
        completion: (() -> Void)? = nil
    ) {
        CustomAlertManager.shared.showAlert(
            title: title,
            message: message,
            completion: completion
        )
    }
    
    func showErrorAlert(message: String, completion: (() -> Void)? = nil) {
        CustomAlertManager.shared.showErrorAlert(
            message: message,
            completion: completion
        )
    }
    
    func showConfirmAlert(
        title: String = "알림",
        message: String,
        onConfirm: (() -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    ) {
        CustomAlertManager.shared.showConfirmAlert(
            title: title,
            message: message,
            onConfirm: onConfirm,
            onCancel: onCancel
        )
    }
    
    func showWebAlert(message: String, completion: (() -> Void)? = nil) {
        CustomAlertManager.shared.showWebAlert(
            message: message,
            completion: completion
        )
    }
}

// MARK: - 사용 예시
//
//struct ContentView: View {
//    var body: some View {
//        NavigationView {
//            VStack(spacing: 20) {
//                Button("기본 알림") {
//                    CustomAlertManager.shared.showAlert(message: "저장이 완료되었습니다.")
//                }
//                
//                Button("확인/취소 알림") {
//                    CustomAlertManager.shared.showConfirmAlert(
//                        message: "정말 삭제하시겠습니까?",
//                        onConfirm: {
//                            Logger.dev("삭제 확인됨")
//                        },
//                        onCancel: {
//                            Logger.dev("삭제 취소됨")
//                        }
//                    )
//                }
//                
//                Button("에러 알림") {
//                    CustomAlertManager.shared.showErrorAlert(
//                        message: "네트워크 연결에 실패했습니다."
//                    )
//                }
//                
//                Button("웹뷰 스타일 알림") {
//                    CustomAlertManager.shared.showWebAlert(
//                        message: "웹뷰에서 alert() 안녕"
//                    )
//                }
//                
//                Button("다중 선택 알림") {
//                    CustomAlertManager.shared.showMultipleChoiceAlert(
//                        title: "옵션 선택",
//                        message: "어떤 작업을 수행하시겠습니까?",
//                        choices: [
//                            ("저장", .default, { Logger.dev("저장됨") }),
//                            ("삭제", .destructive, { Logger.dev("삭제됨") }),
//                            ("공유", .default, { Logger.dev("공유됨") })
//                        ]
//                    )
//                }
//            }
//            .navigationTitle("Custom Alert 예시")
//        }
//        .overlay(
//            // 앱 전체에서 알림을 표시하기 위한 오버레이
//            AlertContainer()
//        )
//    }
//}

// MARK: - 앱 진입점에서 사용하는 방법
/*
@main
struct MyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .overlay(AlertContainer()) // 여기에 AlertContainer 추가
        }
    }
}
*/
