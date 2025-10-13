import SwiftUI
import WebKit

struct RegisterWebViewScreen: View {
    @StateObject private var webViewManager = RegisterWebViewManager()
    @StateObject private var networkViewModel = NetworkViewModel(
        identifier: "RegisterWebViewScreen"
    )
    
    let onRegistrationComplete: () -> Void
    let onClose: () -> Void
    
    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 웹뷰 영역
                if webViewManager.isLoaded,
                   let webView = webViewManager.getWebView()
                {
                    RegisterWebViewRepresentable(webView: webView)
                } else if webViewManager.isLoading {
                    // 로딩 중
                    VStack {
                        Spacer()
                    }
                }
            }
        }
        .onAppear {
            webViewManager.preloadWebView(url: "https://www.ggac.or.kr/?p=62#url")
        }
        .onChange(of: webViewManager.registrationCompleted) { completed in
            if completed {
                Logger.dev("✅ 회원가입 완료 감지")
                onRegistrationComplete()
            }
        }
        .onReceive(webViewManager.$scriptMessage) { scriptMessage in
            // null, 공백 체크
            guard let message = scriptMessage,
                  !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            else {
                return
            }
            parseAndHandleScriptMessage(message)
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
                case "gologin" :
                    networkViewModel.callSNSLogin(
                        snsType: UserDefaultsManager.getSNSType(),
                        snsId: UserDefaultsManager.getSNSId(),
                        onSuccess: { result in

                            webViewManager.registrationCompleted = true
                        },
                        onError: { error in
                            CustomAlertManager.shared.showErrorAlert(
                                message: error
                            )
                        }
                    )
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
                    // 웹뷰 닫기
                    Logger.dev("웹뷰 닫기 요청")
                    
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
            // JavaScript 코드 실행
            let jsCode = callback.replacingOccurrences(of: "javascript:", with: "")
            webView.evaluateJavaScript(jsCode) { result, error in
                if let error = error {
                    Logger.error("JavaScript 실행 오류: \(error.localizedDescription)")
                } else {
                    Logger.dev("JavaScript 콜백 실행 완료")
                }
            }
        } else if let url = URL(string: callback) {
            // URL 로드
            let request = URLRequest(url: url)
            webView.load(request)
            Logger.dev("Callback URL 로드: \(callback)")
        } else {
            Logger.dev("⚠️ 유효하지 않은 callback: \(callback)")
        }
    }
}

// MARK: - RegisterWebViewManager
class RegisterWebViewManager: NSObject, ObservableObject {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var currentURL: String = ""
    @Published var registrationCompleted = false
    @Published var scriptMessage: String? = nil  // JavaScript 메시지
    
    private var webView: WKWebView?
    private var jsInterface: UclassJsInterface?
    
    override init() {
        super.init()
        setupWebView()
    }
    
    private func setupWebView() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        
        // ✅ JS 인터페이스 설정
        jsInterface = UclassJsInterface { [weak self] message in
            DispatchQueue.main.async {
                self?.scriptMessage = message
                // ✅ 값 전달 후 바로 초기화
                self?.scriptMessage = nil
            }
        }
        
        // ✅ Script Message Handler 등록
        configuration.userContentController.add(jsInterface!, name: "uclass")
        
        // ✅ JavaScript 인터페이스 코드 주입
        let userScript = WKUserScript(
            source: UclassJsInterface.getJavaScriptCode(),
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        )
        configuration.userContentController.addUserScript(userScript)
        
        webView = WKWebView(frame: .zero, configuration: configuration)
        webView?.navigationDelegate = self
        
        // 웹뷰 초기 배경색을 흰색으로 설정
        webView?.backgroundColor = UIColor.white
        webView?.scrollView.backgroundColor = UIColor.white
        webView?.isOpaque = false
        webView?.allowsBackForwardNavigationGestures = true
        
        if Constants.isDebug {
            if #available(iOS 16.4, *) {
                webView?.isInspectable = true
            }
        }
    }
    
    func preloadWebView(url: String) {
        guard let webView = webView,
              let URL = URL(string: url) else {
            Logger.error("웹뷰 URL 생성 실패: \(url)")
            return
        }
        
        DispatchQueue.main.async {
            self.isLoading = true
            self.isLoaded = false
            self.currentURL = url
            
            Logger.dev("회원가입 웹뷰 로딩 시작: \(url)")
            webView.load(URLRequest(url: URL))
        }
    }
    
    func getWebView() -> WKWebView? {
        return webView
    }
    
    deinit {
        // Script Message Handler 제거
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "uclass")
        Logger.dev("RegisterWebViewManager deinit")
    }
}

// MARK: - WKNavigationDelegate
extension RegisterWebViewManager: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        DispatchQueue.main.async {
            self.isLoading = true
            Logger.dev("회원가입 웹뷰 로딩 시작")
            CustomLoadingManager.shared.showLoading()
        }
    }
    
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        DispatchQueue.main.async {
            self.isLoading = false
            self.isLoaded = true
            CustomLoadingManager.shared.hideLoading()
        }
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        DispatchQueue.main.async {
            self.isLoading = false
            CustomLoadingManager.shared.hideLoading()
            Logger.error("회원가입 웹뷰 로딩 실패: \(error.localizedDescription)")
        }
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url?.absoluteString {
            Logger.dev("웹뷰 네비게이션: \(url)")
        }
        
        decisionHandler(.allow)
    }
}

// MARK: - RegisterWebViewRepresentable
struct RegisterWebViewRepresentable: UIViewRepresentable {
    let webView: WKWebView
    
    func makeUIView(context: Context) -> WKWebView {
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
        // 필요시 업데이트 로직
    }
}
