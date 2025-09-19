package com.ubase.uclass.presentation.view

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ViewCallbackManager.notifyResult(CHAT_BADGE, false)
    }

    // 더미 데이터 생성 (9월 1일 ~ 9월 10일, 200개 메시지)
    var messages by remember {
        //mutableStateOf(emptyList<ChatMessage>())
        mutableStateOf(generateDummyMessages()) // 더미 데이터 테스트시 주석 해제
    }

    // 새로 추가된 메시지 ID를 추적하기 위한 상태
    var newlyAddedMessageIds by remember { mutableStateOf(setOf<String>()) }

    // 새 메시지 알림 상태
    var showNewMessageAlert by remember { mutableStateOf(false) }

    // 스크롤 위치 확인을 위한 상태
    var isAtBottom by remember { mutableStateOf(true) }

    // 더 많은 메시지 로딩 상태
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreMessages by remember { mutableStateOf(true) }
    var messageCounter by remember { mutableStateOf(200) } // 현재 메시지 개수

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    //인터랙션 제거
    val interactionSource = remember { MutableInteractionSource() }
    //키보드 포커스
    val focusManager = LocalFocusManager.current

    // 뒤로가기
    BackHandler { onBack() }

    // 스크롤 위치 모니터링
    LaunchedEffect(listState) {
        snapshotFlow {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
        }.collect { isAtBottomNow ->
            isAtBottom = isAtBottomNow
            // 최신 위치에 있으면 새 메시지 알림 숨기기
            if (isAtBottomNow) {
                showNewMessageAlert = false
            }
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
                Logger.dev("최하단 감지 - 더 많은 메시지 로드 시작")
                isLoadingMore = true

                // 네트워크 지연 시뮬레이션
                kotlinx.coroutines.delay(1000)

                // 더 많은 더미 데이터 생성
                val newMessages = generateMoreDummyMessages(
                    startIndex = messageCounter,
                    count = 50,
                    baseDate = messages.minByOrNull { it.timestamp }?.timestamp ?: Date()
                )

                if (newMessages.isNotEmpty()) {
                    messages = newMessages + messages // 기존 메시지 앞에 추가
                    messageCounter += newMessages.size
                    Logger.dev("${newMessages.size}개의 이전 메시지 로드 완료")

                    // 1000개가 넘으면 더 이상 로드하지 않음
                    if (messageCounter >= 1000) {
                        hasMoreMessages = false
                        Logger.dev("최대 메시지 수 도달 - 더 이상 로드하지 않음")
                    }
                } else {
                    hasMoreMessages = false
                }

                isLoadingMore = false
            }
        }
    }

    // 상대방 메시지 시뮬레이션 (테스트용)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000) // 5초 후 상대방 메시지 추가
        val otherMessage = ChatMessage(text = "안녕하세요! 상대방 메시지입니다.", isMe = false)
        newlyAddedMessageIds = newlyAddedMessageIds + otherMessage.id
        messages = messages + otherMessage

        // 스크롤이 최신 위치가 아니면 알림 표시
        if (!isAtBottom) {
            showNewMessageAlert = true
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
                        tint = Color.Black // 필요에 따라 조정
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "채팅",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )
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
                            add("MESSAGE_${message.id}")
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
                                val message = messages.find { it.id == messageId }
                                message?.let {
                                    // 새로 추가된 메시지인지 확인
                                    val isNewMessage = newlyAddedMessageIds.contains(messageId)

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = true,
                                        enter = if (isNewMessage) {
                                            // 새 메시지 애니메이션: 아래에서 위로 슬라이드 + 페이드인 + 확장
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
                                            newlyAddedMessageIds = newlyAddedMessageIds - messageId
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
                                    showNewMessageAlert = false
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
        }

        // 채팅 입력창
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
                        color = Color(0xFFF0F0F0), // 연한 회색
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp) // 내부 패딩
            ) {
                var isFocused by remember { mutableStateOf(false) }

                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black
                    ),
                    maxLines = 6,
                    singleLine = false,
                    cursorBrush = SolidColor(Color.Black) // 검은색 커서
                )

                // Placeholder
                if (messageText.isEmpty() && !isFocused) {
                    Text(
                        text = "메시지 입력",
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
                        if (messageText.isNotEmpty()) {

                            //TODO 삭제하기
                            if(messageText == "로그아웃") {
                                ViewCallbackManager.notifyResult(ViewCallbackManager.ResponseCode.NAVIGATION , HOME)
                                ViewCallbackManager.notifyResult(ViewCallbackManager.ResponseCode.LOGOUT, true)
                            }

                            // 새 메시지를 맨 앞에 추가 (reverseLayout이므로 시각적으로는 맨 아래)
                            val newMessage = ChatMessage(text = messageText, isMe = true)
                            messages = messages + newMessage

                            // 내가 보낸 메시지는 항상 애니메이션과 스크롤 적용
                            newlyAddedMessageIds = newlyAddedMessageIds + newMessage.id

                            messageText = ""

                            // 내가 보낸 메시지는 항상 최신으로 스크롤
                            coroutineScope.launch {
                                try {
                                    // 약간의 지연 후 스크롤 (애니메이션과 함께)
                                    kotlinx.coroutines.delay(50)
                                    listState.animateScrollToItem(0)
                                    showNewMessageAlert = false // 알림 숨기기
                                    Logger.dev("새 메시지로 자동 스크롤 완료")
                                } catch (e: Exception) {
                                    Logger.error("자동 스크롤 중 오류 발생: ${e.message}")
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconRes =
                    if (messageText.isEmpty()) R.drawable.icon_send_off else R.drawable.icon_send_on
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "전송"
                )
            }
        }
    }
}

// 더미 데이터 생성 함수
private fun generateDummyMessages(): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    val calendar = Calendar.getInstance()

    // 9월 1일부터 시작
    calendar.set(2024, Calendar.SEPTEMBER, 1, 9, 0, 0)

    repeat(200) { index ->
        val message = ChatMessage(
            text = if (index % 2 == 0) "내 메시지 $index" else "상대방 메시지 $index",
            isMe = index % 2 == 0,
            timestamp = calendar.time.clone() as Date
        )
        messages.add(message)

        // 시간을 랜덤하게 증가 (1분 ~ 2시간)
        val randomMinutes = (1..120).random()
        calendar.add(Calendar.MINUTE, randomMinutes)
    }

    return messages
}

// 추가 더미 데이터 생성 함수 (이전 메시지)
private fun generateMoreDummyMessages(startIndex: Int, count: Int, baseDate: Date): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    val calendar = Calendar.getInstance()
    calendar.time = baseDate

    // 기준 날짜에서 시간을 거꾸로 가면서 생성
    repeat(count) { index ->
        // 시간을 랜덤하게 감소 (1분 ~ 2시간 전)
        val randomMinutes = (1..120).random()
        calendar.add(Calendar.MINUTE, -randomMinutes)

        val message = ChatMessage(
            text = if ((startIndex + index) % 2 == 0) "내 이전 메시지 ${startIndex + index}" else "상대방 이전 메시지 ${startIndex + index}",
            isMe = (startIndex + index) % 2 == 0,
            timestamp = calendar.time.clone() as Date
        )
        messages.add(0, message) // 앞에 추가해서 시간순 정렬 유지
    }

    return messages
}