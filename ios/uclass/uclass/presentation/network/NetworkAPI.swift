import Foundation
import Alamofire

/**
 * 싱글톤 패턴으로 구현된 네트워크 API 클래스
 * 모든 네트워크 요청을 담당하며, 결과는 NetworkAPIManager를 통해 콜백으로 전달됩니다.
 */
class NetworkAPI {
    static let shared = NetworkAPI()
    
    private var operationQueue: OperationQueue?
    private var sessionManager: Session?
    private var isInitialized = false
    
    private init() {}
    
    protocol LogListener: AnyObject {
        func onLog(message: String)
    }
    
    /**
     * NetworkAPI 초기화
     * 앱 시작 시 한 번만 호출하면 됩니다.
     */
    func initialize() {
        if !isInitialized {
            self.operationQueue = OperationQueue()
            self.operationQueue?.maxConcurrentOperationCount = OperationQueue.defaultMaxConcurrentOperationCount
            
            // Alamofire Session 설정
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = 15
            configuration.timeoutIntervalForResource = 15
            
            var interceptors: [RequestInterceptor] = []
            
            // 로깅 인터셉터 추가 (디버그 모드에서)
            if Constants.isDebug {
                let logger = NetworkLogger()
                interceptors.append(logger)
            }
            
            self.sessionManager = Session(
                configuration: configuration,
                interceptor: Interceptor(interceptors: interceptors)
            )
            
            self.isInitialized = true
            Logger.dev("NetworkAPI initialized")
        }
    }
    
    /**
     * 초기화 여부 확인
     */
    func getIsInitialized() -> Bool {
        return isInitialized
    }
    
    /**
     * 에러 발생 시 NetworkAPIManager를 통해 콜백 전달
     */
    private func sendError(code: Int, error: Error) {
        let errorData = ErrorData(code: code, msg: AppUtil.getExceptionLog(error))
        NetworkAPIManager.shared.notifyResult(code: NetworkAPIManager.ResponseCode.API_ERROR, result: errorData)
    }
    
    /**
     * 에러 발생 시 NetworkAPIManager를 통해 콜백 전달
     */
    private func sendError(code: Int, error: String) {
        let errorData = ErrorData(code: code, msg: error)
        NetworkAPIManager.shared.notifyResult(code: NetworkAPIManager.ResponseCode.API_ERROR, result: errorData)
    }
    
    /**
     * 성공 결과를 NetworkAPIManager를 통해 콜백 전달
     */
    private func sendCallback(code: Int, data: Any?) {
        NetworkAPIManager.shared.notifyResult(code: code, result: data)
    }
    
    private func logRequest(request: Any) {
        if Logger.isEnable() {
            do {
                let jsonData = try JSONSerialization.data(withJSONObject: request, options: .prettyPrinted)
                if let prettyJsonString = String(data: jsonData, encoding: .utf8) {
                    Logger.dev("REQUEST: \(prettyJsonString)")
                }
            } catch {
                Logger.error(error)
            }
        }
    }
    
    private func logResponse(json: String) {
        if Logger.isEnable() {
            do {
                if let jsonData = json.data(using: .utf8) {
                    let parsedJson = try JSONSerialization.jsonObject(with: jsonData, options: [])
                    if let prettyJsonData = try? JSONSerialization.data(withJSONObject: parsedJson, options: .prettyPrinted),
                       let prettyJsonString = String(data: prettyJsonData, encoding: .utf8) {
                        Logger.dev("RESPONSE: \(prettyJsonString)")
                    }
                }
            } catch {
                Logger.error(error)
                Logger.dev("RESPONSE (Raw): \(json)")
            }
        }
    }
    
    /**
     * 초기화 체크
     */
    private func checkInitialized() {
        if !isInitialized {
            fatalError("NetworkAPI is not initialized. Call initialize() first.")
        }
    }
    
    /**
     * 인증 스토어 초기화 API
     */
    func authInitStore(version: String) {
        checkInitialized()
        
        operationQueue?.addOperation { [weak self] in
            guard let self = self else { return }
            
            let url = Constants.baseURL + NetworkAPIManager.Endpoint.AUTH_INIT_STORE
            let responseCode = NetworkAPIManager.ResponseCode.API_AUTH_INIT_STORE
            
            var headers: HTTPHeaders = [
                "User-Agent": "iOS"
            ]
            
            // JWT 토큰 헤더 추가
            if let jwtToken = Constants.jwtToken, !jwtToken.isEmpty {
                headers["JWT_TOKEN"] = jwtToken
                headers["Authorization"] = jwtToken
            } else {
                headers["JWT_TOKEN"] = ""
            }
            
            let parameters = ["version": version]
            
            self.sessionManager?.request(
                url,
                method: .get,
                parameters: parameters,
                headers: headers
            ).responseString { [weak self] response in
                guard let self = self else { return }
                
                switch response.result {
                case .success(let responseBody):
                    self.logResponse(json: responseBody)
                    
                    if let statusCode = response.response?.statusCode,
                       statusCode >= 200 && statusCode < 300 {
                        
                        if !responseBody.isEmpty {
                            do {
                                if let jsonData = responseBody.data(using: .utf8) {
                                    let parsedResponse = try JSONSerialization.jsonObject(with: jsonData, options: [])
                                    self.sendCallback(code: responseCode, data: parsedResponse)
                                } else {
                                    self.sendError(code: responseCode, error : "body parsing error")
                                }
                            } catch {
                                self.sendError(code: responseCode, error: error)
                            }
                        } else {
                            self.sendError(code: responseCode, error: "Empty response body")
                        }
                    } else {
                        let statusCode = response.response?.statusCode ?? -1
                        let statusMessage = HTTPURLResponse.localizedString(forStatusCode: statusCode)
                        let errorData = ErrorData(
                            code: responseCode,
                            msg: "HTTP \(statusCode): \(statusMessage)"
                        )
                        self.sendCallback(code: NetworkAPIManager.ResponseCode.API_ERROR, data: errorData)
                    }
                    
                case .failure(let error):
                    self.sendError(code: responseCode, error: error)
                }
            }
        }
    }
    
    /**
     * NetworkAPI 종료
     * 앱 종료 시 호출하여 리소스를 정리합니다.
     */
    func shutdown() {
        operationQueue?.cancelAllOperations()
        operationQueue = nil
        sessionManager?.session.invalidateAndCancel()
        sessionManager = nil
        isInitialized = false
        Logger.dev("NetworkAPI shutdown")
    }
}

// MARK: - Network Logger (Alamofire Interceptor)
private class NetworkLogger: RequestInterceptor {
    func adapt(_ urlRequest: URLRequest, for session: Session, completion: @escaping (Result<URLRequest, Error>) -> Void) {
        completion(.success(urlRequest))
    }
    
    func retry(_ request: Request, for session: Session, dueTo error: Error, completion: @escaping (RetryResult) -> Void) {
        completion(.doNotRetry)
    }
}
