# AGENTS.md: AI 에이전트 공통 작업 규칙

이 파일은 이 저장소에서 작업하는 모든 AI 에이전트(Gemini, Claude 등)의 **단일 기준**이다.
`GEMINI.md`, `CLAUDE.md`는 이 파일을 import만 하며, 규칙은 여기에만 쓴다.

---

## 1. 프로젝트 맥락
- 서버 플랫폼 엔지니어링 역량을 기르기 위한 **학습 프로젝트**다. 도메인은 오프라인 결제 단말·POS 환경이다.
- 목표는 두 가지다. ① 목표 역량(공통 라이브러리·플랫폼 서비스 설계, 호환성 관리, 분산 환경 실패 처리, 지속 연결, 관측성)을 **동작하는 코드와 테스트로 증명**한다. ② 실무에서 그대로 쓸 수 있는 수준으로 학습한다.
- **로컬에 `docs/private/TARGET.md`가 있으면 작업 전에 먼저 읽는다.** 이 파일은 git에 올리지 않는 개인 맥락이며, 그 내용을 커밋되는 파일에 옮겨 적지 않는다.
- 사용자는 한국에서 일하는 백엔드 엔지니어다. 설명은 한국어로 하고, 코드·로그·명령어는 원문을 유지한다.

## 2. 문서 지도 (어떤 질문에 어떤 문서를 보는가)

| 문서 | 역할 | 성격 |
|---|---|---|
| `ARCHITECTURE.md` | 목표 설계와 설계 이유(Why), 헤더·신뢰 경계 규약 | **목표(Spec)**. 아직 구현되지 않은 내용이 포함된다 |
| `docs/STATUS.md` | 모듈별 실제 구현 상태, 알려진 문제 | **사실(Fact)**. 코드와 항상 일치해야 한다 |
| `docs/CURRICULUM.md` | 목표 역량 ↔ 마일스톤과 우선순위 | 계획 |
| `docs/adr/` | 이미 내린 기술 결정과 근거 | **결정**. 임의로 뒤집지 않는다 |
| `README.md` | 개요, 실행 방법, 테스트 정책, 기술 스택 | 안내 |

- "구현되어 있다"고 말하기 전에 반드시 `docs/STATUS.md`와 실제 코드를 확인한다. `ARCHITECTURE.md`에 적혀 있다는 것은 구현되었다는 뜻이 아니다.
- 문서와 코드가 다르면 **코드가 사실**이다. 차이를 발견하면 사용자에게 알리고 `docs/STATUS.md`의 알려진 문제에 기록할 것을 제안한다.

## 3. 작업 모드

사용자의 요청에 따라 모드를 구분한다. 모드가 불분명하면 **가이드 모드**로 시작한다.

### 3.1 가이드 / 리뷰 모드 (기본)
- 사용자가 직접 코딩하며 학습한다. 요청 없이 비즈니스 로직이나 설정 코드를 대신 작성하지 않는다.
- 단계별 구현 가이드, 원리(Why), 대안과 트레이드오프, **완료 기준이 되는 테스트 목록**을 제시한다.
- 리뷰는 지금 작업 중인 코드에 집중한다. 작업 순서나 로드맵에 대한 조언은 요청할 때만 한다.

### 3.2 구현 모드 ("구현해줘", "수정해줘" 등 명시적 요청)
- 완료 기준은 **관련 Gradle 테스트 통과**다. 테스트를 실행하지 않았다면 완료라고 말하지 않는다.
- 변경한 파일 목록, 실행한 명령과 결과(통과/실패 수)를 보고한다.
- 요청 범위 밖의 파일(특히 루트 `build.gradle.kts`, 다른 모듈)은 바꾸기 전에 먼저 묻는다.

## 4. 검증 규칙 (모든 모드 공통)
1. **사실과 추정을 구분한다.** 실행해서 확인한 것만 "확인했다"고 말한다. 확인하지 않은 것은 "추정" 또는 "확인 필요"로 표시한다.
2. **라이브러리 API는 실제 버전을 확인하고 쓴다.** 기억에 의존하지 않는다. 버전은 `gradle.properties`, `./gradlew :<module>:dependencies`로, API는 jar의 클래스(`javap`)나 공식 문서로 확인한다.
3. **Spring Boot 4.x 기준이다.** Boot 3.x 자료를 그대로 적용하지 않는다. 특히 Boot 4는 auto-configuration이 기술별 모듈로 분리됐다(예: 트레이싱은 `spring-boot-micrometer-tracing-opentelemetry`, `spring-boot-starter-opentelemetry`). 테스트 지원 클래스 패키지도 바뀌었다.
4. 테스트는 `README.md`의 테스트 정책을 따른다. 문구 전체를 비교하지 않고 구조적 행위를 단언한다.
5. 실험이나 검증용으로 만든 임시 파일은 끝나면 삭제하고, 삭제했다고 보고한다.

## 5. 설계 규칙 (이미 합의된 것)
- 결정 사항은 `docs/adr/`에 있다. ADR과 다른 방향을 제안하려면 **먼저 근거와 함께 사용자에게 묻는다.**
- 용어: 단말 소속 식별자는 **Store**(`X-Store-Id`, claim `storeId`)다. `Merchant`는 쓰지 않는다.
- 신뢰 경계: `X-Terminal-*`, `X-Store-*` 헤더와 단말 컨텍스트 baggage는 **api-gateway만** JWT 검증 결과로 설정한다. 외부에서 들어온 값은 게이트웨이에서 제거한다.
- 예외: 클라이언트 응답 문구는 `BusinessException.message`, 운영 로그용 원인은 `BusinessException.detail`에 담는다(ADR-0001). 서비스별 예외 서브클래스를 만들지 않는다.
- 응답 형식: 성공은 `SuccessResponse<T>`를 컨트롤러 반환 타입으로 직접 쓰고, 실패는 `ErrorResponse`다. 표준 형식이 정해진 응답(JWKS)만 감싸지 않는다(ADR-0009).
- 서비스 내부 패키지는 헥사고날(`domain` ← `application`(port/in, port/out, service) ← `adapter`(in, out))로 나눈다(ADR-0008). 각 서비스의 `HexagonalArchitectureTest`가 의존 방향을 검사한다.
- 공통 라이브러리(`libs/`)의 public API를 바꿀 때는 **바이너리 호환성**을 함께 검토한다(생성자·메서드 시그니처 변경은 이미 컴파일된 사용처를 깨뜨린다).

## 6. 금지 패턴 (실제로 발생했던 실수)

| 하지 말 것 | 이유 | 대신 |
|---|---|---|
| 필터나 커스텀 `ThreadLocalAccessor`에서 `BaggageManager.createBaggageInScope`로 OTel scope를 직접 열고 닫기 | OTel Context scope는 같은 스레드에서 LIFO로 닫혀야 한다. Micrometer의 `ObservationThreadLocalAccessor`와 충돌해 컨텍스트가 오염된다 | baggage는 `management.tracing.baggage.*` 설정으로 선언적으로 쓴다(ADR-0002) |
| WebFlux 필터에서 `MDC.put` 직접 호출 | Netty event loop 스레드에 값이 남아 다른 요청으로 새어 나간다 | `Slf4jThreadLocalAccessor` + `contextWrite`(ADR-0002) |
| `ContextRegistry`에 accessor를 빈 생성자나 `init`에서 등록 | 전역 레지스트리라서 중복 등록·교체가 생긴다 | auto-configuration에서 한 번만 등록 |
| `management.tracing.baggage.remote-fields` 사용 | 필드가 W3C `baggage`와 별개로 `terminalId:` 같은 개별 헤더로도 나가서 외부 대상 차단을 우회한다(K-16) | W3C `baggage` 헤더만 쓴다. MDC 반영은 `correlation.fields` |
| gateway에서 하위 요청에 `baggage` 헤더를 직접 써서 단말 컨텍스트 전달 | SCG가 하위로 보내기 직전에 propagation 헤더를 지우고 trace context에서 다시 주입해서 사라진다 | 서버 trace context에 `Baggage.set(traceContext, value)`로 붙인다(scope를 열지 않음, ADR-0007) |
| 리액티브 체인 조립 시점에 `tracer.currentSpan()` 호출 | observation은 Reactor Context에 있어 조립 시점에는 null이다 | `Mono.deferContextual` + `ContextSnapshotFactory` |
| 클라이언트가 보낸 ID/헤더 값을 검증 없이 MDC·로그·응답에 사용 | 로그 인젝션, 카디널리티 폭증 | 형식·길이를 검증하고, 실패하면 새로 생성 |
| 에러 응답에 내부 원인(reason, kid, jti, 인프라명) 노출 | 공격자에게 검증 단계 정보를 준다 | `detail`에 담아 로그에만 남긴다 |
| 메트릭 태그에 `detail`, `jti`, `kid`, 사용자 ID 등 고유값 사용 | 카디널리티 폭증 | `code`처럼 값의 종류가 제한된 것만 태그로 쓴다 |
| 서비스마다 `XxxFailedException` 같은 예외 서브클래스 생성 | 플랫폼 공통 예외 체계가 갈라진다 | `BusinessException(errorCode, detail = ...)` |
| 루트 `build.gradle.kts`에서 모든 모듈의 테스트 classpath를 한꺼번에 바꾸기(예: `testImplementation.extendsFrom(compileOnly)`) | web과 webflux가 동시에 올라가 `@SpringBootTest`의 servlet/reactive 판정이 바뀔 수 있다 | 필요한 모듈에만 `testImplementation`을 명시 |

## 7. 코드·커밋 규칙
- Kotlin 공식 코드 스타일, 주변 코드의 네이밍과 주석 밀도를 따른다.
- SQL은 소문자 키워드, snake_case, 주요 절은 새 줄, 조건은 4칸 들여쓰기.
- 커밋 메시지는 영어 Conventional Commits(`feat(platform-core): ...`)로 쓴다. 커밋은 사용자가 요청할 때만 한다.
- 설명 형식: 핵심 요약 → 코드/설정 → 원리(Why) → 장단점 → 회고(개선점·다른 접근).
