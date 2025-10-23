import SwiftUI
import WebKit

struct WebViewScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @Environment(\.dismiss) var dismiss

    var body: some View {
        VStack(spacing: 0) {
            if webViewManager.isLoaded,
                let webView = webViewManager.getWebView()
            {
                WebViewRepresentable(webView: webView)
            } else {
                // ✅ 로딩 중 표시
                VStack {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                        .scaleEffect(1.5)
                    
                    Text("페이지를 불러오는 중...")
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .padding(.top, 20)
                }
            }
        }
        .onAppear {
            // ✅ 키보드 노티피케이션 등록
            webViewManager.registerKeyboardNotifications()
        }
        .onDisappear {
            // ✅ 키보드 노티피케이션 해제
            webViewManager.unregisterKeyboardNotifications()
        }
        .onReceive(webViewManager.$scriptMessage) { scriptMessage in
            guard let message = scriptMessage,
                !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            else {
                return
            }
            parseAndHandleScriptMessage(message)
        }
        .onReceive(webViewManager.$isLoaded) { isLoaded in
            // WebView 로딩 완료 시 대기 중인 푸시 URL 처리
            if isLoaded {
                Logger.dev("✅ WebView 로딩 완료 - 대기 중인 푸시 URL 확인")
                
                // ✅ JWT 토큰 설정
                setJWTTokenToWebView()
                
                // 대기 중인 푸시 URL 처리
                PushNotificationManager.shared.handlePendingNavigationAfterWebViewLoaded()
            }
        }
    }
    
    /**
     * WebView에 JWT 토큰 설정
     */
    private func setJWTTokenToWebView() {
        guard let webView = webViewManager.getWebView() else {
            Logger.error("WebView를 가져올 수 없음 - JWT 토큰 설정 실패")
            return
        }
        
        // JWT 토큰이 있는 경우에만 설정
        if let jwtToken = Constants.jwtToken, !jwtToken.isEmpty {
            let jsCode = "setToken('\(jwtToken)')"
            
            webView.evaluateJavaScript(jsCode) { result, error in
                if let error = error {
                    Logger.error("JWT 토큰 설정 실패: \(error.localizedDescription)")
                } else {
                    Logger.dev("✅ JWT 토큰 설정 완료: \(jwtToken.prefix(20))...")
                }
            }
        } else {
            Logger.dev("⚠️ JWT 토큰이 없음 - 토큰 설정 스킵")
        }
    }

    private func parseAndHandleScriptMessage(_ message: String) {
        Logger.dev("📩 웹뷰에서 받은 메시지: \(message)")
        
        guard let data = message.data(using: .utf8) else {
            Logger.dev("❌ Failed to convert message to data")
            return
        }

        do {
            if let json = try JSONSerialization.jsonObject(
                with: data,
                options: []
            ) as? [String: Any],
                let action = json["action"] as? String
            {
                Logger.dev("📌 Action: \(action)")
                
                let actionLowercase = action.lowercased()
                
                switch actionLowercase {
                case "showloading":
                    CustomLoadingManager.shared.showLoading()
                    
                case "hideloading":
                    CustomLoadingManager.shared.hideLoading()
                    
                case "showalert":
                    let alertTitle = json["title"] as? String ?? ""
                    let alertMessage = json["message"] as? String ?? ""
                    let callback = json["callback"] as? String ?? ""
                    
                    CustomAlertManager.shared.showAlert(
                        title: alertTitle,
                        message: alertMessage,
                        completion: {
                            handleCallback(callback)
                        }
                    )
                    
                case "showconfirm":
                    let alertTitle = json["title"] as? String ?? ""
                    let alertMessage = json["message"] as? String ?? ""
                    let callback = json["callback"] as? String ?? ""
                    
                    CustomAlertManager.shared.showConfirmAlert(
                        title: alertTitle,
                        message: alertMessage,
                        onConfirm: {
                            handleCallback(callback)
                        },
                        onCancel: {
                            Logger.dev("사용자가 확인 다이얼로그를 취소함")
                        }
                    )
                    
                case "goclose":
                    Logger.dev("웹뷰 닫기 요청")
                    dismiss()
                    
                case "godm":
                    Logger.dev("💬 채팅 화면 이동 요청")
                    NotificationCenter.default.post(
                        name: Notification.Name("NavigateToChat"),
                        object: nil
                    )
                    
                default:
                    Logger.dev("⚠️ Unknown action: \(action)")
                }
            } else {
                Logger.dev("❌ Invalid JSON format or missing 'action' key")
            }
        } catch {
            Logger.error("❌ JSON parsing error: \(error.localizedDescription)")
        }
    }
    
    private func handleCallback(_ callback: String) {
        guard !callback.isEmpty,
              let webView = webViewManager.getWebView() else {
            return
        }
        
        // JavaScript 콜백 실행
        if callback.hasPrefix("javascript:") {
            let jsCode = callback.replacingOccurrences(of: "javascript:", with: "")
            webView.evaluateJavaScript(jsCode) { result, error in
                if let error = error {
                    Logger.error("JavaScript 실행 오류: \(error.localizedDescription)")
                } else {
                    Logger.dev("JavaScript 콜백 실행 완료")
                }
            }
        } else if let url = URL(string: callback) {
            let request = URLRequest(url: url)
            webView.load(request)
            Logger.dev("Callback URL 로드: \(callback)")
        } else {
            Logger.dev("⚠️ 유효하지 않은 callback: \(callback)")
        }
    }
}

struct WebViewRepresentable: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView {
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // 필요시 업데이트 로직
    }
}
