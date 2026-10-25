# ADR-0009: 성공 응답도 공통 형식(`SuccessResponse<T>`)으로 감싼다

- 상태: 채택 (2026-10-25)
- 범위: `libs:platform-core`, 모든 서비스의 외부·내부 HTTP API

## 배경
- 실패 응답은 `ErrorResponse`(`code`, `message`, `path`, `errors`, `timestamp`)로 통일돼 있었지만 성공 응답은 형식이 없었다.
- API 문서(Swagger/OpenAPI)와 클라이언트 처리를 일관되게 하려면 성공 응답 구조도 정해져 있어야 한다.

## 결정
1. 성공 응답은 `io.aegis.platform.core.response.SuccessResponse<T>`로 감싼다.
   ```json
   { "timestamp": "2026-10-25T03:00:00Z", "code": "SUCCESS", "data": { ... } }
   { "timestamp": "2026-10-25T03:00:00Z", "code": "SUCCESS" }          ← 결과가 없을 때(SuccessResponse.empty())
   ```
   - 성공 메시지(`message`)는 넣지 않는다. 성공 여부는 `code`로 충분하고, 고정 문구는 정보가 없다.
   - `data`는 nullable이고 값이 없으면 **필드 자체를 뺀다**(`@JsonInclude(NON_NULL)`). `null`을 내려보내지 않는다. 이를 위해 `platform-core`가 `jackson-annotations`에 의존한다(Jackson 2·3 공통 어노테이션 패키지).
   - `code`, `timestamp` 이름은 `ErrorResponse`와 같고 **필드 순서도 `timestamp`, `code`가 앞에 오도록 맞춘다**. 클라이언트는 `code` 하나로 성공/실패를 읽는다.
     ```json
     { "timestamp": "...", "code": "NOT_FOUND", "message": "...", "path": "/...", "errors": [] }
     ```
2. 컨트롤러가 **반환 타입으로 직접** 쓴다(`fun issue(): SuccessResponse<TokenResponse>`). `ResponseBodyAdvice`로 자동으로 감싸지 않는다.
   - 반환 타입에 구조가 드러나야 OpenAPI 문서가 실제 응답과 같아진다. 자동 래핑은 문서와 실제 응답이 어긋난다.
3. 이름은 `ApiResponse`가 아니라 `SuccessResponse`다. Swagger 어노테이션(`io.swagger.v3.oas.annotations.responses.ApiResponse`)과 이름이 겹치지 않게 하고 `ErrorResponse`와 짝을 맞춘다.
4. **예외: 표준 형식이 정해진 응답은 감싸지 않는다.** 현재는 JWKS(`/api/v1/auth/jwks`, RFC 7517 JWK Set) 하나다. gateway가 nimbus `JWKSet.parse`로 그대로 읽는다.

## 기각한 대안
- **감싸지 않음(HTTP 상태 코드 + 실패 시만 `ErrorResponse`)**: 단순하지만 응답마다 구조가 달라 문서와 클라이언트 처리가 제각각이 된다.
- **`ResponseBodyAdvice` 자동 래핑**: 컨트롤러 코드는 짧아지지만 반환 타입과 실제 응답이 달라 OpenAPI 문서가 틀린다. 표준 형식 예외 처리도 숨겨진다.

## 결과
- 서비스 간 호출에서 응답을 받는 쪽은 `data`를 꺼내야 한다. `platform-http-client-starter`의 성공 응답 언래핑은 **처음으로 감싼 응답을 내부 호출할 때** 추가한다(현재 내부 호출은 감싸지 않는 JWKS뿐).
- `ErrorResponse`도 `io.aegis.platform.core.response`로 옮겨 `SuccessResponse`와 한 패키지에 둔다(2026-10-25). 패키지 이동은 이미 컴파일된 사용처를 깨뜨리지만(바이너리 비호환) 아직 배포 전이고 저장소 안 사용처(mvc·webflux 예외 처리기, http-client)는 함께 바꿨다.
- HTTP 상태 코드는 그대로 의미를 가진다(성공 200/201, 실패 4xx/5xx). 본문 `code`와 상태 코드를 둘 다 본다.
