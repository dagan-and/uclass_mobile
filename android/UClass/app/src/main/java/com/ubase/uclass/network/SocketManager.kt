package com.ubase.uclass.network

import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * SockJS + STOMP 프로토콜을 지원하는 WebSocket 연결 관리 클래스
 */
object SocketManager {

    // STOMP 프레임 타입
    private object StompCommand {
        const val CONNECT = "CONNECT"
        const val CONNECTED = "CONNECTED"
        const val SUBSCRIBE = "SUBSCRIBE"
        const val UNSUBSCRIBE = "UNSUBSCRIBE"
        const val SEND = "SEND"
        const val MESSAGE = "MESSAGE"
        const val RECEIPT = "RECEIPT"
        const val ERROR = "ERROR"
        const val DISCONNECT = "DISCONNECT"
    }

    // 연결 상태
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var isInitialized = false
    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val messageSubjectId = AtomicInteger(0)
    private val subscriptionMap = mutableMapOf<String, String>() // subscriptionId -> destination
    private val messageFlow = MutableSharedFlow<StompMessage>(replay = 0, extraBufferCapacity = 100)
    private val isReconnecting = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 설정값
    private var serverUrl: String = "wss://dev-umanager.ubase.kr/ws"
    private var userId: String = Constants.getUserId().toString()
    private var branchId: String = Constants.getBranchId().toString()
    private var reconnectDelay = 3000L // 3초
    private var maxReconnectAttempts = 5
    private var currentReconnectAttempts = 0

    // 콜백
    private var onDmMessageReceived: ((String) -> Unit)? = null
    private var onJoinedReceived: ((String) -> Unit)? = null

    /**
     * STOMP 메시지 데이터 클래스
     */
    data class StompMessage(
        val command: String,
        val headers: Map<String, String>,
        val body: String
    )

    /**
     * SocketManager 초기화
     */
    fun initialize() {
        if (!isInitialized) {
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket은 무제한
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            isInitialized = true
            Logger.dev("SocketManager initialized")
        }
    }

    /**
     * WebSocket 연결
     */
    fun connect(
        onDmMessage: ((String) -> Unit)? = null,
        onJoined: ((String) -> Unit)? = null
    ) {
        if (!isInitialized) {
            Logger.error("SocketManager not initialized. Call initialize() first.")
            return
        }

        this.onDmMessageReceived = onDmMessage
        this.onJoinedReceived = onJoined

        if (connectionState.value == ConnectionState.CONNECTED ||
            connectionState.value == ConnectionState.CONNECTING) {
            Logger.dev("WebSocket already connected or connecting")
            return
        }

        connectInternal()
    }

    /**
     * 내부 연결 메서드
     */
    private fun connectInternal() {
        try {
            connectionState.value = ConnectionState.CONNECTING

            // SockJS URL 생성 (일반적으로 /websocket 또는 SockJS transport 사용)
            val wsUrl = buildSockJSUrl(serverUrl)
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = okHttpClient?.newWebSocket(request, StompWebSocketListener())
            Logger.dev("WebSocket connecting to: $wsUrl")

        } catch (e: Exception) {
            Logger.error("WebSocket connection failed: ${e.message}")
            connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    /**
     * SockJS WebSocket URL 생성
     */
    private fun buildSockJSUrl(baseUrl: String): String {
        // SockJS의 경우 일반적으로 /websocket 엔드포인트 사용
        // 또는 SockJS transport를 사용할 수도 있음
        return when {
            baseUrl.endsWith("/websocket") -> baseUrl
            baseUrl.endsWith("/") -> "${baseUrl}websocket"
            else -> "$baseUrl/websocket"
        }
    }

    /**
     * STOMP CONNECT 프레임 전송
     */
    private fun sendConnectFrame() {
        val connectFrame = buildStompFrame(
            command = StompCommand.CONNECT,
            headers = mapOf(
                "accept-version" to "1.0,1.1,1.2",
                "heart-beat" to "10000,10000",
                "host" to serverUrl.substringAfter("://").substringBefore("/"),
                "login" to userId
            ),
            body = ""
        )

        webSocket?.send(connectFrame)
        Logger.dev("STOMP CONNECT frame sent")
    }

    /**
     * 자동 구독 및 방 참가
     */
    private fun autoSubscribeAndJoin() {
        coroutineScope.launch {
            // DM 메시지 구독
            subscribe("/user/queue/dm/joined") { message ->
                Logger.dev("DM joined message received: $message")
                onJoinedReceived?.invoke(message)
            }

            // 방 참가
            joinDmRoom()
        }
    }

    /**
     * DM 방 참가
     */
    fun joinDmRoom() {
        val joinMessage = JSONObject().apply {
            put("userId", userId.toLongOrNull() ?: Constants.getUserId())
            put("branchId", branchId)
        }

        sendJsonMessage("/app/dm/native/join", joinMessage)
        Logger.dev("DM room join request sent - userId: $userId, branchId: $branchId")
    }

    /**
     * DM 메시지 전송
     */
    fun sendDmMessage(content: String) {
        val messageJson = JSONObject().apply {
            put("content", content)
        }

        sendJsonMessage("/app/dm/native/send", messageJson)
        Logger.dev("DM message sent: $content")
    }

    /**
     * 구독
     */
    fun subscribe(destination: String, onMessage: (String) -> Unit = {}): String? {
        if (connectionState.value != ConnectionState.CONNECTED) {
            Logger.error("Cannot subscribe - WebSocket not connected")
            return null
        }

        val subscriptionId = "sub-${messageSubjectId.incrementAndGet()}"
        subscriptionMap[subscriptionId] = destination

        val subscribeFrame = buildStompFrame(
            command = StompCommand.SUBSCRIBE,
            headers = mapOf(
                "id" to subscriptionId,
                "destination" to destination
            ),
            body = ""
        )

        webSocket?.send(subscribeFrame)
        Logger.dev("Subscribed to $destination with id: $subscriptionId")

        // 특정 구독에 대한 메시지 처리
        coroutineScope.launch {
            messageFlow
                .filter { it.command == StompCommand.MESSAGE }
                .filter { it.headers["subscription"] == subscriptionId }
                .collect { message ->
                    onMessage(message.body)
                }
        }

        return subscriptionId
    }

    /**
     * 구독 해제
     */
    fun unsubscribe(subscriptionId: String) {
        if (connectionState.value != ConnectionState.CONNECTED) {
            Logger.error("Cannot unsubscribe - WebSocket not connected")
            return
        }

        val unsubscribeFrame = buildStompFrame(
            command = StompCommand.UNSUBSCRIBE,
            headers = mapOf("id" to subscriptionId),
            body = ""
        )

        webSocket?.send(unsubscribeFrame)
        subscriptionMap.remove(subscriptionId)
        Logger.dev("Unsubscribed from subscription: $subscriptionId")
    }

    /**
     * 메시지 전송
     */
    fun sendMessage(destination: String, message: String, headers: Map<String, String> = emptyMap()) {
        if (connectionState.value != ConnectionState.CONNECTED) {
            Logger.error("Cannot send message - WebSocket not connected")
            return
        }

        val sendHeaders = mutableMapOf<String, String>().apply {
            put("destination", destination)
            putAll(headers)
        }

        val sendFrame = buildStompFrame(
            command = StompCommand.SEND,
            headers = sendHeaders,
            body = message
        )

        webSocket?.send(sendFrame)
        Logger.dev("Message sent to $message")
    }

    /**
     * JSON 메시지 전송
     */
    fun sendJsonMessage(destination: String, jsonObject: JSONObject, headers: Map<String, String> = emptyMap()) {
        val jsonHeaders = mutableMapOf<String, String>().apply {
            put("content-type", "application/json")
            putAll(headers)
        }
        sendMessage(destination, jsonObject.toString(), jsonHeaders)
    }

    /**
     * STOMP 프레임 생성
     */
    private fun buildStompFrame(command: String, headers: Map<String, String>, body: String): String {
        val frame = StringBuilder()
        frame.append(command).append("\n")

        headers.forEach { (key, value) ->
            frame.append("$key:$value\n")
        }

        frame.append("\n").append(body).append("\u0000")
        return frame.toString()
    }

    /**
     * STOMP 프레임 파싱
     */
    private fun parseStompFrame(frameText: String): StompMessage? {
        try {
            val lines = frameText.split("\n")
            if (lines.isEmpty()) return null

            val command = lines[0].trim()
            val headers = mutableMapOf<String, String>()
            var bodyStartIndex = 1

            // 헤더 파싱
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.trim().isEmpty()) {
                    bodyStartIndex = i + 1
                    break
                }

                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                }
            }

            // 바디 파싱
            val body = if (bodyStartIndex < lines.size) {
                lines.subList(bodyStartIndex, lines.size).joinToString("\n")
                    .replace("\u0000", "") // null character 제거
            } else {
                ""
            }

            return StompMessage(command, headers, body)
        } catch (e: Exception) {
            Logger.error("Failed to parse STOMP frame: ${e.message}")
            return null
        }
    }

    /**
     * 연결 해제
     */
    fun disconnect() {
        try {
            connectionState.value = ConnectionState.DISCONNECTING
            isReconnecting.set(false)
            reconnectJob?.cancel()
            currentReconnectAttempts = 0

            // STOMP DISCONNECT 프레임 전송
            if (webSocket != null && connectionState.value == ConnectionState.CONNECTED) {
                val disconnectFrame = buildStompFrame(
                    command = StompCommand.DISCONNECT,
                    headers = emptyMap(),
                    body = ""
                )
                webSocket?.send(disconnectFrame)
            }

            // WebSocket 연결 종료
            webSocket?.close(1000, "User disconnected")
            webSocket = null
            connectionState.value = ConnectionState.DISCONNECTED
            subscriptionMap.clear()

            Logger.dev("WebSocket disconnected")
        } catch (e: Exception) {
            Logger.error("Error disconnecting WebSocket: ${e.message}")
        }
    }

    /**
     * 자동 재연결 스케줄링
     */
    private fun scheduleReconnect() {
        if (isReconnecting.get() || currentReconnectAttempts >= maxReconnectAttempts) {
            Logger.error("Max reconnect attempts reached or already reconnecting")
            return
        }

        isReconnecting.set(true)
        currentReconnectAttempts++

        reconnectJob = coroutineScope.launch {
            delay(reconnectDelay)
            if (isReconnecting.get() && connectionState.value == ConnectionState.DISCONNECTED) {
                Logger.dev("Attempting reconnect (${currentReconnectAttempts}/$maxReconnectAttempts)")
                connectInternal()
            }
        }
    }

    /**
     * 연결 상태 확인
     */
    fun isConnected(): Boolean {
        return connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * 연결 상태 Flow
     */
    fun getConnectionStateFlow(): StateFlow<ConnectionState> = connectionState.asStateFlow()

    /**
     * 메시지 Flow
     */
    fun getMessageFlow(): SharedFlow<StompMessage> = messageFlow.asSharedFlow()

    /**
     * 리소스 정리
     */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
        okHttpClient = null
        isInitialized = false
        Logger.dev("SocketManager cleaned up")
    }

    /**
     * WebSocket 이벤트 리스너
     */
    private class StompWebSocketListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Logger.dev("WebSocket opened, sending STOMP CONNECT")
            SocketManager.sendConnectFrame()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Logger.dev("WebSocket message received: $text")

            val message = SocketManager.parseStompFrame(text)
            message?.let { stompMessage ->
                SocketManager.coroutineScope.launch {
                    SocketManager.messageFlow.emit(stompMessage)
                }

                when (stompMessage.command) {
                    StompCommand.CONNECTED -> {
                        SocketManager.connectionState.value = ConnectionState.CONNECTED
                        SocketManager.isReconnecting.set(false)
                        SocketManager.currentReconnectAttempts = 0
                        Logger.dev("STOMP connected successfully")

                        // 연결 완료 후 자동 구독 및 방 참가
                        SocketManager.autoSubscribeAndJoin()
                    }

                    StompCommand.MESSAGE -> {
                        val destination = stompMessage.headers["destination"]
                        Logger.dev("STOMP message received for destination: $destination")

                        // DM 메시지 처리
                        when (destination) {
                            "/user/queue/dm/joined" -> {
                                SocketManager.onJoinedReceived?.invoke(stompMessage.body)
                            }
                            else -> {
                                // 기타 메시지 처리
                                SocketManager.onDmMessageReceived?.invoke(stompMessage.body)
                            }
                        }
                    }

                    StompCommand.ERROR -> {
                        Logger.error("STOMP error: ${stompMessage.body}")
                        SocketManager.connectionState.value = ConnectionState.DISCONNECTED
                        SocketManager.scheduleReconnect()
                    }

                    StompCommand.RECEIPT -> {
                        Logger.dev("STOMP receipt: ${stompMessage.headers["receipt-id"]}")
                    }

                    else -> {}
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Logger.dev("WebSocket binary message received")
            // STOMP는 일반적으로 텍스트 기반이므로 필요시 처리
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Logger.dev("WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.dev("WebSocket closed: $code - $reason")
            SocketManager.webSocket = null

            if (SocketManager.connectionState.value != ConnectionState.DISCONNECTING) {
                SocketManager.connectionState.value = ConnectionState.DISCONNECTED
                SocketManager.scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Logger.error("WebSocket failure: ${t.message}")
            SocketManager.webSocket = null
            SocketManager.connectionState.value = ConnectionState.DISCONNECTED
            SocketManager.scheduleReconnect()
        }
    }
}