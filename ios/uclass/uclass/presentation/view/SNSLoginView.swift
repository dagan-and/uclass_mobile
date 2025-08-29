import SwiftUI

struct SNSLoginView: View {
    @StateObject private var kakaoLoginManager = KakaoLoginManager()
    @StateObject private var appleLoginManager = AppleLoginManager()
    @StateObject private var naverLoginManager = NaverLoginManager()
    @EnvironmentObject var webViewManager: WebViewManager
    
    @State private var navigateToMain = false
    @State private var showLoadingView = false
    
    var body: some View {
        NavigationView {
            ZStack {
                if showLoadingView {
                    WebViewLoadingView()
                        .environmentObject(webViewManager)
                } else if navigateToMain {
                    MainTabView()
                        .environmentObject(webViewManager)
                } else {
                    loginContentView
                }
            }
            .navigationBarHidden(true)
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .onChange(of: kakaoLoginManager.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                startWebViewLoading()
            }
        }
        .onChange(of: appleLoginManager.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                startWebViewLoading()
            }
        }
        .onChange(of: naverLoginManager.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                startWebViewLoading()
            }
        }
        .onChange(of: webViewManager.isLoaded) { isLoaded in
            if isLoaded && showLoadingView {
                withAnimation(.easeInOut(duration: 0.3)) {
                    navigateToMain = true
                    showLoadingView = false
                }
            }
        }
    }
    
    private var loginContentView: some View {
        VStack(spacing: 0) {
            Spacer()
            
            // 로고 및 제목 영역
            VStack(spacing: 24) {
                // 로그인 아이콘
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.blue)
                
                VStack(spacing: 8) {
                    Text("간편 로그인")
                        .font(.title)
                        .foregroundColor(.black)
                        .fontWeight(.bold)
                    
                    Text("소셜 계정으로 간편하게 로그인하세요")
                        .font(.body)
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                }
            }
            
            Spacer()
            
            // 로그인 버튼들
            VStack(spacing: 16) {
                // 카카오 로그인 버튼
                LoginButton(
                    title: "카카오로 로그인",
                    backgroundColor: Color(red: 1.0, green: 0.9, blue: 0.0),
                    textColor: .black,
                    icon: "bubble.left.fill",
                    isLoading: kakaoLoginManager.isLoading
                ) {
                    kakaoLoginManager.startKakaoLogin()
                }
                
                // 네이버 로그인 버튼
                LoginButton(
                    title: "네이버로 로그인",
                    backgroundColor: Color(red: 0.0, green: 0.7, blue: 0.0),
                    textColor: .white,
                    icon: "n.circle.fill",
                    isLoading: naverLoginManager.isLoading
                ) {
                    naverLoginManager.startNaverLogin()
                }
                
                // Apple 로그인 버튼
                LoginButton(
                    title: "Apple로 로그인",
                    backgroundColor: .black,
                    textColor: .white,
                    icon: "applelogo",
                    isLoading: appleLoginManager.isLoading
                ) {
                    appleLoginManager.startAppleLogin()
                }
            }
            .padding(.horizontal, 32)
            
            Spacer()
                .frame(height: 60)
        }
        .padding()
        .background(Color.white)
        .alert("오류", isPresented: .constant(hasError)) {
            Button("확인", role: .cancel) {
                clearErrors()
            }
        } message: {
            Text(errorMessage)
        }
    }
    
    private var hasError: Bool {
        return kakaoLoginManager.errorMessage != nil ||
               appleLoginManager.errorMessage != nil ||
               naverLoginManager.errorMessage != nil
    }
    
    private var errorMessage: String {
        return kakaoLoginManager.errorMessage ??
               appleLoginManager.errorMessage ??
               naverLoginManager.errorMessage ??
               ""
    }
    
    private func clearErrors() {
        kakaoLoginManager.errorMessage = nil
        appleLoginManager.errorMessage = nil
        naverLoginManager.errorMessage = nil
    }
    
    private func startWebViewLoading() {
        withAnimation(.easeInOut(duration: 0.3)) {
            showLoadingView = true
        }
        
        // 웹뷰 로딩 시작 (네이버 URL로 설정)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            webViewManager.preloadWebView(url: "https://www.naver.com")
        }
    }
}

struct LoginButton: View {
    let title: String
    let backgroundColor: Color
    let textColor: Color
    let icon: String
    let isLoading: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: textColor))
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 20))
                }
                
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                
                Spacer()
            }
            .foregroundColor(textColor)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(backgroundColor)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )
        }
        .disabled(isLoading)
        .opacity(isLoading ? 0.8 : 1.0)
    }
}

struct WebViewLoadingView: View {
    @EnvironmentObject var webViewManager: WebViewManager
    
    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            
            VStack(spacing: 24) {
                // 로딩 애니메이션
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .blue))
                    .scaleEffect(1.5)
                
                VStack(spacing: 8) {
                    Text("잠시만 기다려주세요")
                        .font(.headline)
                        .fontWeight(.medium)
                    
                    Text("페이지를 준비중입니다...")
                        .font(.body)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}
