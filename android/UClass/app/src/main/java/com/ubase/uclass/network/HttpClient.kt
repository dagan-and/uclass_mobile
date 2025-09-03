package com.ubase.uclass.network

import android.text.TextUtils
import android.util.Log
import com.ubase.uclass.util.Constants
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class HttpClient private constructor(private val call: Call) {

    class Builder {
        private val okhttpBuilder = OkHttpClient.Builder()
        private var cookie: CookieJar? = null
        private var url: String = ""
        private var addJWTToken: Boolean = true
        private var jsonData: String = ""
        private var isPost: Boolean = true
        private var isLogging: Boolean = false
        private var requestBody: RequestBody? = null
        private var parameters: HashMap<String, String>? = null
        private var headers: HashMap<String, String>? = null
        private var timeout: Int = 15
        private var logListener: NetworkAPI.LogListener? = null

        fun setCookie(cookie: CookieJar) = apply {
            this.cookie = cookie
        }

        fun setUrl(url: String) = apply {
            this.url = url
        }

        fun addJWTToken(addToken: Boolean) = apply {
            this.addJWTToken = addToken
        }

        fun setJsonData(data: String) = apply {
            this.jsonData = data
        }

        fun isPost(isPost: Boolean) = apply {
            this.isPost = isPost
        }

        fun enableLogging(isLogging: Boolean) = apply {
            this.isLogging = isLogging
        }

        fun setBody(body: RequestBody) = apply {
            this.requestBody = body
        }

        fun setParameters(parameters: HashMap<String, String>) = apply {
            this.parameters = parameters
        }

        fun setHeaders(headers: HashMap<String, String>) = apply {
            this.headers = headers
        }

        fun setTimeout(timeout: Int) = apply {
            this.timeout = timeout
        }

        fun setLogListener(logListener: NetworkAPI.LogListener) = apply {
            this.logListener = logListener
        }

        fun build(): HttpClient {
            // OkHttp 클라이언트 설정
            okhttpBuilder.apply {
                connectTimeout(timeout.toLong(), TimeUnit.SECONDS)
                readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                writeTimeout(timeout.toLong(), TimeUnit.SECONDS)
                callTimeout(timeout.toLong(), TimeUnit.SECONDS)
                retryOnConnectionFailure(false)
            }

            // 쿠키 설정
            cookie?.let { okhttpBuilder.cookieJar(it) }

            // 로깅 설정
            if (Constants.isDebug) {
                val loggingLevel = if (isLogging) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.HEADERS
                }

                val interceptor = if (logListener != null) {
                    createLoggingInterceptor(loggingLevel, logListener!!)
                } else {
                    createLoggingInterceptor(loggingLevel)
                }
                okhttpBuilder.addInterceptor(interceptor)
            }

            val okHttpClient = okhttpBuilder.build()
            val requestBuilder = Request.Builder().url(url)

            // JWT 토큰 헤더 추가
            if (addJWTToken && !TextUtils.isEmpty(Constants.jwtToken)) {
                requestBuilder.addHeader("JWT_TOKEN", Constants.jwtToken ?: "")
                requestBuilder.addHeader("Authorization", Constants.jwtToken ?: "")
            } else {
                requestBuilder.addHeader("JWT_TOKEN", "")
            }
            requestBuilder.addHeader("User-Agent", "AOS")

            // 추가 헤더 설정
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 요청 바디 설정
            var body: RequestBody? = null

            if (!TextUtils.isEmpty(jsonData)) {
                body = jsonData.toRequestBody("application/json".toMediaType())
            }

            requestBody?.let { body = it }

            // POST/GET 요청 설정
            if (isPost && body != null) {
                requestBuilder.post(body!!)
            } else {
                val urlBuilder = url.toHttpUrlOrNull()?.newBuilder()
                    ?: throw IllegalArgumentException("Invalid URL: $url")

                parameters?.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                }

                requestBuilder.url(urlBuilder.build()).get()
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            return HttpClient(call)
        }

        private fun createLoggingInterceptor(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor {
            return HttpLoggingInterceptor { message ->
                Log.d("UCLASS_API", message)
            }.setLevel(level)
        }

        private fun createLoggingInterceptor(
            level: HttpLoggingInterceptor.Level,
            logListener: NetworkAPI.LogListener
        ): HttpLoggingInterceptor {
            return HttpLoggingInterceptor { message ->
                Log.d("UCLASS_API", message)
                logListener.onLog(message)
            }.setLevel(level)
        }
    }

    fun getCall(): Call = call
}