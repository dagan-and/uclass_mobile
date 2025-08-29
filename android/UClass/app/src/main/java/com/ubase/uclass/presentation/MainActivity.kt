package com.ubase.uclass.presentation

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ubase.uclass.presentation.view.MainApp
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.Logger

class MainActivity : ComponentActivity() {

    private lateinit var webViewManager: WebViewManager

    // 로그인 성공/실패 상태를 관리하기 위한 콜백
    private var loginSuccessCallback: (() -> Unit)? = null
    private var loginFailureCallback: (() -> Unit)? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //폰의 경우 세로 고정
        if (!AppUtil.isTabletDevice(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        AppUtil.setWhiteStatusBareMode(this)


        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            //저장된 로그인 정보가 있다면 로그인 API 실행 후 메인으로 이동
            //저장된 로그인 정보가 없으면 바로 로그인 페이지로 이동
            false
        }

        webViewManager = WebViewManager(this)

        enableEdgeToEdge()
        setContent {
            Box(Modifier.safeDrawingPadding()) {
                MainApp(
                    onKakaoLogin = { successCallback, failureCallback ->
                        loginSuccessCallback = successCallback
                        loginFailureCallback = failureCallback

                        AppUtil.loginWithKakaoTalk(this@MainActivity,
                            onSuccess = { token ->
                                Logger.info("## 카카오 로그인 성공. 토큰 : $token")
                                loginSuccessCallback?.invoke()
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
                                Logger.info("## 네이버 로그인 성공. 정보 : $userInfo")
                                loginSuccessCallback?.invoke()
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
                    webViewManager = webViewManager
                )
            }
        }
    }

    // Google 로그인 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            AppUtil.handleGoogleSignInResult(
                data = data,
                onSuccess = { userInfo ->
                    Logger.info("## 구글 로그인 성공. 정보 : $userInfo")
                    loginSuccessCallback?.invoke()
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
        webViewManager.destroy()
        loginSuccessCallback = null
        loginFailureCallback = null
    }
}