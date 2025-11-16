import SwiftUI
import WebKit

struct RegisterWebViewScreen: View {
    let registrationUrl: String
    let onRegistrationComplete: () -> Void
    let onClose: () -> Void
    
    @StateObject private var webViewManager = RegisterWebViewManager()
    @StateObject private var networkViewModel = NetworkViewModel(
        identifier: "RegisterWebViewScreen"
    )
    
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
            Logger.dev("회원가입 웹뷰 로드: \(registrationUrl)")
            webViewManager.preloadWebView(url: registrationUrl)
            webViewManager.registerKeyboardNotifications()
        }
        .onDisappear {
            webViewManager.unregisterKeyboardNotifications()
        }
        .onChange(of: webViewManager.registrationCompleted) { completed in
            if completed {
                Logger.dev("✅ 회원가입 완료 감지")
                onRegistrationComplete()
            }
        }
        .onReceive(webViewManager.$scriptMessage) { scriptMessage in
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
                            // ✅ 키보드 내리기
                            if let webView = webViewManager.getWebView() {
                                webView.endEditing(true)
                                Logger.dev("⌨️ 키보드 내리기 완료")
                            }
                            
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
                    
                    // ✅ 키보드 내리기
                    if let webView = webViewManager.getWebView() {
                        webView.endEditing(true)
                        Logger.dev("⌨️ 키보드 내리기 완료")
                    }
                    
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
                    
                    // ✅ 키보드 내리기
                    if let webView = webViewManager.getWebView() {
                        webView.endEditing(true)
                        Logger.dev("⌨️ 키보드 내리기 완료")
                    }
                    
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
                    onClose()
                    
                case "gobrowser":
                    let urlString = json["title"] as? String ?? ""
                    if let url = URL(string: urlString) {
                        if UIApplication.shared.canOpenURL(url) {
                            UIApplication.shared.open(url, options: [:]) { success in
                                if success {
                                    print("URL이 성공적으로 열렸습니다")
                                } else {
                                    print("URL 열기 실패")
                                }
                            }
                        }
                    }
                    
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

// MARK: - String Extension for JavaScript Escaping
extension String {
    /// JavaScript 문자열로 안전하게 escape
    func escapedForJavaScript() -> String {
        return self
            .replacingOccurrences(of: "\\", with: "\\\\")  // \ -> \\
            .replacingOccurrences(of: "\"", with: "\\\"")  // " -> \"
            .replacingOccurrences(of: "\'", with: "\\'")   // ' -> \'
            .replacingOccurrences(of: "\n", with: "\\n")   // 개행
            .replacingOccurrences(of: "\r", with: "\\r")   // 캐리지 리턴
            .replacingOccurrences(of: "\t", with: "\\t")   // 탭
    }
}

// MARK: - RegisterWebViewManager
class RegisterWebViewManager: NSObject, ObservableObject, WKUIDelegate {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var currentURL: String = ""
    @Published var registrationCompleted = false
    @Published var scriptMessage: String? = nil
    
    private var webView: WKWebView?
    private var jsInterface: UclassJsInterface?
    
    // 키보드 상태 추적
    private var isKeyboardVisible = false
    private var currentKeyboardHeight: CGFloat = 0
    
    override init() {
        super.init()
        setupWebView()
    }
    
    private func setupWebView() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        
        // JS 인터페이스 설정
        jsInterface = UclassJsInterface { [weak self] message in
            DispatchQueue.main.async {
                self?.scriptMessage = message
                // 1초 후 메시지 초기화
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    self?.scriptMessage = nil
                }
            }
        }
        
        // Script Message Handler 등록
        configuration.userContentController.add(jsInterface!, name: "uclass")
        
        // JavaScript 인터페이스 코드 주입
        let userScript = WKUserScript(
            source: UclassJsInterface.getJavaScriptCode(),
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        )
        configuration.userContentController.addUserScript(userScript)
        
        webView = WKWebView(frame: .zero, configuration: configuration)
        webView?.navigationDelegate = self
        
        // ✅ UIDelegate 설정 추가 - JavaScript alert 처리를 위해 필수
        webView?.uiDelegate = self
        
        // 키보드 대응을 위한 ScrollView 설정
        webView?.scrollView.keyboardDismissMode = .interactive
        webView?.scrollView.contentInsetAdjustmentBehavior = .never
        
        // 스크롤 바운스 제거
        webView?.scrollView.bounces = false
        webView?.scrollView.alwaysBounceVertical = false
        
        Logger.dev("✅ RegisterWebView 초기화 완료")
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
    
    // MARK: - Native Binding
    
    /// 로그인 정보를 웹뷰로 전달
    private func sendNativeBinding() {
        guard let webView = webView else { return }
        
        // UserDefaultsManager에서 로그인 정보 JSON 가져오기
        guard let jsonString = UserDefaultsManager.getLoginInfoAsJson() else {
            Logger.error("로그인 정보 JSON 생성 실패")
            return
        }
        
        // JavaScript로 안전하게 escape
        let escapedJson = jsonString.escapedForJavaScript()
        let script = "javascript:nativeBinding('\(escapedJson)')"
        
        Logger.info("전송 전: \(jsonString)")
        Logger.info("전송 스크립트: \(script)")
        
        // JavaScript 실행
        webView.evaluateJavaScript(script) { result, error in
            if let error = error {
                Logger.error("JavaScript 실행 오류: \(error.localizedDescription)")
            } else {
                Logger.dev("JavaScript 실행 결과: \(String(describing: result))")
            }
        }
    }
    
    // MARK: - Keyboard Notifications
    
    func registerKeyboardNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillShow(notification:)),
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillHide(notification:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
        
        Logger.dev("✅ 키보드 노티피케이션 등록 완료")
    }
    
    func unregisterKeyboardNotifications() {
        NotificationCenter.default.removeObserver(
            self,
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        
        NotificationCenter.default.removeObserver(
            self,
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
        
        Logger.dev("✅ 키보드 노티피케이션 해제 완료")
    }
    
    @objc func keyboardWillShow(notification: NSNotification) {
        guard let webView = webView,
              let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else {
            return
        }
        
        let keyboardHeight = keyboardFrame.cgRectValue.height
        
        // 중복 호출 방지
        if isKeyboardVisible && currentKeyboardHeight == keyboardHeight {
            Logger.dev("⌨️ 키보드 이미 표시 중 - 중복 호출 무시")
            webView.scrollView.contentInset = .zero
            webView.scrollView.scrollIndicatorInsets = .zero
            return
        }
        
        Logger.dev("⌨️ 키보드 표시: 높이 = \(keyboardHeight)")
        
        // contentInset 조정
        webView.scrollView.contentInset = UIEdgeInsets(
            top: 0,
            left: 0,
            bottom: -keyboardHeight,
            right: 0
        )
        
        webView.scrollView.scrollIndicatorInsets = webView.scrollView.contentInset
        
        isKeyboardVisible = true
        currentKeyboardHeight = keyboardHeight
    }
    
    @objc func keyboardWillHide(notification: NSNotification) {
        guard let webView = webView else {
            return
        }
        
        // 중복 호출 방지
        if !isKeyboardVisible {
            Logger.dev("⌨️ 키보드 이미 숨김 - 중복 호출 무시")
            return
        }
        
        Logger.dev("⌨️ 키보드 숨김")
        
        webView.scrollView.contentInset = .zero
        webView.scrollView.scrollIndicatorInsets = .zero
        
        isKeyboardVisible = false
        currentKeyboardHeight = 0
    }
    
    deinit {
        unregisterKeyboardNotifications()
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
            
            Logger.info("## RegisterWebView onPageFinished: \(webView.url?.absoluteString ?? "")")
            
            // 🔥 nativeBinding 호출 - 로그인 정보를 웹뷰로 전달
            self.sendNativeBinding()
        }
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        DispatchQueue.main.async {
            self.isLoading = false
            self.isLoaded = true  // 에러가 발생해도 로딩 완료로 처리
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

// MARK: - WKUIDelegate Extension
extension RegisterWebViewManager {
    /// JavaScript alert() 처리
    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping () -> Void
    ) {
        Logger.dev("🔔 JavaScript alert 호출: \(message)")
        
        // ✅ completionHandler가 반드시 호출되도록 보장
        var handlerCalled = false
        let safeCompletionHandler = {
            guard !handlerCalled else {
                Logger.warning("⚠️ completionHandler 중복 호출 방지")
                return
            }
            handlerCalled = true
            completionHandler()
        }
        
        DispatchQueue.main.async {
            
            CustomAlertManager.shared.showAlert(
                message: message,
                completion: {
                    Logger.dev("✅ Alert 완료 - completionHandler 호출")
                    safeCompletionHandler()
                }
            )
            
            // ✅ 안전장치: 5초 후에도 completionHandler가 호출되지 않으면 강제 호출
            DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                if !handlerCalled {
                    Logger.warning("⚠️ Alert completionHandler 타임아웃 - 강제 호출")
                    safeCompletionHandler()
                }
            }
        }
    }
    
    /// JavaScript confirm() 처리
    func webView(
        _ webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping (Bool) -> Void
    ) {
        Logger.dev("🔔 JavaScript confirm 호출: \(message)")
        
        // ✅ completionHandler가 반드시 호출되도록 보장
        var handlerCalled = false
        let safeCompletionHandler: (Bool) -> Void = { result in
            guard !handlerCalled else {
                Logger.warning("⚠️ completionHandler 중복 호출 방지")
                return
            }
            handlerCalled = true
            completionHandler(result)
        }
        
        DispatchQueue.main.async {
            
            CustomAlertManager.shared.showConfirmAlert(
                message: message,
                onConfirm: {
                    Logger.dev("✅ Confirm 확인 - completionHandler 호출")
                    safeCompletionHandler(true)
                },
                onCancel: {
                    Logger.dev("✅ Confirm 취소 - completionHandler 호출")
                    safeCompletionHandler(false)
                }
            )
            
            // ✅ 안전장치: 5초 후에도 completionHandler가 호출되지 않으면 강제 호출
            DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                if !handlerCalled {
                    Logger.warning("⚠️ Confirm completionHandler 타임아웃 - 강제 호출 (취소로 처리)")
                    safeCompletionHandler(false)
                }
            }
        }
    }
    
    /// JavaScript prompt() 처리
    func webView(
        _ webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt prompt: String,
        defaultText: String?,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping (String?) -> Void
    ) {
        Logger.dev("🔔 JavaScript prompt 호출: \(prompt)")
        
        // ✅ completionHandler가 반드시 호출되도록 보장
        var handlerCalled = false
        let safeCompletionHandler: (String?) -> Void = { result in
            guard !handlerCalled else {
                Logger.warning("⚠️ completionHandler 중복 호출 방지")
                return
            }
            handlerCalled = true
            completionHandler(result)
        }
        
        DispatchQueue.main.async {
            
            // UIAlertController로 prompt 구현
            let alertController = UIAlertController(
                title: prompt,
                message: nil,
                preferredStyle: .alert
            )
            
            alertController.addTextField { textField in
                textField.text = defaultText
            }
            
            alertController.addAction(UIAlertAction(
                title: "확인",
                style: .default,
                handler: { _ in
                    let text = alertController.textFields?.first?.text
                    Logger.dev("✅ Prompt 확인 - completionHandler 호출: \(text ?? "nil")")
                    safeCompletionHandler(text)
                }
            ))
            
            alertController.addAction(UIAlertAction(
                title: "취소",
                style: .cancel,
                handler: { _ in
                    Logger.dev("✅ Prompt 취소 - completionHandler 호출")
                    safeCompletionHandler(nil)
                }
            ))
            
            // ✅ 최상위 ViewController 찾아서 present
            if let topVC = UIApplication.shared.topViewController() {
                topVC.present(alertController, animated: true, completion: nil)
            } else {
                Logger.error("topViewController를 찾을 수 없음")
                safeCompletionHandler(nil)
            }
            
            // ✅ 안전장치: 5초 후에도 completionHandler가 호출되지 않으면 강제 호출
            DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                if !handlerCalled {
                    Logger.warning("⚠️ Prompt completionHandler 타임아웃 - 강제 호출 (취소로 처리)")
                    safeCompletionHandler(nil)
                }
            }
        }
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
