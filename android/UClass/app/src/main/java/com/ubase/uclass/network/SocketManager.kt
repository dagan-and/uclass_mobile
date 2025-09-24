package com.ubase.uclass.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ubase.uclass.network.response.ChatMessage
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
    private val shouldReconnect = AtomicBoolean(true) // 재연결 허용 여부 플래그 추가
    private var reconnectJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 하트비트 관련
    private var heartbeatJob: Job? = null
    private var clientHeartbeatInterval = 10000L // 클라이언트 -> 서버 (10초)
    private var serverHeartbeatInterval = 10000L // 서버 -> 클라이언트 (10초)
    private var lastHeartbeatReceived = 0L
    private var heartbeatTimeoutJob: Job? = null

    // JSON 파싱을 위한 Gson 인스턴스
    private val gson = Gson()

    // 설정값
    private var serverUrl: String = "wss://dev-umanager.ubase.kr/ws"
    private var userId: Int = Constants.getUserId()
    private var branchId: Int = Constants.getBranchId()
    private var reconnectDelay = 3000L // 3초
    private var maxReconnectAttempts = 5
    private var currentReconnectAttempts = 0

    // 콜백
    private var onMessageReceived: ((ChatMessage) -> Unit)? = null
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
            shouldReconnect.set(true) // 초기화 시 재연결 허용
            Logger.dev("SocketManager initialized")
        }
    }

    /**
     * WebSocket 연결
     */
    fun connect(
        onDmMessage: ((ChatMessage) -> Unit)? = null,
        onJoined: ((String) -> Unit)? = null,
    ) {
        if (!isInitialized) {
            Logger.error("SocketManager not initialized. Call initialize() first.")
            return
        }

        this.onMessageReceived = onDmMessage
        this.onJoinedReceived = onJoined

        if (connectionState.value == ConnectionState.CONNECTED ||
            connectionState.value == ConnectionState.CONNECTING) {
            Logger.dev("WebSocket already connected or connecting")
            return
        }

        // 재연결 허용 설정
        shouldReconnect.set(true)
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
            // 재연결이 허용된 경우에만 스케줄링
            if (shouldReconnect.get()) {
                scheduleReconnect()
            }
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
                "heart-beat" to "$clientHeartbeatInterval,$serverHeartbeatInterval",
                "host" to serverUrl.substringAfter("://").substringBefore("/"),
                "login" to userId.toString()
            ),
            body = ""
        )

        webSocket?.send(connectFrame)
        Logger.dev("STOMP CONNECT frame sent with heartbeat: $clientHeartbeatInterval,$serverHeartbeatInterval")
    }

    /**
     * 자동 구독 및 방 참가
     */
    private fun autoSubscribeAndJoin() {
        coroutineScope.launch {
            // 입/출 구독
            subscribe("/user/queue/dm/joined") { message ->
                Logger.dev("user/queue/dm/joined: $message")
                onJoinedReceived?.invoke(message)
            }
            // DM 메시지 구독 - ChatMessage 객체로 파싱
            subscribe("/user/$userId/queue/messages") { message ->
                Logger.dev("user/$userId/queue/messages: $message")
                parseAndDeliverChatMessage(message)
            }

            // 방 참가
            joinDmRoom()
        }
    }

    /**
     * JSON 메시지를 ChatMessage 객체로 파싱하여 전달
     */
    private fun parseAndDeliverChatMessage(jsonMessage: String) {
        try {
            val chatMessage = gson.fromJson(jsonMessage, ChatMessage::class.java)
            Logger.dev("ChatMessage 파싱 성공: ${chatMessage.messageId}")
            onMessageReceived?.invoke(chatMessage)
        } catch (e: JsonSyntaxException) {
            Logger.error("ChatMessage JSON 파싱 실패: ${e.message}")
            Logger.error("원본 메시지: $jsonMessage")
        } catch (e: Exception) {
            Logger.error("ChatMessage 처리 중 오류: ${e.message}")
        }
    }

    /**
     * DM 방 참가
     */
    fun joinDmRoom() {
        val joinMessage = JSONObject().apply {
            put("userId", userId)
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
            put("userId", userId)
            put("branchId", branchId)
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
        Logger.dev("Message sent to $destination")
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
     * 연결 해제 - 재연결 방지 추가
     */
    fun disconnect() {
        try {
            Logger.dev("SocketManager disconnect 호출")

            // 재연결 차단
            shouldReconnect.set(false)
            isReconnecting.set(false)
            reconnectJob?.cancel()
            currentReconnectAttempts = 0

            // 하트비트 중지
            stopHeartbeat()

            connectionState.value = ConnectionState.DISCONNECTING

            // STOMP DISCONNECT 프레임 전송
            if (webSocket != null && connectionState.value == ConnectionState.CONNECTED) {
                val disconnectFrame = buildStompFrame(
                    command = StompCommand.DISCONNECT,
                    headers = emptyMap(),
                    body = ""
                )
                webSocket?.send(disconnectFrame)
                Logger.dev("STOMP DISCONNECT 프레임 전송")
            }

            // WebSocket 연결 종료
            webSocket?.close(1000, "User disconnected")
            webSocket = null
            connectionState.value = ConnectionState.DISCONNECTED
            subscriptionMap.clear()

            Logger.dev("WebSocket 연결 해제 완료")
        } catch (e: Exception) {
            Logger.error("Error disconnecting WebSocket: ${e.message}")
        }
    }

    /**
     * 하트비트 헤더 파싱 및 설정
     */
    private fun parseHeartbeatHeader(heartbeatHeader: String) {
        try {
            val parts = heartbeatHeader.split(",")
            if (parts.size == 2) {
                val serverSend = parts[0].toLongOrNull() ?: 0L  // 서버가 보내는 간격
                val serverExpect = parts[1].toLongOrNull() ?: 0L // 서버가 기대하는 클라이언트 간격

                // 실제 사용할 하트비트 간격 계산
                // 클라이언트가 서버에게 보내는 간격: max(클라이언트 설정, 서버 기대값)
                if (serverExpect > 0 && clientHeartbeatInterval > 0) {
                    clientHeartbeatInterval = maxOf(clientHeartbeatInterval, serverExpect)
                } else if (serverExpect > 0) {
                    clientHeartbeatInterval = serverExpect
                } else {
                    clientHeartbeatInterval = 0 // 하트비트 비활성화
                }

                // 서버가 보내는 간격 설정
                serverHeartbeatInterval = if (serverSend > 0 && serverHeartbeatInterval > 0) {
                    maxOf(serverSend, serverHeartbeatInterval)
                } else if (serverSend > 0) {
                    serverSend
                } else {
                    0 // 하트비트 비활성화
                }

                Logger.dev("하트비트 협상 완료 - 클라이언트 송신: ${clientHeartbeatInterval}ms, 서버 송신: ${serverHeartbeatInterval}ms")
            }
        } catch (e: Exception) {
            Logger.error("하트비트 헤더 파싱 실패: ${e.message}")
        }
    }

    /**
     * 하트비트 시작
     */
    private fun startHeartbeat() {
        stopHeartbeat() // 기존 하트비트 중지

        // 클라이언트가 서버에게 보내는 하트비트
        if (clientHeartbeatInterval > 0) {
            heartbeatJob = coroutineScope.launch {
                while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                    try {
                        delay(clientHeartbeatInterval)
                        if (connectionState.value == ConnectionState.CONNECTED) {
                            webSocket?.send("\n") // 하트비트 프레임 (단순 개행)
                            Logger.dev("Client heartbeat sent")
                        }
                    } catch (e: Exception) {
                        Logger.error("하트비트 전송 실패: ${e.message}")
                        break
                    }
                }
            }
        }

        // 서버 하트비트 타임아웃 감지
        if (serverHeartbeatInterval > 0) {
            lastHeartbeatReceived = System.currentTimeMillis()
            val timeoutInterval = serverHeartbeatInterval * 2 // 타임아웃은 하트비트 간격의 2배

            heartbeatTimeoutJob = coroutineScope.launch {
                while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                    try {
                        delay(timeoutInterval)
                        val now = System.currentTimeMillis()
                        val timeSinceLastHeartbeat = now - lastHeartbeatReceived

                        if (timeSinceLastHeartbeat > timeoutInterval &&
                            connectionState.value == ConnectionState.CONNECTED) {
                            Logger.error("서버 하트비트 타임아웃 - 연결 재시작")
                            webSocket?.close(1000, "Heartbeat timeout")
                            break
                        }
                    } catch (e: Exception) {
                        Logger.error("하트비트 타임아웃 감지 실패: ${e.message}")
                        break
                    }
                }
            }
        }

        Logger.dev("하트비트 시작 완료")
    }

    /**
     * 하트비트 중지
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatTimeoutJob?.cancel()
        heartbeatTimeoutJob = null
        Logger.dev("하트비트 중지 완료")
    }

    /**
     * 자동 재연결 스케줄링 - 재연결 허용 여부 체크 추가
     */
    private fun scheduleReconnect() {
        // 재연결이 허용되지 않으면 스케줄링하지 않음
        if (!shouldReconnect.get()) {
            Logger.dev("재연결이 차단되어 있어 재연결 스케줄링 건너뜀")
            return
        }

        if (isReconnecting.get() || currentReconnectAttempts >= maxReconnectAttempts) {
            Logger.error("Max reconnect attempts reached or already reconnecting")
            return
        }

        isReconnecting.set(true)
        currentReconnectAttempts++

        reconnectJob = coroutineScope.launch {
            delay(reconnectDelay)
            // 재연결이 여전히 허용되고 연결이 끊어진 상태인지 다시 확인
            if (shouldReconnect.get() &&
                isReconnecting.get() &&
                connectionState.value == ConnectionState.DISCONNECTED) {
                Logger.dev("Attempting reconnect (${currentReconnectAttempts}/$maxReconnectAttempts)")
                connectInternal()
            } else {
                Logger.dev("재연결 조건이 맞지 않아 재연결 취소")
                isReconnecting.set(false)
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
     * 리소스 정리 - 완전한 정리
     */
    fun cleanup() {
        Logger.dev("SocketManager cleanup 시작")

        // 재연결 완전히 차단
        shouldReconnect.set(false)

        // 하트비트 중지
        stopHeartbeat()

        // 연결 해제
        disconnect()

        // 코루틴 정리
        coroutineScope.cancel()

        // 리소스 정리
        okHttpClient = null
        isInitialized = false

        // 콜백 정리
        onMessageReceived = null
        onJoinedReceived = null

        Logger.dev("SocketManager 완전 정리 완료")
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
            // 하트비트 수신 시간 업데이트
            if (text.trim() == "\n") {
                // 서버에서 오는 하트비트 (단순 개행 문자)
                SocketManager.lastHeartbeatReceived = System.currentTimeMillis()
                Logger.dev("Server heartbeat received")
                return
            }

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

                        // 서버에서 협상된 하트비트 값 파싱
                        val heartbeatHeader = stompMessage.headers["heart-beat"]
                        if (heartbeatHeader != null) {
                            SocketManager.parseHeartbeatHeader(heartbeatHeader)
                        }

                        // 하트비트 시작
                        SocketManager.startHeartbeat()

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
                            }
                        }
                    }

                    StompCommand.ERROR -> {
                        Logger.error("STOMP error: ${stompMessage.body}")
                        SocketManager.connectionState.value = ConnectionState.DISCONNECTED
                        // 재연결이 허용된 경우에만 스케줄링
                        if (SocketManager.shouldReconnect.get()) {
                            SocketManager.scheduleReconnect()
                        } else {

                        }
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

            // 사용자가 의도적으로 연결을 해제한 경우가 아닐 때만 재연결
            if (SocketManager.connectionState.value != ConnectionState.DISCONNECTING &&
                SocketManager.shouldReconnect.get()) {
                SocketManager.connectionState.value = ConnectionState.DISCONNECTED
                SocketManager.scheduleReconnect()
            } else {
                Logger.dev("의도적인 연결 해제이므로 재연결하지 않음")
                SocketManager.connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Logger.error("WebSocket failure: ${t.message}")
            SocketManager.webSocket = null
            SocketManager.connectionState.value = ConnectionState.DISCONNECTED

            // 재연결이 허용된 경우에만 스케줄링
            if (SocketManager.shouldReconnect.get()) {
                SocketManager.scheduleReconnect()
            } else {
                Logger.dev("재연결이 차단되어 있어 실패 시 재연결하지 않음")
            }
        }
    }
}