package com.ubase.uclass.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.NidOAuthLogin
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.navercorp.nid.profile.NidProfileCallback
import com.navercorp.nid.profile.data.NidProfileResponse
import com.ubase.uclass.R
import org.json.JSONException
import org.json.JSONObject
import java.util.*


object AppUtil {

    /**
     * DP 값을 Pixel 로 변환
     */
    fun convertDPtoPX(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    /**
     * 디바이스가 테블릿 인지 체크
     */
    fun isTabletDevice(context: Activity): Boolean {
        val tabletSize: Boolean = context.getResources().getBoolean(R.bool.isTablet)
        return tabletSize && !isFoldableDevice(context)
    }

    fun isFoldableDevice(context: Context): Boolean {
        val packageManager = context.packageManager

        // Check if the device supports foldable features
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
        } else {
            val features = packageManager.systemAvailableFeatures
            features.any { it.name.equals("android.hardware.type.foldable", ignoreCase = true) }
        }
    }

    /**
     * 디바이스 정보 가져오기
     */
    @SuppressLint("HardwareIds")
    @Throws(JSONException::class)
    fun getDeviceInfo(context: Context): JSONObject {
        val resultJson = JSONObject()
        resultJson.put("OS", "ADR")
        resultJson.put("OS_VER", Build.VERSION.SDK_INT)
        resultJson.put("DEVICE_ID", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
        resultJson.put("DEVICE_MODEL", Build.MODEL)
        try {
            resultJson.put(
                "APP_VER",
                context.packageManager.getPackageInfo(
                    context.packageName,
                    0
                ).versionName?.trim { it <= ' ' })
        } catch (e: java.lang.Exception) {
            resultJson.put("APP_VER", "")
        }
        resultJson.put("APP_ID", context.packageName)
        return resultJson
    }

    /**
     * 소프트키보드 숨기기
     *
     * @param ctx Context
     * @param focusView 포커스 뷰
     */
    fun softkeyboardHide(ctx: Context, focusView: View?) {
        if (focusView == null) return
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(focusView.windowToken, 0)
    }

    /**
     * 소프트키보드 보이기
     *
     * @param ctx Context
     * @param focusView 포커스 뷰
     */
    fun softkeyboardShow(ctx: Context, focusView: EditText?) {
        val inputMethodManager =
            ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(focusView, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * StatusBar 를 흰색으로 설정
     * onlyFlag : true 상단의 StatusBar 색상을 흰색으로 설정
     *          : false 상단의 StatusBar 색상은 변경하지 않고 StatusBar 아이콘 색상만 White모드로 설정
     */
    fun setWhiteStatusBareMode(context: Activity) {
        context.window.statusBarColor = ContextCompat.getColor(context, R.color.white)
        context.window.navigationBarColor = ContextCompat.getColor(context, R.color.white)
    }

    /**
     * SDK 35 상하단 여백 주기
     */
    fun setupWindowInsets(activity: Activity) {
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    fun setupWindowInsets(container: View) {
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    /**
     * 전체화면 모드 설정
     */
    fun setFullScreenMode(context: Activity , view : View) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowCompat.setDecorFitsSystemWindows(context.window, false)
        } else {
            context.window.apply {
                setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
            }
        }
        view.setPadding(0, statusBarHeight(context), 0 , navigationHeight(context))
    }

    fun statusBarHeight(context: Activity): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")

        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId)
        else convertDPtoPX(context , 25)
    }

    fun navigationHeight(context: Activity): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")

        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId)
        else convertDPtoPX(context , 48)
    }

    /**
     * 화면 가로 길이 구하기
     */
    fun getScreenWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    /**
     * 화면 세로 길이 구하기
     */
    fun getScreenHeight(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.height() - insets.bottom - insets.top
        } else {
            val displayMetrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
    }

    /**
     * Exception 로그 가져오기
     */
    fun getExceptionLog(e: java.lang.Exception): String {
        var msg = e.javaClass.toString() + "[ " + e.message + " ]" + " >>> "
        val traceElements = e.stackTrace
        for (traceElement in traceElements) {
            msg += traceElement.toString() + "\n"
        }
        return msg
    }

    /**
     * Exception 로그 가져오기
     */
    fun getExceptionLog(e: Throwable): String {
        var msg = e.javaClass.toString() + "[ " + e.message + " ]" + " >>> "
        val traceElements = e.stackTrace
        for (traceElement in traceElements) {
            msg += traceElement.toString() + "\n"
        }
        return msg
    }

    /**
     * 암호화된 로그인 정보가 저장 되어있는지
     */
    fun isSavedLoginInfo() : Boolean {
        return true
    }

    //푸시 인텐트 설정
    fun setFCMIntent(
        set: Intent,
        get: Intent?,
        bundle: Bundle? = null,
        map: Map<String, String>? = null
    ) {
        // 각 값들을 우선순위에 따라 설정: Map -> Bundle -> Intent
        val finalPushType = map?.get("pushType") ?: bundle?.getString("pushType") ?: get?.getStringExtra("pushType")
        val finalServiceIndividualKey = map?.get("serviceIndividualKey") ?: bundle?.getString("serviceIndividualKey") ?: get?.getStringExtra("serviceIndividualKey")
        val finalUtterance = map?.get("utterance") ?: bundle?.getString("utterance") ?: get?.getStringExtra("utterance")
        val finalVocSeq = map?.get("vocSeq") ?: bundle?.getString("vocSeq") ?: get?.getStringExtra("vocSeq")
        val finalUrl = map?.get("url") ?: bundle?.getString("url") ?: get?.getStringExtra("url")
        val useInttId = map?.get("useInttId") ?: bundle?.getString("useInttId") ?: get?.getStringExtra("useInttId")


        // 값이 존재할 경우 Intent에 추가
        finalPushType?.let { set.putExtra("pushType", it) }
        finalServiceIndividualKey?.let { set.putExtra("serviceIndividualKey", it) }
        finalUtterance?.let { set.putExtra("utterance", it) }
        finalVocSeq?.let { set.putExtra("vocSeq", it) }
        finalUrl?.let { set.putExtra("url", it) }
        useInttId?.let { set.putExtra("useInttId", it) }
    }

    //카카오 로그인
    fun loginWithKakaoTalk(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            if (error != null) {
                onFailure(error)
            } else if (token != null) {
                UserApiClient.instance.me { user, error ->
                    if (error != null) {
                        onFailure(error)
                    } else if (user != null) {
                        val kakaoId = user.id
                        val nickname = user.kakaoAccount?.profile?.nickname
                        val email = user.kakaoAccount?.email
                        val profileImage = user.kakaoAccount?.profile?.profileImageUrl
                        onSuccess("ID: $kakaoId, Nickname: $nickname, Email: $email, ProfileImage: $profileImage")
                    }
                }
            }
        }
    }

    /**
     * 네이버 로그인
     */
    fun loginWithNaver(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val oAuthLoginCallback = object : OAuthLoginCallback {
            override fun onSuccess() {
                // 네이버 로그인 성공 - 사용자 정보 가져오기
                NidOAuthLogin().callProfileApi(object : NidProfileCallback<NidProfileResponse> {
                    override fun onSuccess(result: NidProfileResponse) {
                        val profile = result.profile
                        val naverId = profile?.id
                        val name = profile?.name
                        val email = profile?.email
                        val profileImage = profile?.profileImage
                        val nickname = profile?.nickname
                        val mobile = profile?.mobile

                        val userInfo = "ID: $naverId, Name: $name, Email: $email, Nickname: $nickname, Mobile: $mobile, ProfileImage: $profileImage"
                        onSuccess(userInfo)
                    }

                    override fun onFailure(httpStatus: Int, message: String) {
                        onFailure(Exception("Profile API 호출 실패: $message"))
                    }

                    override fun onError(errorCode: Int, message: String) {
                        onFailure(Exception("Profile API 오류: $message"))
                    }
                })
            }

            override fun onFailure(httpStatus: Int, message: String) {
                onFailure(Exception("네이버 로그인 실패: $message"))
            }

            override fun onError(errorCode: Int, message: String) {
                onFailure(Exception("네이버 로그인 오류: $message"))
            }
        }

        NaverIdLoginSDK.authenticate(context, oAuthLoginCallback)
    }


    /**
     * 구글 로그인 클라이언트 생성
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            // 필요시 서버 클라이언트 ID 추가
            // .requestServerAuthCode(context.getString(R.string.google_client_id))
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * 구글 로그인
     * Activity에서 startActivityForResult로 호출해야 함
     */
    fun loginWithGoogle(
        activity: Activity,
        requestCode: Int = 100
    ) {
        val googleSignInClient = getGoogleSignInClient(activity)
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, requestCode)
    }

    /**
     * 구글 로그인 결과 처리
     * Activity의 onActivityResult에서 호출
     */
    fun handleGoogleSignInResult(
        data: Intent?,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            val googleId = account.id
            val name = account.displayName
            val email = account.email
            val profileImage = account.photoUrl?.toString()
            val givenName = account.givenName
            val familyName = account.familyName

            val userInfo = "ID: $googleId, Name: $name, Email: $email, GivenName: $givenName, FamilyName: $familyName, ProfileImage: $profileImage"
            onSuccess(userInfo)

        } catch (e: ApiException) {
            onFailure(Exception("구글 로그인 실패: ${e.message}"))
        }
    }
}