import Foundation
import Combine

class ChatBadgeViewModel: ObservableObject {
    static let shared = ChatBadgeViewModel()
    
    @Published var showChatBadge: Bool = false
    
    private init() {}
    
    func showBadge() {
        DispatchQueue.main.async {
            self.showChatBadge = true
        }
    }
    
    func hideBadge() {
        DispatchQueue.main.async {
            self.showChatBadge = false
        }
    }
}
