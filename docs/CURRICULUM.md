# 학습 커리큘럼: 목표 역량과 마일스톤

> 원칙: 기술의 개수보다 **문제를 정확히 이해하고 적절한 해결 방법을 선택하는 것**을 우선한다.
> 그래서 각 마일스톤은 **"왜 이걸 골랐고 무엇을 포기했는가"**를 ADR과 테스트로 남기는 것으로 끝난다.

## 1. 목표 역량 ↔ 프로젝트 매핑

| 목표 역량 | 프로젝트 대응 | 현재 |
|---|---|---|
| 인증·인가, JWT 발급·JWKS 서비스, API Gateway | api-gateway, auth-service | Gateway ✅(M1) / auth 📋 |
| Feature Flag, A/B Test, 메시징 플랫폼 | Feature Flag 서비스 + SDK, Messaging 라이브러리 | 📋 |
| SSE Server, MQTT Broker (지속 연결, 높은 동시 연결 수) | realtime-broker-service를 연결 관리 플랫폼으로 | 📋 |
| Metric, Logging, Distributed Lock, Messaging 공통 라이브러리 | `libs/` 스타터 | Lock·Logging(예외) ✅ / Metric·Messaging 📋 |
| 사용하기 쉬운 인터페이스와 확장 지점 | AutoConfiguration + `@ConditionalOnMissingBean` | ✅ |
| 테스트, 호환성·버전 관리, 점진 적용, 마이그레이션, 문서·예제 | 라이브러리 배포·BOM·호환성 검증·마이그레이션 도구 | 📋 (가장 큰 공백) |
| IDP / 개발 도구 | Gradle convention plugin, 서비스 템플릿, OpenRewrite 레시피 | 📋 |
| 관측 데이터 제공, 장애 대응·재발 방지 | 메트릭 규약, 트레이싱, Grafana, Chaos 실험과 회고 | 🚧 (트레이싱 연결 ✅, 메트릭 규약·대시보드 📋) |
| 동시성, 상태, 타임아웃, 재시도, 중복 처리, 부분 실패 | payment Flow 2, 락-트랜잭션 분리, Outbox, 망취소 | 설계 ✅ / 구현 📋 |
| 여러 계층의 데이터로 장애·병목 분석 | JVM·Netty·커넥션 메트릭, 부하 시뮬레이터 | 📋 |
| 운영 환경: MySQL, Redis, Kafka, K8s, Istio, ArgoCD, Vault, Prometheus/Grafana, Chaos Mesh | MySQL 전환(M3), Tier 2 로컬 k3d + Istio, KMS(서명키, 로컬은 LocalStack) | 📋 |

## 2. 마일스톤 (우선순위 순)

각 마일스톤의 완료 조건 = **테스트 통과 + ADR 또는 회고 문서 1장 + `docs/STATUS.md` 갱신**.

| # | 마일스톤 | 핵심 산출물 | 목표 역량 |
|---|---|---|---|
| M1 | **api-gateway 마무리** | `baggage` sanitize, 테스트 3종(JwksKeyProvider·JwtTokenVerifier·E2E), 커밋 | 인증·인가, API Gateway |
| M2 | **관측성 기반 복구** | Boot 4 `spring-boot-starter-opentelemetry` 연결(K-1), `ReactiveTraceFilter` 재작성(K-2), 코루틴(`mono {}`) 경계 전파 테스트 | 관측 데이터 제공 |
| M3 | **auth-service** | MySQL + LocalStack KMS, JWT 발급(KMS 서명 위임, ADR-0006), JWKS, 서명키 로테이션(새 키 + alias 전환, 이전·신규 키 동시 게시), `iss`/`aud` 확정, gateway baggage 설정(ADR-0007), JWKS warm-up(K-12) | JWT 발급·JWKS 서비스 |
| M4 | **라이브러리 변경 관리** | `maven-publish` + SemVer + `platform-bom`, Kotlin binary-compatibility-validator를 CI에 적용, K-7(`detail`)을 사례로 deprecation 경로 문서화 | 호환성·버전 관리·마이그레이션 |
| M5 | **연결 관리 플랫폼 (SSE)** | 인스턴스 간 라우팅(Redis Pub/Sub 또는 Kafka), 배포 시 drain, jitter backoff 재연결, `Last-Event-ID` 재전송, 대량 연결 부하 테스트 | SSE/MQTT, 높은 동시 연결 |
| M6 | **Messaging 라이브러리 + payment Flow 2** | Outbox(+ trace 컨텍스트 저장), 멱등 컨슈머, 락-트랜잭션 분리, 망취소, MySQL 전환, 100 스레드 동시 요청에서 정확히 1건 승인 증명 | Messaging, 중복 처리, 부분 실패 |
| M7 | **메트릭 규약 + 장애 실험** | 공통 태그·카디널리티 규약 스타터, Grafana 대시보드, Toxiproxy(Tier 1)·Chaos Mesh(Tier 2) 실험과 장애 회고 | Metric, 장애 대응·재발 방지 |
| M8 | **Feature Flag** | Flag 서비스 + Spring SDK + 로컬 캐시 + SSE 변경 전파 | Feature Flag / A/B Test |
| M9 | (선택) **개발 도구** | Gradle convention plugin, 서비스 템플릿, OpenRewrite 마이그레이션 레시피 | IDP / 개발 도구 |

## 3. 산출물 규칙
- **ADR**: `docs/adr/NNNN-제목.md`. 배경 → 결정 → 대안과 기각 이유 → 결과(트레이드오프).
- **회고**: 장애 실험이나 시행착오는 `docs/retro/`에 가설 → 데이터 → 원인 → 조치 → 재발 방지 순으로 남긴다. 장애 대응·트러블슈팅 경험을 설명하는 근거 자료가 된다.
- 블로그로 옮길 주제는 ADR과 회고에서 고른다.
