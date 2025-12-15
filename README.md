# UClass Android 앱 인수인계 문서

## 목차
1. [프로젝트 개요](#1-프로젝트-개요)
2. [프로젝트 구조](#2-프로젝트-구조)
3. [아키텍처 상세](#3-아키텍처-상세)
4. [핵심 컴포넌트 상세](#4-핵심-컴포넌트-상세)
5. [화면별 상세](#5-화면별-상세)
6. [상태 관리](#6-상태-관리)
7. [권한 및 설정](#7-권한-및-설정)
8. [외부 라이브러리](#8-외부-라이브러리)
9. [주요 상수](#9-주요-상수)
10. [디버그 기능](#10-디버그-기능)
11. [알려진 이슈 및 주의사항](#11-알려진-이슈-및-주의사항)
12. [빌드 및 배포](#12-빌드-및-배포)

---

## 1. 프로젝트 개요

### 1.1 앱 정보

| 항목 | 내용 |
|------|------|
| 앱 이름 | UClass |
| 패키지명 | `com.ubase.uclass` |
| 최소 SDK | API 23 (Android 6.0) |
| 타겟 SDK | API 32+ |
| UI 프레임워크 | Jetpack Compose |
| 아키텍처 | MVVM |

### 1.2 주요 기능

| 기능 | 설명 |
|------|------|
| SNS 로그인 | 카카오, 네이버, 구글 소셜 로그인 |
| WebView 기반 메인 콘텐츠 | 하이브리드 앱 구조 |
| 실시간 채팅(DM) | WebSocket(STOMP) 기반 1:1 채팅 |
| 푸시 알림 | FCM 기반 알림 |
| 회원가입 | WebView 기반 회원가입 플로우 |

---

## 2. 프로젝트 구조

```
com.ubase.uclass/
├── App.kt                          # Application 클래스
├── network/                        # 네트워크 레이어
│   ├── HttpClient.kt              # OkHttp 클라이언트 빌더
│   ├── NetworkAPI.kt              # API 호출 싱글톤
│   ├── NetworkAPIManager.kt       # 네트워크 콜백 관리자
│   ├── SocketManager.kt           # WebSocket/STOMP 관리자
│   └── ViewCallbackManager.kt     # View 상태 콜백 관리자
├── presentation/                   # UI 레이어
│   ├── MainActivity.kt            # 메인 액티비티
│   ├── view/                      # 화면 컴포저블
│   │   ├── MainScreen.kt          # 메인 화면 (탭 구조)
│   │   ├── ChatScreen.kt          # 채팅 화면
│   │   ├── SNSLoginScreen.kt      # 로그인 화면
│   │   ├── PermissionScreen.kt    # 권한 요청 화면
│   │   ├── MainBottomNavigationBar.kt # 하단 네비게이션
│   │   └── RegisterWebViewScreen.kt # 회원가입 웹뷰
│   ├── viewmodel/                 # ViewModel
│   │   ├── ChatViewModel.kt       # 채팅 비즈니스 로직
│   │   ├── ChatBadgeViewModel.kt  # 채팅 뱃지 상태
│   │   ├── NavigationViewModel.kt # 네비게이션 상태
│   │   ├── LogoutViewModel.kt     # 로그아웃 상태
│   │   └── ReloadViewModel.kt     # 리로드 상태
│   ├── ui/                        # 공통 UI 컴포넌트
│   │   ├── ChatBubble.kt          # 채팅 버블
│   │   ├── CustomAlert.kt         # 커스텀 알림 다이얼로그
│   │   └── CustomLoading.kt       # 로딩 인디케이터
│   ├── web/                       # WebView 관련
│   │   ├── WebViewManager.kt      # 메인 웹뷰 관리자
│   │   ├── WebViewScreen.kt       # 웹뷰 화면
│   │   ├── NotificationScreen.kt  # 공지사항 웹뷰
│   │   ├── RegisterWebViewManager.kt # 회원가입 웹뷰 관리자
│   │   └── UclassJsInterface.kt   # JS 브릿지 인터페이스
│   └── fcm/                       # FCM 관련
│       ├── FirebaseMessagingService.kt
│       └── PushRelayActivity.kt
└── util/                          # 유틸리티
    ├── Constants.kt               # 상수 정의
    ├── Logger.kt                  # 로깅 유틸
    ├── PreferenceManager.kt       # SharedPreferences 관리
    ├── AppUtil.kt                 # 앱 유틸리티
    ├── DateUtils.kt               # 날짜 유틸리티
    ├── BadgeManager.kt            # 뱃지 관리
    └── PermissionHelper.kt        # 권한 헬퍼
```

---

## 3. 아키텍처 상세

### 3.1 앱 흐름도

```
┌─────────────────────────────────────────────────────────────────┐
│                        App Launch                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MainActivity.onCreate()                       │
│  • NetworkAPI 초기화                                             │
│  • 자동 로그인 체크                                              │
│  • FCM 데이터 확인                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│   PermissionScreen  │         │  자동 로그인 시도    │
│   (최초 실행 시)     │         │                     │
└─────────────────────┘         └─────────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SNSLoginScreen                              │
│  • 카카오/네이버/구글 로그인                                      │
│  • API: /api/auth/sns/check → /api/auth/sns/login               │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│  신규 사용자         │         │  기존 사용자         │
│  RegisterWebView    │         │  MainScreen         │
└─────────────────────┘         └─────────────────────┘
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                        ┌─────────┐     ┌─────────┐     ┌─────────┐
                        │   홈    │     │   DM    │     │  사유   │
                        │(WebView)│     │ (Chat)  │     │(WebView)│
                        └─────────┘     └─────────┘     └─────────┘
```

### 3.2 네비게이션 구조

| 탭 인덱스 | 화면 | 컴포넌트 | 설명 |
|-----------|------|----------|------|
| 0 | 홈 | `WebViewScreen` | 메인 콘텐츠 (WebView) |
| 1 | DM | `ChatScreen` | 실시간 채팅 (Native) |
| 2 | 사유 | `NotificationScreen` | 공지사항 (WebView) |

### 3.3 MVVM 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                          View Layer                              │
│  (Compose UI: Screen, UI Components)                            │
└─────────────────────────────────────────────────────────────────┘
                              │ collectAsState()
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       ViewModel Layer                            │
│  (ChatViewModel, NavigationViewModel, etc.)                     │
│  • StateFlow로 UI 상태 관리                                      │
│  • viewModelScope로 비동기 처리                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Network Layer                              │
│  (NetworkAPI, SocketManager)                                    │
│  • HTTP: OkHttp + Gson                                          │
│  • WebSocket: STOMP 프로토콜                                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 핵심 컴포넌트 상세

### 4.1 네트워크 레이어

#### NetworkAPI.kt (싱글톤)

API 호출을 담당하는 싱글톤 클래스입니다.

```kotlin
object NetworkAPI {
    fun initialize()           // 앱 시작 시 초기화
    fun snsCheck(...)          // SNS 계정 확인
    fun snsLogin(...)          // SNS 로그인
    fun snsRegister(...)       // SNS 회원가입
    fun chatInit(...)          // 채팅 초기화
    fun chatMessage(...)       // 채팅 메시지 조회
    fun shutdown()             // 종료 시 정리
}
```

#### NetworkAPIManager.kt (콜백 관리)

네트워크 콜백 등록/제거/알림을 담당하는 중앙 관리 클래스입니다.

```kotlin
object NetworkAPIManager {
    fun registerCallback(key: String, callback: NetworkCallback)
    fun unregisterCallback(key: String)
    fun notifyResult(code: Int, result: Any?)  // 모든 콜백에 결과 전달
}
```

#### API 엔드포인트

| 엔드포인트 | 응답 코드 | 도메인 | 설명 |
|------------|-----------|--------|------|
| `/api/auth/sns/check` | 1001 | uclassURL | SNS 계정 확인 |
| `/api/auth/sns/login` | 1002 | uclassURL | SNS 로그인 |
| `/api/auth/sns/register` | 1003 | uclassURL | SNS 회원가입 |
| `/api/dm/native/init` | 2001 | umanagerURL | 채팅 초기화 |
| `/api/dm/native/messages` | 2002 | umanagerURL | 채팅 메시지 조회 |
| `/api/dm/native/read` | 2003 | umanagerURL | 읽음 처리 |
| `/api/dm/native/send` | 2004 | umanagerURL | 메시지 전송 |
| `/api/dm/native/status` | 2005 | umanagerURL | 상태 조회 |
| `/api/dm/native/unread` | 2006 | umanagerURL | 안읽은 메시지 |

#### HttpClient.kt (빌더 패턴)

OkHttp 클라이언트를 빌더 패턴으로 생성합니다.

```kotlin
val httpClient = HttpClient.Builder()
    .setUrl(url)
    .setCookie(getCookieJar())
    .setJsonData(Gson().toJson(requestBody))
    .isPost(true)
    .enableLogging(true)
    .setTimeout(15)
    .build()
```

### 4.2 WebSocket (SocketManager.kt)

#### STOMP 프로토콜 기반 실시간 채팅

```kotlin
object SocketManager {
    // 연결 상태
    enum class ConnectionState { 
        DISCONNECTED, 
        CONNECTING, 
        CONNECTED, 
        DISCONNECTING 
    }
    
    // 주요 메서드
    fun initialize(userId: Int, branchId: Int)
    fun connect(onDmMessage: ((ChatMessage) -> Unit)?, onFailed: (() -> Unit)?)
    fun disconnect()
    fun sendDmMessage(content: String)
    fun joinDmRoom()
    fun cleanup()
    
    // 상태 Flow
    fun getConnectionStateFlow(): StateFlow<ConnectionState>
    fun getMessageFlow(): SharedFlow<StompMessage>
}
```

#### STOMP 구독 토픽

| 토픽 | 용도 |
|------|------|
| `/user/queue/dm/joined` | 입/퇴장 알림 |
| `/user/{userId}/queue/messages` | DM 메시지 수신 |

#### STOMP 발행 경로

| 경로 | 용도 |
|------|------|
| `/app/dm/native/join` | 채팅방 입장 |
| `/app/dm/native/send` | 메시지 전송 |

#### 연결 설정

| 설정 | 값 |
|------|------|
| 최대 재연결 시도 | 5회 |
| 재연결 딜레이 | 3,000ms |
| 클라이언트 하트비트 | 20,000ms |
| 서버 하트비트 | 20,000ms |
| 연결 타임아웃 | 10초 |
| 쓰기 타임아웃 | 30초 |

### 4.3 WebView 브릿지 (JavaScript Interface)

#### JS → Native 통신

```kotlin
// UclassJsInterface.kt
class UclassJsInterface(
    private val context: Context, 
    private val onMessage: (String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        onMessage(message)
    }
}
```

#### 지원 액션 (JS → Native)

| 액션 | 설명 | 파라미터 |
|------|------|----------|
| `showLoading` | 로딩 표시 | - |
| `hideLoading` | 로딩 숨김 | - |
| `showAlert` | 알림 다이얼로그 | `title`, `message`, `callback` |
| `showConfirm` | 확인 다이얼로그 | `title`, `message`, `callback` |
| `goLogin` | 회원가입 완료 → 로그인 | - |
| `goClose` | 화면 닫기 | - |
| `goDm` | DM 화면으로 이동 | - |
| `goBrowser` | 외부 브라우저 열기 | `title` (URL) |

#### Native → JS 통신

```kotlin
// 웹뷰에 JWT 토큰 전달
webView.evaluateJavascript("javascript:setToken('$token')") { result ->
    Logger.info("setToken 실행 결과: $result")
}

// 뒤로가기 처리
webView.evaluateJavascript("javascript:goBackPress()") { }

// 회원가입 시 SNS 정보 전달
val jsonString = PreferenceManager.getLoginInfoAsJson(context).toString()
webView.evaluateJavascript("javascript:nativeBinding('$jsonString')") { }
```

#### JS 메시지 JSON 형식

```json
{
    "action": "showAlert",
    "title": "알림",
    "message": "메시지 내용",
    "callback": "javascript:callbackFunction()"
}
```

---

## 5. 화면별 상세

### 5.1 MainActivity.kt

#### 주요 책임

- 앱 진입점
- SNS 로그인 결과 처리 (`onActivityResult`)
- FCM 데이터 처리
- 세션 타임아웃 관리
- WebViewManager 생명주기 관리

#### 생명주기 처리

```kotlin
onCreate() {
    // NetworkAPI 초기화
    if (!NetworkAPI.isInitialized()) {
        NetworkAPI.initialize()
    }
    
    // 자동 로그인 체크
    val autoLoginInfo = AppUtil.tryAutoLogin(this)
    
    // FCM 데이터 확인
    checkIntentForFCMData(intent)
    
    // Compose UI 설정
    setContent { ... }
}

onResume() {
    // 세션 타임아웃 체크 (현재 주석 처리됨)
}

onPause() {
    // 백그라운드 전환 시간 저장
    backgroundTimestamp = System.currentTimeMillis()
    saveBackgroundTimestamp(backgroundTimestamp)
}

onNewIntent() {
    // 새로운 Intent에서 FCM 데이터 확인
    checkIntentForFCMData(intent)
}

onDestroy() {
    NetworkAPI.shutdown()
    NetworkAPIManager.clearAllCallbacks()
    mainWebViewManager.destroy()
    notificationWebViewManager.destroy()
}
```

#### FCM 데이터 처리

```kotlin
fun setFCMIntent(bundle: Bundle?) {
    // type이 "CHAT"이면 채팅 탭으로 이동
    if (bundle?.getString("type").equals("CHAT", true)) {
        ViewCallbackManager.notifyResult(NAVIGATION, CHAT)
    }
    
    // URL이 있으면 WebView 로딩 후 이동
    bundle?.getString("url")?.let { url ->
        pendingFCMUrl = url
        observeWebViewLoadingState()
    }
}
```

### 5.2 MainScreen.kt

#### 로그인 플로우

```
SNS 로그인 시작
    ↓
API: /api/auth/sns/check
    ↓
┌─────────────────┬─────────────────┐
│  기존 사용자     │  신규 사용자     │
│       ↓         │       ↓         │
│ /api/auth/sns   │ RegisterWebView │
│ /login          │ (회원가입)       │
└─────────────────┴─────────────────┘
    ↓
로그인 성공
    ↓
JWT 토큰 저장 (Constants.jwtToken)
    ↓
WebView 프리로드 (homeURL, noticeURL)
    ↓
MainContent (탭 구조)
```

#### NetworkAPI 콜백 처리

```kotlin
NetworkAPIManager.registerCallback(callbackId, object : NetworkCallback {
    override fun onResult(code: Int, result: Any?) {
        when (code) {
            API_AUTH_SNS_CHECK -> {
                // 기존 사용자: snsLogin() 호출
                // 신규 사용자: RegisterWebView 표시
            }
            API_AUTH_SNS_LOGIN -> {
                // JWT 토큰 저장
                // WebView 프리로드
                // 메인 화면 전환
            }
            API_AUTH_SNS_REGISTER -> {
                // 회원가입 완료 후 로그인 시도
            }
            API_ERROR -> {
                // 에러 처리
            }
        }
    }
})
```

### 5.3 ChatScreen.kt + ChatViewModel.kt

#### 채팅 생명주기

```kotlin
// Lifecycle 이벤트 관찰
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                // 소켓 재연결
                chatViewModel.reconnectSocketIfNeeded()
            }
            Lifecycle.Event.ON_STOP -> {
                // 소켓 연결 종료
                chatViewModel.disconnectSocket()
            }
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

// 최초 진입 시
LaunchedEffect(Unit) {
    val userId = PreferenceManager.getUserId(context)
    chatViewModel.initializeChat(userId.toString())
    BadgeManager.getInstance().clearBadgeCount(context)
}

// 화면 종료 시
DisposableEffect(Unit) {
    onDispose {
        chatViewModel.cleanup()
    }
}
```

#### ChatViewModel 상태

| 상태 | 타입 | 설명 |
|------|------|------|
| `isChatInitialized` | `StateFlow<Boolean>` | 채팅 초기화 완료 |
| `isInitializingChat` | `StateFlow<Boolean>` | 초기화 진행 중 |
| `messages` | `StateFlow<List<ChatMessage>>` | 메시지 목록 |
| `messageText` | `StateFlow<String>` | 입력 텍스트 |
| `newlyAddedMessageIds` | `StateFlow<Set<String>>` | 새로 추가된 메시지 ID |
| `showNewMessageAlert` | `StateFlow<Boolean>` | 새 메시지 알림 |
| `isAtBottom` | `StateFlow<Boolean>` | 스크롤 최하단 여부 |
| `isLoadingMore` | `StateFlow<Boolean>` | 이전 메시지 로딩 중 |
| `hasMoreMessages` | `StateFlow<Boolean>` | 추가 메시지 존재 |
| `branchName` | `StateFlow<String>` | 지점명 |
| `shouldScrollToBottom` | `StateFlow<Long>` | 자동 스크롤 트리거 |
| `shouldExitChat` | `StateFlow<Boolean>` | 채팅 종료 트리거 |

#### 동시성 처리

```kotlin
// Mutex를 사용한 메시지 중복 방지
private val messagesMutex = Mutex()
private val processingMessageIds = mutableSetOf<String>()

private fun handleNewWebSocketMessage(newMessage: ChatMessage) {
    viewModelScope.launch {
        messagesMutex.withLock {
            // 1차 체크: 처리 중인 메시지인지 확인
            if (processingMessageIds.contains(newMessage.messageId)) return@launch
            
            // 2차 체크: 이미 추가된 메시지인지 확인
            if (_messages.value.any { it.messageId == newMessage.messageId }) return@launch
            
            // 처리 중 목록에 추가
            processingMessageIds.add(newMessage.messageId)
            
            // 메시지 추가
            _messages.value += newMessage
        }
        
        // UI 업데이트
        _newlyAddedMessageIds.value += newMessage.messageId
        
        // 자동 스크롤 또는 알림 표시
        if (_isAtBottom.value) {
            _shouldScrollToBottom.value = System.currentTimeMillis()
        } else {
            _showNewMessageAlert.value = true
        }
        
        // 애니메이션 완료 후 정리
        delay(500)
        _newlyAddedMessageIds.value -= newMessage.messageId
        processingMessageIds.remove(newMessage.messageId)
    }
}
```

#### 페이지네이션

```kotlin
// LazyColumn의 스크롤 위치 감지
LaunchedEffect(listState) {
    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        // 마지막에서 5개 아이템 이내에 도달했을 때
        totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
    }.collect { shouldLoadMore ->
        if (shouldLoadMore && !isLoadingMore && hasMoreMessages) {
            chatViewModel.loadMoreMessages()
        }
    }
}
```

### 5.4 WebViewScreen.kt

#### 파일 업로드 처리

```kotlin
// 파일 선택 Launcher
val fileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val uri = result.data?.data
        val contentType = uri?.let { context.contentResolver.getType(it) }
        
        if (contentType?.contains("image/") == true) {
            webViewManager.handleFileChooserResult(uri, contentType)
        } else {
            Toast.makeText(context, "이미지 파일만 업로드할 수 있습니다.", Toast.LENGTH_SHORT).show()
            webViewManager.cancelFileChooser()
        }
    } else {
        webViewManager.cancelFileChooser()
    }
}

// 파일 선택 트리거 감지
LaunchedEffect(webViewManager.shouldOpenFileChooser.value) {
    if (webViewManager.shouldOpenFileChooser.value) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        fileChooserLauncher.launch(intent)
        webViewManager.shouldOpenFileChooser.value = false
    }
}
```

#### 뒤로가기 처리

```kotlin
BackHandler {
    // 홈 URL과 동일하면 앱 종료
    if (webViewManager.preloadedWebView?.url == Constants.homeURL) {
        (context as? Activity)?.finishAffinity()
    } else {
        // 웹뷰에 뒤로가기 이벤트 전달
        webViewManager.preloadedWebView?.evaluateJavascript("javascript:goBackPress()") { }
    }
}
```

---

## 6. 상태 관리

### 6.1 ViewCallbackManager (전역 상태 이벤트 버스)

#### 응답 코드

| 코드 | 상수 | 설명 |
|------|------|------|
| 1 | `CHAT_BADGE` | 채팅 뱃지 표시/숨김 |
| 2 | `NAVIGATION` | 탭 이동 |
| 3 | `LOGOUT` | 로그아웃 |
| 4 | `RELOAD` | 재로그인 |

#### 페이지 코드

| 코드 | 상수 | 설명 |
|------|------|------|
| 0 | `HOME` | 홈 탭 |
| 1 | `CHAT` | DM 탭 |
| 2 | `NOTICE` | 사유 탭 |

#### 사용 예시

```kotlin
// 탭 이동
ViewCallbackManager.notifyResult(NAVIGATION, HOME)

// 채팅 뱃지 표시
ViewCallbackManager.notifyResult(CHAT_BADGE, true)

// 채팅 뱃지 숨김
ViewCallbackManager.notifyResult(CHAT_BADGE, false)

// 로그아웃 트리거
ViewCallbackManager.notifyResult(LOGOUT, true)

// 재로그인 트리거
ViewCallbackManager.notifyResult(RELOAD, true)
```

### 6.2 ViewModel 패턴

```
┌─────────────────────────────────────────────────────────────────┐
│                    ViewCallbackManager                           │
│                   (전역 이벤트 버스)                              │
└─────────────────────────────────────────────────────────────────┘
                          │ notifyResult()
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  ChatBadgeViewModel  │  NavigationViewModel  │ LogoutViewModel  │
│                      │                       │ ReloadViewModel  │
│  (각 ViewModel이 init 블록에서 콜백 등록하여 상태 수신)           │
└─────────────────────────────────────────────────────────────────┘
                          │ State 변경
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                Compose UI (Recomposition)                        │
└─────────────────────────────────────────────────────────────────┘
```

#### ViewModel 콜백 등록 예시

```kotlin
class ChatBadgeViewModel : ViewModel() {
    var chatBadgeVisible by mutableStateOf(false)
        private set

    init {
        ViewCallbackManager.registerCallback("ChatBadge", object : ViewCallback {
            override fun onResult(code: Int, result: Any?) {
                if (code == CHAT_BADGE) {
                    chatBadgeVisible = (result as? Boolean) == true
                }
            }
        })
    }
}
```

### 6.3 전역 UI 컴포넌트

#### CustomLoadingManager

```kotlin
object CustomLoadingManager {
    private val _isPresented = MutableStateFlow(false)
    val isPresented: StateFlow<Boolean> = _isPresented
    
    fun showLoading() { _isPresented.value = true }
    fun hideLoading() { _isPresented.value = false }
}
```

#### CustomAlertManager

```kotlin
object CustomAlertManager {
    fun showAlert(title: String, content: String, onConfirm: (() -> Unit)? = null)
    fun showConfirmAlert(title: String, content: String, onConfirm: (() -> Unit)? = null)
    fun showErrorAlert(title: String, content: String, onConfirm: (() -> Unit)? = null)
    fun hideAlert()
}
```

---

## 7. 권한 및 설정

### 7.1 AndroidManifest.xml 권한

| 권한 | 용도 | 필수 여부 |
|------|------|-----------|
| `INTERNET` | 네트워크 통신 | 필수 |
| `POST_NOTIFICATIONS` | 푸시 알림 | 선택 |
| `READ_EXTERNAL_STORAGE` | 파일 읽기 (API 32 이하) | 선택 |
| `READ_MEDIA_IMAGES` | 이미지 읽기 (API 33+) | 선택 |
| `READ_MEDIA_VIDEO` | 비디오 읽기 (API 33+) | 선택 |
| `READ_MEDIA_VISUAL_USER_SELECTED` | 선택적 미디어 (API 34+) | 선택 |

### 7.2 Application 설정

```xml
<application
    android:name="com.ubase.uclass.App"
    android:allowBackup="false"
    android:largeHeap="true"
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="true"
    ...>
```

### 7.3 Activity 설정

```xml
<activity
    android:name=".presentation.MainActivity"
    android:configChanges="keyboardHidden|screenSize|smallestScreenSize|screenLayout"
    android:windowSoftInputMode="adjustResize"
    android:exported="true"
    android:theme="@style/Theme.App.Splash">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

---

## 8. 외부 라이브러리

| 라이브러리 | 용도 | 비고 |
|------------|------|------|
| OkHttp | HTTP 클라이언트 | 네트워크 통신 |
| OkHttp Logging Interceptor | HTTP 로깅 | 디버그용 |
| Gson | JSON 파싱 | Codable 대안 |
| Firebase Messaging | FCM 푸시 알림 | |
| Kakao SDK | 카카오 로그인 | `cc0faae5b1dd0468f0440656b12b8601` |
| Naver SDK | 네이버 로그인 | |
| Google Sign-In | 구글 로그인 | |
| Jetpack Compose | UI 프레임워크 | |
| Lottie | 애니메이션 | |
| AndroidX Core SplashScreen | 스플래시 화면 | |

---

## 9. 주요 상수

### Constants.kt

```kotlin
object Constants {
    var isDebug: Boolean = false       // 디버그 모드
    var fcmToken: String = ""          // FCM 토큰
    var jwtToken: String = ""          // JWT 인증 토큰
    var uclassURL: String = ""         // 인증 API 도메인
    var umanagerURL: String = ""       // 채팅 API 도메인
    var homeURL: String = ""           // 홈 WebView URL
    var noticeURL: String = ""         // 사유 WebView URL
    
    fun getUserId(): Int               // 사용자 ID
    fun getBranchId(): Int             // 지점 ID
}
```

### PreferenceManager 저장 키

| 키 | 용도 |
|------|------|
| `FCM_TOKEN` | FCM 토큰 |
| `SNS_TYPE` | SNS 로그인 타입 (KAKAO, NAVER, GOOGLE) |
| `SNS_ID` | SNS 사용자 ID |
| `USER_ID` | 앱 사용자 ID |
| `BRANCH_ID` | 지점 ID |
| `TAB` | 현재 선택된 탭 |
| `CHAT_INIT` | 채팅 초기화 필요 여부 |

---

## 10. 디버그 기능

### 10.1 채팅 화면 특수 명령어

| 입력 | 동작 |
|------|------|
| `로그아웃` | 강제 로그아웃 |
| `전화` | 전화 앱 실행 (01075761690) |
| `리로드` | 앱 재시작 (재로그인) |
| `로그` | 로그 파일 공유 |

### 10.2 로깅

```kotlin
// App.kt에서 설정
Logger.setEnable(true)
Constants.isDebug = true

// 사용 예시
Logger.dev("개발용 로그")
Logger.info("정보 로그")
Logger.error("에러 로그")
Logger.error(exception)
Logger.web(consoleMessage)  // WebView 콘솔 로그
```

### 10.3 WebView 디버깅

```kotlin
if (Constants.isDebug) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

Chrome DevTools에서 `chrome://inspect`로 WebView 디버깅 가능

---

## 11. 알려진 이슈 및 주의사항

### 11.1 세션 타임아웃

```kotlin
// MainActivity.kt - 현재 주석 처리됨
// 10분 세션 타임아웃 로직 존재하나 비활성화 상태
private val SESSION_TIMEOUT_MS = 10 * 60 * 1000L // 10분

// onResume()에서 체크하는 코드가 주석 처리됨
// if (elapsedTime > SESSION_TIMEOUT_MS) {
//     triggerRelogin()
// }
```

### 11.2 Google 로그인 테스트 코드

```kotlin
// MainActivity.kt - onActivityResult
// 구글 로그인 실패 시 테스트 계정으로 우회 처리됨
// TODO: 배포 전 제거 필요
onFailure = { error ->
    PreferenceManager.saveLoginInfo(
        context = this,
        snsType = "GOOGLE",
        userId = "AAAAA1",      // 하드코딩된 테스트 계정
        email = "AAA1@gmail.com",
        name = "AAA1"
    )
    callSNSCheck()
}
```

### 11.3 WebSocket 재연결

- 최대 재연결 시도: 5회
- 재연결 딜레이: 3초
- 하트비트 간격: 20초
- 연결 불가능한 에러 코드 (400, 401, 403, 404, 405)는 즉시 실패 처리
- 연결 실패 시 `onConnectionFailed` 콜백 호출

### 11.4 HTTP 403 에러 처리

```kotlin
// WebViewManager.kt
override fun onReceivedHttpError(...) {
    if (request?.isForMainFrame == true && errorResponse?.statusCode == 403) {
        // 홈으로 이동 후 재로그인
        ViewCallbackManager.notifyResult(NAVIGATION, HOME)
        ViewCallbackManager.notifyResult(RELOAD, true)
    }
}
```

### 11.5 파일 업로드 제한

- 이미지 파일만 업로드 가능 (`image/*`)
- 다른 파일 형식 선택 시 Toast 메시지 표시 후 취소

---

## 12. 빌드 및 배포

### 12.1 빌드 환경

```groovy
// build.gradle.kts (예상)
android {
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.ubase.uclass"
        minSdk = 23
        targetSdk = 34
    }
    
    buildFeatures {
        compose = true
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}
```

### 12.2 서명 키

- 릴리즈 빌드 시 별도 keystore 필요
- SNS 로그인용 해시 키 등록 필요:
  - 카카오: 키 해시 등록
  - 네이버: 클라이언트 ID/Secret 설정
  - 구글: OAuth 클라이언트 ID 설정

### 12.3 환경별 설정

| 환경 | 설정 |
|------|------|
| Debug | `Logger.setEnable(true)`, `Constants.isDebug = true` |
| Release | `Logger.setEnable(false)`, `Constants.isDebug = false` |

---

## 13. 체크리스트

### 배포 전 확인 사항

- [ ] Google 로그인 테스트 코드 제거 (MainActivity.kt)
- [ ] 디버그 로깅 비활성화
- [ ] WebView 디버깅 비활성화
- [ ] 하드코딩된 전화번호 확인 (01075761690)
- [ ] 서버 URL 프로덕션 환경으로 변경
- [ ] ProGuard/R8 난독화 설정 확인
- [ ] 서명 키 설정 확인

### 신규 개발자 온보딩

1. 프로젝트 클론
2. `local.properties` 설정 (SDK 경로)
3. Firebase `google-services.json` 추가
4. SNS SDK 키 설정 확인
5. 빌드 및 실행

---

## 14. 연락처

| 역할 | 담당 | 연락처 |
|------|------|--------|
| 개발 | - | - |
| 서버 API | - | - |
| 기획 | - | - |

---

*문서 작성일: 2024년*  
*문서 버전: 1.0*  
*기준 소스: 제공된 프로젝트 파일*
