import SwiftUI

struct DetailScreen: View {
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    HeaderView()
                    DetailInfoCard()
                    FeaturesList()
                    Spacer()
                }
                .padding()
            }
            .navigationTitle("📄 상세 정보")
            .navigationBarTitleDisplayMode(.large)
        }
    }
}

struct HeaderView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(.blue)
            
            Text("상세 정보")
                .font(.title2)
                .fontWeight(.bold)
        }
    }
}

struct DetailInfoCard: View {
    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "doc.text")
                        .foregroundColor(.blue)
                    Text("설명")
                        .font(.headline)
                    Spacer()
                }
                
                Text("이것은 상세 화면입니다. 하단 네비게이션을 통해 홈 화면으로 이동하여 네이버 웹사이트를 확인할 수 있습니다.")
                    .font(.body)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct FeaturesList: View {
    private let features = [
        ("홈", "house.fill", "네이버 웹사이트를 확인할 수 있습니다"),
        ("로그인", "person.circle.fill", "카카오, 네이버, Apple 로그인을 지원합니다"),
        ("네비게이션", "arrow.left.arrow.right", "하단 탭을 통해 화면을 전환할 수 있습니다")
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("주요 기능")
                .font(.headline)
            
            LazyVStack(spacing: 12) {
                ForEach(features, id: \.0) { feature in
                    FeatureRow(
                        title: feature.0,
                        icon: feature.1,
                        description: feature.2
                    )
                }
            }
        }
    }
}

struct FeatureRow: View {
    let title: String
    let icon: String
    let description: String
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.blue)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.leading)
            }
            
            Spacer()
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

