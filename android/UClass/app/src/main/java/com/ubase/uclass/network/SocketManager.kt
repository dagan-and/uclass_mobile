package com.ubase.uclass.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ubase.uclass.network.response.ChatMessage
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val subscriptionMap = mutableMapOf<String, String>()
    private val messageFlow = MutableSharedFlow<StompMessage>(replay = 0, extraBufferCapacity = 100)
    private val isReconnecting = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)
    private var reconnectJob: Job? = null

    // 안전한 코루틴 스코프 생성 - SupervisorJob과 예외 핸들러 추가
    private val coroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.error("SocketManager 코루틴 예외: ${throwable.message}")
        }
    )

    // 하트비트 관련 - 안전성 개선
    private var heartbeatJob: Job? = null
    private var clientHeartbeatInterval = 20000L
    private var serverHeartbeatInterval = 20000L
    private var lastHeartbeatReceived = 0L
    private var heartbeatTimeoutJob: Job? = null
    private val heartbeatMutex = Mutex() // 하트비트 동기화를 위한 뮤텍스

    // JSON 파싱을 위한 Gson 인스턴스
    private val gson = Gson()
    // 설정값
    private val serverUrl: String
        get() {
            val uri = java.net.URI(Constants.umanagerURL)
            val host = uri.host ?: return ""
            return "wss://$host/ws"
        }
    private var userId: Int = Constants.getUserId()
    private var branchId: Int = Constants.getBranchId()
    private var reconnectDelay = 3000L
    private var maxReconnectAttempts = 5
    private var currentReconnectAttempts = 0

    // 콜백
    private var onMessageReceived: ((ChatMessage) -> Unit)? = null
    private var onConnectionFailed: (() -> Unit)? = null // 연결 실패 콜백 추가

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
    fun initialize(userId : Int , branchId : Int) {
        this.userId = userId
        this.branchId = branchId
        if (!isInitialized) {
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            isInitialized = true
            shouldReconnect.set(true)
            Logger.dev("SocketManager initialized")
        }
    }

    /**
     * WebSocket 연결
     */
    fun connect(
        onDmMessage: ((ChatMessage) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        if (!isInitialized) {
            Logger.error("SocketManager not initialized. Call initialize() first.")
            onFailed?.invoke()
            return
        }

        this.onMessageReceived = onDmMessage
        this.onConnectionFailed = onFailed

        if (connectionState.value == ConnectionState.CONNECTED ||
            connectionState.value == ConnectionState.CONNECTING) {
            Logger.dev("WebSocket already connected or connecting")
            return
        }

        shouldReconnect.set(true)
        currentReconnectAttempts = 0 // 재연결 카운터 초기화
        connectInternal()
    }

    /**
     * 내부 연결 메서드
     */
    private fun connectInternal() {
        try {
            connectionState.value = ConnectionState.CONNECTING

            val wsUrl = buildSockJSUrl(serverUrl)
            val request = Request.Builder()
                .addHeader("JWT-TOKEN", Constants.jwtToken)
                .url(wsUrl)
                .build()

            webSocket = okHttpClient?.newWebSocket(request, StompWebSocketListener())
            Logger.dev("WebSocket connecting to: $wsUrl")

        } catch (e: Exception) {
            Logger.error("WebSocket connection failed: ${e.message}")
            connectionState.value = ConnectionState.DISCONNECTED
            if (shouldReconnect.get()) {
                scheduleReconnect()
            }
        }
    }

    /**
     * SockJS WebSocket URL 생성
     */
    private fun buildSockJSUrl(baseUrl: String): String {
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
                "login" to userId.toString(),
                "JWT-TOKEN" to Constants.jwtToken
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
            try {
                // 입/출 구독
                subscribe("/user/queue/dm/joined") { message ->
                    Logger.dev("user/queue/dm/joined: $message")
                }

                // DM 메시지 구독
                subscribe("/user/$userId/queue/messages") { message ->
                    Logger.dev("user/$userId/queue/messages: $message")
                    parseAndDeliverChatMessage(message)
                }

                // 방 참가
                joinDmRoom()
            } catch (e: Exception) {
                Logger.error("자동 구독 및 방 참가 실패: ${e.message}")
            }
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

        send(
            destination = "/app/dm/join",
            body = joinMessage.toString()
        )
        Logger.dev("DM 방 참가 메시지 전송: $joinMessage")
    }

    /**
     * DM 메시지 전송
     */
    fun sendDmMessage(content: String) {
        val dmMessage = JSONObject().apply {
            put("senderId", userId)
            put("senderType", "STUDENT")
            put("receiverId", branchId)
            put("receiverType", "admin")
            put("content", content)
        }

        send(
            destination = "/app/dm/send",
            body = dmMessage.toString()
        )
        Logger.dev("DM 메시지 전송: $dmMessage")
    }

    /**
     * 구독 (토픽/큐 구독)
     */
    private fun subscribe(destination: String, callback: (String) -> Unit) {
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
        Logger.dev("Subscribed to: $destination (ID: $subscriptionId)")

        // 메시지 수신 처리
        coroutineScope.launch {
            messageFlow.collect { stompMessage ->
                if (stompMessage.command == StompCommand.MESSAGE &&
                    stompMessage.headers["subscription"] == subscriptionId) {
                    callback(stompMessage.body)
                }
            }
        }
    }

    /**
     * 구독 취소
     */
    private fun unsubscribe(subscriptionId: String) {
        val unsubscribeFrame = buildStompFrame(
            command = StompCommand.UNSUBSCRIBE,
            headers = mapOf("id" to subscriptionId),
            body = ""
        )

        webSocket?.send(unsubscribeFrame)
        subscriptionMap.remove(subscriptionId)
        Logger.dev("Unsubscribed from: $subscriptionId")
    }

    /**
     * 메시지 전송
     */
    private fun send(destination: String, body: String) {
        val sendFrame = buildStompFrame(
            command = StompCommand.SEND,
            headers = mapOf(
                "destination" to destination,
                "content-type" to "application/json"
            ),
            body = body
        )

        webSocket?.send(sendFrame)
        Logger.dev("Message sent to: $destination")
    }

    /**
     * STOMP 프레임 빌드
     */
    private fun buildStompFrame(command: String, headers: Map<String, String>, body: String): String {
        val frame = StringBuilder()
        frame.append(command).append("\n")

        headers.forEach { (key, value) ->
            frame.append("$key:$value\n")
        }

        frame.append("\n")
        frame.append(body)
        frame.append("\u0000") // null terminator

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
            var bodyStartIndex = -1

            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isEmpty()) {
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

            val body = if (bodyStartIndex >= 0 && bodyStartIndex < lines.size) {
                lines.subList(bodyStartIndex, lines.size)
                    .joinToString("\n")
                    .replace("\u0000", "")
                    .trim()
            } else {
                ""
            }

            return StompMessage(command, headers, body)
        } catch (e: Exception) {
            Logger.error("STOMP frame parsing error: ${e.message}")
            return null
        }
    }

    /**
     * 연결 해제
     */
    fun disconnect() {
        Logger.dev("Disconnecting WebSocket")

        // 재연결 차단
        shouldReconnect.set(false)

        // 재연결 작업 취소
        reconnectJob?.cancel()
        reconnectJob = null
        isReconnecting.set(false)

        // 하트비트 중지
        stopHeartbeat()

        // 연결 상태 업데이트
        connectionState.value = ConnectionState.DISCONNECTING

        try {
            // 모든 구독 취소
            subscriptionMap.keys.toList().forEach { subscriptionId ->
                unsubscribe(subscriptionId)
            }

            // DISCONNECT 프레임 전송
            val disconnectFrame = buildStompFrame(
                command = StompCommand.DISCONNECT,
                headers = mapOf("receipt" to "disconnect-${System.currentTimeMillis()}"),
                body = ""
            )
            webSocket?.send(disconnectFrame)

            // 짧은 지연 후 WebSocket 종료
            coroutineScope.launch {
                delay(100)
                webSocket?.close(1000, "Normal closure")
                webSocket = null
                connectionState.value = ConnectionState.DISCONNECTED
                Logger.dev("WebSocket disconnected")
            }
        } catch (e: Exception) {
            Logger.error("Error during disconnect: ${e.message}")
            webSocket?.close(1000, "Error during disconnect")
            webSocket = null
            connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * 하트비트 헤더 파싱 및 설정
     */
    private fun parseHeartbeatHeader(heartbeatHeader: String) {
        try {
            val values = heartbeatHeader.split(",")
            if (values.size == 2) {
                val serverSendInterval = values[0].toLongOrNull() ?: 0
                val serverReceiveInterval = values[1].toLongOrNull() ?: 0

                // 실제 사용할 하트비트 간격 계산
                clientHeartbeatInterval = if (serverReceiveInterval > 0) {
                    maxOf(clientHeartbeatInterval, serverReceiveInterval)
                } else {
                    0
                }

                serverHeartbeatInterval = if (serverSendInterval > 0) {
                    maxOf(serverHeartbeatInterval, serverSendInterval)
                } else {
                    0
                }

                Logger.dev("하트비트 협상 완료 - Client: $clientHeartbeatInterval ms, Server: $serverHeartbeatInterval ms")
            }
        } catch (e: Exception) {
            Logger.error("하트비트 헤더 파싱 실패: ${e.message}")
        }
    }

    /**
     * 하트비트 시작 - 개선된 버전
     */
    private fun startHeartbeat() {
        // 기존 하트비트 중지
        stopHeartbeat()

        // 하트비트가 필요한 경우에만 시작
        if (clientHeartbeatInterval <= 0 && serverHeartbeatInterval <= 0) {
            Logger.dev("하트비트가 비활성화되어 있음")
            return
        }

        heartbeatJob = coroutineScope.launch {
            heartbeatMutex.withLock {
                try {
                    // 클라이언트 -> 서버 하트비트 전송
                    if (clientHeartbeatInterval > 0) {
                        launch {
                            try {
                                while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                                    delay(clientHeartbeatInterval)
                                    if (connectionState.value == ConnectionState.CONNECTED) {
                                        webSocket?.send("\n")
                                        Logger.dev("Client heartbeat sent")
                                    }
                                }
                            } catch (e: CancellationException) {
                                Logger.dev("하트비트 전송이 취소됨")
                            } catch (e: Exception) {
                                Logger.error("하트비트 전송 예외: ${e.message}")
                            }
                        }
                    }

                    // 서버 -> 클라이언트 하트비트 타임아웃 감지
                    if (serverHeartbeatInterval > 0) {
                        // 초기 하트비트 수신 시간 설정
                        lastHeartbeatReceived = System.currentTimeMillis()

                        heartbeatTimeoutJob = launch {
                            try {
                                val checkInterval = serverHeartbeatInterval / 2 // 절반 간격으로 체크
                                val timeoutInterval = serverHeartbeatInterval * 2 // 2배를 타임아웃으로 설정

                                while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                                    delay(checkInterval)

                                    val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived

                                    if (timeSinceLastHeartbeat > timeoutInterval &&
                                        connectionState.value == ConnectionState.CONNECTED) {
                                        Logger.error("서버 하트비트 타임아웃 감지 - 연결 재시작 (${timeSinceLastHeartbeat}ms)")

                                        // WebSocket 안전하게 종료
                                        webSocket?.close(1000, "Heartbeat timeout")
                                        break
                                    }
                                }
                            } catch (e: CancellationException) {
                                Logger.dev("하트비트 타임아웃 감지 작업이 취소됨")
                            } catch (e: Exception) {
                                Logger.error("하트비트 타임아웃 감지 예외: ${e.message}")
                            }
                        }
                    }

                    Logger.dev("하트비트 시작 완료")
                } catch (e: Exception) {
                    Logger.error("하트비트 시작 실패: ${e.message}")
                }
            }
        }
    }

    /**
     * 하트비트 중지 - 안전한 중지 보장
     */
    private fun stopHeartbeat() {
        try {
            heartbeatJob?.let { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            heartbeatJob = null

            heartbeatTimeoutJob?.let { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            heartbeatTimeoutJob = null

            Logger.dev("하트비트 중지 완료")
        } catch (e: Exception) {
            Logger.error("하트비트 중지 중 오류: ${e.message}")
        }
    }

    /**
     * 자동 재연결 스케줄링
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) {
            Logger.dev("재연결이 차단되어 있어 재연결 스케줄링 건너뜀")
            return
        }

        if (isReconnecting.get()) {
            Logger.dev("이미 재연결 중입니다")
            return
        }

        if (currentReconnectAttempts >= maxReconnectAttempts) {
            Logger.error("최대 재연결 시도 횟수 초과 (${currentReconnectAttempts}/$maxReconnectAttempts)")
            // 연결 실패 콜백 호출
            coroutineScope.launch(Dispatchers.Main) {
                onConnectionFailed?.invoke()
            }
            return
        }

        isReconnecting.set(true)
        currentReconnectAttempts++

        reconnectJob = coroutineScope.launch {
            try {
                delay(reconnectDelay)

                if (shouldReconnect.get() &&
                    isReconnecting.get() &&
                    connectionState.value == ConnectionState.DISCONNECTED) {
                    Logger.dev("Attempting reconnect (${currentReconnectAttempts}/$maxReconnectAttempts)")
                    connectInternal()
                } else {
                    Logger.dev("재연결 조건이 맞지 않아 재연결 취소")
                    isReconnecting.set(false)
                }
            } catch (e: CancellationException) {
                Logger.dev("재연결 작업이 취소됨")
                isReconnecting.set(false)
            } catch (e: Exception) {
                Logger.error("재연결 스케줄링 오류: ${e.message}")
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
        try {
            coroutineScope.cancel()
        } catch (e: Exception) {
            Logger.error("코루틴 스코프 정리 실패: ${e.message}")
        }

        // 리소스 정리
        okHttpClient = null
        isInitialized = false

        // 콜백 정리
        onMessageReceived = null
        onConnectionFailed = null

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
            if (text == "\n") {
                SocketManager.lastHeartbeatReceived = System.currentTimeMillis()
                Logger.dev("Server heartbeat received")
                return
            }

            Logger.dev("WebSocket message received: $text")

            val message = SocketManager.parseStompFrame(text)
            message?.let { stompMessage ->
                SocketManager.coroutineScope.launch {
                    try {
                        SocketManager.messageFlow.emit(stompMessage)
                    } catch (e: Exception) {
                        Logger.error("메시지 플로우 emit 실패: ${e.message}")
                    }
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
                    }

                    StompCommand.ERROR -> {
                        Logger.error("STOMP error: ${stompMessage.body}")
                        SocketManager.connectionState.value = ConnectionState.DISCONNECTED
                        if (SocketManager.shouldReconnect.get()) {
                            SocketManager.scheduleReconnect()
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
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Logger.dev("WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.dev("WebSocket closed: $code - $reason")
            SocketManager.webSocket = null

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
            Logger.error("Response code: ${response?.code}")
            SocketManager.webSocket = null
            SocketManager.connectionState.value = ConnectionState.DISCONNECTED

            // 재연결이 불가능한 에러 코드 체크 (404, 401, 403 등)
            val shouldNotRetry = response?.code in listOf(400, 401, 403, 404, 405)

            if (shouldNotRetry) {
                Logger.error("재연결이 불가능한 에러 코드 (${response?.code}) - 즉시 연결 실패 처리")
                // 재연결 차단
                SocketManager.shouldReconnect.set(false)
                // 연결 실패 콜백 호출
                SocketManager.coroutineScope.launch(Dispatchers.Main) {
                    SocketManager.onConnectionFailed?.invoke()
                }
            } else if (SocketManager.shouldReconnect.get()) {
                SocketManager.scheduleReconnect()
            } else {
                Logger.dev("재연결이 차단되어 있어 실패 시 재연결하지 않음")
            }
        }
    }
}