import Combine
import Foundation
import Network

// MARK: - STOMP Message Model
struct StompMessage {
    let command: String
    let headers: [String: String]
    let body: String
}

// MARK: - Connection State
enum ConnectionState {
    case disconnected
    case connecting
    case connected
    case disconnecting
}

// MARK: - SocketManager
/// SockJS + STOMP 프로토콜을 지원하는 WebSocket 연결 관리 클래스
class SocketManager: NSObject, ObservableObject {

    // MARK: - STOMP Commands
    private struct StompCommand {
        static let connect = "CONNECT"
        static let connected = "CONNECTED"
        static let subscribe = "SUBSCRIBE"
        static let unsubscribe = "UNSUBSCRIBE"
        static let send = "SEND"
        static let message = "MESSAGE"
        static let receipt = "RECEIPT"
        static let error = "ERROR"
        static let disconnect = "DISCONNECT"
    }

    // MARK: - Properties
    static let shared = SocketManager()

    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var isInitialized = false

    @Published private(set) var connectionState = ConnectionState.disconnected

    private var subscriptionMap: [String: String] = [:]
    private var messageSubjectId = 0
    private let messageSubject = PassthroughSubject<StompMessage, Never>()
    private var cancellables = Set<AnyCancellable>()

    // 재연결 관련
    private var isReconnecting = false
    private var shouldReconnect = true
    private var reconnectTask: Task<Void, Never>?
    private var reconnectDelay: TimeInterval = 3.0
    private var maxReconnectAttempts = 5
    private var currentReconnectAttempts = 0

    // 하트비트 관련
    private var heartbeatTask: Task<Void, Never>?
    private var clientHeartbeatInterval: TimeInterval = 10.0
    private var serverHeartbeatInterval: TimeInterval = 10.0
    private var lastHeartbeatReceived: TimeInterval = 0
    private var heartbeatTimeoutTask: Task<Void, Never>?

    // 설정값
    private var serverUrl: String {
        guard let url = URL(string: Constants.baseURL),
            let host = url.host
        else {
            return ""
        }
        return "wss://\(host)/ws"
    }
    private var userId = Constants.getUserId()
    private var branchId = Constants.getBranchId()

    // 콜백
    private var onMessageReceived: ((ChatMessage) -> Void)?
    private var onConnected: ((String) -> Void)?

    // MARK: - Initialization
    override private init() {
        super.init()
    }

    /**
     * SocketManager 초기화
     */
    func initialize() {
        guard !isInitialized else { return }

        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 0  // WebSocket은 무제한

        urlSession = URLSession(
            configuration: configuration,
            delegate: self,
            delegateQueue: nil
        )
        isInitialized = true
        shouldReconnect = true

        Logger.dev("SocketManager initialized")
    }

    // MARK: - Connection Management

    /**
     * WebSocket 연결
     */
    func connect(
        onDmMessage: ((ChatMessage) -> Void)? = nil,
        onConnected: ((String) -> Void)? = nil
    ) {
        guard isInitialized else {
            Logger.error(
                "SocketManager not initialized. Call initialize() first."
            )
            return
        }

        self.onMessageReceived = onDmMessage
        self.onConnected = onConnected

        guard connectionState != .connected && connectionState != .connecting
        else {
            Logger.dev("WebSocket already connected or connecting")
            return
        }

        shouldReconnect = true
        connectInternal()
    }

    /**
     * 내부 연결 메서드
     */
    private func connectInternal() {
        DispatchQueue.main.async {
            self.connectionState = .connecting
        }

        let wsUrl = buildSockJSUrl(serverUrl)
        guard let url = URL(string: wsUrl) else {
            Logger.error("Invalid WebSocket URL: \(wsUrl)")
            connectionState = .disconnected
            return
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 30

        webSocketTask = urlSession?.webSocketTask(with: request)
        webSocketTask?.resume()

        // 메시지 수신 시작
        receiveMessage()

        Logger.dev("WebSocket connecting to: \(wsUrl)")
    }

    /**
     * SockJS WebSocket URL 생성
     */
    private func buildSockJSUrl(_ baseUrl: String) -> String {
        if baseUrl.hasSuffix("/websocket") {
            return baseUrl
        } else if baseUrl.hasSuffix("/") {
            return "\(baseUrl)websocket"
        } else {
            return "\(baseUrl)/websocket"
        }
    }

    // MARK: - Message Handling

    /**
     * WebSocket 메시지 수신
     */
    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            switch result {
            case .success(let message):
                self?.handleWebSocketMessage(message)
                // 다음 메시지 수신 대기
                self?.receiveMessage()

            case .failure(let error):
                Logger.error(
                    "WebSocket receive error: \(error.localizedDescription)"
                )
                self?.handleConnectionClosed()
            }
        }
    }

    /**
     * WebSocket 메시지 처리
     */
    private func handleWebSocketMessage(
        _ message: URLSessionWebSocketTask.Message
    ) {
        switch message {
        case .string(let text):
            // 하트비트 체크
            if text == "\n" {
                // 수정: timeIntervalSince1970 사용
                lastHeartbeatReceived = Date().timeIntervalSince1970
                Logger.dev("Server heartbeat received")
                return
            }

            Logger.dev("WebSocket message received: \(text)")

            if let stompMessage = parseStompFrame(text) {
                DispatchQueue.main.async {
                    self.messageSubject.send(stompMessage)
                }

                handleStompMessage(stompMessage)
            }

        case .data(let data):
            Logger.dev("WebSocket binary message received: \(data.count) bytes")

        @unknown default:
            Logger.error("Unknown WebSocket message type")
        }
    }

    /**
     * STOMP 메시지 처리
     */
    private func handleStompMessage(_ message: StompMessage) {
        switch message.command {
        case StompCommand.connected:
            DispatchQueue.main.async {
                self.connectionState = .connected
                self.isReconnecting = false
                self.currentReconnectAttempts = 0
            }

            Logger.dev("STOMP connected successfully")

            // 서버에서 협상된 하트비트 값 파싱
            if let heartbeatHeader = message.headers["heart-beat"] {
                parseHeartbeatHeader(heartbeatHeader)
            }

            // 하트비트 시작
            startHeartbeat()

            // 연결 완료 후 자동 구독 및 방 참가
            Task {
                await autoSubscribeAndJoin()
            }

            DispatchQueue.main.async {
                self.onConnected?("Connect")
            }

        case StompCommand.message:
            let destination = message.headers["destination"]
            Logger.dev(
                "STOMP message received for destination: \(destination ?? "unknown")"
            )

            // DM 메시지 처리
            switch destination {
            default:
                // 기타 메시지 처리
                break
            }

        case StompCommand.error:
            Logger.error("STOMP error: \(message.body)")
            DispatchQueue.main.async {
                self.connectionState = .disconnected
            }

            if shouldReconnect {
                scheduleReconnect()
            }

        case StompCommand.receipt:
            Logger.dev(
                "STOMP receipt: \(message.headers["receipt-id"] ?? "unknown")"
            )

        default:
            break
        }
    }

    // MARK: - STOMP Protocol

    /**
     * STOMP CONNECT 프레임 전송
     */
    private func sendConnectFrame() {
        let connectFrame = buildStompFrame(
            command: StompCommand.connect,
            headers: [
                "accept-version": "1.0,1.1,1.2",
                "heart-beat":
                    "\(Int(clientHeartbeatInterval * 1000)),\(Int(serverHeartbeatInterval * 1000))",
                "host": String(
                    serverUrl.dropFirst(6).split(separator: "/").first ?? ""
                ),
                "login": String(userId),
            ],
            body: ""
        )

        sendFrame(connectFrame)
        Logger.dev(
            "STOMP CONNECT frame sent with heartbeat: \(Int(clientHeartbeatInterval * 1000)),\(Int(serverHeartbeatInterval * 1000))"
        )
    }

    /**
     * 자동 구독 및 방 참가
     */
    private func autoSubscribeAndJoin() async {
        // 입/출 구독
        subscribe("/user/queue/dm/joined") { message in
            Logger.dev("user/queue/dm/joined: \(message)")
        }

        // DM 메시지 구독 - ChatMessage 객체로 파싱
        subscribe("/user/\(userId)/queue/messages") { [weak self] message in
            Logger.dev("user/\(self?.userId ?? 0)/queue/messages: \(message)")
            self?.parseAndDeliverChatMessage(message)
        }

        // 방 참가
        joinDmRoom()
    }

    /**
     * JSON 메시지를 ChatMessage 객체로 파싱하여 전달
     */
    private func parseAndDeliverChatMessage(_ jsonMessage: String) {
        do {
            guard let data = jsonMessage.data(using: .utf8) else {
                Logger.error("ChatMessage JSON 데이터 변환 실패")
                return
            }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let chatMessage = try decoder.decode(ChatMessage.self, from: data)

            Logger.dev("ChatMessage 파싱 성공: \(chatMessage.messageId)")
            DispatchQueue.main.async {
                self.onMessageReceived?(chatMessage)
            }

        } catch {
            Logger.error(
                "ChatMessage JSON 파싱 실패: \(error.localizedDescription)"
            )
            Logger.error("원본 메시지: \(jsonMessage)")
        }
    }

    /**
     * 구독
     */
    @discardableResult
    func subscribe(
        _ destination: String,
        onMessage: @escaping (String) -> Void = { _ in }
    ) -> String? {
        guard connectionState == .connected else {
            Logger.error("Cannot subscribe - WebSocket not connected")
            return nil
        }

        messageSubjectId += 1
        let subscriptionId = "sub-\(messageSubjectId)"
        subscriptionMap[subscriptionId] = destination

        let subscribeFrame = buildStompFrame(
            command: StompCommand.subscribe,
            headers: [
                "id": subscriptionId,
                "destination": destination,
            ],
            body: ""
        )

        sendFrame(subscribeFrame)
        Logger.dev("Subscribed to \(destination) with id: \(subscriptionId)")

        // 특정 구독에 대한 메시지 처리
        messageSubject
            .filter { $0.command == StompCommand.message }
            .filter { $0.headers["subscription"] == subscriptionId }
            .sink { message in
                onMessage(message.body)
            }
            .store(in: &cancellables)

        return subscriptionId
    }

    /**
     * 구독 해제
     */
    func unsubscribe(_ subscriptionId: String) {
        guard connectionState == .connected else {
            Logger.error("Cannot unsubscribe - WebSocket not connected")
            return
        }

        let unsubscribeFrame = buildStompFrame(
            command: StompCommand.unsubscribe,
            headers: ["id": subscriptionId],
            body: ""
        )

        sendFrame(unsubscribeFrame)
        subscriptionMap.removeValue(forKey: subscriptionId)
        Logger.dev("Unsubscribed from subscription: \(subscriptionId)")
    }

    /**
     * 메시지 전송
     */
    func sendMessage(
        _ destination: String,
        message: String,
        headers: [String: String] = [:]
    ) {
        guard connectionState == .connected else {
            Logger.error("Cannot send message - WebSocket not connected")
            return
        }

        var sendHeaders = headers
        sendHeaders["destination"] = destination

        let frameToSend = buildStompFrame(
            command: StompCommand.send,
            headers: sendHeaders,
            body: message
        )

        sendFrame(frameToSend)
        Logger.dev("Message sent to \(destination)")
    }

    /**
     * JSON 메시지 전송
     */
    func sendJsonMessage(
        _ destination: String,
        jsonObject: [String: Any],
        headers: [String: String] = [:]
    ) {
        do {
            let jsonData = try JSONSerialization.data(
                withJSONObject: jsonObject
            )
            guard let jsonString = String(data: jsonData, encoding: .utf8)
            else {
                Logger.error("JSON 문자열 변환 실패")
                return
            }

            var jsonHeaders = headers
            jsonHeaders["content-type"] = "application/json"

            sendMessage(destination, message: jsonString, headers: jsonHeaders)

        } catch {
            Logger.error("JSON 직렬화 실패: \(error.localizedDescription)")
        }
    }

    /**
     * DM 방 참가
     */
    func joinDmRoom() {
        let joinMessage: [String: Any] = [
            "userId": userId,
            "branchId": branchId,
        ]

        sendJsonMessage("/app/dm/native/join", jsonObject: joinMessage)
        Logger.dev(
            "DM room join request sent - userId: \(userId), branchId: \(branchId)"
        )
    }

    /**
     * DM 메시지 전송
     */
    func sendDmMessage(_ content: String) {
        let messageJson: [String: Any] = [
            "userId": userId,
            "branchId": branchId,
            "content": content,
        ]

        sendJsonMessage("/app/dm/native/send", jsonObject: messageJson)
        Logger.dev("DM message sent: \(content)")
    }

    // MARK: - Frame Building & Parsing

    /**
     * STOMP 프레임 생성
     */
    private func buildStompFrame(
        command: String,
        headers: [String: String],
        body: String
    ) -> String {
        var frame = command + "\n"

        for (key, value) in headers {
            frame += "\(key):\(value)\n"
        }

        frame += "\n" + body + "\u{0000}"
        return frame
    }

    /**
     * STOMP 프레임 파싱
     */
    private func parseStompFrame(_ frameText: String) -> StompMessage? {
        let lines = frameText.components(separatedBy: "\n")
        guard !lines.isEmpty else { return nil }

        let command = lines[0].trimmingCharacters(in: .whitespaces)
        var headers: [String: String] = [:]
        var bodyStartIndex = 1

        // 헤더 파싱
        for i in 1..<lines.count {
            let line = lines[i]
            if line.trimmingCharacters(in: .whitespaces).isEmpty {
                bodyStartIndex = i + 1
                break
            }

            if let colonIndex = line.firstIndex(of: ":") {
                let key = String(line[..<colonIndex]).trimmingCharacters(
                    in: .whitespaces
                )
                let value = String(line[line.index(after: colonIndex)...])
                    .trimmingCharacters(in: .whitespaces)
                headers[key] = value
            }
        }

        // 바디 파싱
        let body: String
        if bodyStartIndex < lines.count {
            body = lines[bodyStartIndex...].joined(separator: "\n")
                .replacingOccurrences(of: "\u{0000}", with: "")
        } else {
            body = ""
        }

        return StompMessage(command: command, headers: headers, body: body)
    }

    /**
     * 프레임 전송
     */
    private func sendFrame(_ frame: String) {
        let message = URLSessionWebSocketTask.Message.string(frame)
        webSocketTask?.send(message) { error in
            if let error = error {
                Logger.error("Frame 전송 실패: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Heartbeat Management

    /**
     * 하트비트 헤더 파싱 및 설정
     */
    private func parseHeartbeatHeader(_ heartbeatHeader: String) {
        let parts = heartbeatHeader.components(separatedBy: ",")
        guard parts.count == 2,
            let serverSend = Double(parts[0]),
            let serverExpect = Double(parts[1])
        else {
            Logger.error("하트비트 헤더 파싱 실패: \(heartbeatHeader)")
            return
        }
        Logger.dev("하트비트 헤더 파싱 성공: \(heartbeatHeader)")

        // 실제 사용할 하트비트 간격 계산
        if serverExpect > 0 && clientHeartbeatInterval > 0 {
            clientHeartbeatInterval = max(
                clientHeartbeatInterval,
                serverExpect / 1000.0
            )
        } else if serverExpect > 0 {
            clientHeartbeatInterval = serverExpect / 1000.0
        } else {
            clientHeartbeatInterval = 0
        }

        if serverSend > 0 && serverHeartbeatInterval > 0 {
            serverHeartbeatInterval = max(
                serverSend / 1000.0,
                serverHeartbeatInterval
            )
        } else if serverSend > 0 {
            serverHeartbeatInterval = serverSend / 1000.0
        } else {
            serverHeartbeatInterval = 0
        }

        Logger.dev(
            "하트비트 협상 완료 - 클라이언트 송신: \(clientHeartbeatInterval)s, 서버 송신: \(serverHeartbeatInterval)s"
        )
    }

    /**
     * 하트비트 시작
     */
    private func startHeartbeat() {
        stopHeartbeat()

        // 클라이언트가 서버에게 보내는 하트비트
        if clientHeartbeatInterval > 0 {
            heartbeatTask = Task {
                while !Task.isCancelled && connectionState == .connected {
                    try? await Task.sleep(
                        nanoseconds: UInt64(
                            clientHeartbeatInterval * 1_000_000_000
                        )
                    )

                    if connectionState == .connected {
                        let heartbeatMessage = URLSessionWebSocketTask.Message
                            .string("\n")
                        webSocketTask?.send(heartbeatMessage) { error in
                            if let error = error {
                                Logger.error(
                                    "하트비트 전송 실패: \(error.localizedDescription)"
                                )
                            } else {
                                Logger.dev("Client heartbeat sent")
                            }
                        }
                    }
                }
            }
        }

        // 서버 하트비트 타임아웃 감지
        if serverHeartbeatInterval > 0 {
            // 수정: timeIntervalSince1970 사용
            lastHeartbeatReceived = Date().timeIntervalSince1970
            let timeoutInterval = serverHeartbeatInterval * 2.5  // 여유시간 추가

            heartbeatTimeoutTask = Task {
                while !Task.isCancelled && connectionState == .connected {
                    try? await Task.sleep(
                        nanoseconds: UInt64(timeoutInterval * 1_000_000_000)
                    )

                    if connectionState == .connected {
                        let now = Date().timeIntervalSince1970
                        let timeSinceLastHeartbeat = now - lastHeartbeatReceived

                        Logger.dev(
                            "하트비트 체크 - 마지막 수신: \(timeSinceLastHeartbeat)초 전, 타임아웃: \(timeoutInterval)초"
                        )

                        if timeSinceLastHeartbeat > timeoutInterval {
                            Logger.error(
                                "서버 하트비트 타임아웃 - 연결 재시작 (마지막 수신: \(timeSinceLastHeartbeat)초 전)"
                            )
                            await MainActor.run {
                                webSocketTask?.cancel(
                                    with: .normalClosure,
                                    reason: "Heartbeat timeout".data(
                                        using: .utf8
                                    )
                                )
                            }
                            break
                        }
                    }
                }
            }
        }

        Logger.dev("하트비트 시작 완료")
    }

    /**
     * 하트비트 중지
     */
    private func stopHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
        heartbeatTimeoutTask?.cancel()
        heartbeatTimeoutTask = nil
        Logger.dev("하트비트 중지 완료")
    }

    // MARK: - Connection Management

    /**
     * 연결 해제
     */
    func disconnect() {
        Logger.dev("SocketManager disconnect 호출")

        // 재연결 차단
        shouldReconnect = false
        isReconnecting = false
        reconnectTask?.cancel()
        currentReconnectAttempts = 0

        // 하트비트 중지
        stopHeartbeat()

        DispatchQueue.main.async {
            self.connectionState = .disconnecting
        }

        // STOMP DISCONNECT 프레임 전송
        if webSocketTask != nil && connectionState == .connected {
            let disconnectFrame = buildStompFrame(
                command: StompCommand.disconnect,
                headers: [:],
                body: ""
            )
            sendFrame(disconnectFrame)
            Logger.dev("STOMP DISCONNECT 프레임 전송")
        }

        // WebSocket 연결 종료
        webSocketTask?.cancel(
            with: .normalClosure,
            reason: "User disconnected".data(using: .utf8)
        )
        webSocketTask = nil

        DispatchQueue.main.async {
            self.connectionState = .disconnected
        }

        subscriptionMap.removeAll()
        Logger.dev("WebSocket 연결 해제 완료")
    }

    /**
     * 연결 상태 확인
     */
    func isConnected() -> Bool {
        return connectionState == .connected
    }

    /**
     * 자동 재연결 스케줄링
     */
    private func scheduleReconnect() {
        guard shouldReconnect else {
            Logger.dev("재연결이 차단되어 있어 재연결 스케줄링 건너뜀")
            return
        }

        guard !isReconnecting && currentReconnectAttempts < maxReconnectAttempts
        else {
            Logger.error(
                "Max reconnect attempts reached or already reconnecting"
            )
            return
        }

        isReconnecting = true
        currentReconnectAttempts += 1

        reconnectTask = Task {
            try? await Task.sleep(
                nanoseconds: UInt64(reconnectDelay * 1_000_000_000)
            )

            if shouldReconnect && isReconnecting
                && connectionState == .disconnected
            {
                Logger.dev(
                    "Attempting reconnect (\(currentReconnectAttempts)/\(maxReconnectAttempts))"
                )
                connectInternal()
            } else {
                Logger.dev("재연결 조건이 맞지 않아 재연결 취소")
                isReconnecting = false
            }
        }
    }

    /**
     * 연결 닫힘 처리
     */
    private func handleConnectionClosed() {
        webSocketTask = nil

        // 사용자가 의도적으로 연결을 해제한 경우가 아닐 때만 재연결
        if connectionState != .disconnecting && shouldReconnect {
            DispatchQueue.main.async {
                self.connectionState = .disconnected
            }
            scheduleReconnect()
        } else {
            Logger.dev("의도적인 연결 해제이므로 재연결하지 않음")
            DispatchQueue.main.async {
                self.connectionState = .disconnected
            }
        }
    }

    /**
     * 리소스 정리
     */
    func cleanup() {
        Logger.dev("SocketManager cleanup 시작")

        // 재연결 완전히 차단
        shouldReconnect = false

        // 하트비트 중지
        stopHeartbeat()

        // 연결 해제
        disconnect()

        // 리소스 정리
        cancellables.removeAll()
        urlSession = nil
        isInitialized = false

        // 콜백 정리
        onMessageReceived = nil
        onConnected = nil

        Logger.dev("SocketManager 완전 정리 완료")
    }
}

// MARK: - URLSessionWebSocketDelegate
extension SocketManager: URLSessionWebSocketDelegate {

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        Logger.dev("WebSocket opened, sending STOMP CONNECT")
        sendConnectFrame()
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        let reasonString = reason?.string ?? "Unknown"
        Logger.dev("WebSocket closed: \(closeCode.rawValue) - \(reasonString)")
        handleConnectionClosed()
    }
}

// MARK: - Data Extension
extension Data {
    fileprivate var string: String {
        return String(data: self, encoding: .utf8) ?? ""
    }
}
