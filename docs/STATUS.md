# 구현 상태와 알려진 문제

> **사실(Fact) 문서**다. 코드와 항상 일치해야 한다. 작업을 마치면 이 문서를 함께 갱신한다.
> 최종 확인: 2026-10-25

범례: ✅ 구현됨 · 🚧 진행 중 · 📋 계획 · ⚠️ 문제 있음

## 1. 모듈 상태

| 모듈 | 상태 | 테스트 | 메모 |
|---|---|---|---|
| `libs:platform-core` | ✅ | `BusinessExceptionTest`, `ErrorMessageTest`, `SuccessResponseSerializationTest`(mvc-starter에서 JSON 구조 확인) | `BusinessException.detail` 추가(ADR-0001). 성공 응답 `SuccessResponse<T>`(ADR-0009). `ErrorResponse` 필드 순서를 `timestamp, code, message, path, errors`로 변경(생성자 파라미터 순서가 바뀌어 바이너리 비호환이지만, 저장소 안에서 `of()`로만 생성하고 아직 배포 전이라 영향 없음). `CommonErrorCode.NOT_FOUND`/`METHOD_NOT_ALLOWED`(K-15) |
| `libs:platform-mvc-starter` | ✅ | `CurrentTerminalArgumentResolverTest`, `CurrentTerminalBaggageIntegrationTest`, `GlobalExceptionHandlerTest`, `SensitiveHeaderMaskingFilterTest`, `SuccessResponseSerializationTest` | `@CurrentTerminal`은 baggage에서 읽고, 없으면 401 `MISSING_TERMINAL_CONTEXT`(ADR-0007) |
| `libs:platform-webflux-starter` | ✅ | `ReactiveGlobalExceptionHandlerTest` | 404/405를 500으로 바꾸던 문제 수정(K-15) |
| `libs:platform-lock-starter` | ✅ | 없음 | Redisson 기반. 락 해제 보장·lease 테스트 필요 |
| `libs:platform-http-client-starter` | ✅ | `HttpHeaderPropagatorTest`, `RestClientTargetPropagationTest`, `WebClientTargetPropagationTest` | `HttpClientSpec.target`(기본 `EXTERNAL`)으로 내부 식별자·baggage 전파 범위를 나눈다(K-3). Boot 4 client builder 모듈 포함(K-11) |
| `libs:platform-observability-starter` | ✅ | `RequestIdsTest`, `ReactiveTraceFilterTest`, `ServletTraceMdcFilterTest` | `spring-boot-starter-opentelemetry` 기반(K-1 해결). Request ID는 헤더 + MDC(ADR-0002). 코루틴은 `mono(PropagationContextElement())` 필요(테스트로 확인). `correlation.fields: terminalId, storeId`를 기본값으로 제공(`PlatformObservabilityEnvironmentPostProcessor`, ADR-0007). `kotlin-reflect` 의존(Spring 7이 spring.factories의 Kotlin 클래스 생성에 필요) |
| `services:api-gateway` | 🚧 | `HexagonalArchitectureTest`(ArchUnit, ADR-0008), `JwksKeyProviderTest`, `JwtTokenVerifierTest`, `InboundBaggageRejectingPropagatorTest`, `GatewayE2ETest` | M1·M2 완료. JWT 기반 단말 baggage 설정은 📋 |
| `services:auth-service` | 🚧 | `HexagonalArchitectureTest`(ArchUnit, ADR-0008), `TerminalPersistenceAdapterTest`(Testcontainers MySQL 8.4), `KmsJwtSignerTest`(Testcontainers LocalStack 4.14.0 + compose와 같은 init 스크립트), `TerminalPublicKeysTest`, `TerminalTest`, `ScopesConverterTest`, `TokenIssueServiceTest`, `ChallengeServiceTest`, `TerminalSignaturesTest`, `RedisChallengeStoreTest`(Testcontainers Valkey 8.0), `AuthServiceApplicationTest`(challenge → token 전체 흐름, JWKS) | M3 2~4단계: challenge(`/api/v1/auth/challenge`, Redis 1회용 nonce 60초)·토큰 발급(`/api/v1/auth/token`, ECDSA P-256 서명 검증, 재전송 401)(ADR-0010). 기동 골격, `infra/mysql/init/02-aegis-auth-schema.sql`의 `terminal` 테이블(단말 공개키·store·scopes·상태, 테스트도 같은 파일 사용), JPA `validate`. 단말 키는 EC P-256(SPKI DER base64). `KmsJwtSigner`: RS256, KMS `Sign`(`DIGEST`), `kid`=JWK thumbprint, 키는 기동 시 1회 로드(없으면 기동 실패), 서명 실패는 503 `TOKEN_SIGNING_UNAVAILABLE`. `GET /api/v1/auth/jwks`(내부 전용). 단말 등록 API·challenge 요청량 제한·alias 전환 반영은 📋 |
| `services:payment-ledger-service` | 📋 | — | 코드 없음 |
| `services:realtime-broker-service` | 📋 | — | 코드 없음 |
| `simulators:terminal-simulator` | 📋 | — | 코드 없음 |
| `simulators:mock-van-server` | 📋 | — | 코드 없음 |
| `infra/` (Grafana, Loki, Tempo, Prometheus, OTel Collector) | 📋 | — | 디렉터리만 있음(`.gitkeep`) |
| `docker-compose.yml` | ✅ | `docker compose up -d --wait`로 확인 | MySQL 8.4(호스트 3307, `aegis_auth`), Valkey 8.0, Kafka 3.9(KRaft), LocalStack 4.14.0(KMS, 기동 시 JWT 서명 키 + alias 생성). Swarm 설정 제거(ADR-0004) |

## 2. api-gateway 기능 상태

| 기능 | 상태 |
|---|---|
| Header Sanitizer (`X-Terminal-*`, `X-Store-*` 제거) | ✅ |
| Header Sanitizer (`baggage` 제거) | ✅ 헤더 전달 단계 |
| 외부 baggage 추출 차단 (`InboundBaggageRejectingPropagator`) | ✅ 추출 단계(K-8, ADR-0005) |
| 단말 컨텍스트 baggage(`terminalId`, `storeId`)를 JWT 검증 결과로 설정 | ✅ 서버 trace context에 `Baggage.set`(scope 열지 않음). `X-Terminal-Id`·`X-Store-Id` 주입 중단, scopes는 `X-Terminal-Scopes`(ADR-0007). 동시 요청 50개 섞임 없음 확인 |
| JWKS 기동 시 warm-up, 적재 전 readiness false | 📋 K-12 |
| 트레이싱: 클라이언트 trace를 이어받아 게이트웨이 span 생성, `X-Trace-Id` 응답 헤더 | ✅ (K-4 해결) |
| Request ID 검증·교체 후 하위 전달, 응답 헤더 | ✅ |
| permit-all 경로 설정화 (`/api/v1/auth/token`) | ✅ |
| 외부 차단 경로 (`blocked-paths`: `/api/v1/auth/jwks`, 토큰이 있어도 404) | ✅ |
| JWT 검증: RS256 고정, `kid` 필수, `sub`/`storeId`/`jti` 필수, `exp` + 30초 leeway | ✅ |
| JWT 검증: `iss`/`aud` | ✅ auth-service와 같은 값(`JWT_ISSUER`, `JWT_AUDIENCE`) 확인(ADR-0010). `nbf`는 발급하지 않음 |
| permit-all에 `/api/v1/auth/challenge` 추가 | ✅ |
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
| K-13 | ~~단말 컨텍스트 전파 경로가 둘 다 비어 있다(MDC → 헤더 복사는 동작하지 않고, gateway가 baggage를 설정하지 않음)~~ | `GatewayE2ETest`, `CurrentTerminalBaggageIntegrationTest`, `*TargetPropagationTest` | 해결: baggage로 일원화(ADR-0007). gateway가 JWT로 baggage 설정, MDC → 헤더 복사 제거, `@CurrentTerminal`은 baggage에서 읽음 |
| K-16 | ~~`remote-fields` 설정 때문에 `terminalId`가 W3C `baggage`와 별개로 `terminalId:` 헤더로도 나가, 외부 대상(VAN)에 새고 있었다(K-3은 `baggage` 헤더만 지움)~~ | `*TargetPropagationTest`(모든 헤더 출력으로 발견), `HttpHeaderPropagatorTest` | 해결: `remote-fields` 제거(ADR-0002 보완), 외부 대상·gateway 입구에서 `terminalId`/`storeId` 헤더도 제거 |
| K-14 | 스키마를 마이그레이션 도구 없이 `infra/mysql/init/*.sql`로 관리한다. MySQL 컨테이너는 **빈 볼륨으로 처음 뜰 때만** init SQL을 실행하므로, 스키마를 바꾸면 기존 로컬 볼륨에는 반영되지 않고 적용 이력도 남지 않는다 | `docker-entrypoint-initdb.d` 동작 | 로컬: `docker compose down -v` 후 재기동. 변경은 새 번호 파일(`03-...sql`)로 추가하고 운영 반영 절차는 배포 단계(M3 이후)에서 정한다 |
| K-15 | ~~공통 예외 처리기가 처리 대상을 정하지 않은 예외를 모두 500으로 바꿔서, 없는 경로(404)·지원하지 않는 메서드(405)도 500 + ERROR 로그가 됐다~~ | `GatewayE2ETest`(라우트 없는 경로), `ReactiveGlobalExceptionHandlerTest`, `GlobalExceptionHandlerTest` | 해결: WebFlux·MVC 처리기에 `NoResourceFoundException`/`NoHandlerFoundException` → 404, `MethodNotAllowedException`/`HttpRequestMethodNotSupportedException` → 405 추가. `CommonErrorCode`에 `NOT_FOUND`, `METHOD_NOT_ALLOWED` 추가(enum 상수 추가는 바이너리 호환) |

## 4. 결정됐지만 구현 전 (M3)

| 결정 | 근거 |
|---|---|
| JWT 서명 비밀키는 KMS에 두고 서명만 위임 — 서명·JWKS 구현 완료. 키 교체(alias 전환을 앱이 반영)는 구현 전 | ADR-0006 |
| auth-service부터 MySQL 사용 — docker-compose 전환, `terminal` 테이블(init SQL) 완료 | ADR-0004 3항 |
