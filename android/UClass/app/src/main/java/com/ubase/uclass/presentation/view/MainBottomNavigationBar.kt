package com.ubase.uclass.presentation.view

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ubase.uclass.App
import com.ubase.uclass.R
import com.ubase.uclass.network.ViewCallbackManager
import com.ubase.uclass.network.ViewCallbackManager.PageCode.CHAT
import com.ubase.uclass.network.ViewCallbackManager.PageCode.HOME
import com.ubase.uclass.network.ViewCallbackManager.PageCode.NOTICE
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.CHAT_BADGE
import com.ubase.uclass.network.ViewCallbackManager.ResponseCode.NAVIGATION
import com.ubase.uclass.presentation.viewmodel.ChatBadgeViewModel
import com.ubase.uclass.presentation.viewmodel.NavigationViewModel
import com.ubase.uclass.util.Logger


@Composable
fun MainBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    chatBadgeViewModel: ChatBadgeViewModel = viewModel(),
    navigaionViewModel: NavigationViewModel = viewModel()
) {
    val chatBadgeVisible = chatBadgeViewModel.chatBadgeVisible
    val navigation = navigaionViewModel.navigation

    // navigation 값이 변경될 때 onTabSelected 호출
    LaunchedEffect(navigation) {
        Logger.dev("navigation 값이 변경되었습니다: $navigation")
        onTabSelected(navigation)
    }

    NavigationBar(
        containerColor = Color.White,
        contentColor = Color.White,
    ) {
        // 홈
        NavigationBarItem(
            icon = {
                Icon(
                    painter = painterResource(
                        id = if (selectedTab == 0) R.drawable.navi_home_on else R.drawable.navi_home_off
                    ),
                    contentDescription = "홈",
                    tint = Color.Unspecified
                )
            },
            label = { Text("홈") },
            selected = selectedTab == 0,
            onClick = { ViewCallbackManager.notifyResult(NAVIGATION, HOME) },
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
                        painter = painterResource(
                            id = if (selectedTab == 1) R.drawable.navi_chat_on else R.drawable.navi_chat_off
                        ),
                        contentDescription = "쪽지",
                        tint = Color.Unspecified
                    )

                    // 뱃지 표시
                    if (chatBadgeVisible) {
                        Icon(
                            painter = painterResource(id = R.drawable.navi_icon_new),
                            contentDescription = "새 메시지",
                            modifier = Modifier
                                .size(12.dp)
                                .offset(x = 5.dp, y = (-5).dp)
                                .align(Alignment.TopEnd),
                            tint = Color.Unspecified
                        )
                    }
                }
            },
            label = { Text("쪽지") },
            selected = selectedTab == 1,
            onClick = {
                ViewCallbackManager.notifyResult(NAVIGATION, CHAT)
                ViewCallbackManager.notifyResult(CHAT_BADGE, false)
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
                    painter = painterResource(
                        id = if (selectedTab == 2) R.drawable.navi_info_on else R.drawable.navi_info_off
                    ),
                    contentDescription = "공지사항",
                    tint = Color.Unspecified
                )
            },
            label = { Text("공지사항") },
            selected = selectedTab == 2,
            onClick = { ViewCallbackManager.notifyResult(NAVIGATION, NOTICE) },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = Color.Black,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.White
            )
        )
    }
}
