package com.ubase.uclass.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.R
import com.ubase.uclass.presentation.viewmodel.ChatBadgeViewModel
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PreferenceManager

@Composable
fun MainBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onChatTabSelected: () -> Unit = {},
    chatBadgeViewModel: ChatBadgeViewModel
) {

    val chatBadgeVisible by chatBadgeViewModel.chatBadgeVisible
    Logger.error("chatBadgeVisible::" + chatBadgeVisible)

    NavigationBar(
        containerColor = Color.White,
        contentColor = Color.White,
    ) {
        val context = LocalContext.current

        // 홈
        NavigationBarItem(
            icon = {
                Icon(
                    painter = painterResource(
                        id = R.drawable.navi_home_on
                    ),
                    contentDescription = "홈",
                    tint = Color.Unspecified // 원본 이미지 색상 그대로
                )
            },
            label = { Text("홈") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = Color.Black,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.White,
            )
        )

        // 쪽지 (뱃지 포함)
        NavigationBarItem(
            icon = {
                Box {
                    Icon(
                        painter = painterResource(id = R.drawable.navi_icon_chat),
                        contentDescription = "쪽지",
                        tint = Color.Unspecified
                    )

                    // 뱃지 표시
                    if (chatBadgeVisible) {
                        Icon(
                            painter = painterResource(id = R.drawable.navi_icon_new), // 뱃지 이미지 리소스
                            contentDescription = "새 메시지",
                            modifier = Modifier
                                .size(12.dp)
                                .offset(x = 5.dp, y = (-5).dp) // 아이콘 우상단에 배치
                                .align(Alignment.TopEnd),
                            tint = Color.Unspecified // 원본 이미지 색상 그대로
                        )
                    }
                }
            },
            label = { Text("쪽지") },
            selected = selectedTab == 1,
            onClick = {
                onTabSelected(1)
                onChatTabSelected() // 뱃지 숨김 처리
            },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = Color.Black,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.White
            )
        )

        // 공지사항
        NavigationBarItem(
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.navi_icon_notice),
                    contentDescription = "공지사항",
                    tint = Color.Unspecified
                )
            },
            label = { Text("공지사항") },
            selected = selectedTab == 2,
            onClick = {
                //PreferenceManager.clearLoginInfo(context)
                chatBadgeViewModel.setChatBadge(true)
            },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = Color.Black,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.White
            )
        )
    }
}