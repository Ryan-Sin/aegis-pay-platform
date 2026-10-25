# ADR-0007: 단말 컨텍스트(terminalId, storeId)는 baggage 하나로 전파한다

- 상태: 채택 (2026-10-25), **구현됨**(2026-10-25, M3 5단계)
- 범위: api-gateway, `libs:platform-http-client-starter`, `libs:platform-mvc-starter`, `libs:platform-observability-starter`
- 관련: ADR-0002(전파 방식), ADR-0005(외부 baggage 차단)

## 배경
단말 컨텍스트를 하위로 나르는 경로가 두 개 섞여 있다.

| 경로 | 동작 | 현재 상태(2026-10-25 코드 확인) |
|---|---|---|
| 헤더 | gateway가 `X-Terminal-Id`·`X-Store-Id` 주입 → 서비스가 MDC에 넣음 → `HttpHeaderPropagator`가 MDC에서 꺼내 다음 호출 헤더로 복사 | **중간 고리가 없다.** MDC에 `terminalId`/`storeId`를 넣는 운영 코드가 없어 두 번째 홉부터 전파되지 않는다 |
| baggage | gateway가 JWT 검증 후 baggage 설정 → tracing이 모든 홉(HTTP, Kafka)에 자동 전파, `correlation.fields`로 MDC에도 자동 반영 | gateway 설정(`remote-fields`, `correlation.fields`)만 있고 **baggage를 설정하는 코드가 없다** |

같은 정보를 두 경로로 나르면 어느 쪽이 기준인지 헷갈리고, 지금처럼 둘 다 반쯤 비어 있는 상태가 된다.

## 결정
1. **terminalId, storeId의 기준(source of truth)은 baggage다.**
   - api-gateway만 JWT 검증 결과(`sub` → `terminalId`, `storeId` 클레임)로 baggage를 설정한다. 외부 baggage는 추출 단계에서 버린다(ADR-0005).
   - 설정할 때도 필터에서 OTel scope를 직접 열고 닫지 않는다(ADR-0002, AGENTS.md 6장). 구체적인 설정 방법은 구현 시 검증한다(아래 "확인 필요").
2. **MDC → 헤더 복사를 제거한다.** `HttpHeaderPropagator`는 `X-Request-Id`만 MDC에서 전파한다. 단말 컨텍스트는 tracing instrumentation이 baggage로 전파한다. 외부 대상에서는 baggage를 지운다(K-3).
3. **`@CurrentTerminal`은 baggage에서 읽는다.** gateway는 `X-Terminal-Id`·`X-Store-Id` 헤더를 더 이상 주입하지 않는다. 외부에서 들어온 같은 이름의 헤더는 HeaderSanitizer가 계속 제거한다(신뢰 경계 유지).
4. **scopes는 baggage에 넣지 않는다.** `X-Terminal-Scopes` 헤더로 gateway → 첫 서비스에만 전달한다.
   - 권한 정보는 첫 서비스의 인가 판단에만 필요하다. baggage에 넣으면 모든 홉과 외부 로그 상관관계에 퍼지고 크기가 커진다.

## 기각한 대안
- **헤더로 일원화**: 단순하고 눈에 보이지만, Kafka 같은 비HTTP 홉마다 서비스 코드가 직접 옮겨 담아야 한다. Outbox → Kafka → broker 경로(Flow 2)에서 누락되기 쉽다.
- **둘 다 유지**: 기준이 둘이 되어 값이 어긋날 때 무엇을 믿을지 정할 수 없다.

## 결과
- 단말 컨텍스트가 HTTP·Kafka 전 구간에서 자동으로 전파되고 모든 서비스 로그에 `terminalId`가 찍힌다.
- **tracing이 필수 인프라가 된다.** `management.tracing.enabled=false`로 끄면 baggage가 전파되지 않아 `@CurrentTerminal`이 동작하지 않는다. 서비스 공통 설정(observability starter 기본값)으로 강제하는 방안을 함께 검토한다.
- `remote-fields`·`correlation.fields` 설정이 서비스마다 필요하다. observability starter 기본값으로 옮겨 중복을 없앤다.
- `@CurrentTerminal`의 동작이 바뀐다(헤더 → baggage). 라이브러리 public API 시그니처는 그대로지만 의존하는 입력이 바뀌므로 변경 이력에 남긴다.

## 구현 시 확인한 것 (2026-10-25)

### gateway가 baggage를 싣는 방법: (a)는 안 되고 (b)를 택했다
- **(a) 라우팅 요청에 `baggage` 헤더를 직접 쓰기 → 실패.** Spring Cloud Gateway의 `GatewayPropagatingSenderTracingObservationHandler`가 하위로 보내기 직전에 propagator가 다루는 헤더(`traceparent`, `baggage` 등)를 **모두 지우고** 현재 trace context에서 다시 주입한다. 직접 쓴 헤더는 항상 사라진다(SCG 5.0.3 바이트코드와 E2E로 확인).
- **(b) 서버 trace context에 baggage를 붙이기 → 채택.** `JwtAuthenticationGlobalFilter`가 `tracer.currentTraceContext().context()`를 얻어 `tracer.createBaggage(name).set(traceContext, value)`로 값을 붙인다. scope를 열고 닫지 않는다(AGENTS.md 6장 위반 아님). SCG가 하위 요청을 보낼 때 이 context에서 `baggage`를 주입한다.
- 서로 다른 단말 50개가 동시에 요청해도 각 하위 요청은 자기 토큰의 값만 받는다(`GatewayE2ETest`).

### `remote-fields`를 쓰지 않는다 (ADR-0002 2항 보완)
- `remote-fields`에 넣은 필드는 W3C `baggage` 헤더와 **별개로 필드 이름 그대로의 헤더(`terminalId: TERM-001`)로도** 나간다.
- K-3에서 외부 대상(VAN)에는 `baggage` 헤더만 지웠기 때문에 **`terminalId` 헤더가 외부로 새고 있었다**(K-16, 헤더를 직접 출력해서 확인).
- 조치:
  - `remote-fields` 설정을 제거했다. W3C `baggage` 헤더 하나로 모든 값이 전달되므로 기능 손실이 없다.
  - 외부 대상에서는 `baggage`와 함께 `terminalId`·`storeId` 이름의 헤더도 지운다(누가 `remote-fields`를 다시 켜도 새지 않게).
  - gateway 입구(HeaderSanitizer)에서도 `terminalId`·`storeId` 헤더를 지운다(위조 방지).
- 로그 MDC 반영(`correlation.fields: terminalId, storeId`)은 observability starter가 기본값으로 넣는다(`PlatformObservabilityEnvironmentPostProcessor`, 가장 낮은 우선순위라 서비스 설정으로 덮어쓸 수 있다).

### MVC 서비스에서 읽는 시점
- `@CurrentTerminal`은 `BaggageManager.getBaggage(name).get()`으로 읽는다. 컨트롤러 인자 해석 시점에는 서버 observation 필터가 이미 baggage를 추출해 scope에 올려 둔 상태다(`CurrentTerminalBaggageIntegrationTest`로 확인).
- baggage가 없으면 `401 MISSING_TERMINAL_CONTEXT`. 예전 `X-Terminal-Id` 헤더만 있으면 거절한다(헤더는 신뢰하지 않는다).
- tracing이 없으면(`BaggageManager` 빈 없음) 항상 거절된다. tracing은 필수 인프라다.

### 남은 전제
- 하위 서비스가 받은 baggage를 신뢰하는 전제: 메시 환경에서 Istio `AuthorizationPolicy`로 호출 주체를 gateway로 제한(ARCHITECTURE 5장). 로컬(Tier 1)에서는 이 제한이 없으므로 하위 서비스에 직접 `baggage`를 보내면 위조할 수 있다.
- gateway 자신의 로그 MDC에는 `terminalId`가 들어가지 않는다(baggage를 붙이는 시점이 MDC 반영 이후). 인증 실패 로그의 `detail`에는 terminalId가 남는다.
