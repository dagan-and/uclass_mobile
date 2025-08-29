package com.ubase.uclass.presentation.view


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(
    navController: NavController,
    onCreateNotification: () -> Unit,
    onKakaoLogin: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "알림"
                )
            },
            label = { Text("샘플 알림") },
            selected = false,
            onClick = onCreateNotification
        )

        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "상세"
                )
            },
            label = { Text("Go to Detail") },
            selected = currentRoute == "detail",
            onClick = {
                if (currentRoute != "detail") {
                    navController.navigate("detail")
                }
            }
        )

        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "카카오 로그인"
                )
            },
            label = { Text("카카오 로그인") },
            selected = false,
            onClick = onKakaoLogin
        )
    }
}