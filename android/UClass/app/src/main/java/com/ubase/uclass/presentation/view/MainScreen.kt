package com.ubase.uclass.presentation.view

import android.os.Handler
import android.os.Looper
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
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.network.response.BaseData
import com.ubase.uclass.network.response.EmptyData
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.network.response.SNSCheckData
import com.ubase.uclass.network.response.SNSLoginData
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.viewmodel.LogoutViewModel
import com.ubase.uclass.presentation.viewmodel.ReloadViewModel
import com.ubase.uclass.presentation.web.NotificationScreen
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
    mainWebViewManager: WebViewManager,
    notificationWebViewManager: WebViewManager,
    autoLoginInfo: Pair<String, String>? = null,
    initialNavigationTarget: Int? = null
) {
    val context = LocalContext.current

    // LogoutViewModel 추가
    val logoutViewModel: LogoutViewModel = viewModel()
    val reloadViewModel : ReloadViewModel = viewModel()

    // 상태 관리
    var isLoggedIn by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(false) }
    var loginSuccess by remember { mutableStateOf(false) }
    var isAPIInitialized by remember { mutableStateOf(false) }
    var isAutoLogin by remember { mutableStateOf(autoLoginInfo != null) }
    var showRegistrationWebView by remember { mutableStateOf(false) }
    var registrationUrl by remember { mutableStateOf<String?>(null) }

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
                                        Logger.dev("신규 사용자 - 회원가입 웹뷰 표시")
                                        Logger.dev("회원가입 URL: ${checkData.redirectUrl}")

                                        registrationUrl = checkData.redirectUrl
                                        isWebViewLoading = false
                                        showRegistrationWebView = true
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
                        result.asBaseData<SNSLoginData>()?.let { response ->
                            if (response.isSuccess) {
                                Logger.dev("로그인 성공: ${response.message}")

                                response.data?.let { loginData ->

                                    //로그인 했지만 토큰이 없으면 대기중 페이지로 이동
                                    if(TextUtils.isEmpty(loginData.accessToken)) {
                                        Handler(Looper.getMainLooper()).post({
                                            registrationUrl = loginData.redirectUrl
                                            isWebViewLoading = false
                                            showRegistrationWebView = true
                                        })
                                        return
                                    }


                                    // JWT 토큰 저장
                                    Constants.jwtToken = loginData.accessToken
                                    PreferenceManager.setUserId(context, loginData.userId)
                                    PreferenceManager.setBranchId(context, loginData.branchId)

                                    logoutViewModel.reset()
                                    reloadViewModel.reset()

                                    Logger.dev("사용자 정보:")
                                    Logger.dev("- ID: ${loginData.userId}")
                                    Logger.dev("- 이름: ${loginData.userName}")
                                    Logger.dev("- 승인상태: ${loginData.approvalStatus}")
                                    Logger.dev("- 지점: ${loginData.branchName}")
                                    Logger.dev("- redirectUrl: ${loginData.redirectUrl}")
                                    Logger.dev("- reasonUrl: ${loginData.reasonUrl}")

                                    // ✅ 로그인 성공 시 메인 WebView 로드
                                    loginSuccess = true

                                    Handler(Looper.getMainLooper()).post({
                                        Constants.homeURL = loginData.redirectUrl
                                        mainWebViewManager.preloadWebView(loginData.redirectUrl)
                                        notificationWebViewManager.preloadWebView(loginData.reasonUrl)
                                    })

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
                            if (result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_CHECK ||
                                result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_LOGIN ||
                                result.code == NetworkAPIManager.ResponseCode.API_AUTH_SNS_REGISTER
                            ) {
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
            showRegistrationWebView = false
            registrationUrl = null
        }
    }

    //재로그인 하기
    LaunchedEffect(reloadViewModel.reload) {
        if (reloadViewModel.reload) {
            Logger.dev("재시작 감지 - 로그인으로 이동")
            Constants.jwtToken = ""

            // 상태 초기화
            isLoggedIn = false
            isAPIInitialized = false
            loginSuccess = false
            isWebViewLoading = true
            isAutoLogin = true

            val snsType = PreferenceManager.getSNSType(context)
            val userId = PreferenceManager.getSNSId(context)

            if (snsType.isNotEmpty() && userId.isNotEmpty()) {
                Logger.dev("저장된 SNS 정보로 자동 재로그인: $snsType, $userId")
                NetworkAPI.snsCheck(snsType, userId)
            } else {
                // SNS 정보가 없으면 수동 로그인
                isAutoLogin = false
                isWebViewLoading = false
            }
        }
    }

    // 자동 로그인 초기 처리 (웹뷰 로드는 API 성공 후에만)
    LaunchedEffect(autoLoginInfo) {
        if (autoLoginInfo != null) {
            Logger.info("## 자동 로그인 시작: ${autoLoginInfo.first}")
            isWebViewLoading = true
            // 웹뷰 로드는 하지 않고 로딩 상태만 표시
        }
    }

    // 로그인 성공 후 공통 처리 함수
    val handleLoginSuccess = {
        Logger.info("## handleLoginSuccess 호출됨 - 수동 로그인")
        isWebViewLoading = true
        isAutoLogin = false
        // 웹뷰 로드는 API 성공 후에만 수행
    }

    // 로그인 실패 후 공통 처리 함수
    val handleLoginFailure = {
        Logger.info("## handleLoginFailure 호출됨")
        isWebViewLoading = false
        loginSuccess = false
        isAutoLogin = false
    }

    // 회원가입 완료 후 처리
    val handleRegistrationComplete = {
        Logger.dev("회원가입 완료 - 로그인 시도")
        showRegistrationWebView = false
        registrationUrl = null

        // 회원가입 완료 후 다시 SNS 체크 진행
        isWebViewLoading = true
        isAutoLogin = false

        // 웹뷰 로드는 로그인 API 성공 후에만

        val snsType = PreferenceManager.getSNSType(context)
        val userId = PreferenceManager.getSNSId(context)
        NetworkAPI.snsLogin(snsType, userId)
    }

    // 회원가입 취소 처리
    val handleCloseRegistration = {
        Logger.dev("회원가입 취소 - SNS 정보 초기화 및 로그인 화면 복귀")

        // SNS 정보 초기화
        PreferenceManager.clearLoginInfo(context = context)

        // 상태 초기화
        showRegistrationWebView = false
        registrationUrl = null
        isWebViewLoading = false
        loginSuccess = false
        isAutoLogin = false
        isLoggedIn = false
        isAPIInitialized = false
    }

    // 상태 변화 모니터링 및 메인 화면 전환 로직
    LaunchedEffect(
        isAPIInitialized,
        loginSuccess,
        mainWebViewManager.isWebViewLoaded.value,
        mainWebViewManager.isWebViewLoading.value
    ) {
        if (isAPIInitialized && loginSuccess) {
            if (mainWebViewManager.isWebViewLoaded.value) {
                Logger.info("## 모든 조건 만족 - 메인 화면으로 전환")
                isLoggedIn = true
                isWebViewLoading = false
            } else {
                // 웹뷰 로딩 대기 - 최대 5초
                var waitTime = 0
                while (waitTime < 5000 && !mainWebViewManager.isWebViewLoaded.value) {
                    delay(500)
                    waitTime += 500
                }

                if (mainWebViewManager.isWebViewLoaded.value) {
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
    when {
        // ✅ 회원가입 웹뷰 표시
        showRegistrationWebView && registrationUrl != null -> {
            Logger.info("## 회원가입 웹뷰 화면 렌더링: $registrationUrl")
            RegisterWebViewScreen(
                url = registrationUrl!!,
                onRegistrationComplete = handleRegistrationComplete,
                onClose = handleCloseRegistration
            )
        }

        // 로그인 화면
        !isLoggedIn -> {
            Logger.info("## 로그인 화면 렌더링")
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
        }

        // 메인 앱 화면
        else -> {
            Logger.info("## 메인 화면 렌더링")
            MainContent(
                mainWebViewManager = mainWebViewManager,
                notificationWebViewManager = notificationWebViewManager,
                initialNavigationTarget = initialNavigationTarget
            )
        }
    }
}

@Composable
private fun MainContent(
    mainWebViewManager: WebViewManager,
    notificationWebViewManager: WebViewManager,
    initialNavigationTarget: Int? = null,
    reasonUrl: String? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    var previousTab by remember { mutableStateOf(0) }

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
                selectedTab = previousTab
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
                    0 -> WebViewScreen(webViewManager = mainWebViewManager)
                    2 -> NotificationScreen(webViewManager = notificationWebViewManager)
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