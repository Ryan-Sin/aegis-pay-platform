package io.aegis.platform.httpclient.support

import com.fasterxml.jackson.databind.ObjectMapper
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.response.ErrorResponse
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException

internal class HttpClientErrorTranslator(
    private val objectMapper: ObjectMapper,
) {

    fun translateHttpStatus(
        targetName: String,
        statusCode: Int,
        responseBody: String?,
    ): BusinessException {
        val parsed = parseErrorResponse(responseBody)
        val remoteCode = parsed?.code ?: "UNKNOWN"
        val remoteMessage = parsed?.message
            ?: responseBody?.take(MAX_RAW_BODY_LENGTH)?.ifBlank { null}
            ?: "Empty error response"

        val errorCode = if(statusCode in 400..499) {
            HttpClientErrorCode.EXTERNAL_API_CLIENT_ERROR
        } else {
            HttpClientErrorCode.EXTERNAL_API_SERVER_ERROR
        }

        return BusinessException(
            errorCode,
            targetName,
            statusCode,
            remoteCode,
            remoteMessage,
        )
    }

    fun translateException(
        targetName: String,
        uri: String,
        throwable: Throwable,
    ): BusinessException {
        return when {
            throwable is BusinessException -> throwable
            throwable is CallNotPermittedException -> BusinessException(
                HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN,
                targetName,
            )
            isTimeoutException(throwable) -> BusinessException(
                HttpClientErrorCode.EXTERNAL_API_TIMEOUT,
                targetName,
                uri,
            )
            else -> BusinessException(
                HttpClientErrorCode.EXTERNAL_API_SERVER_ERROR,
                targetName,
                502,
                throwable.javaClass.simpleName,
                throwable.message ?: "Unexpected I/O error",
            )
        }
    }

    private fun parseErrorResponse(responseBody: String?): ErrorResponse? {
        if(responseBody.isNullOrBlank()) {
            return null
        }

        return runCatching {
            objectMapper.readValue(responseBody, ErrorResponse::class.java)
        }.getOrNull()
    }

    private fun isTimeoutException(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause}.any {
            it is SocketTimeoutException ||
                    it is HttpTimeoutException ||
                    it is TimeoutException ||
                    it.javaClass.simpleName.contains("TimeoutException", ignoreCase = true)
        }
    }

    companion object {
        private const val MAX_RAW_BODY_LENGTH = 200
    }
}