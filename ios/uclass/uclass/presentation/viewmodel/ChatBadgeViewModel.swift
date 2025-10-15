import Foundation
import Combine
import UIKit

class ChatBadgeViewModel: ObservableObject {
    static let shared = ChatBadgeViewModel()
    
    @Published var showChatBadge: Bool = false
    
    private init() {
        let count = UIApplication.shared.applicationIconBadgeNumber
        if(count > 0) {
            DispatchQueue.main.async {
                self.showChatBadge = true
            }
        }
    }
    
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
