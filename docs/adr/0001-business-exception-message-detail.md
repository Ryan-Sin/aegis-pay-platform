# ADR-0001: BusinessException의 외부 노출 문구와 내부 로깅 원인 분리

- 상태: 채택 (2026-10-21)
- 범위: `libs:platform-core`, mvc/webflux 전역 예외 처리기, 모든 서비스

## 배경
api-gateway 인증 실패 응답에 `reason=JWT 서명 불일치`, `jti=...` 같은 내부 원인이 노출됐다. 공격자에게 어느 검증 단계에서 막혔는지 알려 주는 오라클이 된다. 그런데 원인을 응답에서 지우자 운영 로그에서도 사라졌다. `BusinessException.message`는 그대로 클라이언트 응답이 되므로 내부 원인을 담을 자리가 없었다.

## 결정
`BusinessException`에 로그 전용 `detail`을 추가한다.

```kotlin
open class BusinessException(
    val errorCode: ErrorCode,
    vararg args: Any?,
    val detail: String? = null,          // 로그 전용, 응답 body에 포함하지 않음
    override val cause: Throwable? = null
) : RuntimeException(null, cause)
```

- 전역 예외 처리기가 `code`, `uri`, `message`, `detail`을 **한 곳에서 한 줄로** 기록한다. 던지는 지점에서는 로그를 남기지 않는다.
- `detail`은 vararg 뒤에 있어 named argument로만 넘길 수 있다. 포맷 인자와 섞이지 않는다.
- 테스트: core(`detail`이 `message`에 섞이지 않음), mvc 처리기(`detail`이 응답 body에 없음).

## 기각한 대안
- **서비스별 예외 서브클래스**(`AuthenticationFailedException(reason)`): 같은 요구가 모든 서비스에 있으므로 서비스마다 서브클래스가 생기고 공통 예외 체계가 갈라진다.
- **던지는 지점에서만 로그**: 요청 정보(path 등)가 없고 로그 위치가 흩어진다.

## 결과
- 모든 서비스가 같은 방식으로 응답 문구와 운영 원인을 분리한다.
- **바이너리 비호환 변경**이다. 생성자 JVM 시그니처가 `(ErrorCode, Object[], Throwable)`에서 `(ErrorCode, Object[], String, Throwable)`로 바뀌었다. monorepo에서는 문제없지만 artifact로 배포하면 기존 사용처가 `NoSuchMethodError`로 깨진다. 라이브러리 배포 단계(커리큘럼 M4)에서 호환성 검증과 deprecation 경로를 도입한다.
