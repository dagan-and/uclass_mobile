import SwiftUI

struct ChatScreen: View {
    var body: some View {
        NavigationView {
            VStack {
                Text("채팅")
                    .font(.largeTitle)
                    .padding()
                
                Text("채팅이 여기에 표시됩니다.")
                    .foregroundColor(.gray)
                
                Spacer()
            }
            .navigationTitle("채팅")
        }
    }
}
