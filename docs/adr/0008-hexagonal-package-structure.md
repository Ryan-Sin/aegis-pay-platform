# ADR-0008: 서비스 내부 패키지는 헥사고날(포트/어댑터) 구조로 나눈다

- 상태: 채택 (2026-10-25)
- 범위: `services/*` (api-gateway, auth-service에 적용 완료. 이후 서비스도 같은 규칙). `libs/`는 대상이 아니다

## 배경
- auth-service의 `signing` 패키지 하나에 컨트롤러, KMS 구현체, 설정, 프로퍼티가 섞여 있었다.
- api-gateway는 `config / exception / filter / security / tracing`처럼 기술 기준으로 나뉘어 있어 서비스마다 기준이 달랐다.
- 서비스가 늘기 전에 한 가지 규칙을 정하고 테스트로 강제한다.

## 결정

### 1. 패키지 구조
```
io.aegis.services.<service>
├── <Service>Application.kt
├── config/                     프레임워크 연결(Clock, 공통 프로퍼티). 계층 밖
├── domain/                     순수 모델과 규칙. Spring·JPA·AWS SDK 의존 금지
├── application/
│   ├── port/in/                유스케이스 인터페이스 (*UseCase)
│   ├── port/out/               외부 의존 인터페이스 (*Port)
│   ├── service/                유스케이스 구현 (*Service), 도메인 규칙 조합
│   └── exception/              서비스 ErrorCode/ErrorMessage (ADR-0001)
└── adapter/
    ├── in/<채널>/              web(Controller, GlobalFilter), tracing 등 들어오는 쪽
    └── out/<기술>/             persistence, kms, jwks, redis 등 나가는 쪽 (*Port 구현)
```

### 2. 의존 방향
`adapter → application → domain`. 반대 방향과 `adapter.in → adapter.out` 직접 호출은 금지한다.
각 서비스의 `HexagonalArchitectureTest`(ArchUnit)가 다음을 검사한다.
- 계층 방향
- domain에 Spring·JPA·AWS SDK 의존 없음
- application에 JPA·Spring Data·AWS SDK·Gateway·WebFlux·HTTP 클라이언트·캐시 구현 의존 없음
- adapter.in이 adapter.out을 직접 참조하지 않음

### 3. 실용적으로 허용하는 것
| 허용 | 이유 |
|---|---|
| application의 `@Service`, ErrorCode의 `HttpStatus`(Spring) | 빈 등록과 플랫폼 공통 예외(`platform-core`)를 따로 감싸면 코드만 늘고 얻는 것이 없다 |
| 포트 시그니처에 nimbus-jose-jwt 타입(`JWTClaimsSet`, `SignedJWT`, `JWKSet`) | JOSE 표준 모델 라이브러리라서 인프라 기술이 아니다. 자체 타입으로 감싸면 변환 코드만 늘어난다 |
| 기술 설정(`KmsConfiguration`, `*Properties`)을 해당 adapter 패키지에 둠 | 그 어댑터만 쓰는 설정이라 같이 있어야 찾기 쉽다. 여러 계층이 쓰는 설정만 `config/`에 둔다 |
| 도메인 모델과 JPA 엔티티 분리(`Terminal` / `TerminalJpaEntity`) | 도메인이 JPA를 모르게 한다. 대신 매핑 코드(`toDomain`/`from`)가 생긴다 |

### 4. 이름
- 포트: `*UseCase`(in), `*Port`(out)
- 어댑터: 구현 기술이 드러나는 이름(`KmsJwtSigner`, `JwksKeyProvider`, `RedisTokenBlacklistAdapter`, `TerminalPersistenceAdapter`)

## 기각한 대안
- **기능 우선 + 내부 계층**: 기능 경계는 잘 보이지만 기술 의존을 막는 규칙이 약하다. KMS·Redis·JWKS처럼 외부 의존이 핵심인 서비스에서는 포트 경계가 더 중요하다고 봤다.
- **계층 우선(controller/service/repository)**: 단순하지만 기능이 늘면 경계가 흐려지고 도메인이 JPA에 묶인다.

## 결과
- 외부 기술 교체(KMS → Vault, Redis → 다른 저장소)가 adapter 추가로 끝난다.
- 단점: 파일과 매핑 코드가 늘어난다. 작은 기능도 `UseCase`·`Service`·`Port`를 거친다(예: JWKS 조회).
- 적용 시 변경(2026-10-25):
  - api-gateway: `TokenBlacklistChecker`를 `TokenBlacklistPort` + `RedisTokenBlacklistAdapter`(조회만)와 `TokenAuthenticationService`(폐기 판단)로 나눴다. `JwtTokenVerifier`는 `LoadJwtPublicKeyPort`에 의존하고 `JwksKeyProvider`가 이를 구현한다. 동작은 바뀌지 않았다(기존 테스트 전부 통과).
  - auth-service: `Terminal`을 순수 도메인 모델로 바꾸고 `TerminalJpaEntity` + `TerminalPersistenceAdapter`를 추가했다.
