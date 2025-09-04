import Foundation

// MARK: - Logger
/**
 * 로깅 유틸리티 클래스
 */
class Logger {
    static func dev(_ message: String) {
        if Constants.isDebug {
            print("[DEV] \(message)")
        }
    }
    
    static func error(_ error: Error) {
        if Constants.isDebug {
            print("[ERROR] \(error.localizedDescription)")
        }
    }
    
    static func error(_ message: String) {
        if Constants.isDebug {
            print("[ERROR] \(message)")
        }
    }
    
    static func isEnable() -> Bool {
        return Constants.isDebug
    }
}
