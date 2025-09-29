package com.ubase.uclass.presentation.view

import android.text.TextUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.network.SocketManager
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.network.response.BaseData
import com.ubase.uclass.network.response.EmptyData
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.network.response.SNSCheckData
import com.ubase.uclass.network.response.SNSLoginData
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.viewmodel.LogoutViewModel
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.presentation.web.WebViewScreen
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PreferenceManager
import kotlinx.coroutines.delay

// 안전한 캐스팅을 위한 확장 함수들
inline fun <reified T> Any?.asBaseData(): BaseData<T>? {
    return try {
        @Suppress("UNCHECKED_CAST")
        this as? BaseData<T>
    } catch (e: ClassCastException) {
        Logger.error("타입 캐스팅 실패: ${e.message}")
        null
    }
}

@Composable
fun MainScreen(
    onKakaoLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    onNaverLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    onGoogleLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    webViewManager: WebViewManager,
    autoLoginInfo: Pair<String, String>? = null,
    initialNavigationTarget: Int? = null
) {
    val context = LocalContext.current

    // LogoutViewModel 추가
    val logoutViewModel: LogoutViewModel = viewModel()

    // 상태 관리
    var isLoggedIn by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(false) }
    var loginSuccess by remember { mutableStateOf(false) }
    var isAPIInitialized by remember { mutableStateOf(false) }
    var isAutoLogin by remember { mutableStateOf(autoLoginInfo != null) }

    // API 오류 처리 함수
    fun handleAPIError(errorMessage: String) {
        Logger.error("API 오류 처리: $errorMessage")

        if (isAutoLogin) {
            Logger.info("## 자동 로그인 실패 - 수동 로그인으로 전환")
            isAutoLogin = false
            loginSuccess = false
            isWebViewLoading = false
        } else {
            // 수동 로그인 실패
            isWebViewLoading = false
            loginSuccess = false
        }
        CustomAlertManager.showAlert(content = errorMessage)
    }

    // NetworkAPI 콜백 등록
    DisposableEffect(Unit) {
        val callbackId = "MainApp_${System.currentTimeMillis()}"

        NetworkAPIManager.registerCallback(callbackId, object : NetworkAPIManager.NetworkCallback {
            override fun onResult(code: Int, result: Any?) {
                when (code) {
                    NetworkAPIManager.ResponseCode.API_AUTH_SNS_CHECK -> {
                        // BaseData<SNSCheckData>로 안전한 캐스팅
                        result.asBaseData<SNSCheckData>()?.let { response ->
                            if (response.isSuccess) {
                                Logger.dev("SNS 체크 성공: ${response.message}")

                                response.data?.let { checkData ->
                                    if (checkData.isExistingUser) {
                                        Logger.dev("기존 사용자 - 로그인 API 호출")
                                        val snsType = PreferenceManager.getSNSType(context)
                                        val userId = PreferenceManager.getSNSId(context)
                                        NetworkAPI.snsLogin(snsType, userId)
                                    } else {
                                        Logger.dev("신규 사용자 - 회원가입 API 호출")
                                        val snsType = PreferenceManager.getSNSType(context)
                                        val userId = PreferenceManager.getSNSId(context)
                                        var userName = PreferenceManager.getUserName(context)
                                        var userEmail = PreferenceManager.getUserEmail(context)

                                        if (TextUtils.isEmpty(userName)) {
                                            userName = "기본값"
                                        }
                                        if (TextUtils.isEmpty(userEmail)) {
                                            userEmail = "default@default.com"
                                        }

                                        NetworkAPI.snsRegister(snsType, userId, userName, userEmail)
                                    }
                                }
                            } else {
                                Logger.error("SNS 체크 실패: ${response.message}")
                                handleAPIError("SNS 체크 실패")
                            }
                        } ?: run {
                            Logger.error("SNS 체크 응답 타입 오류")
                            handleAPIError("응답 타입 오류")
                        }
                    }

                    NetworkAPIManager.ResponseCode.API_AUTH_SNS_LOGIN -> {
                        // BaseData<SNSLoginData>로 안전한 캐스팅
                        result.asBaseData<SNSLoginData>()?.let { response ->
                            if (response.isSuccess) {
                                Logger.dev("로그인 성공: ${response.message}")

                                response.data?.let { loginData ->
                                    // JWT 토큰 저장
                                    Constants.jwtToken = loginData.accessToken
                                    PreferenceManager.setUserId(context, loginData.userId)
                                    PreferenceManager.setBranchId(context , loginData.branchId)

                                    logoutViewModel.reset()

                                    Logger.dev("사용자 정보:")
                                    Logger.dev("- ID: ${loginData.userId}")
                                    Logger.dev("- 이름: ${loginData.userName}")
                                    Logger.dev("- 승인상태: ${loginData.approvalStatus}")
                                    Logger.dev("- 지점: ${loginData.branchName}")

                                    val content = """
                                            사용자: ${loginData.userName}(${loginData.userId})
                                            지점: ${loginData.branchName}(${loginData.branchId})
                                            승인 상태: ${loginData.approvalStatus}
                                            로그인 시간: ${loginData.loginAt}
                                            사용자 타입: ${loginData.userType}
                                        """.trimIndent()
                                    CustomAlertManager.showAlert(
                                        content = content
                                    )

                                    // API 초기화 완료 표시
                                    isAPIInitialized = true
                                }
                            } else {
                                Logger.error("로그인 실패: ${response.message}")
                                handleAPIError("로그인 실패")
                            }
                        } ?: run {
                            Logger.error("로그인 응답 타입 오류")
                            handleAPIError("응답 타입 오류")
                        }
                    }

                    NetworkAPIManager.ResponseCode.API_AUTH_SNS_REGISTER -> {
                        // BaseData<EmptyData>로 안전한 캐스팅
                        result.asBaseData<EmptyData>()?.let { response ->
                            if (response.isSuccess) {
                                Logger.dev("회원가입 성공: ${response.message}")

                                // 회원가입이 완료되면 로그인 API 호출
                                val snsType = PreferenceManager.getSNSType(context)
                                val userId = PreferenceManager.getSNSId(context)
                                NetworkAPI.snsLogin(snsType, userId)
                            } else {
                                Logger.error("회원가입 실패: ${response.message}")
                                handleAPIError("회원가입 실패")
                            }
                        } ?: run {
                            Logger.error("회원가입 응답 타입 오류")
                            handleAPIError("응답 타입 오류")
                        }
                    }

                    NetworkAPIManager.ResponseCode.API_ERROR -> {
                        if (result is ErrorData) {
                            if(result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_CHECK ||
                                result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_LOGIN ||
                                result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_REGISTER) {
                                Logger.error("API 오류: ${result.code}::${result.msg}")
                                handleAPIError("${result.msg}")
                            }
                        }
                    }
                }
            }
        })

        onDispose {
            NetworkAPIManager.unregisterCallback(callbackId)
        }
    }

    // 로그아웃 상태 감지
    LaunchedEffect(logoutViewModel.logout) {
        if (logoutViewModel.logout) {
            Logger.dev("로그아웃 감지 - 초기화 진행")
            PreferenceManager.clearLoginInfo(context = context)
            Constants.jwtToken = ""

            // 상태 초기화
            isLoggedIn = false
            isAPIInitialized = false
            loginSuccess = false
            isWebViewLoading = false
            isAutoLogin = false
        }
    }

    // 자동 로그인 초기 처리
    LaunchedEffect(autoLoginInfo) {
        if (autoLoginInfo != null) {
            Logger.info("## 자동 로그인 시작: ${autoLoginInfo.first}")
            loginSuccess = true
            isWebViewLoading = true
            webViewManager.preloadWebView()
        }
    }

    // 로그인 성공 후 공통 처리 함수
    val handleLoginSuccess = {
        Logger.info("## handleLoginSuccess 호출됨 - 수동 로그인")
        loginSuccess = true
        isWebViewLoading = true
        isAutoLogin = false // 수동 로그인이므로 자동 로그인 플래그 해제
        webViewManager.preloadWebView()
    }

    // 로그인 실패 후 공통 처리 함수
    val handleLoginFailure = {
        Logger.info("## handleLoginFailure 호출됨")
        isWebViewLoading = false
        loginSuccess = false
        isAutoLogin = false
    }

    // 상태 변화 모니터링 및 메인 화면 전환 로직
    LaunchedEffect(
        isAPIInitialized,
        loginSuccess,
        webViewManager.isWebViewLoaded.value,
        webViewManager.isWebViewLoading.value
    ) {
        if (isAPIInitialized && loginSuccess) {
            if (webViewManager.isWebViewLoaded.value) {
                Logger.info("## 모든 조건 만족 - 메인 화면으로 전환")
                isLoggedIn = true
                isWebViewLoading = false
            } else {
                // 웹뷰 로딩 대기 - 최대 5초
                var waitTime = 0
                while (waitTime < 5000 && !webViewManager.isWebViewLoaded.value) {
                    delay(500)
                    waitTime += 500
                }

                if (webViewManager.isWebViewLoaded.value) {
                    Logger.info("## 웹뷰 로딩 완료 - 메인 화면으로 전환")
                } else {
                    Logger.info("## 웹뷰 로딩 타임아웃 - 강제로 메인 화면 진행")
                }
                isLoggedIn = true
                isWebViewLoading = false
            }
        }
    }

    // UI 렌더링
    if (!isLoggedIn) {
        // 로그인 화면 표시
        SNSLoginScreen(
            onKakaoLogin = {
                onKakaoLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            onNaverLogin = {
                onNaverLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            onGoogleLogin = {
                onGoogleLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            isLoading = isWebViewLoading,
            isAutoLogin = isAutoLogin,
            autoLoginType = autoLoginInfo?.first
        )
    } else {
        // 메인 앱 화면
        Logger.info("## 메인 화면 렌더링")
        MainContent(
            webViewManager = webViewManager,
            initialNavigationTarget = initialNavigationTarget
        )
    }
}

@Composable
private fun MainContent(
    webViewManager: WebViewManager,
    initialNavigationTarget: Int? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    var previousTab by remember { mutableStateOf(0) } // 이전 탭 저장

    // FCM으로 인한 채팅 화면 이동 처리
    LaunchedEffect(Unit) {
        Logger.info("##initialNavigationTarget: $initialNavigationTarget 이동")
        if (initialNavigationTarget != null) {
            previousTab = selectedTab
            ViewCallbackManager.notifyResult(NAVIGATION, initialNavigationTarget)
        }
    }

    // 탭 변경 시 이전 탭 업데이트
    val onTabSelected: (Int) -> Unit = { newTab ->
        previousTab = selectedTab
        selectedTab = newTab
    }

    // 채팅 화면일 때는 전체 화면으로 표시
    if (selectedTab == 1) {
        ChatScreen(
            modifier = Modifier,
            onBack = {
                selectedTab = previousTab // 이전 탭으로 이동
                ViewCallbackManager.notifyResult(NAVIGATION, selectedTab)
            }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 메인 컨텐츠 영역
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> WebViewScreen(webViewManager = webViewManager)
                    2 -> NotificationScreen()
                }
            }

            // 하단 네비게이션 바
            MainBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }
    }
}