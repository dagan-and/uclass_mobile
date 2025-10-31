import Foundation
import WebKit
import Combine
import UIKit
import PhotosUI

class WebViewManager: NSObject, ObservableObject, WKUIDelegate {
    @Published var isLoaded = false
    @Published var isLoading = false
    @Published var loadingProgress = 0
    @Published var currentURL: String = ""
    @Published var scriptMessage: String? = nil
    
    // 파일 선택 관련
    @Published var shouldShowFilePicker = false
    
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
        
        // ✅ 파일 업로드용 메시지 핸들러 등록 (한 번만)
        configuration.userContentController.add(self, name: "fileUpload")
        
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
            Logger.dev("🔑 JWT-TOKEN 헤더 추가: \(jwtToken)")
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
    
    // MARK: - File Upload Handling
    
    /// 파일 선택 결과 처리 (이미지만 허용) - base64로 변환하여 JavaScript에 전달
    func handleFileSelection(urls: [URL]?) {
        Logger.info("## 파일 선택 결과 처리: \(urls?.count ?? 0)개")
        
        guard let urls = urls, !urls.isEmpty else {
            // 취소된 경우
            Logger.info("## 파일 선택 취소됨")
            return
        }
        
        // 이미지 파일만 필터링
        let imageURLs = urls.filter { url in
            let pathExtension = url.pathExtension.lowercased()
            let imageExtensions = ["jpg", "jpeg", "png", "gif", "heic", "heif"]
            return imageExtensions.contains(pathExtension)
        }
        
        if imageURLs.isEmpty {
            Logger.warning("## 이미지 파일이 아님 - 업로드 취소")
            // 토스트 메시지 표시
            NotificationCenter.default.post(
                name: Notification.Name("ShowToast"),
                object: nil,
                userInfo: ["message": "이미지 파일만 업로드할 수 있습니다. (jpg, jpeg, png)"]
            )
            return
        }
        
        // 첫 번째 이미지만 처리 (단일 선택)
        guard let imageURL = imageURLs.first else { return }
        
        do {
            // 이미지 데이터 읽기
            let imageData = try Data(contentsOf: imageURL)
            
            // base64 인코딩
            let base64String = imageData.base64EncodedString()
            
            // MIME 타입 결정
            let mimeType: String
            switch imageURL.pathExtension.lowercased() {
            case "jpg", "jpeg":
                mimeType = "image/jpeg"
            case "png":
                mimeType = "image/png"
            case "gif":
                mimeType = "image/gif"
            case "heic", "heif":
                mimeType = "image/heic"
            default:
                mimeType = "image/jpeg"
            }
            
            let dataURL = "data:\(mimeType);base64,\(base64String)"
            let fileName = imageURL.lastPathComponent
            
            // JavaScript로 전달
            let jsCode = """
            if (window.handleFileSelected) {
                window.handleFileSelected('\(dataURL)', '\(fileName)', '\(mimeType)');
            }
            """
            
            DispatchQueue.main.async {
                self.webView?.evaluateJavaScript(jsCode) { result, error in
                    if let error = error {
                        Logger.error("JavaScript 실행 오류: \(error.localizedDescription)")
                    } else {
                        Logger.info("✅ 파일 업로드 완료: \(fileName)")
                    }
                }
            }
            
        } catch {
            Logger.error("## 이미지 읽기 실패: \(error.localizedDescription)")
        }
    }
    
    /// 파일 선택 취소 처리
    func cancelFileSelection() {
        Logger.info("## 파일 선택 명시적 취소")
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
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "fileUpload")
        Logger.dev("WebViewManager deinit")
    }
}

// MARK: - WKUIDelegate
extension WebViewManager {
    
    /// JavaScript Alert 처리
    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping () -> Void
    ) {
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
    
    /// 파일 업로드 JavaScript 코드 주입
    func injectFileUploadScript() {
        guard let webView = webView else { return }
        
        let script = """
        (function() {
            // 모든 file input을 감지
            function setupFileInputs() {
                const inputs = document.querySelectorAll('input[type="file"]');
                inputs.forEach(function(input) {
                    if (!input.dataset.nativeHandlerAdded) {
                        input.dataset.nativeHandlerAdded = 'true';
                        
                        input.addEventListener('click', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            
                            // Native로 파일 선택 요청
                            window.webkit.messageHandlers.fileUpload.postMessage({
                                action: 'openFilePicker',
                                inputId: input.id || 'file_input_' + Date.now()
                            });
                            
                            // input ID 저장
                            window._currentFileInput = input;
                        }, true);
                    }
                });
            }
            
            // 페이지 로드 시 실행
            setupFileInputs();
            
            // 동적으로 추가되는 input도 감지
            const observer = new MutationObserver(function(mutations) {
                setupFileInputs();
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // Native에서 호출할 함수 - 선택된 파일 처리
            window.handleFileSelected = function(base64Data, fileName, fileType) {
                const input = window._currentFileInput;
                if (!input) return;
                
                // base64를 Blob으로 변환
                const byteString = atob(base64Data.split(',')[1]);
                const mimeString = base64Data.split(',')[0].split(':')[1].split(';')[0];
                const ab = new ArrayBuffer(byteString.length);
                const ia = new Uint8Array(ab);
                for (let i = 0; i < byteString.length; i++) {
                    ia[i] = byteString.charCodeAt(i);
                }
                const blob = new Blob([ab], { type: mimeString });
                
                // File 객체 생성
                const file = new File([blob], fileName, { type: fileType });
                
                // DataTransfer를 사용하여 input에 파일 설정
                const dataTransfer = new DataTransfer();
                dataTransfer.items.add(file);
                input.files = dataTransfer.files;
                
                // change 이벤트 발생
                const event = new Event('change', { bubbles: true });
                input.dispatchEvent(event);
                
                console.log('✅ 파일 설정 완료:', fileName);
            };
            
            console.log('✅ 파일 업로드 스크립트 주입 완료');
        })();
        """
        
        // JavaScript 실행 (evaluateJavaScript 사용)
        webView.evaluateJavaScript(script) { result, error in
            if let error = error {
                Logger.error("파일 업로드 스크립트 주입 실패: \(error.localizedDescription)")
            } else {
                Logger.dev("✅ 파일 업로드 스크립트 주입 완료")
            }
        }
    }
}

// MARK: - WKScriptMessageHandler
extension WebViewManager: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        // fileUpload 메시지 처리
        if message.name == "fileUpload" {
            guard let body = message.body as? [String: Any],
                  let action = body["action"] as? String else {
                return
            }
            
            if action == "openFilePicker" {
                Logger.info("## 웹에서 파일 선택 요청")
                
                // SwiftUI에서 PHPicker 표시하도록 트리거
                DispatchQueue.main.async {
                    self.shouldShowFilePicker = true
                }
            }
        }
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
