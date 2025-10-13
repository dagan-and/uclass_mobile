package com.ubase.uclass.presentation.view

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubase.uclass.R

@Composable
fun SNSLoginScreen(
    onKakaoLogin: () -> Unit,
    onNaverLogin: () -> Unit,
    onGoogleLogin: () -> Unit,
    isLoading: Boolean = false,
    isAutoLogin: Boolean = false,
    autoLoginType: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            // 앱 아이콘 영역 (원형 배경)
            Image(
                painter = painterResource(id = R.drawable.splash), // 앱 아이콘 이미지
                contentDescription = "앱 아이콘",
                modifier = Modifier
                    .fillMaxWidth(0.5f) // 화면 너비의 50%
                    .aspectRatio(1f),   // 정사각형 유지
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.weight(1f))

            // 로딩 중일 때 또는 자동 로그인 중일 때
            if (isLoading || isAutoLogin) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height(200.dp), // 로그인 버튼과 동일 높이
                    verticalArrangement = Arrangement.Center // 아이템 중앙 정렬
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val color by infiniteTransition.animateColor(
                        initialValue = Color(0xFF007BFF),
                        targetValue = Color(0xFF00CFFF),
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    CircularProgressIndicator(
                        color = color,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "앱을 준비하고 있습니다...",
                        color = Color(0xFF666666),
                        fontSize = 16.sp
                    )
                }
            } else {
                // 로그인 버튼들
                LoginButtonSection(
                    onKakaoLogin = onKakaoLogin,
                    onNaverLogin = onNaverLogin,
                    onGoogleLogin = onGoogleLogin
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun LoginButtonSection(
    onKakaoLogin: () -> Unit,
    onNaverLogin: () -> Unit,
    onGoogleLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 카카오 로그인 버튼
        LoginButton(
            onClick = onKakaoLogin,
            backgroundColor = Color(0xFFFEE500),
            textColor = Color.Black,
            text = "카카오 로그인"
        )

        // 네이버 로그인 버튼
        LoginButton(
            onClick = onNaverLogin,
            backgroundColor = Color(0xFF03C75A),
            textColor = Color.White,
            text = "네이버 로그인"
        )

        // Google 로그인 버튼
        LoginButton(
            onClick = onGoogleLogin,
            backgroundColor = Color.Black,
            textColor = Color.White,
            text = "구글 로그인"
        )
    }
}

@Composable
private fun LoginButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    text: String,
    iconRes: Int? = null
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(28.dp), // 더 둥근 모서리
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            iconRes?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}