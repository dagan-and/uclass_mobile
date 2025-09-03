import SwiftUI

struct SNSLoginView: View {
    @StateObject private var kakaoLoginManager = KakaoLoginManager()
    @StateObject private var appleLoginManager = AppleLoginManager()
    @StateObject private var naverLoginManager = NaverLoginManager()
    @StateObject private var networkViewModel = NetworkViewModel(identifier: "SNSLogin")
    @EnvironmentObject var webViewManager: WebViewManager
    
    @State private var navigateToMain = false
    @State private var showLoadingView = false
    
    private func startAuthentication() {
        networkViewModel.callAuthInitStore(
            onSuccess: { result in
                var messageText = "응답을 받았습니다."
                       
                       // result를 문자열로 변환
                       if let result = result {
                           if let jsonData = try? JSONSerialization.data(withJSONObject: result, options: .prettyPrinted),
                              let jsonString = String(data: jsonData, encoding: .utf8) {
                               messageText = jsonString
                           } else {
                               messageText = "\(result)"
                           }
                       }
                       
                       AlertHelper.shared.showAlert(
                           title: "API 성공",
                           message: messageText
                       ) {
                           Logger.dev("AuthInitStore success confirmed")
                       }
            },
            onError: { error in
                AlertHelper.shared.showErrorAlert(
                    title: "API ERROR",
                    message: error
                ) {
                    // 확인 버튼 클릭 시 실행할 코드
                    Logger.dev("Confirm")
                }
            }
        )
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                if navigateToMain {
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
                startAuthentication()
            }
        }
        .onChange(of: appleLoginManager.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                startAuthentication()
            }
        }
        .onChange(of: naverLoginManager.isLoggedIn) { isLoggedIn in
            if isLoggedIn {
                startAuthentication()
            }
        }
        .onChange(of: networkViewModel.isCompleted) { isCompleted in
            if isCompleted {
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
        ZStack {
            Color.white.ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer()
                    .frame(height: 100)
                
                // 앱 아이콘 영역
                VStack(spacing: 0) {
                    // 앱 아이콘 (UCLASS 로고)
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color(red: 0.2, green: 0.7, blue: 0.6))
                            .frame(width: 100, height: 100)
                        
                        VStack(spacing: 4) {
                            Image(systemName: "graduationcap.fill")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("UCLASS")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                    
                    Spacer()
                        .frame(height: 40)
                    
                    // 타이틀 텍스트
                    Text("간편 로그인")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                    
                    Spacer()
                        .frame(height: 12)
                    
                    Text("소셜 계정으로 간편하게 로그인하세요")
                        .font(.system(size: 16))
                        .foregroundColor(Color(red: 0.4, green: 0.4, blue: 0.4))
                }
                
                Spacer()
                
                // 로딩 중일 때 또는 로그인 버튼들
                if networkViewModel.isLoading || showLoadingView {
                    // 로딩 상태 (버튼 영역만 교체)
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.0, green: 0.48, blue: 1.0)))
                            .scaleEffect(1.0)
                        
                        Text("앱을 준비하고 있습니다...")
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.4, green: 0.4, blue: 0.4))
                    }
                    .padding(.horizontal, 32)
                } else {
                    // 로그인 버튼들
                    VStack(spacing: 16) {
                        // 카카오 로그인 버튼
                        SNSLoginButton(
                            title: "카카오 로그인",
                            backgroundColor: Color(red: 254.0/255.0, green: 229.0/255.0, blue: 0.0/255.0),
                            textColor: .black,
                            isLoading: kakaoLoginManager.isLoading
                        ) {
                            kakaoLoginManager.startKakaoLogin()
                        }
                        
                        // 네이버 로그인 버튼
                        SNSLoginButton(
                            title: "네이버 로그인",
                            backgroundColor: Color(red: 3.0/255.0, green: 199.0/255.0, blue: 90.0/255.0),
                            textColor: .white,
                            isLoading: naverLoginManager.isLoading
                        ) {
                            naverLoginManager.startNaverLogin()
                        }
                        
                        // 구글 로그인 버튼 (Apple 대신)
                        SNSLoginButton(
                            title: "구글 로그인",
                            backgroundColor: .black,
                            textColor: .white,
                            isLoading: appleLoginManager.isLoading
                        ) {
                            appleLoginManager.startAppleLogin()
                        }
                    }
                    .padding(.horizontal, 32)
                }
                
                Spacer()
                    .frame(height: 80)
            }
        }
    }
    
    
    private func startWebViewLoading() {
        showLoadingView = true
        
        // 웹뷰 로딩 시작
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            webViewManager.preloadWebView(url: "https://www.naver.com")
        }
    }
}

struct SNSLoginButton: View {
    let title: String
    let backgroundColor: Color
    let textColor: Color
    let isLoading: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Spacer()
                
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: textColor))
                        .scaleEffect(0.8)
                } else {
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(textColor)
                }
                
                Spacer()
            }
            .frame(height: 56)
            .background(backgroundColor)
            .cornerRadius(28) // 더 둥근 모서리
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
