import SwiftUI

struct SNSLoginView: View {
    var shouldStartAutoLogin: Bool = false

    @StateObject private var kakaoLoginManager = KakaoLoginManager()
    @StateObject private var appleLoginManager = AppleLoginManager()
    @StateObject private var naverLoginManager = NaverLoginManager()
    @StateObject private var networkViewModel = NetworkViewModel(
        identifier: "SNSLogin"
    )
    @EnvironmentObject var webViewManager: WebViewManager

    @State private var navigateToMain = false
    @State private var showLoadingView = false
    @State private var showRegistrationWebView = false
    @State private var registrationUrl: String = ""
    @State private var animationColor = Color(red: 0.0, green: 0.48, blue: 1.0)
    @State private var loadingMessage = "로그인 중..."

    private func apiError(error: String) {
        showLoadingView = false
        Logger.dev("인증 실패: \(error)")
        // 인증 실패 시 저장된 로그인 정보 초기화
        UserDefaultsManager.clearLoginInfo()
        // 현재 로그인 상태 초기화
        resetAllLoginStates()

        CustomAlertManager.shared.showErrorAlert(
            message: error
        )
    }

    private func apiSNSCheck() {
        
        showLoadingView = true
        
        Logger.dev("=== 계정확인 ===")
        Logger.dev("SNS Type: \(UserDefaultsManager.getSNSType())")
        Logger.dev("SNS ID: \(UserDefaultsManager.getSNSId())")
        Logger.dev("================")

        networkViewModel.callSNSCheck(
            snsType: UserDefaultsManager.getSNSType(),
            snsId: UserDefaultsManager.getSNSId(),
            onSuccess: { result in

                if let resultData = result as? BaseData<SNSCheckData> {
                    if resultData.data?.isExistingUser == true {
                        apiSNSLogin()
                    } else {
                        // redirectUrl을 사용하여 회원가입 진행
                        if let redirectUrl = resultData.data?.redirectUrl, !redirectUrl.isEmpty {
                            apiSNSRegister(url: redirectUrl)
                        } else {
                            apiError(error: "회원가입 URL을 찾을 수 없습니다")
                        }
                    }
                } else {
                    apiError(error: "데이터 형식 Error")
                }
            },
            onError: { error in
                apiError(error: error)
            }
        )
    }

    private func apiSNSLogin() {
 
        networkViewModel.callSNSLogin(
            snsType: UserDefaultsManager.getSNSType(),
            snsId: UserDefaultsManager.getSNSId(),
            onSuccess: { result in
                if let resultData = result as? BaseData<SNSLoginData> {
                    
                    Constants.jwtToken = resultData.data?.accessToken
                    Constants.setUserId(resultData.data?.userId ?? 0)
                    Constants.setBranchId(resultData.data?.branchId ?? 0)
                    Constants.setBranchName(resultData.data?.branchName ?? "")
                    
                    // ✅ redirectUrl과 reasonUrl 저장
                    if let redirectUrl = resultData.data?.redirectUrl {
                        Constants.mainUrl = redirectUrl
                        Logger.dev("메인 URL 저장: \(redirectUrl)")
                    }
                    
                    if let reasonUrl = resultData.data?.reasonUrl {
                        Constants.noticeUrl = reasonUrl
                        Logger.dev("공지사항 URL 저장: \(reasonUrl)")
                    }
                    
                    Logger.dev("로그인 성공")
                    
                    // ✅ 로그인 성공 후 WebView 미리 로딩 시작
                    preloadMainWebView()
                } else {
                    apiError(error: "데이터 형식 Error")
                }
            },
            onError: { error in
                apiError(error: error)
            }
        )
    }
    
    /// ✅ 메인 WebView 미리 로딩
    private func preloadMainWebView() {
        guard !Constants.mainUrl.isEmpty else {
            Logger.error("메인 URL이 설정되지 않음")
            apiError(error: "메인 URL을 찾을 수 없습니다")
            return
        }
        
        Logger.dev("🌐 메인 WebView 미리 로딩 시작: \(Constants.mainUrl)")
      
        // WebView 로딩 시작
        webViewManager.preloadWebView(url: Constants.mainUrl)
    }

    private func apiSNSRegister(url: String) {
        Logger.dev("신규 사용자 - 회원가입 웹뷰 표시")
        Logger.dev("회원가입 URL: \(url)")
        
        // 회원가입 URL 저장 및 웹뷰 표시
        DispatchQueue.main.async {
            registrationUrl = url
            showLoadingView = false
            showRegistrationWebView = true
        }
    }

    // 모든 로그인 상태 초기화
    private func resetAllLoginStates() {
        UserDefaultsManager.clearLoginInfo()
        
        kakaoLoginManager.isLoggedIn = false
        kakaoLoginManager.userInfo = nil

        appleLoginManager.isLoggedIn = false
        appleLoginManager.userInfo = nil

        naverLoginManager.isLoggedIn = false
        naverLoginManager.userInfo = nil

        showLoadingView = false
        registrationUrl = ""
        loadingMessage = "앱을 준비하고 있습니다..."
    }

    var body: some View {
        NavigationView {
            ZStack {
                if navigateToMain {
                    MainScreen()
                        .environmentObject(webViewManager)
                } else if showRegistrationWebView {
                    RegisterWebViewScreen(
                        registrationUrl: registrationUrl,
                        onRegistrationComplete: {
                            Logger.dev("회원가입 완료 - 자동 로그인 시도")
                            showRegistrationWebView = false
                            registrationUrl = ""
                            showLoadingView = true
                            apiSNSLogin()
                        },
                        onClose: {
                            Logger.dev("회원가입 취소 - 로그인 화면으로 이동")
                            Logger.dev("저장된 로그인 정보 삭제")
                            showRegistrationWebView = false
                            showLoadingView = false
                            registrationUrl = ""
                            resetAllLoginStates()
                        }
                    )
                } else {
                    loginContentView
                }
            }
            .navigationBarHidden(true)
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .onAppear {
            // 앱 시작 시 저장된 로그인 정보 확인
            checkSavedLoginInfo()
            
            // 색상 애니메이션 시작
            startColorAnimation()
        }
        .onChange(of: kakaoLoginManager.isLoggedIn) { newValue in
            if newValue {
                apiSNSCheck()
            }
        }
        .onChange(of: appleLoginManager.isLoggedIn) { newValue in
            if newValue {
                apiSNSCheck()
            }
        }
        .onChange(of: naverLoginManager.isLoggedIn) { newValue in
            if newValue {
                apiSNSCheck()
            }
        }
        .onChange(of: webViewManager.isLoaded) { isLoaded in
            // ✅ WebView 로딩 완료 시 MainScreen으로 이동
            if isLoaded && showLoadingView {
                Logger.dev("✅ WebView 로딩 완료 - MainScreen으로 이동")
                
                // 네트워크 작업 완료 처리
                networkViewModel.handleSuccess()
                
                // 메인 화면으로 이동
                withAnimation(.easeInOut(duration: 0.3)) {
                    navigateToMain = true
                    showLoadingView = false
                }
            }
        }
    }

    // 저장된 로그인 정보 확인
    private func checkSavedLoginInfo() {
        if UserDefaultsManager.isLoggedIn() {
            Logger.dev("저장된 로그인 정보 발견")
            UserDefaultsManager.printSavedLoginInfo()

            // 저장된 정보가 있으면 바로 인증 진행
            let savedType = UserDefaultsManager.getSNSType()
            let savedToken = UserDefaultsManager.getSNSToken()

            if !savedType.isEmpty && !savedToken.isEmpty {
                Logger.dev("자동 로그인 시도")
                apiSNSCheck()
            }
        }
    }

    // 색상 애니메이션 시작
    private func startColorAnimation() {
        withAnimation(
            Animation.easeInOut(duration: 1.0).repeatForever(autoreverses: true)
        ) {
            animationColor = Color(red: 0.0, green: 0.81, blue: 1.0)
        }
    }

    private var loginContentView: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // 앱 아이콘 영역
                Image("splash")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .frame(width: UIScreen.main.bounds.width * 0.4)
                    .aspectRatio(1, contentMode: .fit)

                Spacer()

                // 로딩 중일 때 또는 로그인 버튼들
                if networkViewModel.isLoading || showLoadingView
                    || shouldStartAutoLogin
                {
                    // 로딩 상태
                    VStack(spacing: 0) {
                        ProgressView()
                            .progressViewStyle(
                                CircularProgressViewStyle(tint: animationColor)
                            )
                            .scaleEffect(1.2)
                            .frame(width: 32, height: 32)

                        Spacer()
                            .frame(height: 32)

                        Text(loadingMessage)
                            .font(.system(size: 16))
                            .foregroundColor(
                                Color(red: 0.4, green: 0.4, blue: 0.4)
                            )
                    }
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 32)
                } else {
                    // 로그인 버튼들
                    VStack(spacing: 16) {
                        // 카카오 로그인 버튼
                        SNSLoginButton(
                            title: "카카오 로그인",
                            backgroundColor: Color(
                                red: 254.0 / 255.0,
                                green: 229.0 / 255.0,
                                blue: 0.0 / 255.0
                            ),
                            textColor: .black,
                            isLoading: kakaoLoginManager.isLoading
                        ) {
                            Logger.dev("카카오 로그인 버튼 클릭")
                            kakaoLoginManager.startKakaoLogin()
                        }

                        // 네이버 로그인 버튼
                        SNSLoginButton(
                            title: "네이버 로그인",
                            backgroundColor: Color(
                                red: 3.0 / 255.0,
                                green: 199.0 / 255.0,
                                blue: 90.0 / 255.0
                            ),
                            textColor: .white,
                            isLoading: naverLoginManager.isLoading
                        ) {
                            Logger.dev("네이버 로그인 버튼 클릭")
                            naverLoginManager.startNaverLogin()
                        }

                        // 애플 로그인 버튼
                        SNSLoginButton(
                            title: "애플 로그인",
                            backgroundColor: .black,
                            textColor: .white,
                            isLoading: appleLoginManager.isLoading
                        ) {
                            Logger.dev("애플 로그인 버튼 클릭")
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
                        .progressViewStyle(
                            CircularProgressViewStyle(tint: textColor)
                        )
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
            .cornerRadius(28)
        }
        .disabled(isLoading)
        .opacity(isLoading ? 0.8 : 1.0)
    }
}
