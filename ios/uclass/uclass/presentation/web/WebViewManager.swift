import Foundation
import WebKit
import Combine
import UIKit

class WebViewManager: NSObject, ObservableObject, WKUIDelegate {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var loadingProgress = 0
    @Published var currentURL: String = ""
    @Published var scriptMessage: String? = nil
    
    private var webView: WKWebView?
    private var jsInterface: UclassJsInterface?
    
    // ✅ 키보드 상태 추적 (중복 호출 방지)
    private var isKeyboardVisible = false
    private var currentKeyboardHeight: CGFloat = 0
    
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
        
        // ✅ 키보드 대응을 위한 ScrollView 설정
        webView?.scrollView.keyboardDismissMode = .interactive
        webView?.scrollView.contentInsetAdjustmentBehavior = .never // ✅ 자동 조정 비활성화
        
        // ✅ 스크롤 바운스 제거
        webView?.scrollView.bounces = false
        webView?.scrollView.alwaysBounceVertical = false
        webView?.scrollView.alwaysBounceHorizontal = false
        
        // 웹뷰 초기 배경색을 흰색으로 설정
        webView?.backgroundColor = UIColor.white
        webView?.scrollView.backgroundColor = UIColor.white
        webView?.isOpaque = false
        webView?.allowsBackForwardNavigationGestures = false
        webView?.uiDelegate = self
        
        if Constants.isDebug {
            if #available(iOS 16.4, *) {
                webView?.isInspectable = true
            }
        }
    }
    
    // MARK: - Private Helper
    
    /// JWT 토큰이 포함된 URLRequest 생성
    private func createURLRequest(url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        
        // ✅ JWT 토큰이 있으면 헤더에 추가
        if let jwtToken = Constants.jwtToken, !jwtToken.isEmpty {
            request.setValue(jwtToken, forHTTPHeaderField: "JWT-TOKEN")
            Logger.dev("🔐 JWT-TOKEN 헤더 추가: \(jwtToken)")
        }
        
        return request
    }
    
    // MARK: - Public Methods
    
    func preloadWebView(url: String) {
        guard let webView = webView,
              let URL = URL(string: url) else {
            Logger.error("유효하지 않은 URL: \(url)")
            return
        }
        
        DispatchQueue.main.async {
            self.isLoading = true
            self.isLoaded = false
            self.currentURL = url
            
            // ✅ JWT 토큰이 포함된 URLRequest로 로드
            let request = self.createURLRequest(url: URL)
            webView.load(request)
        }
    }
    
    func reload() {
        DispatchQueue.main.async {
            self.webView?.reload()
        }
    }
    
    func loadUrl(_ url: String) {
        guard let webView = webView,
              let URL = URL(string: url) else {
            Logger.error("유효하지 않은 URL: \(url)")
            return
        }
        
        DispatchQueue.main.async {
            // ✅ JWT 토큰이 포함된 URLRequest로 로드
            let request = self.createURLRequest(url: URL)
            webView.load(request)
        }
    }
    
    func getWebView() -> WKWebView? {
        return webView
    }
    
    // MARK: - Keyboard Notifications
    
    /// 키보드 노티피케이션 등록
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
        
        Logger.dev("✅ 키보드 노티피케이션 등록 완료 (WebViewManager)")
    }
    
    /// 키보드 노티피케이션 해제
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
        
        Logger.dev("✅ 키보드 노티피케이션 해제 완료 (WebViewManager)")
    }
    
    /// 키보드가 나타날 때 처리
    @objc func keyboardWillShow(notification: NSNotification) {
        guard let webView = webView,
              let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else {
            return
        }
        
        let keyboardHeight = keyboardFrame.cgRectValue.height
        
        // ✅ 중복 호출 방지: 이미 같은 높이로 키보드가 표시 중이면 무시
        if isKeyboardVisible && currentKeyboardHeight == keyboardHeight {
            Logger.dev("⌨️ 키보드 이미 표시 중 - 중복 호출 무시")
            webView.scrollView.contentInset = .zero
            webView.scrollView.scrollIndicatorInsets = .zero
            return
        }
        
        Logger.dev("⌨️ 키보드 표시: 높이 = \(keyboardHeight)")
        
        // ✅ contentInset 조정 (음수로 설정)
        webView.scrollView.contentInset = UIEdgeInsets(
            top: 0,
            left: 0,
            bottom: -keyboardHeight,
            right: 0
        )
        
        // ✅ scrollIndicatorInsets도 함께 조정
        webView.scrollView.scrollIndicatorInsets = webView.scrollView.contentInset
        
        // ✅ 키보드 상태 업데이트
        isKeyboardVisible = true
        currentKeyboardHeight = keyboardHeight
    }
    
    /// 키보드가 사라질 때 처리
    @objc func keyboardWillHide(notification: NSNotification) {
        guard let webView = webView else {
            return
        }
        
        // ✅ 중복 호출 방지: 키보드가 이미 숨겨진 상태면 무시
        if !isKeyboardVisible {
            Logger.dev("⌨️ 키보드 이미 숨김 - 중복 호출 무시")
            return
        }
        
        Logger.dev("⌨️ 키보드 숨김")
        
        // ✅ contentInset 초기화
        webView.scrollView.contentInset = .zero
        webView.scrollView.scrollIndicatorInsets = .zero
        
        // ✅ 키보드 상태 업데이트
        isKeyboardVisible = false
        currentKeyboardHeight = 0
    }
    
    deinit {
        // 노티피케이션 제거
        unregisterKeyboardNotifications()
        
        // Script Message Handler 제거
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "uclass")
        Logger.dev("WebViewManager deinit")
    }
}

// MARK: - WKNavigationDelegate
extension WebViewManager: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        DispatchQueue.main.async {
            self.isLoading = true
        }
    }
    
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        DispatchQueue.main.async {
            self.isLoading = false
            self.isLoaded = true
            self.loadingProgress = 100
        }
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        DispatchQueue.main.async {
            self.isLoading = false
            Logger.dev("WebView failed to load: \(error.localizedDescription)")
        }
    }
    
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        DispatchQueue.main.async {
            let alertController = UIAlertController(
                title: message,
                message: nil,
                preferredStyle: .alert
            )
            
            alertController.addAction(UIAlertAction(
                title: "확인",
                style: .cancel,
                handler: { _ in
                    completionHandler()
                }
            ))
            
            // ✅ 최상위 ViewController 찾아서 present
            if let topVC = UIApplication.shared.topViewController() {
                topVC.present(alertController, animated: true, completion: nil)
            } else {
                Logger.error("topViewController를 찾을 수 없음")
                completionHandler()
            }
        }
    }
    
    func webView(_ webView: WKWebView,
                    decidePolicyFor navigationResponse: WKNavigationResponse,
                    decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
           if let httpResponse = navigationResponse.response as? HTTPURLResponse {
               Logger.error("✅✅HTTResponse.statusCode✅✅:: \(httpResponse.statusCode)")
               if httpResponse.statusCode == 403 {
                   decisionHandler(.cancel)
                   DispatchQueue.main.asyncAfter(deadline: .now()) {
                       NotificationCenter.default.post(
                           name: Notification.Name("RestartApp"),
                           object: nil
                       )
                   }
                   return
               }
           }
           decisionHandler(.allow)
       }
}
