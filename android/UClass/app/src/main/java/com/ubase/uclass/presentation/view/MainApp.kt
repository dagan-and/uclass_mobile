package com.ubase.uclass.presentation.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubase.uclass.network.NetworkAPI
import com.ubase.uclass.network.NetworkAPIManager
import com.ubase.uclass.presentation.web.WebViewManager
import com.ubase.uclass.presentation.web.WebViewScreen

@Composable
fun MainApp(
    onKakaoLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    onNaverLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    onGoogleLogin: (successCallback: () -> Unit, failureCallback: () -> Unit) -> Unit,
    webViewManager: WebViewManager
) {

    // 로그인 상태 관리
    var isLoggedIn by remember { mutableStateOf(false) }
    var isWebViewLoading by remember { mutableStateOf(false) }
    var loginSuccess by remember { mutableStateOf(false) }
    var isAPIInitialized by remember { mutableStateOf(false) }

    // NetworkAPI 콜백 등록
    DisposableEffect(Unit) {
        val callbackId = "MainApp_${System.currentTimeMillis()}"

        NetworkAPIManager.registerCallback(callbackId, object : NetworkAPIManager.NetworkCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == NetworkAPIManager.ResponseCode.API_AUTH_INIT_STORE) {
                    isAPIInitialized = true
                }
            }
        })

        onDispose {
            NetworkAPIManager.unregisterCallback(callbackId)
        }
    }

    // 로그인 성공 후 공통 처리 함수
    val handleLoginSuccess = {
        loginSuccess = true
        isWebViewLoading = true
        webViewManager.preloadWebView()

        // NetworkAPI.authInitStore 호출
        NetworkAPI.authInitStore("1.0.0")
    }

    // 로그인 실패 후 공통 처리 함수
    val handleLoginFailure = {
        isWebViewLoading = false
        loginSuccess = false
    }

    // API 초기화 완료 및 웹뷰 로딩 완료 시 메인 화면으로 전환
    LaunchedEffect(isAPIInitialized, webViewManager.isWebViewLoaded.value, loginSuccess) {
        if (isAPIInitialized && webViewManager.isWebViewLoaded.value && loginSuccess) {
            isLoggedIn = true
            isWebViewLoading = false
        }
    }

    if (!isLoggedIn) {
        // 로그인 화면 표시
        SNSLoginScreen(
            onKakaoLogin = {
                onKakaoLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            onNaverLogin = {
                onNaverLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            onGoogleLogin = {
                onGoogleLogin(
                    { handleLoginSuccess() },
                    { handleLoginFailure() }
                )
            },
            isLoading = isWebViewLoading
        )
    } else {
        // 메인 앱 화면
        MainContent(webViewManager = webViewManager)
    }
}

@Composable
private fun MainContent(webViewManager: WebViewManager) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 메인 컨텐츠 영역
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> WebViewScreen(webViewManager = webViewManager)
                1 -> DetailScreen()
                2 -> NotificationScreen()
            }
        }

        // 하단 네비게이션 바
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = Color.Black
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "홈"
                )
            },
            label = { Text("홈") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Blue,
                selectedTextColor = Color.Blue,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )

        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "상세"
                )
            },
            label = { Text("Detail") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Blue,
                selectedTextColor = Color.Blue,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )

        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "알림"
                )
            },
            label = { Text("알림") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Blue,
                selectedTextColor = Color.Blue,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}

@Composable
private fun NotificationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "알림 화면",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "알림 목록",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "현재 알림이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}