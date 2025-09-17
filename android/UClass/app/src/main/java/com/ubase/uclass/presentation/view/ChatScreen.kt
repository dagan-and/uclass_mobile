package com.ubase.uclass.presentation.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ubase.uclass.util.PreferenceManager

@Composable
fun ChatScreen(
    modifier: Modifier,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ViewCallbackManager.notifyResult(CHAT_BADGE, false)
    }

    var messages by remember {
        mutableStateOf(
            List(100) { index ->
                if (index % 2 == 0) {
                    ChatMessage(text = "내 메시지 $index", isMe = true)
                } else {
                    ChatMessage(text = "상대방 메시지 $index", isMe = false)
                }
            }
        )
    }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    //인터렉션 제거
    val interactionSource = remember { MutableInteractionSource() }
    //키보드 포커스
    val focusManager = LocalFocusManager.current

    // 뒤로가기
    BackHandler { onBack() }



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

        // 채팅 메시지 목록
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
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
                items(messages.reversed()) { message ->
                    ChatBubble(message = message)
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
                        indication = null // 클릭 ripple 제거
                    ) {
                        if (messageText.isNotEmpty()) {

                            //TODO 삭제하기
                            if(messageText == "로그아웃") {
                                ViewCallbackManager.notifyResult(ViewCallbackManager.ResponseCode.NAVIGATION , HOME)
                                ViewCallbackManager.notifyResult(ViewCallbackManager.ResponseCode.LOGOUT, true)
                            }

                            // 새 메시지를 맨 앞에 추가 (reverseLayout이므로 시각적으로는 맨 아래)
                            messages = messages + ChatMessage(text = messageText, isMe = true)
                            messageText = ""

                            // 테스트용 자동 응답도 맨 앞에 추가
                            messages = messages + ChatMessage(text = "안녕하세요! 메시지를 받았습니다.", isMe = false)
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