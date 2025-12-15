# uclass 앱 인수인계 문서

> **앱 이름**: uclass  
> **플랫폼**: Android / iOS

---

## 1. 프로젝트 개요

uclass는 교육 서비스를 위한 하이브리드 앱으로, WebView 기반의 콘텐츠 표시와 네이티브 채팅 기능을 제공합니다.

### 1.1 주요 기능
| 기능 | 설명 |
|------|------|
| SNS 로그인 |구글,애플 로그인 지원 |
| WebView | 메인 콘텐츠를 WebView로 표시 |
| 네이티브 채팅 | STOMP/WebSocket 기반 실시간 채팅 |
| 푸시 알림 | FCM 기반 푸시 알림 |
| 공지사항 | 별도 WebView로 공지사항 표시 |

### 1.2 앱 구조

```
┌─────────────────────────────────────┐
│           MainScreen                │
├─────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌────────┐ │
│  │ 홈(Web) │ │  채팅   │ │ 공지   │ │
│  │ View    │ │ Screen  │ │ Screen │ │
│  └─────────┘ └─────────┘ └────────┘ │
├─────────────────────────────────────┤
│         Bottom Navigation Bar       │
│   [홈]      [채팅]      [공지사항]   │
└─────────────────────────────────────┘
```

---

## 2. 개발 환경

### 2.1 Android

| 항목 | 버전/설정 |
|------|----------|
| 패키지명 | `com.ubase.uclass` |
| Min SDK | API 23 (Android 6.0) |
| Target SDK | API 34 (Android 14) |
| Gradle | 8.9 |
| Java | 11 |
| Kotlin | 최신 |
| UI | Jetpack Compose |

#### 주요 의존성
```kotlin
// 네트워크
implementation("com.squareup.okhttp3:okhttp:4.x")
implementation("com.google.code.gson:gson:2.x")

// SNS 로그인
implementation("com.kakao.sdk:v2-user:2.x")
implementation("com.navercorp.nid:oauth:5.x")
implementation("com.google.android.gms:play-services-auth:x.x")

// Firebase
implementation("com.google.firebase:firebase-messaging:x.x")

// RxJava
implementation("io.reactivex.rxjava3:rxjava:3.x")
```

### 2.2 iOS

| 항목 | 버전/설정 |
|------|----------|
| Bundle ID | (Info.plist 참조) |
| Deployment Target | iOS 15.0+ |
| Swift | 5.9+ |
| Xcode | 최신 버전 |
| UI | SwiftUI |

#### 주요 의존성 (SPM)
```swift
// 네트워크
Alamofire

// SNS 로그인
KakaoSDK
NidThirdPartyLogin (네이버)

// Firebase
FirebaseMessaging
FirebaseCrashlytics
```

---

## 3. 아키텍처

### 3.1 전체 아키텍처
- **패턴**: MVVM (Model-View-ViewModel)
- **네트워크**: REST API + WebSocket (STOMP)
- **상태 관리**: 
  - Android: StateFlow, MutableState (Compose)
  - iOS: @Published, Combine

### 3.2 레이어 구조

```
┌────────────────────────────────────────────┐
│                 View Layer                  │
│  (Compose UI / SwiftUI)                    │
├────────────────────────────────────────────┤
│              ViewModel Layer                │
│  - ChatViewModel                            │
│  - ChatBadgeViewModel                       │
│  - NavigationViewModel                      │
│  - LogoutViewModel                          │
│  - ReloadViewModel                          │
├────────────────────────────────────────────┤
│              Network Layer                  │
│  - NetworkAPI (REST)                        │
│  - SocketManager (WebSocket/STOMP)         │
│  - NetworkAPIManager (콜백 관리)            │
│  - ViewCallbackManager (View 콜백)         │
├────────────────────────────────────────────┤
│               Utility Layer                 │
│  - Constants, Logger, AppUtil              │
│  - PreferenceManager, LoginUserInfo        │
└────────────────────────────────────────────┘
```

---

## 4. 주요 컴포넌트 상세

### 4.1 네트워크 통신

#### 4.1.1 REST API (NetworkAPI)

**서버 URL 구분**
| 도메인 | 용도 |
|--------|------|
| `Constants.uclassURL` | 일반 API (인증, 사용자 정보 등) |
| `Constants.umanagerURL` | 채팅 관련 API (`/api/dm/*`) |

**주요 API 엔드포인트**

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/api/auth/sns/check` | POST | SNS 로그인 체크 |
| `/api/auth/sns/login` | POST | SNS 로그인 |
| `/api/auth/sns/register` | POST | SNS 회원가입 |
| `/api/dm/native/init` | POST | 채팅 초기화 |
| `/api/dm/native/messages` | POST | 채팅 메시지 조회 |

**요청/응답 구조**
```json
// 요청
{
  "provider": "GOOGLE",
  "snsId": "123456789",
  "pushToken": "fcm_token_here"
}

// 응답
{
  "success": true,
  "message": "Success",
  "data": { ... }
}
```

#### 4.1.2 WebSocket/STOMP (SocketManager)

**연결 정보**
- **URL**: `wss://{host}/ws/websocket`
- **프로토콜**: STOMP over SockJS
- **인증**: JWT-TOKEN 헤더

**STOMP 프레임 타입**
| 커맨드 | 설명 |
|--------|------|
| CONNECT | 연결 요청 |
| CONNECTED | 연결 완료 |
| SUBSCRIBE | 토픽 구독 |
| SEND | 메시지 전송 |
| MESSAGE | 메시지 수신 |
| DISCONNECT | 연결 해제 |

**구독 토픽**
```
/user/queue/dm/joined     - 입장/퇴장 알림
/user/{userId}/queue/messages - DM 메시지 수신
```

**Heartbeat 설정**
- 클라이언트 → 서버: 10~20초 간격
- 서버 → 클라이언트: 10~20초 간격
- 타임아웃: 서버 heartbeat 간격 × 2.5

**재연결 로직**
- 최대 재시도 횟수: 5회
- 재연결 딜레이: 3초
- HTTP 400, 401, 403, 404, 405 에러 시 재연결 중단

### 4.2 콜백 관리 시스템

#### NetworkAPIManager (REST API 콜백)
```kotlin
// Android
object NetworkAPIManager {
    object ResponseCode {
        const val API_ERROR = -1
        const val API_AUTH_SNS_CHECK = 1001
        const val API_AUTH_SNS_LOGIN = 1002
        const val API_AUTH_SNS_REGISTER = 1003
        const val API_DM_NATIVE_INIT = 2001
        const val API_DM_NATIVE_MESSAGES = 2002
    }
}
```

#### ViewCallbackManager (View 상태 콜백)
```kotlin
// Android
object ViewCallbackManager {
    object ResponseCode {
        const val CHAT_BADGE = 1   // 채팅 뱃지 표시
        const val NAVIGATION = 2   // 화면 이동
        const val LOGOUT = 3       // 로그아웃
        const val RELOAD = 4       // 재로그인
    }
    
    object PageCode {
        const val HOME = 0
        const val CHAT = 1
        const val NOTICE = 2
    }
}
```

### 4.3 WebView 관리

#### WebViewManager
- **역할**: WebView 인스턴스 관리, URL 로딩, JavaScript 인터페이스
- **주요 기능**:
  - JWT 토큰 전달 (`setToken()` JavaScript 호출)
  - 파일 업로드 처리
  - 403 에러 시 홈으로 리다이렉트

#### JavaScript Interface
```javascript
// 웹에서 네이티브 호출
window.uclass.showMessage(msg)
```

### 4.4 SNS 로그인

#### 로그인 플로우
```
1. SNS SDK 로그인 → 사용자 정보 획득
2. /api/auth/sns/check API 호출
3. 회원 여부에 따라:
   - 기존 회원: /api/auth/sns/login → JWT 발급 → 메인 화면
   - 신규 회원: 회원가입 WebView로 이동
```

#### SNS 설정

**카카오**
```kotlin
// Android - App.kt
KakaoSdk.init(this, "cc0faae5b1dd0468f0440656b12b8601")
```
```swift
// iOS - AppDelegate.swift
KakaoSDK.initSDK(appKey: "cc0faae5b1dd0468f0440656b12b8601")
```

**네이버**
```kotlin
// Android - App.kt
NaverIdLoginSDK.initialize(this,
    getString(R.string.naver_client_id),
    getString(R.string.naver_client_secret),
    getString(R.string.app_name))
```
```swift
// iOS - AppDelegate.swift
NidOAuth.shared.initialize()
```

### 4.5 푸시 알림 (FCM)

#### 토큰 저장
```kotlin
// Android
Constants.fcmToken = task.result
PreferenceManager.putString("FCM_TOKEN", token)
```
```swift
// iOS
Constants.fcmToken = fcmToken
```

#### 푸시 페이로드 처리
```json
{
  "type": "CHAT",
  "url": "https://..."
}
```

#### 처리 로직
1. `type == "CHAT"`: 채팅 화면으로 이동
2. `url` 존재: WebView에서 해당 URL 로드

---

## 5. 화면 구성

### 5.1 화면 목록

| 화면 | Android | iOS | 설명 |
|------|---------|-----|------|
| 스플래시 | Splash Theme | SplashView | 앱 시작 화면 |
| 권한 요청 | PermissionScreen | PermissionView | 알림 권한 요청 |
| SNS 로그인 | SNSLoginScreen | SNSLoginView | 소셜 로그인 선택 |
| 회원가입 | RegisterWebViewScreen | RegisterWebViewScreen | WebView 기반 회원가입 |
| 메인 | MainScreen | MainScreen | 하단 탭 네비게이션 |
| 홈 | WebViewScreen | WebViewScreen | 메인 WebView |
| 채팅 | ChatScreen | ChatScreen | 네이티브 채팅 |
| 공지사항 | NotificationScreen | NoticeScreen | 공지사항 WebView |

### 5.2 네비게이션 플로우

```
앱 시작
    │
    ▼
[스플래시] ─────────────────────────────┐
    │                                   │
    ▼                                   │
[권한 요청] (최초 1회)                   │
    │                                   │
    ▼                                   │
[자동 로그인 체크] ───────────────────────┤
    │         │                         │
    │ 실패    │ 성공                     │
    ▼         ▼                         │
[SNS 로그인] ──────────────────────────►│
    │                                   │
    │ 신규 회원                          │
    ▼                                   │
[회원가입 WebView] ─────────────────────►│
                                        │
                                        ▼
                              [메인 화면 (MainScreen)]
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
               [홈 탭]             [채팅 탭]           [공지사항 탭]
              (WebView)           (Native)            (WebView)
```

### 5.3 세션 관리

#### 백그라운드 타임아웃
- **타임아웃**: 10분
- **동작**: 백그라운드 → 포그라운드 전환 시 10분 초과하면 재로그인

#### 403 에러 처리
- WebView에서 403 응답 수신 시 홈 화면으로 이동 및 새로고침

---

## 6. 주요 클래스/파일 설명

### 6.1 Android

| 파일 | 설명 |
|------|------|
| `App.kt` | Application 클래스, SDK 초기화, FCM 토큰 관리 |
| `MainActivity.kt` | 메인 액티비티, Compose 진입점, 로그인 처리 |
| `NetworkAPI.kt` | REST API 통신 싱글톤 |
| `NetworkAPIManager.kt` | API 콜백 관리 |
| `SocketManager.kt` | WebSocket/STOMP 연결 관리 |
| `ViewCallbackManager.kt` | View 상태 콜백 관리 |
| `WebViewManager.kt` | WebView 인스턴스 관리 |
| `ChatViewModel.kt` | 채팅 비즈니스 로직 |
| `ChatScreen.kt` | 채팅 UI (Compose) |
| `MainScreen.kt` | 메인 화면 (탭 네비게이션) |

### 6.2 iOS

| 파일 | 설명 |
|------|------|
| `AppDelegate.swift` | 앱 델리게이트, SDK 초기화, 푸시 처리 |
| `uclassApp.swift` | SwiftUI App 진입점 |
| `NetworkAPI.swift` | REST API 통신 싱글톤 (Alamofire) |
| `NetworkAPIManager.swift` | API 콜백 관리 |
| `SocketManager.swift` | WebSocket/STOMP 연결 관리 (URLSession) |
| `WebViewManager.swift` | WebView 인스턴스 관리 |
| `ChatScreen.swift` | 채팅 화면 (SwiftUI + UIKit) |
| `MainScreen.swift` | 메인 화면 (탭 네비게이션) |

---

## 7. 플랫폼별 차이점

| 기능 | Android | iOS |
|------|---------|-----|
| HTTP 클라이언트 | OkHttp | Alamofire |
| JSON 파싱 | Gson | Codable (JSONDecoder) |
| WebSocket | OkHttp WebSocket | URLSessionWebSocketTask |
| 상태 관리 | StateFlow, MutableState | @Published, Combine |
| UI 프레임워크 | Jetpack Compose | SwiftUI |
| 채팅 UI | Compose LazyColumn | UITableView (UIViewRepresentable) |
| 화면 전환 알림 | ViewCallbackManager | NotificationCenter |

---

## 8. 빌드 및 배포

### 8.1 Android

#### 빌드 설정
```groovy
// build.gradle.kts (app)
android {
    namespace = "com.ubase.uclass"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.ubase.uclass"
        minSdk = 23
        targetSdk = 34
    }
}
```

#### 서명 설정
- Release 빌드 시 서명 키 필요
- `signingConfigs` 블록에서 설정

### 8.2 iOS

#### Capabilities 설정 (uclass.entitlements)
- Push Notifications
- Associated Domains (필요 시)

#### Info.plist 설정
- URL Schemes (SNS 로그인 콜백)
- Privacy 권한 설명

---

## 9. 상수 및 설정값

### 9.1 Constants 클래스

| 상수 | 설명 |
|------|------|
| `isDebug` | 디버그 모드 여부 |
| `uclassURL` | 메인 API 서버 URL |
| `umanagerURL` | 채팅 API 서버 URL |
| `mainUrl` | WebView 메인 URL |
| `jwtToken` | JWT 인증 토큰 |
| `fcmToken` | FCM 푸시 토큰 |

### 9.2 SharedPreferences / UserDefaults 키

| 키 | 설명 |
|---|------|
| `FCM_TOKEN` | FCM 토큰 |
| `SNS_TYPE` | SNS 로그인 타입 (GOOGLE, APPLE) |
| `SNS_ID` | SNS 사용자 ID |
| `TAB` | 현재 선택된 탭 |
| `CHAT_INIT` | 채팅 초기화 필요 여부 |

---


---

## 11. 테스트 체크리스트

### 11.1 로그인/인증
- [ ] 구글/아이폰 로그인/로그아웃
- [ ] 자동 로그인
- [ ] 세션 만료 후 재로그인

### 11.2 채팅
- [ ] 소켓 연결/해제
- [ ] 메시지 송신/수신
- [ ] 재연결 (네트워크 전환)
- [ ] 이미지 업로드
- [ ] 채팅 뱃지 표시

### 11.3 푸시 알림
- [ ] 포그라운드 수신
- [ ] 백그라운드 수신
- [ ] 앱 종료 상태 수신
- [ ] 딥링크 (채팅/URL)

### 11.4 WebView
- [ ] 초기 로딩
- [ ] JWT 토큰 전달
- [ ] 파일 업로드
- [ ] 403 에러 처리

---

## 12. 연락처 및 참고자료

### 12.1 API 문서
- 메인 API: `{Constants.uclassURL}/swagger-ui/`
- 채팅 API: `{Constants.umanagerURL}/swagger-ui/`

### 12.2 외부 SDK 문서
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [STOMP Protocol](https://stomp.github.io/stomp-specification-1.2.html)

---

