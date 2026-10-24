# ADR-0002: Request ID와 단말 컨텍스트 전파 방식

- 상태: 채택 (2026-10-22)
- 범위: `libs:platform-observability-starter`, api-gateway, 모든 서비스

## 배경
`ReactiveTraceFilter`에서 Request ID를 baggage로 전파하려고 커스텀 `ThreadLocalAccessor`가 `BaggageInScope`를 직접 열고 닫게 만들었는데, 테스트가 실패했다(`expected: <req-123> but was: <null>`). 또한:
- OTel Context scope는 같은 스레드에서 LIFO로 닫혀야 한다. Micrometer `ObservationThreadLocalAccessor`도 같은 Context를 다루기 때문에 두 accessor가 섞이면 순서가 깨진다.
- 현재 트레이싱이 연결되어 있지 않아(`Tracer` null) baggage에 의존하는 설계는 동작하지 않는다.

## 결정
1. **Request ID = HTTP 헤더 + MDC**
   - 필터가 `X-Request-Id`를 검증(`[A-Za-z0-9._-]{8,64}`)하고, 실패하거나 없으면 UUID를 생성한다.
   - 요청 헤더를 덮어써서 하위 서비스로 전달하고, 응답 헤더에도 설정한다.
   - `contextWrite`로 `Slf4jThreadLocalAccessor.KEY`(`"cp.slf4j"`)에 `requestId`를 넣는다. `Slf4jThreadLocalAccessor`(context-propagation 기본 제공)가 각 연산자 실행 스레드의 MDC를 복원하고 정리한다.
   - accessor 등록과 `Hooks.enableAutomaticContextPropagation()`은 auto-configuration에서 한 번만 한다.
2. **단말 컨텍스트(`terminalId`, `storeId`) = OTel baggage, 설정 기반**
   - `management.tracing.baggage.remote-fields`와 `correlation.fields`로 선언한다. 필터에서 scope를 직접 다루지 않는다.
   - baggage는 api-gateway만 JWT 검증 결과로 설정하고, 외부 유입 `baggage` 헤더는 제거한다.
3. trace_id·span_id는 OTel과 Micrometer Tracing이 자동 처리한다.

## 검증
참조 구현으로 다음 테스트 6건이 통과하는 것을 확인했다.
- 스레드 전환(`publishOn(boundedElastic)`) 이후에도 MDC에서 조회되고, 요청이 끝나면 호출 스레드에 남지 않는다
- 잘못된 ID(짧은 값, 공백, `${jndi:x}`, 70자 초과)는 새 UUID로 교체된다
- 동시 요청 200건에서 각 요청이 자기 ID만 본다

## 기각한 대안
- **Request ID를 baggage로 직접 관리**: scope 충돌. 트레이싱이 없으면 동작하지 않는다.
- **필터에서 `MDC.put`**: Netty event loop 스레드에 남아 다른 요청으로 샌다.
- **Request ID 폐지(trace_id로 대체)**: 최근 추세지만 Istio/Envoy가 `x-request-id`를 기본으로 쓰고, 기존 MVC 필터와 `HttpHeaderPropagator`가 헤더 방식이라 유지한다.

## 결과
- 트레이싱 유무와 상관없이 Request ID가 전파된다. 메시 access log와 같은 ID로 연결된다.
- Kafka 같은 비HTTP 홉은 메시지 헤더에 직접 실어야 한다.
- 코루틴(`mono {}`)은 기본 디스패처(`Dispatchers.Default`)에서 실행되어 MDC가 전파되지 않는다. Spring 7의 `PropagationContextElement`를 붙이면(`mono(PropagationContextElement()) { }`) 디스패처를 바꿔도 유지된다(`ReactiveTraceFilterTest`로 확인, 2026-10-24).
- MDC accessor는 `Slf4jThreadLocalAccessor("requestId")`처럼 키를 지정한 selective 모드로 등록한다. 인자 없이 만들면 MDC 전체를 덮어써서 Micrometer Tracing이 넣는 traceId/spanId와 충돌한다.
- 트레이싱 연결 후 외부 baggage가 추출 단계에서 들어오는 문제는 ADR-0005에서 다룬다.
