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
| 인증 예외 경로 | 설정(`permit-all-paths`)으로 관리, `/api/v1/auth/token`, `/.well-known/jwks.json`만 허용 | 하드코딩 이중 관리 제거, logout 등은 토큰 필요 |
| 응답·로그 | 응답은 고정 문구, 원인은 `detail`로 로그에만 (ADR-0001) | 검증 단계 정보 노출 방지 |

## JWKS 캐시 동작 (2026-10-25 보강)
api-gateway는 공개키를 **auth-service의 JWKS**에서 받는다. KMS와 직접 통신하지 않는다(서명키 관리는 ADR-0006).

| 시점 | 동작 | 설정 |
|---|---|---|
| 첫 요청 | 캐시가 비어 있으면 JWKS 호출 후 적재(지연 로딩) | — |
| 평소 | 캐시로 검증, 네트워크 호출 없음 | — |
| 갱신 주기 경과 | 요청은 기존 키로 처리하고 백그라운드 갱신. 실패 시 기존 키 유지 | `jwks-refresh-interval: 30m` |
| 만료 | 다음 요청에서 다시 적재 | `jwks-cache-ttl: 6h` |
| 모르는 `kid` | cooldown 안에서 1회 강제 갱신. 실패해도 401(503 아님) | `jwks-min-refresh-cooldown: 30s` |

키 교체 시: 새 `kid` 토큰이 처음 들어오면 강제 갱신으로 새 공개키를 받고, 이전 `kid` 토큰은 이전 공개키가 JWKS에 남아 있는 동안 계속 검증된다. auth-service 쪽 교체 절차는 ADR-0006 5절.

### 알려진 한계: 기동 직후 auth-service 장애 (STATUS K-12)
지연 로딩이라 gateway가 뜰 때 auth-service가 내려가 있으면 캐시가 비어 있어 "기존 키 유지" 방어가 작동하지 않는다. 복구될 때까지 모든 인증 요청이 503(`JWKS_FETCH_FAILED`)이 된다. 첫 요청도 JWKS 호출만큼 느려진다.
- 개선 방향(M3): 기동 시 JWKS를 미리 적재(warm-up)하고, 적재 전에는 readiness를 false로 둬서 트래픽을 받지 않는다. liveness는 영향받지 않게 분리한다.

## 기각한 대안
- **Spring Security OAuth2 Resource Server(`NimbusReactiveJwtDecoder`)**: JWKS 캐시, `kid` 미스 갱신, `iss`/`aud`, clock skew를 기본 제공한다. 운영 목적이라면 더 낮은 유지보수 비용이다. 다만 이 프로젝트는 JWKS 캐싱과 로테이션의 원리 학습이 목표라 직접 구현한다.
- **Istio `RequestAuthentication`으로 이전**: Blacklist(Redis)는 `ext_authz`가 필요하고 학습 목표와 충돌한다.

## 남은 결정
- `iss`/`aud`/`nbf`/`iat`: auth-service 클레임 설계(커리큘럼 M3)와 함께 정한다.
- 감사 로그의 클라이언트 IP: 메시 access log(Envoy)에 맡긴다. 앱에서는 `remoteAddress`를 기록하지 않는다.
