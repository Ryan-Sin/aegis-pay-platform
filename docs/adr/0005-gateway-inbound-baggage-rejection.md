# ADR-0005: 게이트웨이에서 외부 baggage를 추출 단계에서 버린다

- 상태: 채택 (2026-10-24)
- 범위: `services:api-gateway`

## 배경
단말 컨텍스트 baggage(`terminalId`, `storeId`)는 api-gateway만 JWT 검증 결과로 설정해야 한다(ARCHITECTURE 5장). 지금까지는 `HeaderSanitizerGlobalFilter`가 외부 `baggage` 헤더를 지우는 것으로 막았다.

트레이싱을 연결하자(K-1) E2E 테스트에서 하위 서비스가 `baggage: terminalId=SPOOFED-TERM`을 받았다. 원인은 실행 순서에 있다.
1. WebFlux 서버 observation은 `HttpWebHandlerAdapter`에서 시작되므로 **모든 WebFilter/GlobalFilter보다 먼저** 요청 헤더에서 trace context와 baggage를 추출한다.
2. HeaderSanitizer는 그 뒤에 헤더를 지운다. 이미 추출된 baggage는 OTel Context에 남아 있다.
3. Gateway의 하위 HTTP 호출 instrumentation이 Context의 baggage를 다시 `baggage` 헤더로 주입한다.

헤더 제거만으로는 신뢰 경계를 지킬 수 없다.

## 결정
게이트웨이에서 Boot가 만든 `TextMapPropagator` 빈을 `InboundBaggageRejectingPropagator`로 감싼다(`BeanPostProcessor`).
- **extract**: 위임 결과에서 baggage만 추출 전 상태로 되돌린다. `traceparent`는 그대로 이어받는다.
- **inject**: 바꾸지 않는다. 게이트웨이가 JWT로 설정한 baggage는 하위로 전파된다.
- HeaderSanitizer의 `baggage` 제거는 유지한다. 헤더를 그대로 넘기는 라우팅 경로를 막는 별도 방어선이다.

## 기각한 대안
- **`management.tracing.baggage.enabled=false`**: 게이트웨이가 baggage를 설정해서 넘기는 것 자체가 불가능해진다.
- **TextMapPropagator 빈을 직접 정의**: Boot의 consume/produce(W3C, B3) 조합 로직을 다시 구현해야 한다. 감싸는 방식은 Boot 설정을 그대로 쓴다.
- **Netty 레벨에서 헤더 제거**(`NettyServerCustomizer`): Reactor Netty 내부 구조에 결합되고, 같은 문제가 다른 진입점(예: 향후 WebSocket)에서 다시 생긴다.

## 결과
- 외부 baggage는 추출 단계(propagator)와 전달 단계(HeaderSanitizer) 두 곳에서 버려진다.
- 하위 서비스는 게이트웨이에서 온 baggage만 신뢰할 수 있다. 메시 환경에서는 Istio `AuthorizationPolicy`로 호출 주체를 게이트웨이로 제한하는 것이 전제다.
- 게이트웨이가 JWT 검증 후 baggage를 설정하는 코드는 아직 없다(📋). 설정할 때도 필터에서 scope를 직접 열지 않는다(ADR-0002, AGENTS.md 6장).
