# 🛡️ Aegis Pay Platform

오프라인 결제 단말기·POS 환경을 전제로, 여러 제품이 공통으로 쓰는 **플랫폼 서비스와 공통 라이브러리**를 직접 설계·구현하는 학습 프로젝트입니다.
인증·인가와 API Gateway, 분산 락, 복원력, 관측성, 메시징, 지속 연결을 다루며, 각 기술 선택의 이유를 ADR과 테스트로 남깁니다.

| 문서 | 내용 |
|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 목표 설계와 설계 의도(Why), 헤더·신뢰 경계 규약 |
| [docs/STATUS.md](./docs/STATUS.md) | 모듈별 실제 구현 상태와 알려진 문제 |
| [docs/CURRICULUM.md](./docs/CURRICULUM.md) | 목표 역량과 마일스톤 |
| [docs/adr/](./docs/adr/) | 기술 결정 기록 |
| [AGENTS.md](./AGENTS.md) | AI 에이전트 공통 작업 규칙 (`GEMINI.md`, `CLAUDE.md`가 import) |

---

## 🏗️ 모듈 구조

상태: ✅ 구현됨 · 🚧 진행 중 · 📋 계획 (상세는 [docs/STATUS.md](./docs/STATUS.md))

```text
aegis-pay-platform/
├── libs/                                  # 공통 플랫폼 라이브러리 (jar = true, bootJar = false)
│   ├── platform-core                      # ✅ TerminalContext, ErrorCode, ErrorResponse, BusinessException(message/detail)
│   ├── platform-mvc-starter               # ✅ @CurrentTerminal, MVC 전역 예외 처리, 민감 헤더 마스킹
│   ├── platform-webflux-starter           # ✅ Reactive 전역 예외 처리, 민감 헤더 마스킹 WebFilter
│   ├── platform-lock-starter              # ✅ @DistributedLock (Redisson), SpEL 키 파서
│   ├── platform-http-client-starter       # ✅ SyncHttpClient / AsyncHttpClient, Resilience4j, 에러 번역
│   └── platform-observability-starter     # ✅ OpenTelemetry 연동(Boot 4 starter), Request ID 헤더·MDC 전파
├── services/                              # 서비스 (bootJar = true, jar = false)
│   ├── api-gateway                        # 🚧 Spring Cloud Gateway, Header Sanitizer, JWKS 캐시 JWT 검증, Blacklist, 외부 baggage 차단
│   ├── auth-service                       # 🚧 단말 challenge 인증·토큰 발급, KMS 서명(RS256), JWKS / 📋 단말 등록 API, 서명키 로테이션
│   ├── payment-ledger-service             # 📋 멱등성, 락-트랜잭션 분리, 망취소, Transactional Outbox
│   └── realtime-broker-service            # 📋 Kafka 멱등 컨슈머, SSE 연결 관리
└── simulators/
    ├── terminal-simulator                 # 📋 단말 서명, 동시 요청, SAF, 대량 연결 부하
    └── mock-van-server                    # 📋 정상 승인 / 지연 / 500 에러 VAN 에뮬레이터
```

---

## 🚀 로컬 실행

실행 환경은 단계별로 나눕니다([ADR-0004](./docs/adr/0004-local-infra-and-stack-alignment.md)).

| 단계 | 구성 | 용도 |
|---|---|---|
| Tier 1 (상시) | Docker Compose | 일상 개발과 디버깅 |
| Tier 2 (필요할 때만) | k3d/kind + Istio(minimal) | mTLS, AuthorizationPolicy, 다중 인스턴스 실험 |

### Tier 1: Docker Compose

```bash
# 코어 인프라 실행: MySQL 8.4(호스트 3307), Valkey 8.0, Kafka 3.9 KRaft, LocalStack 4.14.0(KMS)
# --wait: 모든 healthcheck 통과(KMS 서명 키 생성 포함)까지 기다린다
docker compose up -d --wait

# 상태 확인
docker compose ps

# 종료(볼륨까지 지우면 init SQL이 다시 실행된다: docker compose down -v)
docker compose down
```

### 서비스 실행

```bash
# auth-service: local 프로필은 LocalStack KMS(localhost:4566, 고정 test 자격 증명)를 쓴다
SPRING_PROFILES_ACTIVE=local ./gradlew :services:auth-service:bootRun
curl -s localhost:8081/api/v1/auth/jwks
```

### 빌드와 테스트

```bash
./gradlew build
./gradlew :libs:platform-core:test
./gradlew :services:api-gateway:test
./gradlew :services:auth-service:test   # Docker 필요(Testcontainers: MySQL 8.4, LocalStack 4.14.0, Valkey 8.0)
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

## 🛠️ 기술 스택

| 분류 | 기술 | 비고 |
| :--- | :--- | :--- |
| **Language & Runtime** | Kotlin `2.3.21`, Java `21` | |
| **Framework** | Spring Boot `4.1.1`, Spring Cloud `2025.1.3` | Gateway(WebFlux) + 서비스(MVC). Boot 4는 auto-configuration이 기술별 모듈로 분리됨 |
| **Database & Lock** | MySQL `8.4`, Valkey `8.0` (Redisson `4.8.0`) | 서비스별 DB(`aegis_auth` …), 스키마는 `infra/mysql/init/*.sql`(마이그레이션 도구 없음), JPA는 `validate`만. 분산 락 |
| **Messaging** | Apache Kafka `3.9.0` (KRaft) | Transactional Outbox |
| **Key Management** | AWS KMS (로컬: LocalStack `4.14.0`, 토큰 불필요) | JWT 서명키, ADR-0006 |
| **Resilience & Observability** | Resilience4j, Micrometer, OpenTelemetry, Prometheus, Grafana | 트레이싱 연결 완료. 메트릭 수집 방식은 M7에서 결정 ([STATUS K-10](./docs/STATUS.md)) |
| **Testing** | JUnit `6`, Kotlin Test, Testcontainers `2.0.5` | 경계값·장애 복구 중심 행위 검증. DB 테스트는 운영과 같은 MySQL 8.4 컨테이너로(Docker 필요) |
