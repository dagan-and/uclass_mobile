import SwiftUI

// MARK: - Loading Data Models
struct LoadingData {
    let id = UUID()
    
    enum LoadingStyle {
        case spinner           // 기본 스피너
    }
}

// MARK: - Loading Manager (ObservableObject)
class CustomLoadingManager: ObservableObject {
    static let shared = CustomLoadingManager()
    private init() {}
    
    @Published var currentLoading: LoadingData?
    @Published var isPresented: Bool = false
    
    // 기본 로딩
    func showLoading(
        style: LoadingData.LoadingStyle = .spinner
    ) {
        let loading = LoadingData()
        
        DispatchQueue.main.async {
            self.currentLoading = loading
            self.isPresented = true
        }
    }

    func hideLoading() {
        DispatchQueue.main.async {
            self.isPresented = false
            // 애니메이션이 끝난 후 데이터 초기화
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                self.currentLoading = nil
            }
        }
    }
}

// MARK: - Loading Animation Views

// 스피너 애니메이션
struct SpinnerView: View {
    @State private var rotation: Double = 0
        @State private var trimStart: CGFloat = 0.2
        @State private var trimEnd: CGFloat = 1.0
        
        var body: some View {
            Circle()
                .trim(from: trimStart, to: trimEnd)
                .stroke(
                    AngularGradient(
                        gradient: Gradient(colors: [Color.green.opacity(0.3), Color.green]),
                        center: .center
                    ),
                    style: StrokeStyle(lineWidth: 6, lineCap: .round)
                )
                .frame(width: 60, height: 60)
                .rotationEffect(.degrees(rotation))
                .onAppear {
                    // 빠른 회전
                    withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) {
                        rotation = 360
                    }
                    // 시작점과 끝점이 함께 움직여서 호가 늘어났다 줄어들었다 하는 효과
                    withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                        trimStart = 0.8
                        trimEnd = 0.9
                    }
                }
        }
}

// MARK: - Custom Loading View
struct CustomLoadingView: View {
    let loadingData: LoadingData
    let onCancel: () -> Void
    
    var body: some View {
        ZStack {
            // 반투명 배경
            Color.black.opacity(0.4)
                .ignoresSafeArea(.all)
            
            // 로딩 컨테이너
            VStack(spacing: 20) {
                // 로딩 애니메이션
                loadingAnimationView
            }
            .frame(maxWidth: 280)
        }
        .transition(.opacity.combined(with: .scale(scale: 0.8)))
        .animation(.easeInOut(duration: 0.3), value: true)
    }
    
    @ViewBuilder
    private var loadingAnimationView: some View {
        SpinnerView()
    }
}

// MARK: - Backdrop Modifier (iOS 15+ 스타일)
extension View {
    func backdrop(blur radius: CGFloat) -> some View {
        self.background(
            Rectangle()
                .fill(.ultraThinMaterial)
                .blur(radius: radius / 2)
        )
    }
}

// MARK: - Loading Container View (앱의 최상위에 배치)
struct LoadingContainer: View {
    @StateObject private var loadingManager = CustomLoadingManager.shared
    
    var body: some View {
        ZStack {
            if loadingManager.isPresented, let loadingData = loadingManager.currentLoading {
                CustomLoadingView(loadingData: loadingData) {
                    loadingManager.hideLoading()
                }
            }
        }
    }
}

// MARK: - SwiftUI Extensions
extension View {
    /// SwiftUI View에서 쉽게 Loading을 호출할 수 있는 extension
    func showLoading(message: String? = nil) {
        CustomLoadingManager.shared.showLoading()
    }
    
    func hideLoading() {
        CustomLoadingManager.shared.hideLoading()
    }
}

// MARK: - 앱 진입점에서 사용하는 방법
/*
@main
struct MyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .overlay(AlertContainer())   // Alert 컨테이너
                .overlay(LoadingContainer()) // Loading 컨테이너 추가
        }
    }
}
*/
