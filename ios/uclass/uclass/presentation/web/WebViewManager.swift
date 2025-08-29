import Foundation
import WebKit
import Combine

class WebViewManager: NSObject, ObservableObject {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var loadingProgress = 0
    @Published var currentURL: String = ""
    
    private var webView: WKWebView?
    private var progressObserver: NSKeyValueObservation?
    
    override init() {
        super.init()
        setupWebView()
    }
    
    private func setupWebView() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        
        webView = WKWebView(frame: .zero, configuration: configuration)
        webView?.navigationDelegate = self
        
        // 웹뷰 초기 배경색을 흰색으로 설정
        webView?.backgroundColor = UIColor.white
        webView?.scrollView.backgroundColor = UIColor.white
        webView?.isOpaque = false
        webView?.allowsBackForwardNavigationGestures = true
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
    
    func getWebView() -> WKWebView? {
        return webView
    }
    
    deinit {
        progressObserver?.invalidate()
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
            print("WebView failed to load: \(error.localizedDescription)")
        }
    }
}
