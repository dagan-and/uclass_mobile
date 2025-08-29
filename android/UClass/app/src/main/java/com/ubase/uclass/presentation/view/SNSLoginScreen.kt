package com.ubase.uclass.presentation.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 로고 영역
        Image(
            painter = painterResource(id = R.drawable.img_cfo_noti), // 앱 로고 이미지
            contentDescription = "앱 로고",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 타이틀 텍스트
        Text(
            text = "UCLASS",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "로그인하여 시작하기",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(64.dp))

        // 로딩 중일 때
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "앱을 준비하고 있습니다...",
                    color = Color.Gray
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

        Spacer(modifier = Modifier.height(32.dp))

        // 하단 텍스트
        Text(
            text = "계속 진행하면 서비스 이용약관 및 개인정보 처리방침에 동의하는 것으로 간주됩니다.",
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 18.sp
        )
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 카카오 로그인 버튼
        LoginButton(
            onClick = onKakaoLogin,
            backgroundColor = Color(0xFFFFE812),
            textColor = Color.Black,
            text = "카카오로 시작하기"
        )

        // 네이버 로그인 버튼
        LoginButton(
            onClick = onNaverLogin,
            backgroundColor = Color(0xFF00C73C),
            textColor = Color.White,
            text = "네이버로 시작하기"
        )

        // 구글 로그인 버튼
        LoginButton(
            onClick = onGoogleLogin,
            backgroundColor = Color.White,
            textColor = Color.Black,
            text = "Google로 시작하기",
            borderColor = Color(0xFFE0E0E0)
        )
    }
}

@Composable
private fun LoginButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    text: String,
    borderColor: Color? = null
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp),
        border = borderColor?.let {
            androidx.compose.foundation.BorderStroke(1.dp, it)
        }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}