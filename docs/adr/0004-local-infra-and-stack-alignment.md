# ADR-0004: 로컬 실행 환경 단계화와 스택 정렬

- 상태: 채택 (2026-10-22)

## 배경
목표로 하는 운영 환경은 K8s, Istio, ArgoCD, MySQL 기반이다. 프로젝트는 Docker Compose/Swarm과 PostgreSQL을 쓰고 있었다. 노트북에서 k8s, Istio, ArgoCD를 상시로 띄우는 것은 자원상 한계가 있다.

## 결정
1. **실행 환경을 단계화한다.**
   - Tier 1(상시): Docker Compose. 장애 주입은 Toxiproxy.
   - Tier 2(실험할 때만): k3d/kind + Istio minimal 프로필. mTLS, `AuthorizationPolicy`, 다중 인스턴스 실험.
   - 선택: ArgoCD, Chaos Mesh.
2. **Docker Swarm 모드는 제거한다.** 목표 운영 환경과 무관하고 Tier 2가 다중 인스턴스 실험을 대체한다.
3. **PostgreSQL을 MySQL로 전환한다.** 목표 운영 환경과 맞추고, InnoDB 락 동작(gap lock, next-key lock)을 결제 동시성 학습에 직접 활용한다. ~~전환은 payment-ledger 구현(커리큘럼 M6) 시작 시점에 한다.~~ → **2026-10-25 변경: auth-service 구현(커리큘럼 M3) 시작 시점에 전환한다.** auth-service가 DB를 쓰는 첫 서비스(단말 공개키 저장)라 처음부터 MySQL로 시작하면 이후 마이그레이션이 필요 없다.
4. **용어는 Store로 통일한다.** 코드(`X-Store-Id`, `storeId`)를 기준으로 하고 `Merchant`는 쓰지 않는다.
5. **Tier 1에 LocalStack(KMS)을 추가한다(2026-10-25).** JWT 서명 비밀키를 AWS KMS에 두는 구조(ADR-0006)를 로컬에서 재현한다. 버전 고정 이유는 ADR-0006 "결과" 참고.

(2026-10-25 적용) docker-compose를 MySQL 8.4로 전환하고 Swarm 설정(`deploy:`, `overlay` 네트워크)을 제거했다. 호스트 MySQL 포트는 다른 로컬 MySQL과 겹치지 않게 3307(`AEGIS_MYSQL_PORT`)을 기본으로 한다.

## 결과
- 일상 개발 비용은 낮게 유지하고, 메시·다중 인스턴스 학습은 필요할 때만 비용을 낸다.
- Tier 2를 띄우지 않으면 Envoy access log, mTLS 관련 동작은 확인할 수 없다.
