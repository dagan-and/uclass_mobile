import Foundation
import UIKit

// MARK: - Logger
/**
 * 로깅 유틸리티 클래스 (파일 저장 및 공유 기능 포함)
 */
class Logger {
    private static let logFileName = "app_logs.txt"
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
    
    // 파일 쓰기 작업을 위한 전용 큐
    private static let fileWriteQueue = DispatchQueue(label: "logger.filewrite", qos: .utility)
    
    private static var logFileURL: URL? {
        guard let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return nil
        }
        return documentsDirectory.appendingPathComponent(logFileName)
    }
    
    // MARK: - Public Methods
    static func dev(_ message: String) {
        let logMessage = "[DEV] \(message)"
        if Constants.isDebug {
            print(logMessage)
        }
        writeToFile(logMessage)
    }
    
    static func error(_ error: Error) {
        let logMessage = "[ERROR] \(error.localizedDescription)"
        if Constants.isDebug {
            print(logMessage)
        }
        writeToFile(logMessage)
    }
    
    static func error(_ message: String) {
        let logMessage = "[ERROR] \(message)"
        if Constants.isDebug {
            print(logMessage)
        }
        writeToFile(logMessage)
    }
    
    static func info(_ message: String) {
        let logMessage = "[INFO] \(message)"
        if Constants.isDebug {
            print(logMessage)
        }
        writeToFile(logMessage)
    }
    
    static func warning(_ message: String) {
        let logMessage = "[WARNING] \(message)"
        if Constants.isDebug {
            print(logMessage)
        }
        writeToFile(logMessage)
    }
    
    static func isEnable() -> Bool {
        return Constants.isDebug
    }
    
    // MARK: - File Operations
    
    /**
     * 로그를 파일에 안전하게 저장
     */
    private static func writeToFile(_ message: String) {
        guard let logFileURL = logFileURL else { return }
        
        let timestamp = dateFormatter.string(from: Date())
        let logEntry = "[\(timestamp)] \(message)\n"
        
        fileWriteQueue.async {
            do {
                if FileManager.default.fileExists(atPath: logFileURL.path) {
                    // 기존 파일에 안전하게 추가
                    let fileHandle = try FileHandle(forWritingTo: logFileURL)
                    defer { fileHandle.closeFile() }
                    
                    fileHandle.seekToEndOfFile()
                    if let data = logEntry.data(using: .utf8) {
                        fileHandle.write(data)
                    }
                } else {
                    // 새 파일 생성
                    try logEntry.write(to: logFileURL, atomically: true, encoding: .utf8)
                }
            } catch {
                print("[Logger] 파일 쓰기 실패: \(error.localizedDescription)")
            }
        }
    }
    
    /**
     * 로그 파일 내용 읽기
     */
    static func getLogContent() -> String? {
        guard let logFileURL = logFileURL else { return nil }
        
        do {
            return try String(contentsOf: logFileURL, encoding: .utf8)
        } catch {
            print("[Logger] 파일 읽기 실패: \(error.localizedDescription)")
            return nil
        }
    }
    
    /**
     * 로그 파일 안전하게 삭제
     */
    static func clearLogs() {
        guard let logFileURL = logFileURL else { return }
        
        fileWriteQueue.async {
            do {
                if FileManager.default.fileExists(atPath: logFileURL.path) {
                    try FileManager.default.removeItem(at: logFileURL)
                    DispatchQueue.main.async {
                        print("[Logger] 로그 파일 삭제 완료")
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    print("[Logger] 파일 삭제 실패: \(error.localizedDescription)")
                }
            }
        }
    }
    
    /**
     * 로그 파일 크기 확인 (바이트 단위)
     */
    static func getLogFileSize() -> Int64 {
        guard let logFileURL = logFileURL else { return 0 }
        
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: logFileURL.path)
            return attributes[.size] as? Int64 ?? 0
        } catch {
            return 0
        }
    }
    
    /**
     * 로그 파일을 외부 앱과 공유 (개선된 버전)
     */
    static func shareLogFile() {
        // 먼저 파일 존재 여부 확인
        guard let logFileURL = logFileURL else {
            Logger.error("로그 파일 URL을 생성할 수 없음")
            return
        }
        
        // 파일 쓰기 작업이 완료될 때까지 잠시 대기
        fileWriteQueue.sync {
            // 파일 존재 여부 재확인
            guard FileManager.default.fileExists(atPath: logFileURL.path) else {
                DispatchQueue.main.async {
                    Logger.error("공유할 로그 파일이 없음")
                }
                return
            }
            
            // 파일 읽기 권한 확인
            guard FileManager.default.isReadableFile(atPath: logFileURL.path) else {
                DispatchQueue.main.async {
                    Logger.error("로그 파일을 읽을 수 없음")
                }
                return
            }
            
            DispatchQueue.main.async {
                // ActivityViewController 생성
                let activityViewController = UIActivityViewController(
                    activityItems: [logFileURL],
                    applicationActivities: nil
                )
                
                // 공유 완료 후 처리
                activityViewController.completionWithItemsHandler = { activityType, completed, returnedItems, error in
                    if let error = error {
                        Logger.error("공유 중 오류 발생: \(error.localizedDescription)")
                        return
                    }
                    
                    if completed {
                        Logger.dev("로그 파일 공유 완료 - 초기화 진행")
                        // 0.5초 후에 삭제 (공유가 완전히 끝날 때까지 대기)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                            clearLogs()
                        }
                    } else {
                        Logger.dev("로그 파일 공유 취소됨")
                    }
                }
                
                // 최상위 UIViewController 가져오기
                if let topVC = UIApplication.shared.topViewController() {
                    // iPad 대응
                    if let popover = activityViewController.popoverPresentationController {
                        popover.sourceView = topVC.view
                        popover.sourceRect = CGRect(
                            x: topVC.view.bounds.midX,
                            y: topVC.view.bounds.midY,
                            width: 0,
                            height: 0
                        )
                        popover.permittedArrowDirections = []
                    }
                    
                    topVC.present(activityViewController, animated: true)
                } else {
                    Logger.error("topViewController를 찾을 수 없음")
                }
            }
        }
    }
    
    /**
     * 로그 파일 정보 가져오기
     */
    static func getLogFileInfo() -> (exists: Bool, size: String, path: String?) {
        guard let logFileURL = logFileURL else {
            return (false, "0 bytes", nil)
        }
        
        let exists = FileManager.default.fileExists(atPath: logFileURL.path)
        let sizeInBytes = getLogFileSize()
        let sizeString = ByteCountFormatter.string(fromByteCount: sizeInBytes, countStyle: .file)
        
        return (exists, sizeString, logFileURL.path)
    }
    
    /**
     * 로그 파일이 너무 클 경우 오래된 로그 삭제 (선택사항)
     */
    static func rotateLogIfNeeded(maxSizeInMB: Double = 10.0) {
        let maxSizeInBytes = Int64(maxSizeInMB * 1024 * 1024)
        
        if getLogFileSize() > maxSizeInBytes {
            Logger.info("로그 파일이 너무 큼 - 회전 처리")
            
            // 현재 로그의 절반만 유지
            if let content = getLogContent() {
                let lines = content.components(separatedBy: "\n")
                let halfPoint = lines.count / 2
                let newContent = lines[halfPoint...].joined(separator: "\n")
                
                fileWriteQueue.async {
                    guard let logFileURL = logFileURL else { return }
                    do {
                        try newContent.write(to: logFileURL, atomically: true, encoding: .utf8)
                        DispatchQueue.main.async {
                            Logger.info("로그 파일 회전 완료")
                        }
                    } catch {
                        DispatchQueue.main.async {
                            Logger.error("로그 파일 회전 실패: \(error.localizedDescription)")
                        }
                    }
                }
            }
        }
    }
}
