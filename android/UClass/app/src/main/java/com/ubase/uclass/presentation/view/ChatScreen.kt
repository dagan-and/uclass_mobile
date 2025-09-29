package com.ubase.uclass.presentation.view

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubase.uclass.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.App
import com.ubase.uclass.presentation.viewmodel.ChatViewModel
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.CHAT_BADGE
import com.ubase.uclass.network.response.ChatMessage
import com.ubase.uclass.presentation.ui.ChatBubble
import com.ubase.uclass.presentation.ui.CustomLoadingManager
import com.ubase.uclass.presentation.ui.DateSeparator
import com.ubase.uclass.util.DateUtils
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PreferenceManager
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel 상태 구독
    val isChatInitialized by chatViewModel.isChatInitialized.collectAsState()
    val isInitializingChat by chatViewModel.isInitializingChat.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val messageText by chatViewModel.messageText.collectAsState()
    val newlyAddedMessageIds by chatViewModel.newlyAddedMessageIds.collectAsState()
    val showNewMessageAlert by chatViewModel.showNewMessageAlert.collectAsState()
    val isAtBottom by chatViewModel.isAtBottom.collectAsState()
    val isLoadingMore by chatViewModel.isLoadingMore.collectAsState()
    val hasMoreMessages by chatViewModel.hasMoreMessages.collectAsState()
    val branchName by chatViewModel.branchName.collectAsState()
    val shouldScrollToBottom by chatViewModel.shouldScrollToBottom.collectAsState()
    val shouldExitChat by chatViewModel.shouldExitChat.collectAsState()

    // 화면 진입 시 채팅 초기화 및 소켓 연결
    LaunchedEffect(Unit) {
        ViewCallbackManager.notifyResult(CHAT_BADGE, false)

        // 사용자 ID 가져와서 채팅 초기화
        val userId = PreferenceManager.getUserId(context)
        if (userId != 0) {
            Logger.dev("ChatScreen 진입 - 채팅 초기화 시작")
            chatViewModel.initializeChat(userId.toString())
        } else {
            Logger.error("사용자 ID가 없어 채팅 초기화를 건너뜁니다")
        }
    }

    // 화면에서 나갈 때 리소스 정리
    DisposableEffect(Unit) {
        onDispose {
            Logger.dev("ChatScreen 종료 - 리소스 정리")
            chatViewModel.cleanup() // ViewModel을 통한 정리
        }
    }

    val listState = rememberLazyListState()

    //인터랙션 제거
    val interactionSource = remember { MutableInteractionSource() }
    //키보드 포커스
    val focusManager = LocalFocusManager.current

    // 뒤로가기 처리
    BackHandler {
        Logger.dev("ChatScreen 뒤로가기 - 리소스 정리 후 종료")
        chatViewModel.initShouldExitChat()
        onBack()
    }
    LaunchedEffect(shouldExitChat) {
        if (shouldExitChat) {
            chatViewModel.initShouldExitChat()
            onBack()
        }
    }

    // WebSocket 메시지 수신 시 자동 스크롤
    LaunchedEffect(shouldScrollToBottom) {
        if (shouldScrollToBottom > 0) {
            try {
                listState.animateScrollToItem(0)
                chatViewModel.hideNewMessageAlert()
                Logger.dev("WebSocket 메시지 수신으로 자동 스크롤 완료")
            } catch (e: Exception) {
                Logger.error("WebSocket 메시지 자동 스크롤 중 오류 발생: ${e.message}")
            }
        }
    }

    // 스크롤 위치 모니터링
    LaunchedEffect(listState) {
        snapshotFlow {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
        }.collect { isAtBottomNow ->
            chatViewModel.updateScrollPosition(isAtBottomNow)
        }
    }

    // 최하단 스크롤 감지 (더 많은 메시지 로드)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            // 마지막에서 5개 아이템 이내에 도달했을 때
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !isLoadingMore && hasMoreMessages) {
                chatViewModel.loadMoreMessages()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBack() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.icon_back),
                        contentDescription = "뒤로가기",
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // branchName을 타이틀로 표시
                Text(
                    text = branchName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )

                // 채팅 초기화 상태 표시
                if (isInitializingChat) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.Gray
                    )
                }
            }
        }

        // 채팅 메시지 목록 + 새 메시지 알림 (오버레이)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            // 채팅 리스트 (백그라운드)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val sortedMessages = messages.sortedBy { it.timestamp }
                    val messagesWithDateSeparators = buildList {
                        sortedMessages.forEachIndexed { index, message ->
                            // 이전 메시지와 날짜가 다르면 날짜 구분선 추가
                            if (index == 0 || !DateUtils.isSameDay(
                                    sortedMessages[index - 1].timestamp,
                                    message.timestamp
                                )
                            ) {
                                add("DATE_${DateUtils.formatDate(message.timestamp)}")
                            }
                            add("MESSAGE_${message.messageId}")
                        }
                    }.reversed()

                    items(
                        items = messagesWithDateSeparators,
                        key = { it } // 각 아이템의 고유 키 설정
                    ) { item ->
                        when {
                            item.startsWith("DATE_") -> {
                                val date = item.removePrefix("DATE_")
                                DateSeparator(date = date)
                            }

                            item.startsWith("MESSAGE_") -> {
                                val messageId = item.removePrefix("MESSAGE_")
                                val message = messages.find { it.messageId == messageId }
                                message?.let {
                                    // 새로 추가된 메시지인지 확인
                                    val isNewMessage = chatViewModel.isNewMessage(messageId)

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = true,
                                        enter = if (isNewMessage) {
                                            // 새 메시지 애니메이션: 아래서 위로 슬라이드 + 페이드인 + 확장
                                            slideInVertically(
                                                initialOffsetY = { it },
                                                animationSpec = spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = 300f
                                                )
                                            ) + fadeIn(
                                                animationSpec = tween(300)
                                            ) + expandVertically(
                                                animationSpec = spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = 300f
                                                )
                                            )
                                        } else {
                                            // 기존 메시지는 즉시 표시
                                            fadeIn(animationSpec = tween(0))
                                        },
                                        modifier = Modifier.animateItemPlacement(
                                            animationSpec = spring(
                                                dampingRatio = 0.8f,
                                                stiffness = 300f
                                            )
                                        )
                                    ) {
                                        ChatBubble(message = it)
                                    }

                                    // 애니메이션 완료 후 새 메시지 상태 해제
                                    LaunchedEffect(messageId) {
                                        if (isNewMessage) {
                                            kotlinx.coroutines.delay(500) // 애니메이션 완료 대기
                                            chatViewModel.removeNewMessageId(messageId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 새 메시지 알림 (플로팅 오버레이)
            if (showNewMessageAlert) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // 클릭 시 최신 메시지로 스크롤
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                    chatViewModel.hideNewMessageAlert()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "새로운 메시지가 왔습니다",
                            color = Color.White,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 로딩 인디케이터 (상단에 표시)
            if (isLoadingMore) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF000000),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "이전 메시지를 불러오는 중...",
                            color = Color.White,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 채팅 초기화 로딩 화면
            if (isInitializingChat) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFCCCCCC),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "채팅 연결 로딩중...",
                            color = Color.Black,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // 채팅 입력창 (채팅 초기화 완료 후에만 활성화)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isChatInitialized) Color(0xFFF0F0F0) else Color(0xFFE0E0E0), // 초기화 완료 여부에 따라 색상 변경
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp) // 내부 패딩
            ) {
                var isFocused by remember { mutableStateOf(false) }

                BasicTextField(
                    value = messageText,
                    onValueChange = { if (isChatInitialized) chatViewModel.updateMessageText(it) }, // 초기화 완료 후에만 입력 허용
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = if (isChatInitialized) Color.Black else Color.Gray
                    ),
                    maxLines = 6,
                    singleLine = false,
                    cursorBrush = SolidColor(if (isChatInitialized) Color.Black else Color.Gray),
                    enabled = isChatInitialized // 초기화 완료 후에만 활성화
                )

                // Placeholder
                if (messageText.isEmpty() && !isFocused) {
                    Text(
                        text = if (isChatInitialized) "메시지 입력" else "채팅 준비 중...",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Bottom)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (messageText.isNotEmpty() && isChatInitialized) {

                            //TODO 삭제하기
                            if (messageText.trim() == "로그아웃") {
                                ViewCallbackManager.notifyResult(
                                    ViewCallbackManager.ResponseCode.NAVIGATION,
                                    HOME
                                )
                                ViewCallbackManager.notifyResult(
                                    ViewCallbackManager.ResponseCode.LOGOUT,
                                    true
                                )
                            }
                            if (messageText.trim() == "전화") {
                                val intent =
                                    Intent(Intent.ACTION_CALL, Uri.parse("tel:01075761690"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                App.context().startActivity(intent)
                            }

                            // ViewModel을 통해 메시지 전송
                            chatViewModel.sendMessage(messageText)

                            // 자동 스크롤
                            coroutineScope.launch {
                                try {
                                    kotlinx.coroutines.delay(50)
                                    listState.animateScrollToItem(0)
                                    chatViewModel.hideNewMessageAlert()
                                    Logger.dev("새 메시지로 자동 스크롤 완료")
                                } catch (e: Exception) {
                                    Logger.error("자동 스크롤 중 오류 발생: ${e.message}")
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconRes = when {
                    !isChatInitialized -> R.drawable.icon_send_off // 초기화 중일 때
                    messageText.isEmpty() -> R.drawable.icon_send_off
                    else -> R.drawable.icon_send_on
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "전송",
                    tint = if (isChatInitialized && messageText.isNotEmpty()) Color.Unspecified else Color.Gray
                )
            }
        }
    }
}