# ADR-0003: api-gateway JWT 검증, JWKS 캐시, Blacklist 정책

- 상태: 채택 (2026-10-21)
- 범위: `services:api-gateway`

## 결정
| 항목 | 정책 | 이유 |
|---|---|---|
| 서명 알고리즘 | RS256 고정, `kid` 필수 | alg confusion(`none`, HS256) 차단 |
| 필수 클레임 | `sub`(terminalId), `storeId`, `jti`, `exp`(+30초 leeway) | 하위 서비스 신뢰 헤더의 출처 |
| JWKS 캐시 | Caffeine `refreshAfterWrite`(30m) + `expireAfterWrite`(6h) | 백그라운드 갱신 실패 시 기존 키 유지(stale-while-revalidate). auth-service 장애가 곧바로 게이트웨이 장애로 번지지 않게 한다 |
| 모르는 `kid` | 시도 시각 기준 `AtomicLong` CAS cooldown(30s) 안에서 1회만 강제 갱신. 갱신 실패 시 기존 키 유지, 401 | 키 로테이션 대응. 위조 kid 폭격이 auth-service로 전파되지 않게 하고, 위조 토큰은 503이 아니라 401로 분류 |
| Blacklist | Redis `hasKey`, 200ms timeout, 실패 시 **503 fail-closed** | 결제 도메인에서 폐기 토큰 통과는 허용 불가. 의도한 fail-closed임을 코드와 에러 코드(`TOKEN_STORE_UNAVAILABLE`)로 명시 |
| 인증 예외 경로 | 설정(`permit-all-paths`)으로 관리, `/api/v1/auth/token`만 허용 | 하드코딩 이중 관리 제거, logout 등은 토큰 필요 |
| JWKS 경로 (2026-10-25 변경) | `/api/v1/auth/jwks`. gateway가 내부망으로 auth-service에 직접 호출한다. 외부 요청은 `blocked-paths`로 토큰이 있어도 404 | 공개키를 가져가는 쪽은 gateway뿐이라 외부에 열 이유가 없다. 예전 `/.well-known/jwks.json`은 몇몇 제품의 관례일 뿐 표준 경로가 아니다(RFC 8615는 `/.well-known/` 접두사만 정한다). 외부 검증 주체가 생기면 그때 공개 경로를 다시 정한다 |
| 응답·로그 | 응답은 고정 문구, 원인은 `detail`로 로그에만 (ADR-0001) | 검증 단계 정보 노출 방지 |

## JWKS 캐시 동작 (2026-10-25 보강)
api-gateway는 공개키를 **auth-service의 JWKS**에서 받는다. KMS와 직접 통신하지 않는다(서명키 관리는 ADR-0006).

| 시점 | 동작 | 설정 |
|---|---|---|
| 기동 (2026-10-25 추가) | `ApplicationStartedEvent`에서 백그라운드로 JWKS 적재(warm-up). 실패하면 지수 backoff로 성공할 때까지 재시도. 그동안 요청이 오면 기존처럼 그 자리에서 적재를 시도한다 | `jwks-warm-up-initial-backoff: 1s`, `jwks-warm-up-max-backoff: 30s` |
| 첫 요청 | warm-up 전이면 캐시가 비어 있으므로 JWKS 호출 후 적재 | — |
| 평소 | 캐시로 검증, 네트워크 호출 없음 | — |
| 갱신 주기 경과 | 요청은 기존 키로 처리하고 백그라운드 갱신. 실패 시 기존 키 유지 | `jwks-refresh-interval: 30m` |
| 만료 | 다음 요청에서 다시 적재 | `jwks-cache-ttl: 6h` |
| 모르는 `kid` | cooldown 안에서 1회 강제 갱신. 실패해도 401(503 아님) | `jwks-min-refresh-cooldown: 30s` |

키 교체 시: 새 `kid` 토큰이 처음 들어오면 강제 갱신으로 새 공개키를 받고, 이전 `kid` 토큰은 이전 공개키가 JWKS에 남아 있는 동안 계속 검증된다. auth-service 쪽 교체 절차는 ADR-0006 5절.

### 기동 직후 auth-service 장애 (STATUS K-12, 2026-10-25 해결)
지연 로딩만 있으면 gateway가 뜰 때 auth-service가 내려가 있을 경우 캐시가 비어 "기존 키 유지" 방어가 작동하지 않고, 복구될 때까지 모든 인증 요청이 503이 된다. 그래서 다음과 같이 보강했다.

| 항목 | 결정 | 이유 |
|---|---|---|
| warm-up | 기동 시 백그라운드에서 적재, 성공할 때까지 지수 backoff(1s→30s 상한)로 재시도 | 기동을 막지 않는다(블로킹 warm-up은 auth-service 장애 시 gateway 기동 실패 → 재시작 반복으로 번진다) |
| readiness | `jwks` health indicator를 readiness 그룹에 포함. 적재 전 DOWN | 키가 없는 인스턴스로 트래픽이 가지 않게 한다 |
| liveness | `livenessState`만. JWKS 상태는 넣지 않는다 | auth-service 장애로 gateway가 재시작되는 연쇄 장애 방지 |
| 적재 후 | 한 번 적재되면 이후 갱신 실패와 무관하게 readiness UP 유지 | 갱신 실패 시에도 기존 키로 검증할 수 있다. auth-service 장애마다 모든 gateway가 트래픽에서 빠지면 토큰 발급 경로까지 막힌다 |
| 키 없는 JWKS | `{"keys": []}`처럼 쓸 수 있는 RSA 키가 없으면 적재 실패로 처리 | 빈 키 집합으로 readiness가 UP이 되거나, 갱신 시 기존 키를 빈 집합으로 덮어쓰지 않게 한다 |

## 기각한 대안
- **Spring Security OAuth2 Resource Server(`NimbusReactiveJwtDecoder`)**: JWKS 캐시, `kid` 미스 갱신, `iss`/`aud`, clock skew를 기본 제공한다. 운영 목적이라면 더 낮은 유지보수 비용이다. 다만 이 프로젝트는 JWKS 캐싱과 로테이션의 원리 학습이 목표라 직접 구현한다.
- **Istio `RequestAuthentication`으로 이전**: Blacklist(Redis)는 `ext_authz`가 필요하고 학습 목표와 충돌한다.

## 남은 결정
- `iss`/`aud`/`nbf`/`iat`: auth-service 클레임 설계(커리큘럼 M3)와 함께 정한다.
- 감사 로그의 클라이언트 IP: 메시 access log(Envoy)에 맡긴다. 앱에서는 `remoteAddress`를 기록하지 않는다.
