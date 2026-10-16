# 🛡️ Aegis Pay Platform

대규모 트래픽과 극한의 정합성(1원의 오차도 불허)을 요구하는 엔터프라이즈 핀테크/결제/원장 플랫폼 아키텍처 프로젝트입니다.
사내 공통 플랫폼 라이브러리(`libs/`)를 직접 설계하여 비즈니스 로직과 횡단 관심사(금융 보안, 분산 락, 복원력, 관측성)를 분리하고, **Spring CGLIB 프록시 체인, Valkey 분산 락, Transactional Outbox, OpenTelemetry 전 구간 추적**의 밑바닥 원리를 검증합니다.

상세한 아키텍처 명세와 설계 의도(Why)는 [ARCHITECTURE.md](./ARCHITECTURE.md)를 참고하세요.

---

## 🏗️ 멀티모듈 아키텍처 구조 (12 Modules)

```text
aegis-pay-platform/
├── libs/                                  # 사내 공통 플랫폼 Custom Starters & Core (jar = true, bootJar = false)
│   ├── platform-core                      # 순수 공통 모듈 (TerminalContext, ErrorCode, ErrorMessage, ErrorResponse, BusinessException)
│   ├── platform-mvc-starter               # Spring MVC 전용 스타터 (@CurrentTerminal ArgumentResolver, MVC 전역 예외 처리, 민감 헤더 마스킹)
│   ├── platform-webflux-starter           # Spring WebFlux 전용 스타터 (Reactive 전역 예외 처리, Reactive 민감 헤더 마스킹 WebFilter)
│   ├── platform-lock-starter              # @DistributedLock (Valkey/Redisson), SpEL 동적 키 파서, 순수 분산 락 AOP
│   ├── platform-http-client-starter       # SyncHttpClient(RestClient) / AsyncHttpClient(WebClient) 추상화, Resilience4j, 에러 번역기
│   └── platform-observability-starter     # OpenTelemetry OTLP 연동, W3C traceparent & Baggage 전파, MVC/Reactive MDC 필터
├── services/                              # 코어 마이크로서비스 (bootJar = true, jar = false)
│   ├── api-gateway                        # Spring Cloud Gateway (WebFlux/Netty), 로컬 Caffeine JWKS 검증, Header Sanitizer
│   ├── auth-service                       # 단말기 공개키 관리, RS256 JWT 발급기, /.well-known/jwks.json 제공
│   ├── payment-ledger-service             # 멱등성 보장, 복식부기 원장, VAN 연동 및 망취소 보상 트랜잭션, Transactional Outbox
│   └── realtime-broker-service            # Kafka 멱등 컨슈머, 매장 POS 단말기 실시간 SSE 브로드캐스팅
└── simulators/                            # 부하 테스트 및 외부 연동 에뮬레이터 (bootJar = true, jar = false)
    ├── terminal-simulator                 # 가상 Secure Element 전자서명, 100 스레드 따닥(Double-Click) 폭격기, 오프라인 SAF
    └── mock-van-server                    # 외부 카드사(VAN) 에뮬레이터 (정상 승인 / 3초 지연 슬립 / 500 에러 유발)
```

---

## 🚀 로컬 인프라 실행 가이드 (Docker Compose & Docker Swarm 양방향 지원)

본 프로젝트의 `docker-compose.yml`은 **일상적인 로컬 개발 모드(Docker Compose)**와 **실제 다중 인스턴스 MSA 클러스터 환경(Docker Swarm)** 양쪽에서 모두 동작하도록 설계되어 있습니다.

### 1️⃣ 모드 A: 일반 로컬 개발 & 디버깅 모드 (Docker Compose)
평소 IntelliJ IDEA에서 마이크로서비스 코드를 작성하고 브레이크포인트(Breakpoint)를 찍으며 빠르게 디버깅할 때는 코어 인프라(PostgreSQL 17, Valkey 8.0, Kafka 3.9 KRaft)만 컨테이너로 가볍게 구동합니다.

```bash
# 1. 코어 인프라 백그라운드 실행
docker compose up -d

# 2. 실행 상태 및 Healthcheck 확인
docker compose ps

# 3. 종료할 때
docker compose down
```

### 2️⃣ 모드 B: 실전 MSA 클러스터 & 다중 인스턴스 모드 (Docker Swarm)
`overlay` 네트워크와 내장 VIP 로드밸런서를 활성화하여, 다중 복제본(`replicas: 2` 이상) 환경에서 Valkey 분산 락(`@DistributedLock`)과 로컬 캐시(Caffeine) JWKS 검증을 실제 클러스터 수준으로 테스트할 때 사용합니다.

```bash
# 1. Docker Swarm 모드 초기화 (최초 1회)
docker swarm init

# 2. aegis 스택(Stack)으로 클러스터 배포
docker stack deploy -c docker-compose.yml aegis

# 3. 배포된 서비스 및 Replicas 상태 확인
docker stack services aegis
docker stack ps aegis

# 4. 스택 제거 및 Swarm 모드 종료 (필요 시)
docker stack rm aegis
docker swarm leave --force
```

---

## 🧪 테스트 코드 작성 원칙 (Testing Policy)

본 프로젝트는 단순 코드 커버리지 수치를 높이기 위한 기계적인 테스트를 지양하고, **실제 운영 장애를 방어하고 설계 의도를 증명하는 유효한 테스트(High-Value Test)**만 작성하는 것을 원칙으로 합니다.

1. **단순 생성자 및 Getter/Setter 테스트 지양 (No Boilerplate Coverage Padding)**
   - 코틀린 컴파일러가 보장하는 `data class` 프로퍼티 대입, `enum` 상수 조회, 분기 없는 단순 위임(Delegation) 코드는 테스트하지 않습니다.
2. **분기 로직(Branching)과 경계값(Boundary / Edge Cases) 집중 타격**
   - `if / when / 정규식 / SpEL 파싱` 등 논리 분기가 존재하는 지점에서 정상 케이스뿐만 아니라 공백(`"   "`), `null`, 포맷 인자 불일치, 비정상 응답(HTML/Empty Body) 등의 경계 조건을 검증합니다.
3. **장애 발생 시 자원 반납 및 Fail-Safe 보장 (Resource Safety & Resilience)**
   - 비즈니스 로직 수행 중 예외가 발생하더라도 `finally` 블록을 통한 **분산 락 해제(`unlock()`)**와 **스레드 풀 `MDC` 컨텍스트 정리(`remove()`)**가 100% 보장되는지 검증합니다.
   - 예외 메시지 포맷팅이나 에러 응답 역직렬화 과정에서 2차 예외(`MissingFormatArgumentException` 등)로 인해 원본 장애 원인이 증발하지 않도록 Fail-Safe 폴백을 검증합니다.
4. **깨지기 쉬운 테스트 방지 (Non-Fragile Behavior Assertions)**
   - 기획/운영 과정에서 자주 변경될 수 있는 한국어 안내 문구(Copy) 전체를 하드코딩하여 비교(`assertEquals`)하지 않습니다.
   - 대신 **포맷 지정자(`%s`)가 정상 소거되었는지**, **전달한 동적 인자와 에러 코드가 정확히 매핑되었는지** 등 구조적 행위(Behavior)를 단언하여 문구 수정에 강건한 테스트를 유지합니다.

---

## 🛠️ 기술 스택 (Tech Stack)

| 분류 | 기술 스택 | 비고 |
| :--- | :--- | :--- |
| **Language & Runtime** | Kotlin `2.3.21` (K2), Java `21` (Virtual Threads) | LTS & Non-blocking 수준 처리량 |
| **Framework** | Spring Boot `4.1.1`, Spring Cloud `2025.1.3` | Gateway(WebFlux) + Core(MVC/JPA) 하이브리드 |
| **Database & Lock** | PostgreSQL `17`, Valkey `8.0` (Redisson `4.8.0`) | 복식부기 원장 ACID & Pub/Sub 분산 락 |
| **Messaging** | Apache Kafka `3.9.0` (KRaft Mode) | Zookeeper-less, Transactional Outbox 파이프라인 |
| **Resilience & Observability** | Resilience4j, Micrometer Tracing, OpenTelemetry | 전 구간 `traceparent` 및 `X-Request-Id` 전파 |
| **Testing** | JUnit `6.0.3` (Jupiter), Kotlin Test | 경계값/예외 복구 중심의 행동 검증 테스트 |
