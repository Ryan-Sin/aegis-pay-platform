# ADR-0007: 단말 컨텍스트(terminalId, storeId)는 baggage 하나로 전파한다

- 상태: 채택 (2026-10-25), **구현 전**(커리큘럼 M3)
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

## 확인 필요(구현 시)
- gateway에서 scope를 직접 열지 않고 하위 요청에 baggage를 싣는 방법: (a) 라우팅 요청에 W3C `baggage` 헤더를 직접 쓰고 outbound propagator가 덮어쓰지 않는지 확인, (b) observation/컨텍스트 API로 Reactor Context에 baggage를 싣는 방법. 둘 중 테스트로 확인되는 쪽을 택한다.
- MVC 서비스에서 `@CurrentTerminal`이 baggage를 읽는 시점에 서버 observation이 이미 추출을 끝냈는지(필터 순서).
- 하위 서비스가 받은 baggage를 신뢰하는 전제: 메시 환경에서 Istio `AuthorizationPolicy`로 호출 주체를 gateway로 제한(ARCHITECTURE 5장).
