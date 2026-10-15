package io.aegis.platform.lock.exception

import io.aegis.platform.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class LockErrorCode(
    override val status: HttpStatus,
    override val message: String,
): ErrorCode {
    LOCK_KEY_EXPRESSION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, LockErrorMessage.LOCK_KEY_EXPRESSION_INVALID),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, LockErrorMessage.LOCK_ACQUISITION_FAILED),
    LOCK_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, LockErrorMessage.LOCK_INTERRUPTED),
    ;

    override val code: String
        get() = name
}