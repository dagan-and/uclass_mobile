import SwiftUI
import WebKit

struct NoticeScreen: View {
    @StateObject private var noticeWebViewManager = WebViewManager()
    
    var body: some View {
        VStack(spacing: 0) {
            if noticeWebViewManager.isLoaded,
                let webView = noticeWebViewManager.getWebView()
            {
                NoticeWebViewRepresentable(webView: webView)
            } else {
                // ✅ 로딩 중 표시
                VStack {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                        .scaleEffect(1.5)
                    
                    Text("공지사항을 불러오는 중...")
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .padding(.top, 20)
                }
            }
        }
        .onAppear {
            // ✅ 화면이 나타날 때 공지사항 URL 로드
            if !Constants.noticeUrl.isEmpty {
                Logger.dev("공지사항 웹뷰 로드: \(Constants.noticeUrl)")
                noticeWebViewManager.preloadWebView(url: Constants.noticeUrl)
            } else {
                Logger.error("공지사항 URL이 설정되지 않음")
            }
        }
    }
}

struct NoticeWebViewRepresentable: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView {
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // 필요시 업데이트 로직
    }
}
