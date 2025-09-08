package com.ubase.uclass.presentation.view

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.presentation.web.WebViewScreen
import com.ubase.uclass.util.Logger
import kotlinx.coroutines.delay

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

    // 상태 관리
    var isLoggedIn by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(false) }
    var loginSuccess by remember { mutableStateOf(false) }
    var isAPIInitialized by remember { mutableStateOf(false) }
    var isAutoLogin by remember { mutableStateOf(autoLoginInfo != null) }

    // NetworkAPI 콜백 등록
    DisposableEffect(Unit) {
        val callbackId = "MainApp_${System.currentTimeMillis()}"

        NetworkAPIManager.registerCallback(callbackId, object : NetworkAPIManager.NetworkCallback {
            override fun onResult(code: Int, result: Any?) {
                when (code) {
                    NetworkAPIManager.ResponseCode.API_AUTH_INIT_STORE -> {
                        Logger.info("## authInitStore API 응답 성공")
                        isAPIInitialized = true
                    }
                    else -> {
                        Logger.info("## API 응답 오류: code=$code")
                        // API 오류 시 로그인 실패 처리
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
                    }
                }
            }
        })

        onDispose {
            NetworkAPIManager.unregisterCallback(callbackId)
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
    LaunchedEffect(isAPIInitialized, loginSuccess, webViewManager.isWebViewLoaded.value, webViewManager.isWebViewLoading.value) {
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

    // FCM으로 인한 채팅 화면 이동 처리
    LaunchedEffect(Unit) {
        Logger.info("##initialNavigationTarget: $initialNavigationTarget 이동")
        if (initialNavigationTarget != null) {
            ViewCallbackManager.notifyResult(NAVIGATION, initialNavigationTarget)
        }
    }

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
                1 -> ChatScreen()
                2 -> NotificationScreen()
            }
        }

        // 하단 네비게이션 바
        MainBottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
    }
}