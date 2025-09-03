package com.ubase.uclass.presentation.view

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
    isLoading: Boolean = false
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
            Spacer(modifier = Modifier.height(100.dp))

            // 앱 아이콘 영역 (원형 배경)
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher), // 앱 아이콘 이미지
                contentDescription = "앱 아이콘",
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 타이틀 텍스트
            Text(
                text = "간편 로그인",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "소셜 계정으로 간편하게 로그인하세요",
                fontSize = 16.sp,
                color = Color(0xFF666666)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 로딩 중일 때
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF007BFF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "앱을 준비하고 있습니다...",
                        color = Color(0xFF666666),
                        fontSize = 14.sp
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

        // Apple/Google 로그인 버튼 (이미지에서는 Apple로 보임)
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