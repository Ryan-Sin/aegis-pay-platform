package io.aegis.platform.lock.exception

object LockErrorMessage {
    const val LOCK_KEY_EXPRESSION_INVALID = "분산 락 SpEL 키 파싱 결과가 비어있습니다. (expression=%s)"
    const val LOCK_ACQUISITION_FAILED = "현재 동일한 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요. (key=%s)"
    const val LOCK_INTERRUPTED = "분산 락 획득 대기 중 인터럽트가 발생했습니다. (key=%s)"
}