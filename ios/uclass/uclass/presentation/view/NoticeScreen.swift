import SwiftUI

struct NoticeScreen: View {
    var body: some View {
        NavigationView {
            VStack {
                Text("공지사항")
                    .font(.largeTitle)
                    .padding()
                
                Text("공지사항이 여기에 표시됩니다.")
                    .foregroundColor(.gray)
                
                Spacer()
            }
            .navigationTitle("공지사항")
        }
    }
}
