package com.ubase.uclass.presentation.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubase.uclass.R
import com.ubase.uclass.util.Logger
import com.ubase.uclass.util.PermissionHelper

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    // 권한 요청 상태 관리
    var isPermissionRequested by remember { mutableStateOf(false) }
    var allPermissionsGranted by remember { mutableStateOf(false) }

    // 필요한 권한들
    val requiredPermissions = PermissionHelper.getRequiredPermissions()

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Logger.info("권한 요청 결과: $permissions")

        val allGranted = permissions.values.all { it }
        allPermissionsGranted = allGranted

        if (allGranted) {
            Logger.info("모든 권한이 승인되었습니다.")
            onPermissionsGranted()
        } else {
            Logger.info("일부 권한이 거부되었습니다.")
            // 선택적 권한만 필요한 경우 여기서도 진행 가능
            // 현재는 권한이 거부되어도 진행하도록 설정
            onPermissionsGranted()
        }
    }

    // 초기 권한 체크
    LaunchedEffect(Unit) {
        if (PermissionHelper.checkPermissions(context)) {
            Logger.info("이미 모든 권한이 승인되어 있습니다.")
            onPermissionsGranted()
        }
    }

    PermissionRequestContent(
        onRequestPermissions = {
            Logger.info("권한 요청을 시작합니다.")
            isPermissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        },
        isPermissionRequested = isPermissionRequested
    )
}

@Composable
private fun PermissionRequestContent(
    onRequestPermissions: () -> Unit,
    isPermissionRequested: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // 타이틀
        Text(
            text = "UClass",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "서비스 이용을 위한",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Text(
            text = "앱 접근 권한을 안내해 드려요.",
            fontSize = 16.sp,
            color = Color(0xFF007AFF)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "정보통신망법 준수 및 차별화된 서비스를 제공하기 위해 서비스에 꼭 필요한 기능에 접속하고 있습니다.",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 권한 항목들
        PermissionItem(
            icon = R.drawable.navi_chat_on,
            title = "[선택] 알림",
            description = "공지사항 및 채팅 알림을 수신"
        )

        Spacer(modifier = Modifier.height(20.dp))

        PermissionItem(
            icon = R.drawable.navi_home_on,
            title = "[선택] 저장공간",
            description = "프로필 이미지 등록시 사진 찾기"
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "꼭! 확인해주세요.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "• 기능별로 선택적 접근 권한 항목이 다를 수 있습니다.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• 서비스 제공에 접근 권한이 필요한 경우에만 동의를 받고 있으며, 허용하지 않으셔도 서비스 이용이 가능하나 기능 사용에 제한이 있을 수 있습니다.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 확인 버튼
        Button(
            onClick = {
                onRequestPermissions()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C757D)
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = !isPermissionRequested // 권한 요청 중에는 버튼 비활성화
        ) {
            Text(
                text = if (isPermissionRequested) "권한 요청 중..." else "확인",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionItem(
    icon: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}