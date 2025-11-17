package com.ubase.uclass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airbnb.lottie.parser.IntegerParser
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.network.SocketManager
import com.ubase.uclass.network.response.BaseData
import com.ubase.uclass.network.response.ChatInitData
import com.ubase.uclass.network.response.ChatMessage
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.presentation.ui.CustomAlertManager
import com.ubase.uclass.presentation.view.asBaseData
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 채팅 ViewModel - 소켓 연결 생명주기 관리
 */
class ChatViewModel : ViewModel() {

    // 채팅 초기화 상태
    private val _isChatInitialized = MutableStateFlow(false)
    val isChatInitialized: StateFlow<Boolean> = _isChatInitialized.asStateFlow()

    private val _isInitializingChat = MutableStateFlow(false)
    val isInitializingChat: StateFlow<Boolean> = _isInitializingChat.asStateFlow()

    // 메시지 리스트
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 메시지 입력 텍스트
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    // 새로 추가된 메시지 ID 추적
    private val _newlyAddedMessageIds = MutableStateFlow(setOf<String>())
    val newlyAddedMessageIds: StateFlow<Set<String>> = _newlyAddedMessageIds.asStateFlow()

    // 새 메시지 알림
    private val _showNewMessageAlert = MutableStateFlow(false)
    val showNewMessageAlert: StateFlow<Boolean> = _showNewMessageAlert.asStateFlow()

    // 스크롤 위치
    private val _isAtBottom = MutableStateFlow(true)
    val isAtBottom: StateFlow<Boolean> = _isAtBottom.asStateFlow()

    // 더 많은 메시지 로딩
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreMessages = MutableStateFlow(false)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()

    // 브랜치명 저장
    private val _branchName = MutableStateFlow("")
    val branchName: StateFlow<String> = _branchName.asStateFlow()

    // 자동 스크롤 트리거
    private val _shouldScrollToBottom = MutableStateFlow<Long>(0L)
    val shouldScrollToBottom: StateFlow<Long> = _shouldScrollToBottom.asStateFlow()

    private val _shouldExitChat = MutableStateFlow(false)
    val shouldExitChat: StateFlow<Boolean> = _shouldExitChat.asStateFlow()

    // 초기화 상태 관리
    private var isSocketConnected = false
    private var callbackId: String? = null
    private var pageCount = 0

    // ✅ 추가: 동시성 제어를 위한 Mutex
    private val messagesMutex = Mutex()

    // ✅ 추가: 처리 중인 메시지 ID 추적 (중복 방지)
    private val processingMessageIds = mutableSetOf<String>()

    init {
        Logger.dev("ChatViewModel 생성")
        setupNetworkCallbacks()
    }

    /**
     * 채팅 초기화 (API 호출 + WebSocket 연결)
     * onCreate에서 한 번만 호출됨
     */
    fun initializeChat(userId: String) {
        if (_isInitializingChat.value || _isChatInitialized.value) {
            Logger.dev("⚠️ 채팅 초기화 건너뜀 - 이미 진행중이거나 완료됨")
            return
        }

        viewModelScope.launch {
            _isInitializingChat.value = true
            Logger.dev("📱 [onCreate] 채팅 초기화 시작 - userId: $userId")

            try {
                // SocketManager 초기화 (한 번만)
                if (!isSocketConnected) {
                    SocketManager.initialize(Constants.getUserId(), Constants.getBranchId())
                }

                // NetworkAPI의 chatInit 호출
                NetworkAPI.chatInit(userId)
            } catch (e: Exception) {
                Logger.error("❌ 채팅 초기화 중 오류: ${e.message}")
                _isInitializingChat.value = false
                _isChatInitialized.value = true // 에러 시에도 UI 사용 가능
            }
        }
    }

    /**
     * 소켓 연결 종료
     * onStop 시점에 호출됨
     */
    fun disconnectSocket() {
        viewModelScope.launch {
            try {
                if (!isSocketConnected) {
                    Logger.dev("⚠️ 소켓이 이미 끊어져 있음 - 종료 생략")
                    return@launch
                }

                Logger.dev("⏸️ [onStop] 소켓 연결 종료 시작")

                // WebSocket 연결 해제
                SocketManager.disconnect()
                isSocketConnected = false

                Logger.dev("✅ 소켓 연결 종료 완료")
            } catch (e: Exception) {
                Logger.error("❌ 소켓 연결 종료 중 오류: ${e.message}")
            }
        }
    }

    /**
     * 소켓 재연결
     * onResume 시점에 호출됨
     */
    fun reconnectSocketIfNeeded() {
        viewModelScope.launch {
            try {
                Logger.dev("▶️ [onResume] 소켓 연결 상태 확인 시작")

                // 채팅이 초기화되지 않았으면 재연결 불필요
                if (!_isChatInitialized.value) {
                    Logger.dev("⚠️ 채팅이 초기화되지 않아 재연결 불필요")
                    return@launch
                }

                // 소켓이 이미 연결되어 있으면 재연결 불필요
                if (isSocketConnected) {
                    // 실제 SocketManager의 연결 상태도 확인
                    val isActuallyConnected = SocketManager.isConnected()
                    if (isActuallyConnected) {
                        Logger.dev("✅ 소켓이 이미 연결되어 있음 - 재연결 생략")
                        return@launch
                    } else {
                        Logger.dev("⚠️ 플래그는 연결 상태이나 실제로는 끊김 - 플래그 수정")
                        isSocketConnected = false
                    }
                }

                Logger.dev("🔄 소켓 재연결 시도")

                // 기존 소켓 정리 (혹시 모를 연결 잔여 처리)
                SocketManager.disconnect()

                // 잠시 대기 후 재연결
                delay(300)

                // WebSocket 재연결 및 메시지 수신 콜백 재설정
                connectWebSocket()

                Logger.dev("✅ 소켓 재연결 완료")
            } catch (e: Exception) {
                Logger.error("❌ 소켓 재연결 중 오류 발생: ${e.message}")
            }
        }
    }

    /**
     * 메시지 입력 텍스트 업데이트
     */
    fun updateMessageText(text: String) {
        _messageText.value = text
    }

    /**
     * 메시지 전송
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || !_isChatInitialized.value) {
            Logger.dev("⚠️ 메시지 전송 건너뜀 - 텍스트 비어있거나 초기화되지 않음")
            return
        }

        viewModelScope.launch {
            // WebSocket을 통한 메시지 전송
            SocketManager.sendDmMessage(text)

            // 새 메시지 생성 - 실제 API 스펙에 맞게 생성
            val currentTime = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val receiverId = Constants.getBranchId()
            val newMessage = ChatMessage(
                messageId = UUID.randomUUID().toString(),
                senderId = Constants.getUserId(),
                senderType = "STUDENT",
                senderName = "나",
                receiverId = receiverId,
                receiverType = "admin",
                receiverName = "관리자",
                branchId = receiverId,
                branchName = _branchName.value,
                content = text,
                isRead = false,
                readAt = null,
                sentAt = sdf.format(Date(currentTime)),
                roomId = "default_room"
            )

            // ✅ 수정: Mutex를 사용한 안전한 메시지 추가
            messagesMutex.withLock {
                // 메시지 리스트에 추가
                _messages.value += newMessage
            }

            // 새로 추가된 메시지로 표시
            _newlyAddedMessageIds.value += newMessage.messageId

            // 입력창 초기화
            _messageText.value = ""

            Logger.dev("📤 메시지 전송 완료: ${newMessage.messageId}")

            // 애니메이션 완료 후 새 메시지 상태 해제
            kotlinx.coroutines.delay(500)
            _newlyAddedMessageIds.value -= newMessage.messageId
        }
    }

    /**
     * ✅ 수정: WebSocket으로 받은 새 메시지 처리 (동시성 제어 강화)
     */
    private fun handleNewWebSocketMessage(newMessage: ChatMessage) {
        viewModelScope.launch {
            Logger.dev("📩 새로운 WebSocket 메시지 수신: ${newMessage.messageId}")

            // ✅ Mutex로 동시성 제어
            messagesMutex.withLock {
                // ✅ 1차 체크: 이미 처리 중인 메시지인지 확인
                if (processingMessageIds.contains(newMessage.messageId)) {
                    Logger.dev("⚠️ 이미 처리 중인 메시지 무시: ${newMessage.messageId}")
                    return@launch
                }

                // ✅ 2차 체크: 이미 추가된 메시지인지 확인
                val existingMessage = _messages.value.find { it.messageId == newMessage.messageId }
                if (existingMessage != null) {
                    Logger.dev("⚠️ 중복 메시지 무시: ${newMessage.messageId}")
                    return@launch
                }

                // ✅ 처리 중 목록에 추가
                processingMessageIds.add(newMessage.messageId)

                Logger.dev("✅ 메시지 리스트에 추가: ${newMessage.content}")

                // 메시지 리스트에 추가
                _messages.value += newMessage

                Logger.dev("📊 현재 메시지 개수: ${_messages.value.size}")
            }

            // ✅ Mutex 밖에서 처리 (UI 업데이트는 빠르게)
            // 새로 추가된 메시지로 표시
            _newlyAddedMessageIds.value += newMessage.messageId

            // 사용자가 최하단에 있으면 자동 스크롤 트리거
            if (_isAtBottom.value) {
                Logger.dev("📜 최하단에 있음 - 자동 스크롤 트리거")
                _shouldScrollToBottom.value = System.currentTimeMillis()
            } else {
                Logger.dev("⚠️ 최하단이 아님 - 새 메시지 알림 표시")
                // 최하단에 없으면 새 메시지 알림 표시
                _showNewMessageAlert.value = true
            }

            // 애니메이션 완료 후 새 메시지 상태 해제
            delay(500)
            _newlyAddedMessageIds.value -= newMessage.messageId

            // ✅ 처리 완료 후 목록에서 제거
            processingMessageIds.remove(newMessage.messageId)
        }
    }

    /**
     * 스크롤 위치가 최하단인지 업데이트
     */
    fun updateIsAtBottom(isAtBottom: Boolean) {
        _isAtBottom.value = isAtBottom
    }

    /**
     * 스크롤 위치 업데이트
     */
    fun updateScrollPosition(isAtBottom: Boolean) {
        _isAtBottom.value = isAtBottom
        if (isAtBottom) {
            _showNewMessageAlert.value = false
        }
    }

    /**
     * 새 메시지 알림 숨기기
     */
    fun hideNewMessageAlert() {
        _showNewMessageAlert.value = false
    }

    /**
     * 이전 메시지 로드
     */
    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMoreMessages.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            Logger.dev("🔄 더 많은 메시지 로드 시작")

            pageCount += 1

            try {
                NetworkAPI.chatMessage(
                    Constants.getUserId(),
                    Constants.getBranchId(),
                    pageCount,
                    30
                )

            } catch (e: Exception) {
                Logger.error("❌ 이전 메시지 로드 오류: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * NetworkAPI 콜백 설정
     */
    private fun setupNetworkCallbacks() {
        // API 응답을 위한 콜백 등록
        callbackId = "Chat_${System.currentTimeMillis()}"

        NetworkAPIManager.registerCallback(
            callbackId!!,
            object : NetworkAPIManager.NetworkCallback {
                override fun onResult(code: Int, result: Any?) {
                    viewModelScope.launch {
                        try {
                            when (code) {
                                NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT -> {
                                    // API 성공 후 소켓 연결
                                    result.asBaseData<ChatInitData>()?.let { response ->
                                        if (response.isSuccess) {
                                            Logger.dev("✅ 채팅 초기화 API 성공")

                                            response.data?.branchName?.let { branchName ->
                                                _branchName.value = branchName
                                            }

                                            response.data?.hasMore?.let { hasMore ->
                                                _hasMoreMessages.value = hasMore
                                            }

                                            if (response.data?.messages != null) {
                                                // ✅ 수정: 초기 메시지 로드 시에도 Mutex 사용
                                                messagesMutex.withLock {
                                                    // ✅ 중복 제거하여 추가
                                                    val newMessages = response.data.messages.distinctBy { it.messageId }
                                                    _messages.value = newMessages
                                                    Logger.dev("✅ 초기 메시지 로드 완료: ${newMessages.size}개")
                                                }
                                            }

                                            // WebSocket 연결 및 메시지 수신 콜백 설정
                                            connectWebSocket()
                                            _isChatInitialized.value = true
                                            _isInitializingChat.value = false
                                            pageCount = 0

                                            Logger.dev("✅ 채팅 초기화 완료")
                                        }
                                    }
                                }

                                NetworkAPIManager.ResponseCode.API_DM_NATIVE_MESSAGES -> {
                                    // API 성공 후 소켓 연결
                                    result.asBaseData<ChatInitData>()?.let { response ->
                                        if (response.isSuccess) {
                                            Logger.dev("✅ 채팅 메시지 추가 로드 성공")
                                            response.data?.hasMore?.let { hasMore ->
                                                _hasMoreMessages.value = hasMore
                                            }
                                            response.data?.page?.let { page ->
                                                pageCount = page
                                            }
                                            if (response.data?.messages != null) {
                                                // ✅ 수정: 과거 메시지 로드 시에도 Mutex 사용
                                                messagesMutex.withLock {
                                                    val currentMessages = _messages.value
                                                    val newMessages = response.data.messages

                                                    // ✅ 중복 제거: 기존 메시지 ID와 겹치지 않는 것만 추가
                                                    val existingIds = currentMessages.map { it.messageId }.toSet()
                                                    val uniqueNewMessages = newMessages.filter { !existingIds.contains(it.messageId) }

                                                    _messages.value = currentMessages + uniqueNewMessages
                                                    Logger.dev("✅ 과거 메시지 추가: ${uniqueNewMessages.size}개 (중복 제외: ${newMessages.size - uniqueNewMessages.size}개)")
                                                }
                                            }
                                        }
                                    }
                                }

                                NetworkAPIManager.ResponseCode.API_ERROR -> {
                                    Logger.error("❌ ChatInit API 실패: $result")
                                    if (result is ErrorData) {
                                        if (result.code == NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT) {
                                            Logger.error("❌ API 오류: ${result.code}::${result.msg}")
                                            _isInitializingChat.value = false
                                            _isChatInitialized.value = false
                                            CustomAlertManager.showAlert(
                                                content = "채팅을 불러오지 못했습니다.",
                                                onConfirm = {
                                                    _shouldExitChat.value = true  // 플래그 설정
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            CustomAlertManager.showAlert(
                                content = "채팅을 불러오지 못했습니다.",
                                onConfirm = {
                                    _shouldExitChat.value = true  // 플래그 설정
                                }
                            )
                        }
                    }
                }
            })
    }

    /**
     * WebSocket 연결 및 메시지 수신 처리
     */
    private fun connectWebSocket() {
        if (isSocketConnected) {
            Logger.dev("⚠️ WebSocket 이미 연결됨 - 연결 건너뜀")
            return
        }

        SocketManager.connect(
            onDmMessage = { chatMessage ->
                // WebSocket으로 받은 메시지 처리
                handleNewWebSocketMessage(chatMessage)
            },
            onFailed = {
                // 연결 실패 시 Alert 표시
                Logger.error("❌ WebSocket 연결 실패 - Alert 표시")
                CustomAlertManager.showAlert(
                    content = "채팅 서버에 연결하지 못했습니다.",
                    onConfirm = {
                        _shouldExitChat.value = true
                    }
                )
            }
        )

        isSocketConnected = true
        Logger.dev("✅ WebSocket 연결 완료")
    }

    /**
     * 새로 추가된 메시지인지 확인
     */
    fun isNewMessage(messageId: String): Boolean {
        return _newlyAddedMessageIds.value.contains(messageId)
    }

    /**
     * 새 메시지 상태 해제
     */
    fun removeNewMessageId(messageId: String) {
        _newlyAddedMessageIds.value -= messageId
    }

    /**
     * 리소스 정리 (ChatScreen의 onDispose에서 호출)
     */
    fun cleanup() {
        Logger.dev("🗑️ ChatViewModel cleanup 시작")

        viewModelScope.launch {
            try {
                // WebSocket 연결 해제
                if (isSocketConnected) {
                    SocketManager.disconnect()
                    isSocketConnected = false
                    Logger.dev("✅ WebSocket 연결 해제 완료")
                }

                // ✅ 처리 중인 메시지 ID 정리
                processingMessageIds.clear()

                // 상태 초기화
                _isInitializingChat.value = false
                _isChatInitialized.value = false
                _showNewMessageAlert.value = false
                _messageText.value = ""
                _newlyAddedMessageIds.value = emptySet()

                Logger.dev("✅ ChatViewModel 상태 초기화 완료")
            } catch (e: Exception) {
                Logger.error("❌ ChatViewModel cleanup 중 오류: ${e.message}")
            }
        }
    }

    fun initShouldExitChat() {
        _shouldExitChat.value = false
    }

    /**
     * ViewModel 정리 (시스템에서 자동 호출)
     */
    override fun onCleared() {
        super.onCleared()
        Logger.dev("🧹 ChatViewModel onCleared 호출")

        // 콜백 해제
        callbackId?.let {
            NetworkAPIManager.unregisterCallback(it)
            Logger.dev("✅ NetworkAPI 콜백 해제: $it")
        }

        // WebSocket 정리
        if (isSocketConnected) {
            SocketManager.cleanup()
            isSocketConnected = false
        }

        Logger.dev("✅ ChatViewModel cleared")
    }
}