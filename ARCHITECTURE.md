# Aegis Pay Platform: 아키텍처 명세와 설계 의도

> 이 문서는 **목표 설계(Spec)**다. 아직 구현되지 않은 내용이 포함되어 있다.
> 실제 구현 상태는 [`docs/STATUS.md`](./docs/STATUS.md), 이미 내린 결정은 [`docs/adr/`](./docs/adr/), 학습 계획은 [`docs/CURRICULUM.md`](./docs/CURRICULUM.md)를 본다.

## 1. 프로젝트 목표
- **타깃 도메인**: 오프라인 결제 단말기·POS를 운영하는 결제 플랫폼. 대규모 동시 연결, 배포가 어려운 단말 환경, 1원의 오차도 허용하지 않는 정합성을 전제로 한다.
- **핵심 지향점**: 여러 제품이 공통으로 쓰는 **플랫폼 서비스와 라이브러리**(`libs/`)를 직접 설계해 비즈니스 로직과 횡단 관심사(인증·인가, 분산 락, 복원력, 관측성, 메시징)를 분리한다. 그 과정에서 Spring 프록시, Reactor 컨텍스트 전파, 네트워크, 락의 밑바닥 원리를 체득하고 기술 블로그로 자산화한다.
- **목표 역량과 마일스톤**: `docs/CURRICULUM.md`.

## 2. 핵심 학습 목표

1. **인증·인가와 API Gateway**
   - 단말 Secure Element 프로비저닝 모델과 비대칭키(RS256) 서명 검증
   - auth-service의 JWT 발급, JWKS 제공, **서명키 로테이션**(Vault 연동 목표)
   - Stateless Gateway의 JWKS 로컬 캐싱(Caffeine), `kid` 기반 로테이션 대응, 토큰 즉시 무효화(Blacklist)의 트레이드오프
   - 외부 유입 헤더·baggage 스푸핑 차단과 내부 신뢰 헤더 전파(5장 신뢰 경계)
2. **동시성 제어와 정합성**
   - VAN 대기(1~3초) 동안 DB 커넥션 풀 고갈을 막는 **락-트랜잭션 분리**
   - `Idempotency-Key` 기반 멱등성, 부분 실패(타임아웃) 시 망취소 보상 트랜잭션, 오프라인 SAF(Store & Forward) 동기화
3. **메시징과 지속 연결**
   - Transactional Outbox로 DB-Kafka 이중 쓰기 해결, at-least-once 전달과 멱등 컨슈머
   - **SSE 연결 관리 플랫폼**: 인스턴스 간 메시지 라우팅, 배포 시 연결 drain과 재연결 폭주 방지, `Last-Event-ID` 재전송
4. **복원력과 관측성**
   - 표준 HTTP 클라이언트 인터페이스(`SyncHttpClient`/`AsyncHttpClient`) + Resilience4j(Circuit Breaker, Retry, Timeout)
   - 메트릭 공통 규약(공통 태그, 카디널리티 제한, SLO 히스토그램)과 Prometheus/Grafana
   - OpenTelemetry 기반 전 구간(`Gateway → Service → Outbox/Kafka → Broker`) W3C `traceparent`와 단말 컨텍스트 baggage(`terminalId`, `storeId`) 전파
5. **공통 라이브러리 변경 관리**
   - 라이브러리 배포·BOM·SemVer, **바이너리 호환성 자동 검증**, deprecation과 마이그레이션 경로 제공

---

## 3. 전체 데이터 흐름

### [Flow 1] 단말기 부팅과 토큰 발급
1. `terminal-simulator`가 기기 내 비밀키로 Challenge에 전자서명해서 `POST /api/v1/auth/token`을 호출한다(단일 진입점 `api-gateway`).
2. `api-gateway`는 **`/api/v1/auth/token`과 `/.well-known/jwks.json`만** 인증 없이 통과시킨다(`aegis.gateway.auth.permit-all-paths`). logout 등 나머지 `/api/v1/auth/**`는 토큰이 필요하다.
3. `auth-service`는 DB에 등록된 단말 공개키로 서명을 검증한 뒤, **현재 활성 서명키(RS256, 헤더에 `kid` 포함)**로 JWT(유효기간 12시간)를 발급한다. JWKS에는 로테이션 기간 동안 **이전 키와 새 키가 함께** 게시된다.

### [Flow 2] 결제 승인 요청
1. `terminal-simulator`가 `POST /api/v1/payments`를 호출한다.
   - **Header**: `Authorization: Bearer <JWT>`, `Idempotency-Key: <UUID>`, `X-Request-Id: <UUID>`, `X-Device-Firmware: v2.4.1`
   - **Body**: `orderId`, `amount`, `vat`, `emvCryptogram`
2. `api-gateway`의 보안 검문과 헤더 변환(Netty, Non-blocking):
   - **Body 불간섭**: Gateway는 Body를 역직렬화하지 않는다(스트림 소비와 OOM 방지).
   - **Header Sanitizer**: 외부에서 들어온 `X-Terminal-*`, `X-Store-*` 헤더와 `baggage` 헤더를 제거한다.
   - **Local JWKS Verification**: Caffeine 캐시에 보관된 JWKS 키셋으로 서명을 검증한다. 캐시는 `refreshAfterWrite`로 백그라운드 갱신하고(실패 시 기존 키 유지), 모르는 `kid`가 오면 cooldown 내 1회만 강제 갱신한다(ADR-0003).
   - **Blacklist 확인**: Redis 조회 실패 시 fail-closed(503).
   - **Header Injection**: 검증된 JWT 클레임으로 `X-Terminal-Id`, `X-Store-Id`, `X-Terminal-Scopes`를 주입하고, 단말 컨텍스트 baggage를 설정해서 `payment-ledger-service`로 라우팅한다.
3. `payment-ledger-service`의 동시성 제어와 원장 기록(커넥션 풀 보호 플로우):
   - `@CurrentTerminal` ArgumentResolver로 신뢰 헤더를 추출한다.
   - **Step 1 (락 획득)**: `@DistributedLock(key = "#idempotencyKey")` AOP로 Redisson 락을 획득한다. 이 시점에는 DB 트랜잭션을 열지 않는다.
   - **Step 2 (처리 여부 확인)**: 락 없이 단순 조회로 이미 처리된 결제인지 확인하고 커넥션을 즉시 반납한다.
   - **Step 3 (VAN 호출)**: `mock-van-server`로 승인을 요청한다. 대기 동안 DB 커넥션 점유 0개.
   - **Step 4~5 (짧은 트랜잭션)**: VAN 응답 직후 결제 상태, 복식부기 원장, `outbox_events`를 단일 트랜잭션으로 저장하고 즉시 COMMIT한다.
   - **Step 6 (락 반납)**: COMMIT이 끝난 뒤 `finally`에서 락을 해제한다.
   - **최종 방어선**: 분산 락은 성능 최적화다. 락 lease 만료나 Redis 장애에도 중복 승인이 생기지 않도록 **`idempotency_key` unique 제약**이 정합성을 보장한다. 락 lease는 VAN 타임아웃보다 길게 잡는다.
4. 비동기 이벤트 전파와 실시간 알림:
   - Outbox Relay가 이벤트를 Kafka로 발행한다. 저장 시점의 trace 컨텍스트를 outbox 레코드에 함께 저장해서 발행 시 Kafka 헤더로 복원한다.
   - `realtime-broker-service`가 멱등 소비해서 매장 POS에 SSE로 푸시한다.

---

## 4. 모듈별 역할과 설계 규칙

### A. 공통 플랫폼 라이브러리 (`libs/`, `jar = true`, `bootJar = false`)
- **`platform-core`**: 웹 프레임워크에 종속되지 않는 순수 공통 모듈. `TerminalContext`(헤더·클레임 상수 포함), `BusinessException`(외부 노출용 `message`와 내부 로깅용 `detail` 분리, ADR-0001), `ErrorCode`/`CommonErrorCode`, `ErrorMessage`, `ErrorResponse`.
- **`platform-mvc-starter`**: Spring MVC 서비스용. `@CurrentTerminal` ArgumentResolver, 전역 예외 처리(`detail`은 로그에만), `Authorization` 헤더 `[MASKED]` 로깅 필터.
- **`platform-webflux-starter`**: Spring WebFlux 서비스(`api-gateway` 등)용. Reactive 전역 예외 처리, 민감 헤더 마스킹 `WebFilter`.
- **`platform-lock-starter`**: `@DistributedLock`, SpEL 동적 키 파서, Redisson 기반 분산 락 AOP. DB 트랜잭션과 결합하지 않는다.
- **`platform-http-client-starter`**: `SyncHttpClient`(RestClient)/`AsyncHttpClient`(WebClient + Coroutines) 표준 인터페이스와 팩토리, Resilience4j, 원격 `ErrorResponse` 파싱과 `BusinessException` 변환.
  - **헤더 전파 규칙**: `X-Request-Id`, `X-Terminal-Id`, `X-Store-Id`, baggage는 **내부 대상에만** 전파한다. VAN 같은 외부 대상(`HttpClientSpec`에서 external로 지정)에는 내부 식별자를 붙이지 않는다.
  - 망취소 같은 비즈니스 fallback 정책은 공통 모듈에 넣지 않고 호출 측에 위임한다.
- **`platform-observability-starter`**: Spring Boot 4 `spring-boot-starter-opentelemetry` 기반 트레이싱·OTLP 연동.
  - Request ID: 헤더 + MDC(`Slf4jThreadLocalAccessor`)로 전파한다(ADR-0002).
  - 단말 컨텍스트: `management.tracing.baggage.remote-fields`/`correlation.fields` 설정으로 선언적으로 전파한다. 필터에서 OTel scope를 직접 다루지 않는다.
- (계획) **메트릭 공통 규약**, **Messaging(Kafka + Outbox + 멱등 컨슈머) 라이브러리**, **Feature Flag SDK**: `docs/CURRICULUM.md` 참고.

### B. 서비스 (`services/`, `bootJar = true`, `jar = false`)
- **`api-gateway`**: Spring Cloud Gateway(WebFlux/Netty), Header Sanitizer, JWT 검증(JWKS 캐시), Blacklist, 신뢰 헤더 주입.
- **`auth-service`**: 단말 공개키 관리, RS256 JWT 발급, `GET /.well-known/jwks.json`, 서명키 로테이션.
- **`payment-ledger-service`**: 멱등성, 복식부기 원장, VAN 승인 연동과 타임아웃 시 망취소, Transactional Outbox.
- **`realtime-broker-service`**: Kafka 멱등 컨슈머, SSE 연결 관리(인스턴스 간 라우팅, drain, 재전송).

### C. 시뮬레이터 (`simulators/`)
- **`terminal-simulator`**: 가상 Secure Element 서명, 정상 결제, 100 스레드 동시 요청(따닥) 테스트, 오프라인 SAF 재전송, 대량 SSE 연결 부하.
- **`mock-van-server`**: 정상 승인, 의도적 지연(3초), 500 에러를 유발하는 외부 카드사 에뮬레이터.

---

## 5. 헤더·컨텍스트 규약과 신뢰 경계

| 이름 | 종류 | 설정 주체 | 외부 유입 시 | 외부(VAN) 전파 |
|---|---|---|---|---|
| `Authorization` | 요청 헤더 | 단말 | 검증 대상 | ✕ |
| `X-Terminal-Id` | 내부 신뢰 헤더 | **api-gateway만** (JWT `sub`) | 제거 | ✕ |
| `X-Store-Id` | 내부 신뢰 헤더 | **api-gateway만** (JWT `storeId`) | 제거 | ✕ |
| `X-Terminal-Scopes` | 내부 신뢰 헤더 | **api-gateway만** (JWT `scopes`) | 제거 | ✕ |
| `baggage` (`terminalId`, `storeId`) | W3C baggage | **api-gateway만** | 제거 | ✕ |
| `traceparent` | W3C trace context | OTel | 수용 가능, 단 샘플링 결정은 Collector에서 | 대상별 선택 |
| `X-Request-Id` | 상관관계 ID | 단말 또는 gateway | 형식 검증(`[A-Za-z0-9._-]{8,64}`), 실패 시 재생성 | ✕ |
| `Idempotency-Key` | 업무 헤더 | 단말 | 그대로 사용(서비스가 검증) | ✕ |

- 하위 서비스는 `X-Terminal-*`, `X-Store-*`를 **게이트웨이에서 온 요청일 때만** 신뢰한다. 메시 환경에서는 Istio `AuthorizationPolicy`로 호출 주체를 게이트웨이 ServiceAccount로 제한한다.
- 외부 `baggage`는 헤더 전달 단계(HeaderSanitizer)와 trace context 추출 단계(propagator) 양쪽에서 버린다. 서버 observation이 모든 필터보다 먼저 추출하기 때문이다(ADR-0005).
- 용어는 **Store**로 통일한다. `Merchant`는 쓰지 않는다.

## 6. 실행 환경 (로컬 우선, 단계별)

| 단계 | 구성 | 용도 |
|---|---|---|
| Tier 1 (상시) | Docker Compose: MySQL, Valkey, Kafka(KRaft), 관측 스택 | 일상 개발과 디버깅. 장애 주입은 Toxiproxy |
| Tier 2 (필요할 때만) | k3d/kind + Istio(minimal) + 서비스 2개 이상 복제 | mTLS, `AuthorizationPolicy`, 다중 인스턴스 락·SSE 라우팅, 배포 시 연결 drain 실험 |
| 선택 | ArgoCD, Chaos Mesh | GitOps 흐름 확인, Pod kill·네트워크 지연 실험 |

로컬 자원 한계 때문에 Tier 2 이상은 실험할 때만 띄운다(ADR-0004).
