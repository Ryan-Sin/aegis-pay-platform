# ADR-0010: 단말 인증은 서버 challenge(1회용 nonce)에 단말 서명을 받아 확인한다

- 상태: 채택 (2026-10-25)
- 범위: `services:auth-service`(`/api/v1/auth/challenge`, `/api/v1/auth/token`), `services:api-gateway`(permit-all, `iss`/`aud` 검증)
- 관련: ADR-0006(JWT 서명 키), ADR-0003(gateway 검증), ADR-0009(응답 형식)

## 배경
단말은 기기 안 비밀키(Secure Element)로 "내가 등록된 그 단말"임을 증명해야 한다. 같은 증명 값을 다시 보내는 재전송 공격을 막아야 한다.

## 결정

### 1. 2단계 흐름
```
① POST /api/v1/auth/challenge  {"terminalId"}
   → 서버가 32바이트 난수 nonce(base64url)를 만들어 Redis에 60초 저장(키 auth:challenge:{nonce}, 값 terminalId)
② 단말이 아래 메시지를 기기 비밀키로 서명
   "aegis-auth-v1\n{terminalId}\n{nonce}"  (UTF-8)
③ POST /api/v1/auth/token  {"terminalId", "nonce", "signature"}
   → nonce를 꺼내면서 삭제(GETDEL) → 단말 조회·상태 확인 → DB 공개키로 서명 검증 → KMS로 JWT 서명
```
- 서명 방식: ECDSA P-256 + SHA-256(`SHA256withECDSA`), 서명 값은 **DER 형식을 base64**로 보낸다. openssl(`openssl dgst -sha256 -sign`) 결과와 같은 형식이다(2026-10-25 실제 실행으로 확인).
- 메시지 맨 앞의 `aegis-auth-v1`은 이 서명이 다른 용도로 재사용되지 않게 하고, 형식을 바꿀 때 버전을 올리기 위한 것이다.

### 2. 재전송 방지
- nonce는 **꺼내는 순간 삭제**된다. 같은 요청을 다시 보내면 `401 INVALID_CHALLENGE`.
- 서명 검증보다 **먼저** nonce를 소모한다. 서명이 틀려도 nonce는 사라지므로 같은 nonce로 서명을 반복 시도할 수 없다.
- 다른 단말에게 발급된 nonce는 쓸 수 없다(저장된 terminalId와 비교).
- 동시에 20번 꺼내도 성공은 1번이다(Redis GETDEL 원자성, `RedisChallengeStoreTest`).

### 3. 단말 존재 여부를 드러내지 않는다
- 미등록·정지·폐기 단말과 서명 불일치는 모두 같은 `401 TERMINAL_AUTHENTICATION_FAILED`다. 구체적인 사유는 `detail`(로그)에만 남긴다(ADR-0001).
- challenge 발급 시에는 단말을 조회하지 않는다. 조회하면 응답 차이로 단말 ID를 알아낼 수 있다. 대신 아무 ID로나 nonce를 받을 수 있으므로 **요청량 제한은 gateway 과제로 남긴다**.

### 4. 토큰 클레임 (gateway와 맞춘 값)
| 클레임 | 값 | gateway 검증 |
|---|---|---|
| `iss` | `aegis-auth-service` (`JWT_ISSUER`) | 일치해야 함 |
| `aud` | `aegis-api` (`JWT_AUDIENCE`) | 포함해야 함 |
| `sub` | terminalId | 필수 |
| `storeId` | 단말의 store | 필수 |
| `scopes` | 정렬된 문자열 배열 | 선택 |
| `jti` | UUID | 필수(블랙리스트 키) |
| `iat`, `exp` | 발급 시각(초 단위), +12시간 | `exp` 필수, 30초 여유 |

- 두 서비스가 같은 환경변수(`JWT_ISSUER`, `JWT_AUDIENCE`)를 기본값과 함께 읽는다.
- 발급 시각은 초 단위로 자른다. JWT 시각은 초 단위라서, 자르지 않으면 응답의 `expiresAt`이 실제 `exp`보다 1초 미만 늦어진다(테스트에서 발견).

### 5. 응답
- 성공은 `SuccessResponse`로 감싼다(ADR-0009). 토큰 응답: `accessToken`, `tokenType=Bearer`, `expiresIn`(초), `expiresAt`.
- Redis 장애 시 `503 CHALLENGE_STORE_UNAVAILABLE`(fail-closed). nonce 확인 없이 발급하지 않는다.

## 기각한 대안
- **1단계(단말이 시각+난수를 직접 만들어 서명)**: 왕복이 한 번 줄지만 단말 시계가 맞아야 하고, 서버가 사용한 난수를 시간 창 동안 모두 기억해야 한다. 오프라인 환경의 POS 단말은 시계가 틀어지기 쉽다.
- **nonce를 MySQL에 저장**: 정확하지만 인증 요청마다 쓰기·삭제가 생기고 만료 정리가 필요하다. TTL과 원자적 GETDEL이 있는 Redis가 맞다.

## 결과
- 확인(2026-10-25, 실제 실행): openssl로 만든 단말 키·서명으로 gateway를 거쳐 challenge → token 발급 성공, 같은 요청 재전송 401, 발급 토큰으로 보호 경로 통과, 서명·payload 변조 401, 외부 JWKS 404.
- 참고: base64url 서명의 **마지막 글자**는 쓰지 않는 비트를 포함할 수 있어서, 그 비트만 바꾸면 문자열은 달라도 서명 바이트가 같아 검증을 통과한다. 서명 내용이 바뀌지 않으므로 보안 문제는 아니고, 블랙리스트는 토큰 문자열이 아니라 `jti`로 판단한다.
- 남은 과제: challenge 요청량 제한(gateway), 단말 등록 API(현재는 SQL로 직접 등록), terminal-simulator.
