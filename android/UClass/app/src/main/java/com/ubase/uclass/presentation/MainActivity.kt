package com.ubase.uclass.presentation

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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.presentation.ui.CustomLoading
import com.ubase.uclass.presentation.view.MainScreen
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PreferenceManager

class MainActivity : ComponentActivity() {

    private lateinit var webViewManager: WebViewManager

    // 로그인 성공/실패 상태를 관리하기 위한 콜백
    private var loginSuccessCallback: (() -> Unit)? = null
    private var loginFailureCallback: (() -> Unit)? = null

    // 자동 로그인 상태 관리
    private var isAutoLoginAttempted = false

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

        splashScreen.setKeepOnScreenCondition {
            // 자동 로그인 가능한 경우 스플래시 유지
            shouldKeepSplash && !isAutoLoginAttempted
        }

        webViewManager = WebViewManager(this)

        enableEdgeToEdge()
        setContent {
            Box(Modifier.safeDrawingPadding()) {
                MainScreen(
                    onKakaoLogin = { successCallback, failureCallback ->
                        loginSuccessCallback = successCallback
                        loginFailureCallback = failureCallback

                        AppUtil.loginWithKakaoTalk(this@MainActivity,
                            onSuccess = { userInfo ->
                                Logger.info("## 카카오 로그인 성공: ${userInfo.name}")
                                callAuthInitStore()
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
                                callAuthInitStore()
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
                    webViewManager = webViewManager,
                    autoLoginInfo = autoLoginInfo
                )
                CustomLoading()
            }
        }

        // 자동 로그인 시도
        if (autoLoginInfo != null) {
            performAutoLogin(autoLoginInfo)
        }
    }

    /**
     * 자동 로그인 수행
     */
    private fun performAutoLogin(autoLoginInfo: Pair<String, String>) {
        Logger.info("## 자동 로그인 시도: ${autoLoginInfo.first}")

        // 약간의 지연 후 자동 로그인 처리 (스플래시 화면 표시 시간 확보)
        Handler(Looper.getMainLooper()).postDelayed({
            isAutoLoginAttempted = true
            callAuthInitStore()
        }, 1000)
    }

    /**
     * authInitStore API 호출 (snsType, userId 포함)
     */
    private fun callAuthInitStore() {
        val snsType = PreferenceManager.getSNSType(this)
        val userId = PreferenceManager.getUserId(this)

        if (snsType.isEmpty() || userId.isEmpty()) {
            Logger.info("## SNS 로그인 정보 없음 - API 호출 실패")
            loginFailureCallback?.invoke()
            return
        }
        loginSuccessCallback?.invoke()

        Logger.info("## authInitStore 호출: snsType=$snsType, userId=$userId")

        // NetworkAPI.authInitStore에 snsType과 userId 전달
        // 실제 API 시그니처에 맞게 수정 필요
        NetworkAPI.authInitStore("1.0.0", snsType, userId)
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
                    callAuthInitStore()
                },
                onFailure = { error ->
                    Logger.info("## 구글 로그인 실패 : $error")
                    Toast.makeText(this@MainActivity, "구글 로그인에 실패했습니다.$error", Toast.LENGTH_SHORT).show()
                    loginFailureCallback?.invoke()
                }
            )
        }
    }

    fun setFCMIntent(bundle: Bundle?) {
        // FCM 처리 로직
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkAPI.shutdown()
        NetworkAPIManager.clearAllCallbacks()
        webViewManager.destroy()
        loginSuccessCallback = null
        loginFailureCallback = null
    }
}