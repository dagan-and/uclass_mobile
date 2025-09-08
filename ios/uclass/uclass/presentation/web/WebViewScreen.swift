import SwiftUI
import WebKit

struct WebViewScreen: View {
    @EnvironmentObject var webViewManager: WebViewManager

    var body: some View {
        VStack(spacing: 0) {
            if webViewManager.isLoaded,
                let webView = webViewManager.getWebView()
            {
                WebViewRepresentable(webView: webView)
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
                if action.caseInsensitiveCompare("showLoading") == .orderedSame{
                    CustomLoadingManager.shared.showLoading()
                } else if action.caseInsensitiveCompare("hideLoading") == .orderedSame{
                    CustomLoadingManager.shared.hideLoading()
                } else if action.caseInsensitiveCompare("showAlert") == .orderedSame{
                    if let alertMessage = json["message"] as? String {
                        CustomAlertManager.shared.showConfirmAlert(
                            message: alertMessage,
                            onConfirm: {},
                            onCancel: {}
                        )
                    }
                } else {
                    Logger.dev("⚠️ Unknown action: \(action)")
                }
            } else {
                Logger.dev("❌ Invalid JSON format or missing 'action' key")
            }
        } catch {
            Logger.dev("❌ JSON parsing error: \(error.localizedDescription)")
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
