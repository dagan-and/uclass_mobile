import Foundation
import WebKit
import Combine

class WebViewManager: NSObject, ObservableObject, WKUIDelegate {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var loadingProgress = 0
    @Published var currentURL: String = ""
    @Published var scriptMessage: String? = nil
    
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
        webView?.uiDelegate = self
        
        if Constants.isDebug {
            if #available(iOS 16.4, *) {
                webView?.isInspectable = true
            }
        }
    }
    
    func preloadWebView(url: String) {
        guard let webView = webView,
              let URL = URL(string: url) else { return }
        
        DispatchQueue.main.async {
            self.isLoading = true
            self.isLoaded = false
            self.currentURL = url
            webView.load(URLRequest(url: URL))
        }
    }
    
    func reload() {
        DispatchQueue.main.async {
            self.webView?.reload()
        }
    }
    
    func loadUrl(_ url: String) {
        guard let webView = webView,
              let URL = URL(string: url) else { return }
        
        DispatchQueue.main.async {
            webView.load(URLRequest(url: URL))
        }
    }
    
    func getWebView() -> WKWebView? {
        return webView
    }
    
    deinit {
        // Script Message Handler 제거
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "uclass")
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
}
