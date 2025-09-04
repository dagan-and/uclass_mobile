import Foundation
import WebKit

class UclassJsInterface: NSObject, WKScriptMessageHandler {
    private let onMessage: (String) -> Void
    
    init(onMessage: @escaping (String) -> Void) {
        self.onMessage = onMessage
        super.init()
    }
    
    // WKScriptMessageHandler 프로토콜 구현
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "uclass" {
            if let messageBody = message.body as? String {
                Logger.dev("웹에서 받은 메시지: \(messageBody)")
                onMessage(messageBody)
            }
        }
    }
    
    // JavaScript 코드를 반환하는 메서드 (웹에서 window.uclass.postMessage 사용 가능)
    static func getJavaScriptCode() -> String {
        return """
        window.uclass = {
            postMessage: function(message) {
                window.webkit.messageHandlers.uclass.postMessage(message);
            }
        };
        """
    }
}
