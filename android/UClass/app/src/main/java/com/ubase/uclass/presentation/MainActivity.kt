package com.ubase.uclass.presentation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.network.SocketManager
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.CHAT
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.CHAT_BADGE
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.presentation.ui.CustomAlert
import com.ubase.uclass.presentation.ui.CustomLoading
import com.ubase.uclass.presentation.view.MainScreen
import com.ubase.uclass.presentation.view.PermissionScreen
import com.ubase.uclass.presentation.viewmodel.ChatBadgeViewModel
import com.ubase.uclass.presentation.viewmodel.LogoutViewModel
import com.ubase.uclass.presentation.viewmodel.NavigationViewModel
import com.ubase.uclass.presentation.viewmodel.ReloadViewModel
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.BadgeManager
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PermissionHelper
import com.ubase.uclass.util.PreferenceManager

class MainActivity : ComponentActivity() {

    // 메인 WebViewManager
    private lateinit var mainWebViewManager: WebViewManager

    // 공지사항 WebViewManager
    private lateinit var notificationWebViewManager: WebViewManager

    // 로그인 성공/실패 상태를 관리하기 위한 콜백
    private var loginSuccessCallback: (() -> Unit)? = null
    private var loginFailureCallback: (() -> Unit)? = null

    // 자동 로그인 상태 관리
    private var isAutoLoginAttempted = false

    // FCM에서 전달받은 초기 네비게이션 타겟 (MainContent에서 사용)
    var initialNavigationTarget: Int? = null
        private set

    // FCM에서 전달받은 URL (WebView 로딩 완료 후 이동)
    private var pendingFCMUrl: String? = null

    // 백그라운드 전환 시간 관리
    private var backgroundTimestamp: Long = 0L
    private val SESSION_TIMEOUT_MS = 10 * 60 * 1000L // 10분 (밀리초)

    // 푸시로 시작되었는지 플래그
    private var isStartedFromPush = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //폰의 경우 세로 고정
        if (!AppUtil.isTabletDevice(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        AppUtil.setWhiteStatusBareMode(this)

        // NetworkAPI 초기화 (앱에서 한 번만 실행)
        if (!NetworkAPI.isInitialized()) {
            NetworkAPI.initialize()
        }

        val splashScreen = installSplashScreen()

        // 자동 로그인 체크
        val autoLoginInfo = AppUtil.tryAutoLogin(this)
        val shouldKeepSplash = autoLoginInfo != null

        // 권한 상태를 미리 체크
        val initialPermissionState = PermissionHelper.checkPermissions(this)
        val shouldShowPermissionRequest = PermissionHelper.shouldShowPermissionRequest(this)
        Logger.info("## 초기 권한 상태: $initialPermissionState, 권한 요청 표시 필요: $shouldShowPermissionRequest")

        splashScreen.setKeepOnScreenCondition {
            // 자동 로그인 가능한 경우 스플래시 유지
            (shouldKeepSplash && !isAutoLoginAttempted)
        }

        // WebViewManager 초기화
        mainWebViewManager = WebViewManager(this)
        notificationWebViewManager = WebViewManager(this)

        // WebView 로딩 완료 감지 및 FCM URL 이동 처리
        setupWebViewLoadingObserver()

        enableEdgeToEdge()
        setContent {
            val chatBadgeViewModel : ChatBadgeViewModel = viewModel()
            val navigationViewModel: NavigationViewModel = viewModel()
            val logoutViewModel: LogoutViewModel = viewModel()
            val reloadViewModel : ReloadViewModel = viewModel()

            // 권한 화면 표시 여부를 관리하는 State (초기값을 미리 체크한 값으로 설정)
            var shouldShowPermissions by remember { mutableStateOf(shouldShowPermissionRequest) }

            Box(Modifier.safeDrawingPadding()) {
                if (!shouldShowPermissions) {
                    // 처음 실행 시에만 권한 화면 표시
                    PermissionScreen(
                        onPermissionsGranted = {
                            Logger.info("권한 요청 완료 - 권한 화면을 다시 보여주지 않도록 설정")
                            // 권한 요청을 보여줬음을 기록
                            PermissionHelper.markPermissionRequestShown(this@MainActivity)
                            // 권한 화면 숨김
                            shouldShowPermissions = true
                        }
                    )
                } else {
                    // 권한 화면을 이미 보여줬거나 필요 없는 경우 MainScreen 표시
                    MainScreen(
                        chatBadgeViewModel,
                        navigationViewModel,
                        logoutViewModel,
                        reloadViewModel,
                        onKakaoLogin = { successCallback, failureCallback ->
                            loginSuccessCallback = successCallback
                            loginFailureCallback = failureCallback

                            AppUtil.loginWithKakaoTalk(this@MainActivity,
                                onSuccess = { userInfo ->
                                    Logger.info("## 카카오 로그인 성공: ${userInfo.name}")
                                    callSNSCheck()
                                },
                                onFailure = { error ->
                                    Logger.info("## 카카오 로그인 실패 : $error")
                                    Toast.makeText(this@MainActivity, "카카오 로그인에 실패했습니다.$error", Toast.LENGTH_SHORT).show()
                                    loginFailureCallback?.invoke()
                                })
                        },
                        onNaverLogin = { successCallback, failureCallback ->
                            loginSuccessCallback = successCallback
                            loginFailureCallback = failureCallback

                            AppUtil.loginWithNaver(this@MainActivity,
                                onSuccess = { userInfo ->
                                    Logger.info("## 네이버 로그인 성공: ${userInfo.name}")
                                    callSNSCheck()
                                },
                                onFailure = { error ->
                                    Logger.info("## 네이버 로그인 실패 : $error")
                                    Toast.makeText(this@MainActivity, "네이버 로그인에 실패했습니다.$error", Toast.LENGTH_SHORT).show()
                                    loginFailureCallback?.invoke()
                                })
                        },
                        onGoogleLogin = { successCallback, failureCallback ->
                            loginSuccessCallback = successCallback
                            loginFailureCallback = failureCallback

                            AppUtil.loginWithGoogle(this@MainActivity)
                        },
                        mainWebViewManager = mainWebViewManager,
                        notificationWebViewManager = notificationWebViewManager,
                        autoLoginInfo = autoLoginInfo,
                        initialNavigationTarget = initialNavigationTarget
                    )
                }

                // 전역 UI 컴포넌트들
                CustomLoading()  // 전역 로딩
                CustomAlert()    // 전역 알림
            }
        }

        // 자동 로그인 시도
        if (autoLoginInfo != null ) {
            performAutoLogin(autoLoginInfo)
        }

        // Intent에서 FCM 데이터 확인 (앱이 백그라운드에서 시작된 경우)
        checkIntentForFCMData(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 새로운 Intent에서 FCM 데이터 확인
        checkIntentForFCMData(intent)
    }

    override fun onPause() {
        super.onPause()

        // 백그라운드로 전환되는 시점 저장
        backgroundTimestamp = System.currentTimeMillis()
        saveBackgroundTimestamp(backgroundTimestamp)

        Logger.info("## 앱 백그라운드 전환 - 시간 저장: $backgroundTimestamp")
    }

    override fun onResume() {
        super.onResume()

        // 푸시로 시작된 경우 세션 체크 예외 처리
        if (isStartedFromPush) {
            Logger.info("## 푸시로 시작됨 - 세션 체크 건너뜀")
            isStartedFromPush = false
            return
        }

        // 앱이 처음 시작되는 경우 (backgroundTimestamp가 0) 체크 건너뜀
        if (backgroundTimestamp == 0L) {
            backgroundTimestamp = getSavedBackgroundTimestamp()
            if (backgroundTimestamp == 0L) {
                Logger.info("## 앱 첫 시작 - 세션 체크 건너뜀")
                return
            }
        }

        // 포그라운드로 복귀한 시점
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - backgroundTimestamp

        Logger.info("## 앱 포그라운드 복귀 - 경과 시간: ${elapsedTime / 1000}초")

        // 세션 타임아웃 체크 (10분)
        if (elapsedTime > SESSION_TIMEOUT_MS) {
            Logger.info("## 세션 타임아웃 감지 (${elapsedTime / 1000}초 경과) - 재로그인 필요")
            triggerRelogin()
        } else {
            Logger.info("## 세션 유지 중 (${elapsedTime / 1000}초 경과)")
        }
    }

    /**
     * 재로그인 트리거
     */
    private fun triggerRelogin() {
        val mainIntent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
    }

    /**
     * 백그라운드 타임스탬프 저장
     */
    private fun saveBackgroundTimestamp(timestamp: Long) {
        try {
            val prefs = getSharedPreferences("SESSION_PREF", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("BACKGROUND_TIMESTAMP", timestamp)
                apply()
            }
        } catch (e: Exception) {
            Logger.error("백그라운드 타임스탬프 저장 실패: ${e.message}")
        }
    }

    /**
     * 저장된 백그라운드 타임스탬프 조회
     */
    private fun getSavedBackgroundTimestamp(): Long {
        return try {
            val prefs = getSharedPreferences("SESSION_PREF", Context.MODE_PRIVATE)
            prefs.getLong("BACKGROUND_TIMESTAMP", 0L)
        } catch (e: Exception) {
            Logger.error("백그라운드 타임스탬프 조회 실패: ${e.message}")
            0L
        }
    }

    /**
     * WebView 로딩 완료 감지 및 FCM URL 이동 처리
     */
    private fun setupWebViewLoadingObserver() {
        // WebView 로딩 상태 변경 감지를 위한 옵저버 설정
        // (실제 구현은 WebViewManager의 isWebViewLoaded State를 관찰)
        Handler(Looper.getMainLooper()).post {
            observeWebViewLoadingState()
        }
    }

    /**
     * WebView 로딩 상태 관찰 및 FCM URL 이동 처리
     */
    private fun observeWebViewLoadingState() {
        val handler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                // WebView가 로딩 완료되고 pendingFCMUrl이 있으면 URL 이동
                if (mainWebViewManager.isWebViewLoaded.value && !pendingFCMUrl.isNullOrEmpty()) {
                    val url = pendingFCMUrl!!
                    Logger.info("## WebView 로딩 완료 - FCM URL로 이동: $url")

                    // 메인 스레드에서 URL 이동
                    mainWebViewManager.loadUrl(url)

                    // pendingFCMUrl 초기화
                    pendingFCMUrl = null

                    //메인 탭으로 이동
                    ViewCallbackManager.notifyResult(NAVIGATION, HOME)
                } else if (!pendingFCMUrl.isNullOrEmpty()) {
                    // WebView가 아직 로딩 중이면 0.5초 후 다시 체크
                    handler.postDelayed(this, 500)
                }
            }
        }

        // 초기 체크 시작
        handler.post(checkRunnable)
    }

    /**
     * 자동 로그인 수행
     */
    private fun performAutoLogin(autoLoginInfo: Pair<String, String>) {
        Logger.info("## 자동 로그인 시도: ${autoLoginInfo.first}")

        // 약간의 지연 후 자동 로그인 처리 (스플래시 화면 표시 시간 확보)
        Handler(Looper.getMainLooper()).postDelayed({
            isAutoLoginAttempted = true
            callSNSCheck()
        }, 1000)
    }

    /**
     * 유저정보 체크 API 호출 (snsType, userId 포함)
     */
    private fun callSNSCheck() {
        val snsType = PreferenceManager.getSNSType(this)
        val userId = PreferenceManager.getSNSId(this)
        //val userId = System.currentTimeMillis().toString()


        if (snsType.isEmpty() || userId.isEmpty()) {
            Logger.info("## SNS 로그인 정보 없음 - API 호출 실패")
            loginFailureCallback?.invoke()
            return
        }
        loginSuccessCallback?.invoke()

        NetworkAPI.snsCheck(snsType, userId)
    }

    // Google 로그인 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            AppUtil.handleGoogleSignInResult(
                context = this,
                data = data,
                onSuccess = { userInfo ->
                    Logger.info("## 구글 로그인 성공: ${userInfo.name}")
                    callSNSCheck()
                },
                onFailure = { error ->
                    Logger.info("## 구글 로그인 실패 : $error")
                    Toast.makeText(this@MainActivity, "구글 로그인에 실패했습니다.$error", Toast.LENGTH_SHORT).show()
                    loginFailureCallback?.invoke()
                }
            )
        }
    }

    /**
     * 앱이 살아있으면 FCM 데이터 실행
     */
    fun setFCMIntent(bundle: Bundle?) {
        if (bundle != null) {
            Logger.info("## FCM 데이터 수신 : ${bundle.keySet().joinToString { "$it=${bundle.getString(it)}" }}")

            // 푸시로 시작된 것으로 표시 (세션 체크 예외 처리용)
            isStartedFromPush = true

            if (bundle.containsKey("type") && bundle.getString("type").equals("CHAT", true)) {
                ViewCallbackManager.notifyResult(NAVIGATION, CHAT)
            }

            // URL이 있으면 pendingFCMUrl에 저장
            if (bundle.containsKey("url")) {
                val url = bundle.getString("url")
                if (!url.isNullOrEmpty()) {
                    Logger.info("## FCM URL 수신: $url")
                    pendingFCMUrl = url
                    observeWebViewLoadingState()
                }
            }
        }
    }

    /**
     * Intent에서 FCM 데이터를 확인하고 처리
     */
    private fun checkIntentForFCMData(intent: android.content.Intent?) {
        intent?.extras?.let { bundle ->
            Logger.info("## Intent에서 FCM 데이터 수신: ${bundle.keySet().joinToString { "$it=${bundle.getString(it)}" }}")

            // 푸시로 시작된 것으로 표시 (세션 체크 예외 처리용)
            isStartedFromPush = true

            if (bundle.containsKey("type") && bundle.getString("type").equals("CHAT", true)) {
                initialNavigationTarget = CHAT
            }

            // 메인의 WebViewScreen 페이지에서 기본 URL 로딩이 완료된 후
            // bundle의 getString("url")로 이동하게 처리
            if (bundle.containsKey("url")) {
                val url = bundle.getString("url")
                if (!url.isNullOrEmpty()) {
                    Logger.info("## FCM에서 받은 URL: $url")
                    pendingFCMUrl = url

                    // WebView 로딩 상태 관찰 시작
                    observeWebViewLoadingState()
                }
            }
        }
    }

    /**
     * FCM에서 채팅 메시지가 왔을때
     */
    fun updateChatBadgeFromFCM() {
        try {
            if (!isDestroyed && !isFinishing) {
                runOnUiThread {
                    try {
                        ViewCallbackManager.notifyResult(CHAT_BADGE, true)
                        Logger.info("Chat badge updated from FCM")
                    } catch (e: Exception) {
                        Logger.error("Error updating chat badge from FCM: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error in updateChatBadgeFromFCM: ${e.message}")
        }
    }

    /**
     * 초기 네비게이션 타겟을 소비하고 초기화
     */
    fun consumeInitialNavigationTarget(): Int? {
        val target = initialNavigationTarget
        initialNavigationTarget = null
        return target
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkAPI.shutdown()
        NetworkAPIManager.clearAllCallbacks()
        SocketManager.cleanup()

        // 두 개의 WebViewManager 모두 destroy
        mainWebViewManager.destroy()
        notificationWebViewManager.destroy()

        loginSuccessCallback = null
        loginFailureCallback = null
        initialNavigationTarget = null
        pendingFCMUrl = null
    }
}