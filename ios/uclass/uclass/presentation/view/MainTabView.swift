import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var webViewManager: WebViewManager
    @State private var selectedTab = 0
    
    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // 메인 컨텐츠 - Fade 애니메이션
                ZStack {
                    if selectedTab == 0 {
                        WebViewScreen()
                            .transition(.opacity)
                            .zIndex(1)
                    }
                    
                    if selectedTab == 1 {
                        DetailScreen()
                            .transition(.opacity)
                            .zIndex(1)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .animation(.easeInOut(duration: 0.2), value: selectedTab)
                
                // 커스텀 하단 바
                CustomBottomBar(selectedTab: $selectedTab)
            }
        }
        .background(Color.white)
        .navigationBarHidden(true)
    }
}

struct CustomBottomBar: View {
    @Binding var selectedTab: Int
    
    var body: some View {
        HStack(spacing: 0) {
            
            // Home 화면 버튼 (네이버 웹)
            Button(action: { selectedTab = 0 }) {
                VStack(spacing: 4) {
                    Image(systemName: "house.fill")
                        .font(.system(size: 20))
                    Text("홈")
                        .font(.caption)
                }
                .foregroundColor(selectedTab == 0 ? .blue : .gray)
            }
            .frame(maxWidth: .infinity)
            
            // Detail 화면 버튼 (샘플 페이지)
            Button(action: { selectedTab = 1 }) {
                VStack(spacing: 4) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 20))
                    Text("상세")
                        .font(.caption)
                }
                .foregroundColor(selectedTab == 1 ? .blue : .gray)
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
