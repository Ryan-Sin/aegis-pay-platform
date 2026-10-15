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
│   ├── platform-http-client-starter       # @HttpExchange 팩토리, Resilience4j (CircuitBreaker/Bulkhead), 컨텍스트 전파
│   └── platform-observability-starter     # OpenTelemetry OTLP 연동, 분산 락 대기 시간 Custom Span, MDC 자동 주입
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

## 🛠️ 기술 스택 (Tech Stack)

| 분류 | 기술 스택 | 비고 |
| :--- | :--- | :--- |
| **Language & Runtime** | Kotlin `2.1.20` (K2), Java `21` (Virtual Threads) | LTS & Non-blocking 수준 처리량 |
| **Framework** | Spring Boot `3.5.3`, Spring Cloud `2025.0.0` | Gateway(WebFlux) + Core(MVC/JPA) 하이브리드 |
| **Database & Lock** | PostgreSQL `17`, Valkey `8.0` (Redisson `3.45.1`) | 복식부기 원장 ACID & Pub/Sub 분산 락 |
| **Messaging** | Apache Kafka `3.9.0` (KRaft Mode) | Zookeeper-less, Transactional Outbox 파이프라인 |
| **Resilience & Observability** | Resilience4j, Micrometer Tracing, OpenTelemetry | 전 구간 `traceparent` 및 `X-Request-Id` 전파 |
