package com.ubase.uclass.network

import androidx.annotation.Size
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ubase.uclass.network.request.ChatInit
import com.ubase.uclass.network.request.ChatMessage
import com.ubase.uclass.network.request.SNSLogin
import com.ubase.uclass.network.request.SNSRegister
import com.ubase.uclass.network.response.BaseData
import com.ubase.uclass.network.response.ChatInitData
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.network.response.SNSCheckData
import com.ubase.uclass.network.response.SNSLoginData
import com.ubase.uclass.network.response.EmptyData
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import okhttp3.CookieJar
import okhttp3.JavaNetCookieJar
import okhttp3.Response
import java.lang.reflect.Type
import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 싱글톤 패턴으로 구현된 네트워크 API 클래스
 * 모든 네트워크 요청을 담당하며, 결과는 NetworkAPIManager를 통해 콜백으로 전달됩니다.
 */
object NetworkAPI {

    private var executorService: ExecutorService? = null
    private var cookieHandler: CookieHandler? = null
    private var isInitialized = false

    interface LogListener {
        fun onLog(message: String)
    }

    /**
     * NetworkAPI 초기화
     * 앱 시작 시 한 번만 호출하면 됩니다.
     */
    fun initialize() {
        if (!isInitialized) {
            this.cookieHandler = CookieManager()
            this.executorService = Executors.newCachedThreadPool()
            this.isInitialized = true
            Logger.dev("NetworkAPI initialized")
        }
    }

    /**
     * 초기화 여부 확인
     */
    fun isInitialized(): Boolean = isInitialized

    private fun getCookieJar(): CookieJar {
        return JavaNetCookieJar(cookieHandler ?: CookieManager())
    }

    /**
     * 에러 발생 시 NetworkAPIManager를 통해 콜백 전달
     */
    private fun sendError(code: Int, exception: Exception) {
        Logger.error(exception)
        val errorData = ErrorData(code, AppUtil.getExceptionLog(exception))
        NetworkAPIManager.notifyResult(NetworkAPIManager.ResponseCode.API_ERROR, errorData)
    }

    private fun sendError(code: Int, throwable: Throwable) {
        Logger.error(throwable)
        val errorData = ErrorData(code, AppUtil.getExceptionLog(throwable))
        NetworkAPIManager.notifyResult(NetworkAPIManager.ResponseCode.API_ERROR, errorData)
    }

    /**
     * 성공 결과를 NetworkAPIManager를 통해 콜백 전달
     */
    private fun sendCallback(code: Int, data: Any?) {
        NetworkAPIManager.notifyResult(code, data)
    }

    private fun logRequest(request: Any) {
        if (Logger.isEnable()) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val prettyJsonString = gson.toJson(request)
            Logger.dev("REQUEST: $prettyJsonString")
        }
    }

    private fun logResponse(json: String) {
        if (Logger.isEnable()) {
            try {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val parsedJson = gson.fromJson(json, Any::class.java)
                val prettyJsonString = gson.toJson(parsedJson)
                Logger.dev("RESPONSE: $prettyJsonString")
            } catch (e: Exception) {
                Logger.error(e)
                Logger.dev("RESPONSE (Raw): $json")
            }
        }
    }

    /**
     * 초기화 체크
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("NetworkAPI is not initialized. Call initialize(context) first.")
        }
    }

    /**
     * 공통 POST 요청 처리 함수 - BaseData<T> 구조용
     */
    private fun executePostRequest(
        endpoint: String,
        responseCode: Int,
        requestBody: Any,
        responseType: Type
    ) {
        checkInitialized()

        executorService?.execute {
            try {
                val url = Constants.baseURL + endpoint

                logRequest(requestBody)

                val httpClient = HttpClient.Builder()
                    .setUrl(url)
                    .setCookie(getCookieJar())
                    .setJsonData(Gson().toJson(requestBody))
                    .isPost(true)
                    .enableLogging(true)
                    .build()

                val response: Response = httpClient.getCall().execute()

                try {
                    val responseString = response.body.string() ?: ""
                    logResponse(responseString)

                    if (response.isSuccessful) {
                        if (responseString.isNotEmpty()) {
                            try {
                                val gson = Gson()
                                val parsedResponse = gson.fromJson<Any>(responseString, responseType)

                                // BaseData 구조로 파싱된 응답 확인
                                if (parsedResponse is BaseData<*>) {
                                    if (parsedResponse.isSuccess) {
                                        // 성공 시 전체 BaseData 객체 전달
                                        sendCallback(responseCode, parsedResponse)
                                    } else {
                                        // API에서 success: false 응답
                                        val errorData = ErrorData(
                                            responseCode,
                                            parsedResponse.message ?: "API Error"
                                        )
                                        sendCallback(NetworkAPIManager.ResponseCode.API_ERROR, errorData)
                                    }
                                } else {
                                    // BaseData 구조가 아닌 응답
                                    sendCallback(responseCode, parsedResponse)
                                }
                            } catch (e: Exception) {
                                sendError(responseCode, e)
                            }
                        } else {
                            sendCallback(responseCode, null)
                        }
                    } else {
                        val errorResponse = Gson().fromJson(responseString, BaseData::class.java)
                        val errorData = ErrorData(
                            responseCode,
                            errorResponse.message
                        )
                        sendCallback(NetworkAPIManager.ResponseCode.API_ERROR, errorData)
                    }
                } catch (e: Exception) {
                    sendError(responseCode, e)
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                sendError(responseCode, e)
            }
        }
    }

    /**
     * POST /api/auth/sns/check
     */
    fun snsCheck(snsType: String, snsId: String) {
        val requestBody = SNSLogin(
            provider = snsType,
            snsId = snsId,
            pushToken = Constants.fcmToken
        )

        val responseType = object : TypeToken<BaseData<SNSCheckData>>() {}.type

        executePostRequest(
            endpoint = NetworkAPIManager.Endpoint.API_AUTH_SNS_CHECK,
            responseCode = NetworkAPIManager.ResponseCode.API_AUTH_SNS_CHECK,
            requestBody = requestBody,
            responseType = responseType
        )
    }

    /**
     * POST /api/auth/sns/login
     */
    fun snsLogin(snsType: String, snsId: String) {
        val requestBody = SNSLogin(
            provider = snsType,
            snsId = snsId,
            pushToken = Constants.fcmToken
        )

        val responseType = object : TypeToken<BaseData<SNSLoginData>>() {}.type

        executePostRequest(
            endpoint = NetworkAPIManager.Endpoint.API_AUTH_SNS_LOGIN,
            responseCode = NetworkAPIManager.ResponseCode.API_AUTH_SNS_LOGIN,
            requestBody = requestBody,
            responseType = responseType
        )
    }

    /**
     * POST /api/auth/sns/register
     */
    fun snsRegister(
        snsType: String,
        snsId: String,
        name: String,
        email: String,
        phoneNumber: String = "010-1234-5678",
        profileImageUrl: String = "",
        userType: String = "STUDENT",
        branchId: Int = 10000001,
        termsAgreed: Boolean = true,
        privacyAgreed: Boolean = true
    ) {
        val requestBody = SNSRegister(
            provider = snsType,
            snsId = snsId,
            name = name,
            email = email,
            phoneNumber = phoneNumber,
            profileImageUrl = profileImageUrl,
            userType = userType,
            branchId = branchId,
            termsAgreed = termsAgreed,
            privacyAgreed = privacyAgreed
        )

        val responseType = object : TypeToken<BaseData<EmptyData>>() {}.type

        executePostRequest(
            endpoint = NetworkAPIManager.Endpoint.API_AUTH_SNS_REGISTER,
            responseCode = NetworkAPIManager.ResponseCode.API_AUTH_SNS_REGISTER,
            requestBody = requestBody,
            responseType = responseType
        )
    }

    /**
     * POST /api/auth/sns/register
     */
    fun chatInit(
        useId: String,
    ){
        val requestBody = ChatInit(
            userId = useId
        )

        val responseType = object : TypeToken<BaseData<ChatInitData>>() {}.type

        executePostRequest(
            endpoint = NetworkAPIManager.Endpoint.API_DM_NATIVE_INIT,
            responseCode = NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT,
            requestBody = requestBody,
            responseType = responseType
        )
    }

    /**
     * POST /api/auth/sns/register
     */
    fun chatMessage(
        useId: Int,
        branchId: Int,
        page: Int,
        size: Int
    ){
        val requestBody = ChatMessage(
            userId = useId,
            branchId = branchId,
            page = page,
            size = size
        )

        val responseType = object : TypeToken<BaseData<ChatInitData>>() {}.type

        executePostRequest(
            endpoint = NetworkAPIManager.Endpoint.API_DM_NATIVE_MESSAGES,
            responseCode = NetworkAPIManager.ResponseCode.API_DM_NATIVE_MESSAGES,
            requestBody = requestBody,
            responseType = responseType
        )
    }


    /**
     * NetworkAPI 종료
     * 앱 종료 시 호출하여 리소스를 정리합니다.
     */
    fun shutdown() {
        executorService?.shutdown()
        executorService = null
        cookieHandler = null
        isInitialized = false
        Logger.dev("NetworkAPI shutdown")
    }
}