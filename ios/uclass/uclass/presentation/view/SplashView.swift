import SwiftUI

struct SplashView: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @State private var showNextView = false
    @State private var shouldAutoLogin = false
    @State private var shouldShowPermissions = false
    
    var body: some View {
        ZStack {
            if showNextView {
                if shouldShowPermissions {
                    // 권한 요청 화면
                    PermissionView {
                        Logger.info("권한 요청 완료 - 로그인 화면으로 이동")
                        shouldShowPermissions = false
                    }
                } else {
                    // 로그인 화면
                    SNSLoginView(shouldStartAutoLogin: shouldAutoLogin)
                        .environmentObject(webViewManager)
                }
            } else {
                // 스플래시 화면
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
        // 권한 상태와 자동 로그인 상태 체크
        checkPermissionsAndAutoLogin()
        
        // 스플래시 화면 표시 시간
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            showNextView = true
        }
    }
    
    private func checkPermissionsAndAutoLogin() {
        // 자동 로그인 가능한지 체크
        if UserDefaultsManager.canAutoLogin() {
            Logger.dev("=== 자동 로그인 조건 충족 ===")
            UserDefaultsManager.printSavedLoginInfo()
            shouldAutoLogin = true
        } else {
            Logger.dev("자동 로그인 불가 - 수동 로그인 필요")
            shouldAutoLogin = false
        }
        
        // 권한 요청 화면을 보여줘야 하는지 체크
        shouldShowPermissions = PermissionHelper.shouldShowPermissionRequest()
        Logger.info("권한 요청 화면 표시 필요: \(shouldShowPermissions)")
    }
}
