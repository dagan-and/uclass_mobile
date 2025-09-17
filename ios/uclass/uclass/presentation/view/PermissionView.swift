import SwiftUI

// MARK: - Font Style Extension
extension Font {
    struct PermissionStyle {
        // 타이틀
        static let appTitle: Font = .title2.weight(.bold)
        
        // 본문
        static let bodyText: Font = .callout
        static let subtitle: Font = .subheadline
        static let caption: Font = .caption
        static let smallCaption: Font = .caption2
        
        // 버튼
        static let buttonText: Font = .callout.weight(.medium)
        
        // 권한 아이템
        static let itemTitle: Font = .callout.weight(.medium)
        static let itemDescription: Font = .caption2
        
        // 아이콘
        static let iconSize: Font = .title3
    }
}

// MARK: - Color Style Extension
extension Color {
    struct PermissionStyle {
        static let primaryText = Color.black
        static let secondaryText = Color.gray
        static let accentText = Color.blue
        static let buttonBackground = Color(UIColor.systemGray)
        static let noticeBackground = Color(red: 0.95, green: 0.95, blue: 0.95)
        static let buttonText = Color.white
        static let background = Color.white
    }
}

// MARK: - Spacing Values
struct PermissionSpacing {
    static let small: CGFloat = 8
    static let medium: CGFloat = 16
    static let large: CGFloat = 24
    static let extraLarge: CGFloat = 32
    static let buttonHeight: CGFloat = 48
    static let iconSize: CGFloat = 28
    static let cornerRadius: CGFloat = 8
    static let noticeMinHeight: CGFloat = 120
}

struct PermissionView: View {
    let onPermissionsGranted: () -> Void
    
    @State private var isPermissionRequested = false
    @State private var allPermissionsGranted = false
    
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                // 스크롤 가능한 메인 콘텐츠
                ScrollView {
                    VStack(spacing: PermissionSpacing.large) {
                        Spacer(minLength: PermissionSpacing.extraLarge)
                        
                        // 타이틀
                        VStack(spacing: PermissionSpacing.small) {
                            Text("UClass")
                                .font(Font.PermissionStyle.appTitle)
                                .foregroundColor(Color.PermissionStyle.primaryText)
                            
                            VStack(spacing: 4) {
                                Text("서비스 이용을 위한")
                                    .font(Font.PermissionStyle.subtitle)
                                    .foregroundColor(Color.PermissionStyle.secondaryText)
                                
                                Text("앱 접근 권한을 안내해 드려요.")
                                    .font(Font.PermissionStyle.subtitle)
                                    .foregroundColor(Color.PermissionStyle.accentText)
                            }
                        }
                        
                        // 설명 텍스트
                        Text("정보통신망법 준수 및 차별화된 서비스를 제공하기 위해 서비스에 꼭 필요한 기능에 접속하고 있습니다.")
                            .font(Font.PermissionStyle.smallCaption)
                            .foregroundColor(Color.PermissionStyle.secondaryText)
                            .multilineTextAlignment(.center)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, PermissionSpacing.small)
                        
                        // 권한 항목들
                        VStack(spacing: PermissionSpacing.medium) {
                            PermissionItemView(
                                icon: "bell.fill",
                                title: "[선택] 알림",
                                description: "공지사항 및 채팅 알림을 수신"
                            )
                            
                            PermissionItemView(
                                icon: "photo.fill",
                                title: "[선택] 사진",
                                description: "프로필 이미지 등록시 사진 찾기"
                            )
                        }
                        .padding(.vertical, PermissionSpacing.medium)
                        
                        // 주의사항
                        VStack(spacing: PermissionSpacing.medium) {
                            Text("꼭! 확인해주세요.")
                                .font(Font.PermissionStyle.bodyText)
                                .fontWeight(.bold)
                                .foregroundColor(Color.PermissionStyle.primaryText)
                            
                            RoundedRectangle(cornerRadius: PermissionSpacing.cornerRadius)
                                .fill(Color.PermissionStyle.noticeBackground)
                                .overlay(
                                    VStack(spacing: PermissionSpacing.medium) {
                                        Text("• 기능별로 선택적 접근 권한 항목이 다를 수 있습니다.")
                                            .font(Font.PermissionStyle.smallCaption)
                                            .foregroundColor(Color.PermissionStyle.secondaryText)
                                            .multilineTextAlignment(.leading)
                                            .lineLimit(nil)
                                            .fixedSize(horizontal: false, vertical: true)
                                            .frame(maxWidth: .infinity, alignment: .leading)

                                        Text("• 서비스 제공에 접근 권한이 필요한 경우에만 동의를 받고 있으며, 허용하지 않으셔도 서비스 이용이 가능하나 기능 사용에 제한이 있을 수 있습니다.")
                                            .font(Font.PermissionStyle.smallCaption)
                                            .foregroundColor(Color.PermissionStyle.secondaryText)
                                            .multilineTextAlignment(.leading)
                                            .lineLimit(nil)
                                            .fixedSize(horizontal: false, vertical: true)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .padding(PermissionSpacing.medium)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                )
                                .frame(minHeight: PermissionSpacing.noticeMinHeight)

                        }
                        
                        // 버튼 영역만큼 여백 추가 (하단 버튼과 겹치지 않도록)
                        Spacer(minLength: 100)
                    }
                    .padding(.horizontal, PermissionSpacing.large)
                }
                
                // 하단 고정 버튼
                VStack(spacing: 0) {
    
                    VStack(spacing: PermissionSpacing.medium) {
                        Button(action: {
                            requestPermissions()
                        }) {
                            Text(isPermissionRequested ? "권한 요청 중..." : "확인")
                                .font(Font.PermissionStyle.buttonText)
                                .foregroundColor(Color.PermissionStyle.buttonText)
                                .frame(maxWidth: .infinity)
                                .frame(height: PermissionSpacing.buttonHeight)
                                .background(Color.PermissionStyle.buttonBackground)
                                .cornerRadius(PermissionSpacing.cornerRadius)
                        }
                        .disabled(isPermissionRequested)
                        .padding(.horizontal, PermissionSpacing.large)
                        .padding(.top, PermissionSpacing.medium)
                        .padding(.bottom, PermissionSpacing.extraLarge)
                    }
                    .background(Color.PermissionStyle.background)
                }
            }
        }
        .background(Color.PermissionStyle.background)
        .onAppear {
            checkInitialPermissions()
        }
    }
    
    private func checkInitialPermissions() {
        PermissionHelper.checkAllPermissions { allGranted in
            if allGranted {
                Logger.info("이미 모든 권한이 승인되어 있습니다.")
                onPermissionsGranted()
            }
        }
    }
    
    private func requestPermissions() {
        Logger.info("권한 요청을 시작합니다.")
        isPermissionRequested = true
        
        PermissionHelper.requestAllPermissions { success in
            Logger.info("권한 요청 완료: \(success)")
            
            // 권한 요청 화면을 보여줬음을 기록
            PermissionHelper.markPermissionRequestShown()
            
            // 권한이 거부되어도 진행 (선택적 권한이므로)
            onPermissionsGranted()
        }
    }
}

struct PermissionItemView: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: PermissionSpacing.medium) {
            Image(systemName: icon)
                .font(Font.PermissionStyle.iconSize)
                .foregroundColor(Color.PermissionStyle.secondaryText)
                .frame(width: PermissionSpacing.iconSize, height: PermissionSpacing.iconSize)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(Font.PermissionStyle.itemTitle)
                    .foregroundColor(Color.PermissionStyle.primaryText)
                
                Text(description)
                    .font(Font.PermissionStyle.itemDescription)
                    .foregroundColor(Color.PermissionStyle.secondaryText)
            }
            
            Spacer()
        }
    }
}

#Preview {
    PermissionView {
        print("권한 승인 완료")
    }
}
