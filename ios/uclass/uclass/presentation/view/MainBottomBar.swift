import SwiftUI

struct MainBottomBar: View {
    @Binding var selectedTab: Int
    @Binding var showChatBadge: Bool // 쪽지 뱃지 표시 여부
    var onChatTap: () -> Void // 채팅 탭 클릭 콜백
    
    let topPadding: CGFloat = 6
    let iconPadding : CGFloat = 4
    let iconSize: CGFloat = 22
    let fontSize: CGFloat = 16
    let badgeSize: CGFloat = 12
    
    var body: some View {
        HStack(spacing: 0) {

            // Home 화면 버튼 (네이버 웹)
            Button(action: { selectedTab = 0 }) {
                VStack(spacing: 4) {
                    Spacer().frame(height: topPadding)
                    Image(selectedTab == 0 ? "navi_home_on" : "navi_home_off")
                        .resizable()
                        .frame(width: iconSize, height: iconSize)
                        .scaledToFit()
                    Spacer().frame(height: iconPadding)
                    Text("홈")
                        .font(.system(size: fontSize))
                }
                .foregroundColor(selectedTab == 0 ? .black : .gray)
            }
            .frame(maxWidth: .infinity)

            // 채팅 화면 버튼 (뱃지 포함)
            Button(action: onChatTap) {
                ZStack {
                    VStack(spacing: 4) {
                        Spacer().frame(height: topPadding)
                        ZStack {
                            Image(selectedTab == 1 ? "navi_chat_on" : "navi_chat_off")
                                .resizable()
                                .frame(width: iconSize, height: iconSize)
                                .scaledToFit()
                            
                            // 뱃지 아이콘 - 이미지 위에 직접 배치
                            if showChatBadge {
                                Image("navi_icon_new")
                                    .resizable()
                                    .frame(width: badgeSize, height: badgeSize)
                                    .scaledToFit()
                                    .offset(x: 8, y: -8) // 아이콘 우상단에 배치
                            }
                        }
                        Spacer().frame(height: iconPadding)
                        Text("쪽지")
                            .font(.system(size: fontSize))
                    }
                    .foregroundColor(selectedTab == 1 ? .black : .gray)
                }
            }
            .frame(maxWidth: .infinity)

            // 공지사항 화면 버튼
            Button(action: {
                selectedTab = 2
            }) {
                VStack(spacing: 4) {
                    Spacer().frame(height: topPadding)
                    Image(selectedTab == 2 ? "navi_info_on" : "navi_info_off")
                        .resizable()
                        .frame(width: iconSize, height: iconSize)
                        .scaledToFit()
                    Spacer().frame(height: iconPadding)
                    Text("공지사항")
                        .font(.system(size: fontSize))
                }
                .foregroundColor(selectedTab == 2 ? .black : .gray)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 20)
        .background(Color.white)
        .overlay(
            Rectangle()
                .frame(height: 0.5)
                .foregroundColor(Color(.separator)),
            alignment: .top
        )
        .shadow(color: Color.black.opacity(0.1), radius: 5, x: 0, y: -2)
    }
}
