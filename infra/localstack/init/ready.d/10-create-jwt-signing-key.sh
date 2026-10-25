#!/bin/bash
# JWT 서명 키를 KMS에 만든다(ADR-0006). LocalStack 기동 시 ready 단계에서 한 번 실행된다.
# 앱은 키를 만들지 않고 alias만 설정으로 받는다. 비밀키는 KMS 밖으로 나오지 않는다.
set -euo pipefail

KEY_ID="00000000-0000-0000-0000-00000000a001"   # 로컬에서만 키 ID 고정(_custom_id_ 태그)
ALIAS="alias/aegis-jwt-signing"

if awslocal kms describe-key --key-id "$KEY_ID" >/dev/null 2>&1; then
  echo "JWT signing key already exists: $KEY_ID"
  exit 0
fi

awslocal kms create-key \
  --key-spec RSA_2048 \
  --key-usage SIGN_VERIFY \
  --description "Aegis JWT signing key (local)" \
  --tags TagKey=_custom_id_,TagValue="$KEY_ID" >/dev/null

awslocal kms create-alias --alias-name "$ALIAS" --target-key-id "$KEY_ID"

echo "Created JWT signing key: $KEY_ID ($ALIAS)"
