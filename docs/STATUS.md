# 구현 상태와 알려진 문제

> **사실(Fact) 문서**다. 코드와 항상 일치해야 한다. 작업을 마치면 이 문서를 함께 갱신한다.
> 최종 확인: 2026-10-22

범례: ✅ 구현됨 · 🚧 진행 중 · 📋 계획 · ⚠️ 문제 있음

## 1. 모듈 상태

| 모듈 | 상태 | 테스트 | 메모 |
|---|---|---|---|
| `libs:platform-core` | ✅ | `BusinessExceptionTest`, `ErrorMessageTest` | `BusinessException.detail` 추가(ADR-0001) |
| `libs:platform-mvc-starter` | ✅ | `CurrentTerminalArgumentResolverTest`, `GlobalExceptionHandlerTest`, `SensitiveHeaderMaskingFilterTest` | |
| `libs:platform-webflux-starter` | ✅ | 없음 | 전역 예외 처리기 테스트 필요(detail 비노출) |
| `libs:platform-lock-starter` | ✅ | 없음 | Redisson 기반. 락 해제 보장·lease 테스트 필요 |
| `libs:platform-http-client-starter` | ✅ ⚠️ | 없음 | 아래 K-3 참고 |
| `libs:platform-observability-starter` | 🚧 ⚠️ | 커밋된 테스트 없음 | 재작성 작업분은 워킹트리에 미커밋 상태로 있음. 아래 K-1, K-2 참고 |
| `services:api-gateway` | 🚧 | 없음 | 핵심 기능 구현·커밋 완료, 테스트 미작성. 아래 K-4 참고 |
| `services:auth-service` | 📋 | — | 코드 없음 |
| `services:payment-ledger-service` | 📋 | — | 코드 없음 |
| `services:realtime-broker-service` | 📋 | — | 코드 없음 |
| `simulators:terminal-simulator` | 📋 | — | 코드 없음 |
| `simulators:mock-van-server` | 📋 | — | 코드 없음 |
| `infra/` (Grafana, Loki, Tempo, Prometheus, OTel Collector) | 📋 | — | 디렉터리만 있음(`.gitkeep`) |
| `docker-compose.yml` | ✅ | — | PostgreSQL 17, Valkey 8.0, Kafka 3.9(KRaft). MySQL 전환 예정(ADR-0004) |

## 2. api-gateway 기능 상태

| 기능 | 상태 |
|---|---|
| Header Sanitizer (`X-Terminal-*`, `X-Store-*` 제거) | ✅ |
| Header Sanitizer (`baggage` 제거) | ✅ (트레이싱 연결 후 propagator가 필터보다 먼저 baggage를 추출하는지 재확인 필요, K-1) |
| permit-all 경로 설정화 (`/api/v1/auth/token`, `/.well-known/jwks.json`) | ✅ |
| JWT 검증: RS256 고정, `kid` 필수, `sub`/`storeId`/`jti` 필수, `exp` + 30초 leeway | ✅ |
| JWT 검증: `iss`/`aud`/`nbf`/`iat` | 📋 (auth-service 클레임 설계와 함께 결정) |
| JWKS 캐시: `refreshAfterWrite` + `expireAfterWrite`, 모르는 `kid`일 때 cooldown 내 1회 강제 갱신 | ✅ (ADR-0003) |
| Blacklist: Redis 200ms timeout, 실패 시 503 fail-closed | ✅ |
| 인증 실패 원인은 `BusinessException.detail`로 로그에만 기록 | ✅ |
| 테스트 | 📋 `JwksKeyProvider`(cooldown·갱신 실패) → `JwtTokenVerifier`(경계값) → E2E(스푸핑 헤더 교체) 순 |

## 3. 알려진 문제

| ID | 문제 | 확인 방법 / 근거 | 조치 방향 |
|---|---|---|---|
| K-1 | **트레이싱이 연결되어 있지 않다.** `Tracer`/`BaggageManager` 빈이 null이라 OTLP로 아무것도 내보내지 않는다 | api-gateway `@SpringBootTest`에서 `ObjectProvider<Tracer>.ifAvailable == null`. runtime classpath에 `spring-boot-micrometer-tracing-opentelemetry` 없음 | observability starter를 `spring-boot-starter-opentelemetry` 기반으로 교체 |
| K-2 | 커밋된 `ReactiveTraceFilter`는 baggage scope를 조립 스레드에서 열고 다른 스레드에서 닫을 수 있다. 재작성 시도분(커스텀 accessor)은 테스트 실패(`expected: <req-123> but was: <null>`) 상태로 워킹트리에 미커밋. `ServletTraceMdcFilter`는 request ID를 MDC에 넣지 않는다 | `./gradlew :libs:platform-observability-starter:test` | ADR-0002 방식으로 재작성(커리큘럼 M2) |
| K-3 | `HttpHeaderPropagator`가 MDC의 `X-Terminal-Id`/`X-Store-Id`를 **모든 외부 호출(VAN 포함)**에 붙인다 | 코드 확인 | `HttpClientSpec`에 internal/external 구분 추가, external에는 내부 헤더 미전파 |
| K-4 | api-gateway가 클라이언트의 `traceparent`를 그대로 하위로 전달한다(게이트웨이가 span을 만들지 않음) | 임시 probe 테스트로 확인 | K-1 해결 후 다시 확인 |
| K-5 | 루트 `build.gradle.kts`의 `testImplementation.extendsFrom(compileOnly)` 변경이 워킹트리에 미커밋 상태로 있다. 이 변경 없이도 전체 모듈 컴파일과 core/mvc 테스트가 통과함을 확인했다 | `git diff build.gradle.kts` | M2에서 observability 모듈에만 `testImplementation` 명시로 교체 |
| K-6 | ~~observability 테스트 파일이 잘못된 경로로 staged된 상태~~ | — | 해결: 잘못된 경로는 unstage, 올바른 경로(`.../observability/filter/`) 파일은 워킹트리에 untracked로 있음 |
| K-7 | `BusinessException`에 `detail` 파라미터를 추가하면서 생성자 JVM 시그니처가 바뀌었다(바이너리 비호환). monorepo에서는 문제없지만 artifact로 배포하면 사용처가 깨진다 | ADR-0001 | 라이브러리 배포 단계에서 호환성 검증 도입(커리큘럼 M3) |
