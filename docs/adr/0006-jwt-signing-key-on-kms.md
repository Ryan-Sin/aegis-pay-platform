# ADR-0006: JWT 서명 비밀키는 KMS에 두고 서명만 위임한다 (로컬은 LocalStack)

- 상태: 채택 (2026-10-25), **구현 전**(커리큘럼 M3)
- 범위: `services:auth-service`, `docker-compose.yml`, api-gateway(검증 측은 변경 없음)

## 배경

### 시스템에 있는 키쌍은 두 종류다
| | ① 단말 키쌍 | ② JWT 서명 키쌍 (이 ADR의 대상) |
|---|---|---|
| 용도 | 단말이 "나는 진짜 TERM-001이다"를 증명 | auth-service가 발급한 JWT가 위조되지 않았음을 보장 |
| 비밀키 위치 | 단말 기기 안(Secure Element). 기기 밖으로 나오지 않는다 | **KMS**(이 ADR의 결정) |
| 공개키 위치 | auth-service DB(단말 등록 시 저장) | JWKS(`/.well-known/jwks.json`)로 게시, api-gateway가 캐시 |
| 서명 / 검증 | 단말이 서명 / auth-service가 검증 | auth-service가(KMS를 통해) 서명 / api-gateway가 검증 |
| 개수 | 단말마다 1쌍 | 플랫폼 전체 1쌍(교체 기간에만 2쌍) |

- 같은 주체의 비밀키로 서명하고 그 공개키로 검증한다. 모두 **서명**이지 암호화가 아니다. JWT payload는 누구나 읽을 수 있으므로 비밀 정보를 넣지 않는다.
- ①단말 키를 KMS에 두지 않는 이유: 단말 비밀키는 기기 안에 있어야 "이 기기가 진짜"라는 증명이 성립한다. 수만 대·오프라인 환경에서 서명마다 KMS를 호출할 수도 없다.

### ②가 유출되면 플랫폼 전체가 뚫린다
①이 유출되면 단말 하나가 사칭된다. ②가 유출되면 **아무 단말·매장의 JWT나 만들 수 있고** api-gateway는 서명이 맞으므로 통과시킨다. 그래서 ②는 애플리케이션 메모리에 두지 않는다.

## 결정

### 1. 비밀키는 KMS 안에서 생성되고 밖으로 나오지 않는다
| 주체 | 가지는 것 | 가지지 않는 것 |
|---|---|---|
| KMS | 비밀키, 공개키 | |
| auth-service | 키 alias, KMS `Sign`/`GetPublicKey` 호출 권한 | 비밀키 |
| api-gateway | 공개키(JWKS 캐시) | 비밀키, KMS 접근 권한 |

```
          KMS ── 비밀키(반출 불가) ──┐
           ▲ Sign(digest)           │ 서명값 / GetPublicKey
           │                        ▼
      auth-service (alias만 앎) ── JWKS 게시 ──▶ api-gateway (공개키 캐시로 모든 요청 검증)
```

### 2. 환경별 KMS
- 운영: AWS KMS. 로컬(Tier 1): **LocalStack KMS**. 같은 AWS SDK 코드에서 endpoint 설정만 다르다.
- 서명 코드는 `JwtSigner` 인터페이스로 분리해서 테스트용 in-memory 구현을 둔다.

### 3. 최초 키 생성은 인프라 단계에서 한 번 한다 (앱은 키를 만들지 않는다)
앱은 alias만 설정으로 받는다. 키가 없으면 기동에 실패한다(조용히 로컬 키를 만들지 않는다).

로컬(LocalStack 초기화 스크립트, 컨테이너 기동 시 실행):
```bash
# docker/localstack/init/ready.d/10-create-jwt-signing-key.sh (예정)
awslocal kms create-key \
  --key-spec RSA_2048 \
  --key-usage SIGN_VERIFY \
  --tags TagKey=_custom_id_,TagValue=00000000-0000-0000-0000-00000000a001   # 로컬에서 키 ID 고정
awslocal kms create-alias \
  --alias-name alias/aegis-jwt-signing \
  --target-key-id 00000000-0000-0000-0000-00000000a001
```

운영(IaC 예시, Terraform):
```hcl
resource "aws_kms_key" "jwt_signing" {
  customer_master_key_spec = "RSA_2048"
  key_usage                = "SIGN_VERIFY"
}
resource "aws_kms_alias" "jwt_signing" {
  name          = "alias/aegis-jwt-signing"
  target_key_id = aws_kms_key.jwt_signing.key_id
}
```

### 4. 서명 방식
- JWS `RS256` ↔ KMS `SigningAlgorithm = RSASSA_PKCS1_V1_5_SHA_256`.
- 서명 입력(`base64url(header).base64url(payload)`)의 SHA-256을 앱에서 계산하고 `MessageType = DIGEST`로 보낸다. 원문을 KMS로 보내지 않는다.
- `kid`는 공개키의 **JWK thumbprint(RFC 7638)**로 한다. KMS 키 ID·ARN은 JWT 헤더에 노출하지 않는다(인프라 식별자 비노출, AGENTS.md 6장).

### 5. 키 교체(rotation)는 "새 키 생성 + alias 전환"으로 한다
AWS KMS 비대칭키는 자동 교체와 on-demand 교체를 지원하지 않는다. 새 키를 만들고 전환하는 수동 교체만 가능하다.

```
1. 새 KMS 키 생성 → alias/aegis-jwt-signing-next
2. auth-service JWKS에 [현재 키 + 다음 키] 게시 (api-gateway가 새 공개키를 미리 받도록 refresh 주기 이상 대기)
3. 서명 alias를 다음 키로 전환 → 이후 발급 JWT는 새 kid
4. 이전 키로 발급된 JWT가 모두 만료될 때까지(최대 12시간 + leeway) 이전 공개키를 JWKS에 유지
5. 이전 키를 JWKS에서 제거하고 KMS 키 비활성화 → 대기 기간 후 삭제 예약
```
api-gateway는 모르는 `kid`를 받으면 cooldown 안에서 1회 강제 갱신하므로(ADR-0003) 2단계를 건너뛰어도 실패하지는 않는다. 2단계는 그 강제 갱신조차 일어나지 않게 하기 위한 것이다.

### 6. 소스 코드 구조 (예정)
```kotlin
interface JwtSigner {
    fun sign(signingInput: ByteArray): JwsSignature   // 서명값 + kid
    fun publicKeys(): List<RSAKey>                     // JWKS 게시용: 서명 키 + 검증 유지 키
}

class KmsJwtSigner(kmsClient, properties) : JwtSigner   // AWS SDK KMS. Sign(DIGEST), GetPublicKey(결과 캐시)
class InMemoryJwtSigner : JwtSigner                    // 단위 테스트용
```
```yaml
aegis:
  auth:
    signing:
      signing-key: alias/aegis-jwt-signing          # 서명에 쓰는 키
      verification-keys:                            # JWKS에 함께 게시할 키(교체 기간)
        - alias/aegis-jwt-signing
aws:   # 로컬에서만 endpoint를 LocalStack으로
  kms.endpoint: ${KMS_ENDPOINT:http://localhost:4566}
```
- `GetPublicKey` 결과는 auth-service가 캐시한다. JWKS 요청마다 KMS를 호출하지 않는다.
- 토큰 발급(12시간에 1회/단말)마다 KMS `Sign`을 1회 호출한다. 지연과 KMS 요청 한도는 M3에서 측정한다.

## 기각한 대안
- **비밀키를 파일·환경변수로 앱에 로드**: 가장 단순하지만 앱 서버가 침해되면 비밀키가 유출되고 위조가 영구화된다.
- **Secrets Manager/Vault KV에 보관하고 앱이 꺼내서 서명**: 보관은 중앙화되지만 결국 비밀키가 앱 메모리에 올라온다.
- **Vault Transit**: KMS와 같은 위임 모델이고 키 버전 교체가 쉽다. 목표 운영 환경을 AWS로 맞추기 위해 AWS KMS API를 택했다. `JwtSigner` 구현을 추가하면 전환할 수 있다.

## 결과
- auth-service가 침해되어도 비밀키는 유출되지 않는다. 침해 기간 동안의 서명 API 오용은 권한 회수로 끊고 KMS 감사 로그로 추적한다.
- 로컬 개발에 LocalStack auth token이 필요하다. LocalStack 2026.03.0(2026-03-23)부터 컨테이너 기동에 `LOCALSTACK_AUTH_TOKEN`이 필요하며, KMS는 무료 Hobby 플랜(비상업용)에 포함된다. 이미지 태그는 `latest`가 아닌 고정 버전을 쓴다.
- LocalStack은 에뮬레이터라 IAM 권한·실제 HSM 보호는 검증할 수 없다. 해당 동작은 운영 환경에서만 확인된다.
- 확인 필요(구현 시): LocalStack 초기화 스크립트 경로·실행 시점, LocalStack KMS의 `Sign`(`DIGEST`)·`GetPublicKey` 응답 형식이 AWS와 같은지, alias가 첫 접근 이후에만 보이는 제약의 영향.

## 참고
- LocalStack KMS 지원 범위: https://docs.localstack.cloud/aws/services/kms/
- LocalStack auth token 정책: https://blog.localstack.cloud/localstack-single-image-next-steps/
- AWS KMS 키 교체: https://docs.aws.amazon.com/kms/latest/developerguide/rotate-keys.html
