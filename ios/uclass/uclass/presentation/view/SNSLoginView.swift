import SwiftUI

struct SNSLoginView: View {
    var shouldStartAutoLogin: Bool = false  // ✅ 추가

    @StateObject private var kakaoLoginManager = KakaoLoginManager()
    @StateObject private var appleLoginManager = AppleLoginManager()
    @StateObject private var naverLoginManager = NaverLoginManager()
    @StateObject private var networkViewModel = NetworkViewModel(
        identifier: "SNSLogin"
    )
    @EnvironmentObject var webViewManager: WebViewManager

    @State private var navigateToMain = false
    @State private var showLoadingView = false
    @State private var animationColor = Color(red: 0.0, green: 0.48, blue: 1.0)

    private func apiError(error: String) {
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
                        apiSNSRegister()
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
                    
                    Logger.dev("로그인 성공")                    
                    
                    if let loginData = resultData.data {
                            let message = """
                            사용자: \(loginData.userName) (\(loginData.userId))
                            지점: \(loginData.branchName)(\(loginData.branchId))
                            승인 상태: \(loginData.approvalStatus)
                            로그인 시간: \(loginData.loginAt)
                            """
                            
                            CustomAlertManager.shared.showAlert(message: message)
                        }

                    networkViewModel.handleSuccess()
                } else {
                    apiError(error: "데이터 형식 Error")
                }
            },
            onError: { error in
                apiError(error: error)
            }
        )
    }

    private func apiSNSRegister() {

        networkViewModel.callSNSRegister(
            snsType: UserDefaultsManager.getSNSType(),
            snsId: UserDefaultsManager.getSNSId(),
            name: UserDefaultsManager.getUserName(),
            email: UserDefaultsManager.getUserEmail(),
            onSuccess: { result in

                apiSNSLogin()
            },
            onError: { error in
                apiError(error: error)
            }
        )
    }

    // 모든 로그인 상태 초기화
    private func resetAllLoginStates() {
        kakaoLoginManager.isLoggedIn = false
        kakaoLoginManager.userInfo = nil

        appleLoginManager.isLoggedIn = false
        appleLoginManager.userInfo = nil

        naverLoginManager.isLoggedIn = false
        naverLoginManager.userInfo = nil

        showLoadingView = false
    }

    var body: some View {
        NavigationView {
            ZStack {
                if navigateToMain {
                    MainScreen()
                        .environmentObject(webViewManager)
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
        .onChange(of: networkViewModel.isCompleted) { newValue in
            if newValue {
                startWebViewLoading()
            }
        }
        .onChange(of: webViewManager.isLoaded) { newValue in
            if newValue && showLoadingView {
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

                // 앱 아이콘 영역 (Android 스타일)
                Image("splash")  // 앱 아이콘 이미지
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .frame(width: UIScreen.main.bounds.width * 0.4)  // 화면 너비의 50%
                    .aspectRatio(1, contentMode: .fit)  // 정사각형 유지

                Spacer()

                // 로딩 중일 때 또는 로그인 버튼들
                if networkViewModel.isLoading || showLoadingView
                    || shouldStartAutoLogin
                {
                    // 로딩 상태 =
                    VStack(spacing: 0) {
                        ProgressView()
                            .progressViewStyle(
                                CircularProgressViewStyle(tint: animationColor)
                            )
                            .scaleEffect(1.2)
                            .frame(width: 32, height: 32)

                        Spacer()
                            .frame(height: 32)

                        Text("앱을 준비하고 있습니다...")
                            .font(.system(size: 16))
                            .foregroundColor(
                                Color(red: 0.4, green: 0.4, blue: 0.4)
                            )
                    }
                    .frame(height: 200)  // 로그인 버튼과 동일 높이
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
            .cornerRadius(28)  // 더 둥근 모서리
        }
        .disabled(isLoading)
        .opacity(isLoading ? 0.8 : 1.0)
    }
}
