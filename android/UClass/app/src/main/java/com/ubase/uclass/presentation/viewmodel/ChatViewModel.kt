package com.ubase.uclass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.network.SocketManager
import com.ubase.uclass.network.response.BaseData
import com.ubase.uclass.network.response.ChatInitData
import com.ubase.uclass.network.response.ChatMessage
import com.ubase.uclass.network.response.ErrorData
import com.ubase.uclass.network.response.SNSCheckData
import com.ubase.uclass.presentation.view.asBaseData
import com.ubase.uclass.util.Constants
import com.ubase.uclass.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 간단한 채팅 ViewModel
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

    private val _messageCounter = MutableStateFlow(0)
    val messageCounter: StateFlow<Int> = _messageCounter.asStateFlow()

    // 브랜치명 저장
    private val _branchName = MutableStateFlow("")
    val branchName: StateFlow<String> = _branchName.asStateFlow()

    init {
        setupNetworkCallbacks()
    }

    /**
     * 채팅 초기화 (API 호출 + WebSocket 연결)
     */
    fun initializeChat(userId: String) {
        if (_isInitializingChat.value || _isChatInitialized.value) return

        viewModelScope.launch {
            _isInitializingChat.value = true
            Logger.dev("채팅 초기화 시작 - userId: $userId")

            try {
                // NetworkAPI의 chatInit 호출
                NetworkAPI.chatInit(userId)
            } catch (e: Exception) {
                Logger.error("채팅 초기화 중 오류: ${e.message}")
                _isInitializingChat.value = false
                _isChatInitialized.value = true // 에러 시에도 UI 사용 가능
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
        if (text.isBlank() || !_isChatInitialized.value) return

        viewModelScope.launch {

            SocketManager.sendDmMessage(text)

            // 새 메시지 생성 - 실제 API 스펙에 맞게 생성
            val currentTime = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val newMessage = ChatMessage(
                messageId = UUID.randomUUID().toString(),
                senderId = Constants.getUserId().toDouble(),
                senderType = "user", // 또는 적절한 타입
                senderName = "나", // 실제 사용자명으로 대체 필요
                receiverId = 1.0, // 상대방 ID
                receiverType = "admin", // 또는 적절한 타입
                receiverName = "관리자", // 실제 상대방명으로 대체 필요
                branchId = 1.0, // 실제 브랜치 ID
                branchName = _branchName.value,
                content = text,
                isRead = false,
                readAt = null,
                sentAt = sdf.format(Date(currentTime)),
                roomId = "default_room" // 실제 룸 ID로 대체 필요
            )

            // 메시지 리스트에 추가
            _messages.value += newMessage

            // 새로 추가된 메시지로 표시
            _newlyAddedMessageIds.value += newMessage.messageId

            // 입력창 초기화
            _messageText.value = ""

            // 애니메이션 완료 후 새 메시지 상태 해제
            kotlinx.coroutines.delay(500)
            _newlyAddedMessageIds.value -= newMessage.messageId
        }
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
            Logger.dev("더 많은 메시지 로드 시작")

            try {
                // TODO: 실제 서버에서 이전 메시지 로드 API 호출
                kotlinx.coroutines.delay(1000) // 임시 지연

                Logger.dev("이전 메시지 로드 완료 (현재는 빈 구현)")

            } catch (e: Exception) {
                Logger.error("이전 메시지 로드 오류: ${e.message}")
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
        val callbackId = "Chat_${System.currentTimeMillis()}"

        NetworkAPIManager.registerCallback(callbackId, object : NetworkAPIManager.NetworkCallback {
            override fun onResult(code: Int, result: Any?) {
                when (code) {
                    NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT -> {
                        // 2. API 성공 후 소켓 연결
                        viewModelScope.launch {
                            result.asBaseData<ChatInitData>()?.let { response ->
                                if (response.isSuccess) {

                                    // 브랜치명 업데이트
                                    response.data?.branchName?.let { branchName ->
                                        _branchName.value = branchName
                                    }

                                    if(response.data?.messages != null) {
                                        _messages.value = response.data.messages
                                        _messageCounter.value = response.data.messages.size
                                    }

                                    SocketManager.connect()
                                    _isChatInitialized.value = true
                                    _isInitializingChat.value = false
                                }
                            }

                        }
                    }
                    NetworkAPIManager.ResponseCode.API_ERROR -> {
                        Logger.error("ChatInit API 실패: $result")
                        if (result is ErrorData) {
                            if(result.code == NetworkAPIManager.ResponseCode.API_DM_NATIVE_INIT) {
                                Logger.error("API 오류: ${result.code}::${result.msg}")
                                viewModelScope.launch {
                                    //handleAPIError("${result.code}::${result.msg}")
                                    _isInitializingChat.value = false
                                    _isChatInitialized.value = true // 에러 시에도 UI 사용 가능
                                }

                            }
                        }
                    }
                }
            }
        })
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
     * ViewModel 정리
     */
    override fun onCleared() {
        super.onCleared()
        // WebSocket 연결 해제
        SocketManager.disconnect()
        Logger.dev("ChatViewModel cleared")
    }
}