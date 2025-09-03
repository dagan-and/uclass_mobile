import Foundation


class AppUtil {
    static func getExceptionLog(_ error: Error) -> String {
        return error.localizedDescription
    }
}
