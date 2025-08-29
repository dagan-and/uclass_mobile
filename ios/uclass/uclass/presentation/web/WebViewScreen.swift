import SwiftUI
import WebKit

struct WebViewScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager
    
    var body: some View {
        VStack(spacing: 0) {
            if webViewManager.isLoaded, let webView = webViewManager.getWebView() {
                WebViewRepresentable(webView: webView)
            }
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
