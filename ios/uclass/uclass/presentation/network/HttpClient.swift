import Foundation
import Alamofire

/**
 * HTTP 클라이언트 클래스
 * Alamofire를 래핑하여 빌더 패턴으로 HTTP 요청을 구성합니다.
 */
class HttpClient {
    
    struct HTTPResponse {
        let statusCode: Int
        let statusMessage: String
        let body: String
        let headers: [String: String]
        
        var isSuccessful: Bool {
            return statusCode >= 200 && statusCode < 300
        }
    }
    
    private let request: DataRequest
    
    private init(request: DataRequest) {
        self.request = request
    }
    
    class Builder {
        private var url: String = ""
        private var addJWTToken: Bool = true
        private var jsonData: String = ""
        private var isPost: Bool = true
        private var isLogging: Bool = false
        private var requestBody: Data?
        private var parameters: [String: Any]?
        private var headers: [String: String]?
        private var timeout: TimeInterval = 15
        private var logListener: NetworkAPI.LogListener?
        
        func setUrl(url: String) -> Builder {
            self.url = url
            return self
        }
        
        func addJWTToken(addToken: Bool) -> Builder {
            self.addJWTToken = addToken
            return self
        }
        
        func setJsonData(data: String) -> Builder {
            self.jsonData = data
            return self
        }
        
        func isPost(isPost: Bool) -> Builder {
            self.isPost = isPost
            return self
        }
        
        func enableLogging(isLogging: Bool) -> Builder {
            self.isLogging = isLogging
            return self
        }
        
        func setBody(body: Data) -> Builder {
            self.requestBody = body
            return self
        }
        
        func setParameters(parameters: [String: String]) -> Builder {
            self.parameters = parameters
            return self
        }
        
        func setHeaders(headers: [String: String]) -> Builder {
            self.headers = headers
            return self
        }
        
        func setTimeout(timeout: TimeInterval) -> Builder {
            self.timeout = timeout
            return self
        }
        
        func setLogListener(logListener: NetworkAPI.LogListener) -> Builder {
            self.logListener = logListener
            return self
        }
        
        func build() throws -> HttpClient {
            guard !url.isEmpty else {
                throw NSError(domain: "HttpClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "URL is empty"])
            }
            
            // HTTP Headers 설정
            var httpHeaders: HTTPHeaders = [
                "User-Agent": "iOS"
            ]
            
            // JWT 토큰 헤더 추가
            if addJWTToken, let jwtToken = Constants.jwtToken, !jwtToken.isEmpty {
                httpHeaders["JWT_TOKEN"] = jwtToken
                httpHeaders["Authorization"] = jwtToken
            } else {
                httpHeaders["JWT_TOKEN"] = ""
            }
            
            // 추가 헤더 설정
            headers?.forEach { key, value in
                httpHeaders[key] = value
            }
            
            // Session 설정
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = timeout
            configuration.timeoutIntervalForResource = timeout
            
            var interceptors: [RequestInterceptor] = []
            
            // 로깅 설정
            if Constants.isDebug && isLogging {
                let eventMonitor = ClosureEventMonitor()
                eventMonitor.requestDidFinish = { request in
                    if let logListener = self.logListener {
                        logListener.onLog(message: "Request: \(request.description)")
                    }
                }
                eventMonitor.requestDidCompleteTaskWithError = { request, task, error in
                    if let error = error, let logListener = self.logListener {
                        logListener.onLog(message: "Request Error: \(error.localizedDescription)")
                    }
                }
            }
            
            let session = Session(
                configuration: configuration,
                interceptor: interceptors.isEmpty ? nil : Interceptor(interceptors: interceptors)
            )
            
            let method: HTTPMethod = isPost ? .post : .get
            
            var request: DataRequest
            
            if isPost {
                // POST 요청 처리
                if !jsonData.isEmpty {
                    // JSON 데이터가 있는 경우
                    httpHeaders["Content-Type"] = "application/json"
                    
                    request = session.request(
                        url,
                        method: method,
                        parameters: jsonData.data(using: .utf8),
                        headers: httpHeaders
                    )
                } else if let body = requestBody {
                    // 커스텀 바디가 있는 경우
                    request = session.upload(
                        body,
                        to: url,
                        method: method,
                        headers: httpHeaders
                    )
                } else if let params = parameters {
                    // 파라미터가 있는 경우
                    request = session.request(
                        url,
                        method: method,
                        parameters: params,
                        encoding: JSONEncoding.default,
                        headers: httpHeaders
                    )
                } else {
                    // 바디 없는 POST 요청
                    request = session.request(
                        url,
                        method: method,
                        headers: httpHeaders
                    )
                }
            } else {
                // GET 요청 처리
                request = session.request(
                    url,
                    method: method,
                    parameters: parameters,
                    encoding: URLEncoding.default,
                    headers: httpHeaders
                )
            }
            
            return HttpClient(request: request)
        }
    }
    
    func execute(completion: @escaping (Result<HTTPResponse, Error>) -> Void) {
        request.responseString { response in
            switch response.result {
            case .success(let body):
                let httpResponse = HTTPResponse(
                    statusCode: response.response?.statusCode ?? -1,
                    statusMessage: HTTPURLResponse.localizedString(forStatusCode: response.response?.statusCode ?? -1),
                    body: body,
                    headers: response.response?.allHeaderFields.reduce(into: [String: String]()) { result, item in
                        if let key = item.key as? String, let value = item.value as? String {
                            result[key] = value
                        }
                    } ?? [:]
                )
                completion(.success(httpResponse))
                
            case .failure(let error):
                completion(.failure(error))
            }
        }
    }
    
    func getRequest() -> DataRequest {
        return request
    }
}
