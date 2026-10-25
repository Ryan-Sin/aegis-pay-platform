package io.aegis.platform.core.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class SuccessResponse<T>(
    val timestamp: Instant = Instant.now(),
    val code: String,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val data: T? = null,
) {
    companion object {
        const val SUCCESS_CODE = "SUCCESS"

        fun <T> of(data: T): SuccessResponse<T> = SuccessResponse(
            code = SUCCESS_CODE,
            data = data,
        )

        fun empty(): SuccessResponse<Nothing> = SuccessResponse(code = SUCCESS_CODE)
    }
}
