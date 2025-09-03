package com.ubase.uclass.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.util.AppUtil
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import okhttp3.CookieJar
import okhttp3.JavaNetCookieJar
import okhttp3.Response
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
     * 인증 스토어 초기화 API
     */
    fun authInitStore(version: String) {
        checkInitialized()

        executorService?.execute {
            try {
                val url = Constants.baseURL + NetworkAPIManager.Endpoint.AUTH_INIT_STORE
                val responseCode = NetworkAPIManager.ResponseCode.API_AUTH_INIT_STORE

                val httpClient = HttpClient.Builder()
                    .setUrl(url)
                    .setCookie(getCookieJar())
                    .setParameters(hashMapOf("version" to version))
                    .isPost(false)
                    .enableLogging(true)
                    .build()

                val response: Response = httpClient.getCall().execute()

                try {
                    val responseBody = response.body.string() ?: ""
                    logResponse(responseBody)

                    if (response.isSuccessful) {
                        if (responseBody.isNotEmpty()) {
                            try {
                                val gson = Gson()
                                val parsedResponse = gson.fromJson(responseBody, Any::class.java)
                                sendCallback(responseCode, parsedResponse)
                            } catch (e: Exception) {
                                sendError(responseCode, e)
                            }
                        } else {
                            sendCallback(responseCode, null)
                        }
                    } else {
                        val errorData = ErrorData(
                            responseCode,
                            "HTTP ${response.code}: ${response.message}"
                        )
                        sendCallback(NetworkAPIManager.ResponseCode.API_ERROR, errorData)
                    }
                } catch (e: Exception) {
                    sendError(responseCode, e)
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                sendError(NetworkAPIManager.ResponseCode.API_AUTH_INIT_STORE, e)
            }
        }
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