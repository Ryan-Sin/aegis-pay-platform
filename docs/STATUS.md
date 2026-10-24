# 구현 상태와 알려진 문제

> **사실(Fact) 문서**다. 코드와 항상 일치해야 한다. 작업을 마치면 이 문서를 함께 갱신한다.
> 최종 확인: 2026-10-25

범례: ✅ 구현됨 · 🚧 진행 중 · 📋 계획 · ⚠️ 문제 있음

## 1. 모듈 상태

| 모듈 | 상태 | 테스트 | 메모 |
|---|---|---|---|
| `libs:platform-core` | ✅ | `BusinessExceptionTest`, `ErrorMessageTest` | `BusinessException.detail` 추가(ADR-0001) |
| `libs:platform-mvc-starter` | ✅ | `CurrentTerminalArgumentResolverTest`, `GlobalExceptionHandlerTest`, `SensitiveHeaderMaskingFilterTest` | |
| `libs:platform-webflux-starter` | ✅ | 없음 | 전역 예외 처리기 테스트 필요(detail 비노출) |
| `libs:platform-lock-starter` | ✅ | 없음 | Redisson 기반. 락 해제 보장·lease 테스트 필요 |
| `libs:platform-http-client-starter` | ✅ | `HttpHeaderPropagatorTest`, `RestClientTargetPropagationTest`, `WebClientTargetPropagationTest` | `HttpClientSpec.target`(기본 `EXTERNAL`)으로 내부 식별자·baggage 전파 범위를 나눈다(K-3). Boot 4 client builder 모듈 포함(K-11) |
| `libs:platform-observability-starter` | ✅ | `RequestIdsTest`, `ReactiveTraceFilterTest`, `ServletTraceMdcFilterTest` | `spring-boot-starter-opentelemetry` 기반(K-1 해결). Request ID는 헤더 + MDC(ADR-0002). 코루틴은 `mono(PropagationContextElement())` 필요(테스트로 확인) |
| `services:api-gateway` | 🚧 | `JwksKeyProviderTest`, `JwtTokenVerifierTest`, `InboundBaggageRejectingPropagatorTest`, `GatewayE2ETest` | M1·M2 완료. JWT 기반 단말 baggage 설정은 📋 |
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
| Header Sanitizer (`baggage` 제거) | ✅ 헤더 전달 단계 |
| 외부 baggage 추출 차단 (`InboundBaggageRejectingPropagator`) | ✅ 추출 단계(K-8, ADR-0005) |
| 단말 컨텍스트 baggage(`terminalId`, `storeId`)를 JWT 검증 결과로 설정 | 📋 `remote-fields`/`correlation.fields` 설정만 있음. baggage로 일원화 결정(ADR-0007) |
| JWKS 기동 시 warm-up, 적재 전 readiness false | 📋 K-12 |
| 트레이싱: 클라이언트 trace를 이어받아 게이트웨이 span 생성, `X-Trace-Id` 응답 헤더 | ✅ (K-4 해결) |
| Request ID 검증·교체 후 하위 전달, 응답 헤더 | ✅ |
| permit-all 경로 설정화 (`/api/v1/auth/token`, `/.well-known/jwks.json`) | ✅ |
| JWT 검증: RS256 고정, `kid` 필수, `sub`/`storeId`/`jti` 필수, `exp` + 30초 leeway | ✅ |
| JWT 검증: `iss`/`aud`/`nbf`/`iat` | 📋 (auth-service 클레임 설계와 함께 결정) |
| JWKS 캐시: `refreshAfterWrite` + `expireAfterWrite`, 모르는 `kid`일 때 cooldown 내 1회 강제 갱신 | ✅ (ADR-0003) |
| Blacklist: Redis 200ms timeout, 실패 시 503 fail-closed | ✅ |
| 인증 실패 원인은 `BusinessException.detail`로 로그에만 기록 | ✅ |
| 테스트 | ✅ `JwksKeyProvider`(cooldown·동시 위조 kid·갱신 실패·최초 적재 실패), `JwtTokenVerifier`(alg·kid·서명·leeway 경계·필수 클레임), E2E(스푸핑 헤더·baggage 교체, permit-all, 401/503 응답에 detail 비노출, trace·Request ID 전파) |
| `GlobalFilter`에서 던진 `BusinessException`을 `ReactiveGlobalExceptionHandler`(`@RestControllerAdvice`)가 처리 | ✅ (E2E에서 응답 body의 `code`로 확인) |

## 3. 알려진 문제

| ID | 문제 | 확인 방법 / 근거 | 조치 방향 |
|---|---|---|---|
| K-1 | ~~트레이싱이 연결되어 있지 않다~~ | `GatewayE2ETest.wiresOpenTelemetryTracer` | 해결: `spring-boot-starter-opentelemetry`로 교체. span export는 `management.opentelemetry.tracing.export.otlp.endpoint`를 지정해야 켜진다(미지정 시 SpanExporter 빈 없음) |
| K-2 | ~~`ReactiveTraceFilter`의 커스텀 accessor scope 충돌, `ServletTraceMdcFilter`의 MDC 미설정~~ | `ReactiveTraceFilterTest`, `ServletTraceMdcFilterTest` | 해결: ADR-0002 방식으로 재작성. 두 필터 생성자에서 `baggageManager` 파라미터를 제거했다(public API 변경, monorepo 밖 사용처 없음) |
| K-3 | ~~`HttpHeaderPropagator`가 MDC의 `X-Terminal-Id`/`X-Store-Id`를 모든 외부 호출(VAN 포함)에 붙인다~~ | `*TargetPropagationTest` | 해결: `HttpClientSpec.target`(`INTERNAL`/`EXTERNAL`, 기본 `EXTERNAL`). 외부 대상에는 `X-Request-Id`·`X-Terminal-Id`·`X-Store-Id`를 붙이지 않고, tracing이 주입한 `baggage`를 filter/interceptor에서 지운다. `traceparent`는 유지. `HttpClientSpec` 생성자 변경은 바이너리 비호환(K-7과 같은 유형) |
| K-4 | ~~api-gateway가 클라이언트의 `traceparent`를 그대로 하위로 전달한다~~ | `GatewayE2ETest.continuesTraceWithOwnSpanAndPropagatesRequestId` | 해결: K-1 이후 게이트웨이가 같은 trace id로 자기 span을 만든다 |
| K-5 | ~~루트 `build.gradle.kts`의 `testImplementation.extendsFrom(compileOnly)`~~ | — | 해결: 루트에서 제거하고 observability 모듈에만 `testImplementation(spring-boot-starter-webflux)` 명시 |
| K-6 | ~~observability 테스트 파일이 잘못된 경로로 staged된 상태~~ | — | 해결: 올바른 경로(`.../observability/filter/`)로 커밋됨 |
| K-7 | `BusinessException`에 `detail` 파라미터를 추가하면서 생성자 JVM 시그니처가 바뀌었다(바이너리 비호환). monorepo에서는 문제없지만 artifact로 배포하면 사용처가 깨진다 | ADR-0001 | 라이브러리 배포 단계에서 호환성 검증 도입(커리큘럼 M4) |
| K-8 | ~~트레이싱 연결 후 외부 `baggage`가 하위 서비스로 다시 주입된다.~~ 서버 observation이 모든 필터보다 먼저 baggage를 추출하고, HeaderSanitizer가 헤더를 지운 뒤에도 propagator가 컨텍스트에서 다시 주입했다(`terminalId=SPOOFED-TERM` 전달 확인) | `GatewayE2ETest`, `InboundBaggageRejectingPropagatorTest` | 해결: 게이트웨이에서 propagator 추출 단계의 baggage를 버린다(ADR-0005) |
| K-9 | ~~`./gradlew build`가 코드 없는 모듈과 집계 프로젝트(`:libs`, `:services`, `:simulators`)의 `bootJar`에서 실패한다~~ | `./gradlew build` | 해결: 플러그인은 하위 프로젝트가 없는 실제 모듈에만 적용하고, `bootJar`는 `src/main/kotlin`에 코드가 있는 서비스에서만 켠다 |
| K-10 | `spring-boot-starter-opentelemetry`가 `micrometer-registry-otlp`를 포함해서 기본값으로 `localhost:4318`에 메트릭을 push한다 | 컨텍스트의 `OtlpMeterRegistry` 빈 확인 | api-gateway는 `management.otlp.metrics.export.enabled=${OTLP_METRICS_EXPORT_ENABLED:false}`. 다른 서비스도 같은 설정 필요. 메트릭 수집 방식(OTLP vs Prometheus)은 M7에서 결정 |
| K-11 | ~~platform HTTP client가 tracing으로 계측되지 않는다.~~ Boot 4는 `RestClient.Builder`/`WebClient.Builder` auto-configuration을 `spring-boot-restclient`/`spring-boot-webclient`로 분리했는데 classpath에 없어서, 팩토리가 계측되지 않은 기본 builder로 fallback했다(`traceparent`·client span 없음). gateway의 JWKS 호출도 해당 | `*TargetPropagationTest`(수정 전 `traceparent: null`) | 해결: http-client starter가 두 모듈을 `api`로 포함. builder 빈이 없으면 WARN 로그를 남긴다. MVC 서비스에도 `spring-webflux` jar가 함께 들어가지만 web application type 판정(MVC 우선)과 reactive 팩토리 생성 조건에는 영향이 없다 |
| K-12 | api-gateway의 JWKS 캐시가 **지연 로딩**이다. gateway 기동 시 auth-service가 내려가 있으면 캐시가 비어 있어 "기존 키 유지" 방어가 작동하지 않고, 복구될 때까지 모든 인증 요청이 503(`JWKS_FETCH_FAILED`)이 된다. 첫 요청도 JWKS 호출만큼 느려진다 | `JwksKeyProvider`(첫 `getPublicKey`에서 적재) | M3: 기동 시 warm-up + 적재 전 readiness false(ADR-0003 보강) |
| K-13 | 단말 컨텍스트 전파 경로가 둘 다 비어 있다. MDC에 `terminalId`/`storeId`를 넣는 운영 코드가 없어 `HttpHeaderPropagator`의 MDC → `X-Terminal-Id` 복사가 실제로는 동작하지 않고, gateway가 baggage를 설정하는 코드도 없다 | 코드 검색(2026-10-25) | baggage로 일원화(ADR-0007): gateway baggage 설정, MDC → 헤더 복사 제거, `@CurrentTerminal`은 baggage에서 읽음 |

## 4. 결정됐지만 구현 전 (M3)

| 결정 | 근거 |
|---|---|
| JWT 서명 비밀키는 KMS에 두고 서명만 위임. 로컬은 LocalStack KMS(`LOCALSTACK_AUTH_TOKEN` 필요) | ADR-0006 |
| 단말 컨텍스트는 baggage 하나로 전파, scopes는 `X-Terminal-Scopes`로 첫 서비스까지만 | ADR-0007 |
| auth-service부터 MySQL 사용(전환 시점을 M6 → M3로 앞당김) | ADR-0004 3항 |
