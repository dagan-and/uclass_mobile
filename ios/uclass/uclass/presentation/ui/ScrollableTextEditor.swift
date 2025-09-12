import SwiftUI

struct ScrollableTextEditor: View {
    @State private var text: String = ""
    private let maxLines: Int = 6
    private let font: UIFont = .systemFont(ofSize: 16)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("내용을 입력하세요 (최대 6줄 높이, 이후 스크롤)")
                .font(.headline)

            TextEditor(text: $text)
                .font(.system(size: 16))
                .frame(
                    minHeight: font.lineHeight, // 최소 1줄
                    maxHeight: font.lineHeight * CGFloat(maxLines) // 최대 6줄 높이
                )
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.gray.opacity(0.4))
                )

            Spacer()
        }
        .padding()
    }
}
