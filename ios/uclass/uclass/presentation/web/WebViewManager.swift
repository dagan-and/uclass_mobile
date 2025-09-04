import Foundation
import WebKit
import Combine

class WebViewManager: NSObject, ObservableObject {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var loadingProgress = 0
    @Published var currentURL: String = ""
    @Published var scriptMessage: String? = nil
    
    private var webView: WKWebView?
    private var progressObserver: NSKeyValueObservation?
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
        
        // ✅ 로딩 프로그레스 관찰자 설정
        progressObserver = webView?.observe(\.estimatedProgress, options: [.new]) { [weak self] _, change in
            DispatchQueue.main.async {
                if let progress = change.newValue {
                    self?.loadingProgress = Int(progress * 100)
                }
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
        progressObserver?.invalidate()
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
}
