import Foundation

/**
 * 에러 데이터 클래스
 * 네트워크 API 에러 정보를 담는 데이터 클래스
 */
class ErrorData {
    private var code: Int = 0
    private var msg: String?
    
    init(code: Int, msg: String?) {
        self.code = code
        self.msg = msg
    }
    
    func getCode() -> Int {
        return code
    }
    
    func setCode(code: Int) {
        self.code = code
    }
    
    func getMsg() -> String? {
        return msg
    }
    
    func setMsg(msg: String?) {
        self.msg = msg
    }
}
