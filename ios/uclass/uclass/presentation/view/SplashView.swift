import SwiftUI

struct SplashView: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @State private var showLoginView = false
    @State private var shouldAutoLogin = false
    
    var body: some View {
        ZStack {
            if showLoginView {
                SNSLoginView(shouldStartAutoLogin: shouldAutoLogin)
                    .environmentObject(webViewManager)
            } else {
                Color.white.ignoresSafeArea()
                Image("splash")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 200, height: 200)
            }
        }
        .onAppear {
            startSplashAnimation()
        }
    }
    
    private func startSplashAnimation() {
        // 자동 로그인 가능한지 체크
        checkAutoLogin()
        
        // 스플래시 화면 표시 시간
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            showLoginView = true
        }
    }
    
    private func checkAutoLogin() {
        // 저장된 로그인 정보가 있고 자동 로그인이 가능한지 확인
        if UserDefaultsManager.canAutoLogin() {
            print("=== 자동 로그인 조건 충족 ===")
            UserDefaultsManager.printSavedLoginInfo()
            shouldAutoLogin = true
        } else {
            print("자동 로그인 불가 - 수동 로그인 필요")
            shouldAutoLogin = false
        }
    }
}
