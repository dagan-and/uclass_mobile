package com.ubase.uclass.util

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONObject
import java.util.*

object PreferenceManager {
    private const val PREF_NAME = "uclass_preferences"

    // Keys
    private const val KEY_SNS_TYPE = "sns_type"
    private const val KEY_SNS_ID = "sns_id"
    private const val KEY_SNS_TOKEN = "sns_token"
    private const val KEY_USER_ID = "user_id_v1"
    private const val KEY_BRANCH_ID = "branch_id_v1"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_PHONE_NUMBER = "user_phone_number"
    private const val KEY_LOGIN_TIME = "login_time"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // MARK: - SNS 로그인 정보 저장/조회

    /** SNS 타입 저장 */
    fun setSNSType(context: Context, type: String) {
        getPreferences(context).edit().putString(KEY_SNS_TYPE, type).apply()
    }

    /** SNS 타입 조회 */
    fun getSNSType(context: Context): String {
        return getPreferences(context).getString(KEY_SNS_TYPE, "") ?: ""
    }

    /** 사용자 ID 조회 */
    fun getSNSId(context: Context): String {
        return getPreferences(context).getString(KEY_SNS_ID, "") ?: ""
    }

    /** 사용자 이메일 저장 */
    fun setUserEmail(context: Context, email: String) {
        getPreferences(context).edit().putString(KEY_USER_EMAIL, email).apply()
    }

    /** 사용자 이메일 조회 */
    fun getUserEmail(context: Context): String {
        return getPreferences(context).getString(KEY_USER_EMAIL, "") ?: ""
    }

    /** 사용자 이름 저장 */
    fun setUserName(context: Context, name: String) {
        getPreferences(context).edit().putString(KEY_USER_NAME, name).apply()
    }

    /** 사용자 이름 조회 */
    fun getUserName(context: Context): String {
        return getPreferences(context).getString(KEY_USER_NAME, "") ?: ""
    }

    /** 로그인 상태 저장 */
    fun setLoggedIn(context: Context, isLoggedIn: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }

    /** 로그인 상태 조회 */
    fun isLoggedIn(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /** 사용자 ID 조회 */
    fun setUserId(context: Context, userId: Int) {
        getPreferences(context).edit().putInt(KEY_USER_ID , userId).apply()
    }

    /** 사용자 ID 조회 */
    fun getUserId(context: Context): Int {
        return getPreferences(context).getInt(KEY_USER_ID, 0)
    }

    /** 사용자 전화번호 조회 */
    fun setPhoneNumber(context: Context, phone: String) {
        getPreferences(context).edit().putString(KEY_PHONE_NUMBER , phone).apply()
    }

    /** 사용자 전화번호 조회 */
    fun getPhoneNumber(context: Context): String {
        return getPreferences(context).getString(KEY_PHONE_NUMBER, "") ?: ""
    }

    /** 지점 ID 조회 */
    fun setBranchId(context: Context, userId: Int) {
        getPreferences(context).edit().putInt(KEY_BRANCH_ID , userId).apply()
    }

    /** 지점 ID 조회 */
    fun getBranchId(context: Context): Int {
        return getPreferences(context).getInt(KEY_BRANCH_ID, 0)
    }

    // MARK: - 전체 로그인 정보 저장

    /** 모든 로그인 정보를 한번에 저장 */
    fun saveLoginInfo(context: Context, snsType: String, userId: String, email: String, name: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_SNS_TYPE, snsType)
        editor.putString(KEY_SNS_ID, userId)
        editor.putString(KEY_USER_EMAIL, email)
        editor.putString(KEY_USER_NAME, name)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
        editor.apply()
    }

    /** 모든 로그인 정보 삭제 (로그아웃) */
    fun clearLoginInfo(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_SNS_TYPE)
        editor.remove(KEY_SNS_ID)
        editor.remove(KEY_USER_EMAIL)
        editor.remove(KEY_USER_NAME)
        editor.remove(KEY_LOGIN_TIME)
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
        editor.apply()
    }

    /** 저장된 로그인 정보 출력 (디버그용) */
    fun printSavedLoginInfo(context: Context) {
        Logger.info("=== 저장된 로그인 정보 ===")
        Logger.info("SNS Type: ${getSNSType(context)}")
        Logger.info("User ID: ${getSNSId(context)}")
        Logger.info("Email: ${getUserEmail(context)}")
        Logger.info("Name: ${getUserName(context)}")
        Logger.info("Is Logged In: ${isLoggedIn(context)}")
        Logger.info("========================")
    }

    // MARK: - 편의 기능들

    /** 현재 저장된 로그인 정보를 JSON으로 반환 */
    fun getLoginInfoAsJson(context: Context): JSONObject {
        val json = JSONObject()
        json.put("provider", getSNSType(context))
        json.put("snsId", getSNSId(context))
        if(!TextUtils.isEmpty(getUserName(context))) {
            json.put("name", getUserName(context))
        }
        if(!TextUtils.isEmpty(getUserEmail(context))) {
            json.put("email", getUserEmail(context))
        }
        if(!TextUtils.isEmpty(getPhoneNumber(context))) {
            json.put("phoneNumber", getPhoneNumber(context))
        }
        return json
    }

    /** 특정 SNS 타입인지 확인 */
    fun isCurrentSNSType(context: Context, type: String): Boolean {
        return getSNSType(context).lowercase(Locale.getDefault()) == type.lowercase(Locale.getDefault())
    }

    /** 사용자 정보가 완전한지 확인 */
    fun hasCompleteUserInfo(context: Context): Boolean {
        return getSNSId(context).isNotEmpty()
    }

    /** 로그인 시간 조회 */
    fun getLoginTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_LOGIN_TIME, 0L)
    }

    /** 자동 로그인 가능한지 확인 (모든 조건 체크) */
    fun canAutoLogin(context: Context): Boolean {
        return isLoggedIn(context) && hasCompleteUserInfo(context)
    }

    // PreferenceManager.kt에 추가할 메서드들

    /**
     * Boolean 값을 저장
     */
    fun putBoolean(context: Context, key: String, value: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    /**
     * Boolean 값을 가져옴
     */
    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    /**
     * 특정 키의 값을 삭제
     */
    fun remove(context: Context, key: String) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().remove(key).apply()
    }
}