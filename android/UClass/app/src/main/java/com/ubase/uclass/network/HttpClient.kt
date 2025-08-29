package com.ubase.uclass.network

import android.text.TextUtils
import android.util.Log
import com.ubase.uclass.util.Constants
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class HttpClient private constructor(builder: Builder) {

    val call: Call

    class Builder {
        val okhttpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        var cookie: CookieJar? = null
        var url: String = ""
        var addJWTToken: Boolean = true
        var jsonData: String = ""
        var isPost: Boolean = true
        var isLogging: Boolean = false
        var requestBody: RequestBody? = null
        var setParameter: HashMap<String, String>? = null
        var setHeader: HashMap<String, String>? = null
        var timeout: Int = 15
        var cache: HashMap<String, String>? = null
    }

    init {
        // Timeout 설정
        builder.okhttpBuilder.apply {
            connectTimeout(builder.timeout.toLong(), TimeUnit.SECONDS)
            readTimeout(builder.timeout.toLong(), TimeUnit.SECONDS)
            writeTimeout(builder.timeout.toLong(), TimeUnit.SECONDS)
            callTimeout(builder.timeout.toLong(), TimeUnit.SECONDS)
            retryOnConnectionFailure(false)
        }

        val request = Request.Builder()
        var body: RequestBody? = null

        // 쿠키, Session 설정
        builder.cookie?.let {
            builder.okhttpBuilder.cookieJar(it)
        }

        // 디버그 빌드면 Header만 로그 생성
        // 로그를 사용하면 Body까지 로그 생성
        if (Constants.isDebug) {
            val level = if (builder.isLogging) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.HEADERS
            }
            builder.okhttpBuilder.addInterceptor(httpLoggingInterceptor(level))
        }

        val okHttpClient = builder.okhttpBuilder.build()
        request.url(builder.url)

        // Header에 JWT 토큰을 추가
        if (builder.addJWTToken && !TextUtils.isEmpty(Constants.jwtToken)) {
            request.addHeader("JWT_TOKEN", Constants.jwtToken)
            request.addHeader("Authorization", Constants.jwtToken)
        } else {
            request.addHeader("JWT_TOKEN", "")
        }
        request.addHeader("User-Agent", "AOS")

        // 추가 설정한 Header가 있으면 설정
        builder.setHeader?.forEach { (key, value) ->
            request.addHeader(key, value)
        }

        // JSON을 Body에 추가
        if (!TextUtils.isEmpty(builder.jsonData)) {
            body = builder.jsonData.toRequestBody("application/json".toMediaType())
        }

        // Body를 생성자에서 전달받으면 전달받은 Body로 재설정
        builder.requestBody?.let {
            body = it
        }

        // POST 또는 GET 요청 처리
        if (builder.isPost && body != null) {
            request.post(body!!)
        } else {
            val urlBuilder = builder.url.toHttpUrl().newBuilder()

            // 파라미터 추가
            builder.setParameter?.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }

            // 캐시 처리
            builder.cache?.let { cache ->
                val fullUrl = urlBuilder.build().toString()
                cache[fullUrl]?.let { etag ->
                    if (etag.isNotEmpty()) {
                        request.addHeader("If-None-Match", etag)
                    }
                }
            }

            request.url(urlBuilder.build())
            request.get()
        }

        call = okHttpClient.newCall(request.build())
    }

    private fun httpLoggingInterceptor(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor { message ->
            Log.d("UCLASS_API", message)
        }
        return interceptor.setLevel(level)
    }
}

// 사용 예시
/*
val httpClient = HttpClient.Builder()
    .setUrl("https://api.example.com/data")
    .addJWTToken(true)
    .setJsonData("{\"key\":\"value\"}")
    .isPost(true)
    .isLogging(true)
    .setTimeout(30)
    .build()

val call = httpClient.call
*/