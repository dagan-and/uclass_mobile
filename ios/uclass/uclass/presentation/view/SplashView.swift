import SwiftUI

struct SplashView: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @State private var showLoginView = false
    
    var body: some View {
        ZStack {
            if showLoginView {
                SNSLoginView()
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
        // 2초 후 로그인 화면으로 전환
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            withAnimation(.easeInOut(duration: 0.3)) {
                showLoginView = true
            }
        }
    }
}

