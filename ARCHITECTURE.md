# Aegis Pay Platform: 아키텍처 청사진 및 엔지니어링 학습 목표

## 1. 프로젝트 궁극적 목표 (Engineering Goal)
- **타깃 도메인**: 대규모 트래픽과 극한의 정합성(1원의 오차도 불허)을 요구하는 엔터프라이즈 핀테크/결제/원장 플랫폼
- **핵심 지향점**: 단순 기능 구현을 넘어, 사내 공통 플랫폼 라이브러리(`libs/`)를 직접 설계하여 비즈니스 로직과 횡단 관심사(보안, 분산 락, 복원력, 관측성)를 완벽히 분리하고, OS/네트워크/스프링 코어(CGLIB 프록시) 밑바닥 원리를 체득하며 기술 블로그(전 8편)로 자산화한다.

## 2. 4대 핵심 기술 학습 목표 (Target Deep-Dive Competencies)
1. **인증/인가 & 금융 보안 (AuthN/AuthZ & Security)**
   - 오프라인 단말기 하드웨어 보안 칩(Secure Element) 프로비저닝 모델 이해 및 비대칭키(RS256) 서명 검증
   - Stateless API Gateway에서의 JWKS 로컬 캐싱(Caffeine)과 Key Rotation(`kid`), 토큰 즉시 무효화(Blacklist) 트레이드오프 해결
   - 외부 유입 헤더 스푸핑 원천 차단(Sanitize) 및 내부 신뢰 헤더 전파 아키텍처
2. **초고트래픽 동시성 제어 & 원장 정합성 (High Concurrency & ACID)**
   - 외부 카드사(VAN) 네트워크 대기 시간(1~3초) 동안 DB 커넥션 풀(HikariCP) 고갈을 막기 위해, `@DistributedLock` 구간에서는 DB 트랜잭션을 열지 않고 순수 Valkey/Redis 분산 락만 선점하는 **락-트랜잭션 분리 아키텍처**
   - Spring CGLIB 바이트코드 프록시 체인 원리 이해: `1. Valkey 락 획득 -> 2. 멱등성 단순 조회 -> 3. 외부 VAN 호출(No DB Tx) -> 4. 내부 짧은 @Transactional로 원장/Outbox 기록 및 즉시 COMMIT -> 5. finally에서 Valkey 락 반납`으로 커넥션 풀 고갈과 커밋 전 락 해제 버그를 동시에 해결
   - `Idempotency-Key` 헤더 기반 멱등성 보장과 복식부기 원장(Ledger) 기록 및 오프라인 SAF(Store & Forward) 배치 동기화
3. **무손실 실시간 데이터 파이프라인 (Streaming & Pipeline)**
   - RDBMS와 Kafka 간의 이중 쓰기(Dual Write) 문제를 해결하는 Transactional Outbox 패턴 구현
   - At-least-once 전달 보장과 멱등 컨슈머(Idempotent Consumer) 구현, SSE(Server-Sent Events)를 통한 매장 단말기 실시간 알림 푸시
4. **프로덕션 복원력 & 관측 가능성 (Resilience & Full Observability)**
   - 선언적 HTTP 클라이언트(`@HttpExchange`)와 Resilience4j(Circuit Breaker, Bulkhead, Strict Timeout, Exponential Backoff with Jitter)의 공통 Starter 모듈화
   - 외부 VAN사 승인 타임아웃 발생 시 자동 망취소(보상 트랜잭션) 처리
   - OpenTelemetry(OTLP) 기반 전 구간(`Gateway -> Service -> Outbox/Kafka -> Broker`) W3C `traceparent` 및 `X-Request-Id` 컨텍스트 전파, 커스텀 Span(분산 락 대기 시간 측정) 시각화

---

## 3. 전체 시스템 데이터 흐름 (End-to-End Lifecycle)

### [Flow 1] 단말기 부팅 및 토큰 발급 (오전 영업 시작)
1. `terminal-simulator`가 기기 내 비밀키로 Challenge에 전자서명하여 `POST /api/v1/auth/token` 호출 (단일 진입점인 `api-gateway`로 전송).
2. `api-gateway`는 `/api/v1/auth/**` 경로를 `permitAll`로 인식하여 JWT 검증 없이 `auth-service`로 단순 패스스루 라우팅.
3. `auth-service`는 DB에 등록된 단말기 공개키로 서명을 검증한 뒤, **인증 서버의 비밀키(RS256, 헤더에 `kid` 포함)**로 서명한 JWT(유효기간 12시간)를 발급하여 반환.

### [Flow 2] 실시간 결제 승인 요청 (피크타임 수만 건 처리)
1. `terminal-simulator`가 `POST /api/v1/payments` 호출.
   - **HTTP Header**: `Authorization: Bearer <JWT>`, `Idempotency-Key: <UUID>`, `X-Request-Id: <UUID>`, `X-Device-Firmware: v2.4.1`
   - **HTTP Body**: 순수 비즈니스 데이터 (`orderId`, `amount`, `vat`, `emvCryptogram`)
2. `api-gateway`의 보안 검문 및 헤더 변환 (Netty Non-blocking):
   - **Body 불간섭 원칙**: `Flux<DataBuffer>` 스트림 소비 및 OOM 방지를 위해 Gateway는 절대 Body를 역직렬화하지 않음.
   - **Header Sanitizer**: 해커가 위조해 보낸 외부의 모든 `X-Terminal-*`, `X-Store-*` 헤더를 입구에서 강제 삭제(Strip).
   - **Local JWKS Verification**: `auth-service`를 매번 호출하지 않고, Gateway 자신의 JVM 로컬 캐시(Caffeine)에 보관된 **인증 서버 공개키(1개)**로 JWT 서명을 0.01ms 만에 검증 (`kid` 미스매치 시 Single-Flight로 JWKS 갱신).
   - **Header Injection**: 검증된 JWT Payload에서 추출한 진짜 `X-Terminal-Id`, `X-Store-Id`, `X-Terminal-Scopes`를 헤더에 주입하여 `payment-ledger-service`로 라우팅.
3. `payment-ledger-service`의 동시성 제어 및 원장 기록 (DB 커넥션 풀 보호 플로우):
   - `@CurrentTerminal` ArgumentResolver로 신뢰 헤더 추출.
   - **Step 1 (Redis 락 획득)**: `@DistributedLock(key = "#idempotencyKey")` AOP 가동 -> Redisson Pub/Sub 기반 락 획득 (이 시점에는 DB 트랜잭션을 열지 않음!).
   - **Step 2 (결제 진행 여부 확인)**: DB Lock 없이 단순 조회로 이미 처리된 결제인지 빠르게 확인 후 DB 커넥션 즉시 반납.
   - **Step 3 (외부 VAN사 호출)**: `mock-van-server`로 승인 요청 (1~3초 네트워크 대기 동안 DB 커넥션 점유 0개!).
   - **Step 4 & 5 (짧은 DB 트랜잭션 기록 및 COMMIT)**: VAN 응답 수신 직후, 내부 트랜잭션 서비스를 통해 결제 상태, 복식부기 원장(Ledger), `outbox_events` 테이블에 단일 트랜잭션으로 저장하고 즉시 DB COMMIT 완료.
   - **Step 6 (Redis 락 반납)**: DB 커밋이 완전히 끝난 뒤 `finally` 블록에서 안전하게 Valkey 락 해제.
4. 비동기 이벤트 전파 및 실시간 알림:
   - Outbox Relay가 이벤트를 읽어 Kafka로 발행 -> `realtime-broker-service`가 멱등 소비하여 매장 POS 단말기에 SSE로 실시간 결제 완료 푸시.

---

## 4. 모듈별 상세 역할 및 설계 규칙

### A. 사내 공통 플랫폼 라이브러리 (`libs/` -> `bootJar = false`, `jar = true`)
- **`platform-core`**: 웹 프레임워크(MVC/WebFlux)에 종속되지 않는 순수 공통 모듈. `TerminalContext`, 전사 표준 예외(`BusinessException`), 에러 코드 규격(`ErrorCode`, `CommonErrorCode`), 에러 메시지 상수(`ErrorMessage`), 공통 에러 응답(`ErrorResponse`).
- **`platform-mvc-starter`**: Spring MVC(Servlet) 기반 마이크로서비스 전용 스타터. `@CurrentTerminal` ArgumentResolver, MVC 전역 예외 처리(`@RestControllerAdvice`), 로깅 시 `Authorization` 헤더 `[MASKED]` 처리 서블릿 필터.
- **`platform-webflux-starter`**: Spring WebFlux(Reactive/Netty) 기반 마이크로서비스(`api-gateway` 등) 전용 스타터. Reactive 전역 예외 처리기 및 Reactive 민감 헤더(`[MASKED]`) 마스킹 `WebFilter`.
- **`platform-lock-starter`**: `@DistributedLock`, SpEL 동적 키 파서, Redisson Pub/Sub 기반 순수 분산 락 AOP (외부 API 호출 시 DB 커넥션 풀 고갈을 막기 위해 DB 트랜잭션과 강제 결합하지 않음).
- **`platform-http-client-starter`**: `@HttpExchange` 팩토리, Resilience4j 서킷브레이커/벌크헤드/타임아웃 강제 적용, 외부 호출 시 `X-Request-Id` 및 `traceparent` 자동 전파. (단, 망취소 등 비즈니스 Fallback 정책은 공통 모듈에 넣지 않고 호출측에 위임).
- **`platform-observability-starter`**: OpenTelemetry SDK 연동, `@DistributedLock` 대기 시간 커스텀 Span 기록, MDC(`traceId`, `spanId`, `terminalId`) 자동 주입.

### B. 코어 마이크로서비스 (`services/` -> `bootJar = true`, `jar = false`)
- **`api-gateway`**: Spring Cloud Gateway (WebFlux/Netty), Caffeine 로컬 캐시 JWKS 검증기, Header Sanitizer & Injector.
- **`auth-service`**: 단말기 공개키 관리, RS256 JWT 발급기, `GET /.well-known/jwks.json` 엔드포인트.
- **`payment-ledger-service`**: 멱등성 검증, 복식부기 원장 관리, VAN 승인 연동 및 타임아웃 시 망취소 보상 트랜잭션, Transactional Outbox 기록.
- **`realtime-broker-service`**: Kafka Consumer, SSE 연결 관리 및 단말기 실시간 이벤트 브로드캐스팅.

### C. 시뮬레이터 (`simulators/` -> `bootJar = true`, `jar = false`)
- **`terminal-simulator`**: 가상 Secure Element 키 서명, 정상 결제, 100 스레드 따닥(Double-Click) 동시 폭격 테스트, 오프라인 Store & Forward 큐 재전송.
- **`mock-van-server`**: 정상 승인 외에 의도적 지연(3초 슬립) 및 500 에러 유발 기능을 갖춘 외부 카드사 에뮬레이터.
